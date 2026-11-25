import {
  CAMPUS_RU_BY_ID,
  DIA_SEMANA_RU_LIST,
  TIPO_REFEICAO_BY_ID,
  type CampusRu,
  type DiaSemanaRu,
  type DiaSemanaSigaa,
  type RuNotificacaoConfig,
  type TurmaResumo,
} from '../model/models';
import { addDaysIso, getBrasiliaParts, parseIsoDate } from './brasiliaTime';
import { mergeContiguousClassBlocks } from './sigaaNotificationPlanner';

const SIGAA_TO_ICS_BYDAY: Readonly<Record<DiaSemanaSigaa, { byDay: string; isoDow: number }>> = {
  SEGUNDA: { byDay: 'MO', isoDow: 1 },
  TERCA: { byDay: 'TU', isoDow: 2 },
  QUARTA: { byDay: 'WE', isoDow: 3 },
  QUINTA: { byDay: 'TH', isoDow: 4 },
  SEXTA: { byDay: 'FR', isoDow: 5 },
  SABADO: { byDay: 'SA', isoDow: 6 },
};

const RU_TO_ICS_BYDAY: Readonly<Record<DiaSemanaRu, { byDay: string; isoDow: number }>> = {
  SEGUNDA: { byDay: 'MO', isoDow: 1 },
  TERCA: { byDay: 'TU', isoDow: 2 },
  QUARTA: { byDay: 'WE', isoDow: 3 },
  QUINTA: { byDay: 'TH', isoDow: 4 },
  SEXTA: { byDay: 'FR', isoDow: 5 },
  SABADO: { byDay: 'SA', isoDow: 6 },
  DOMINGO: { byDay: 'SU', isoDow: 7 },
};

function escapeIcsText(raw: string): string {
  return raw
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r?\n/g, '\\n');
}

function formatIcsLocalDateTime(isoDate: string, hour: number, minute: number): string {
  const { year, month, day } = parseIsoDate(isoDate);
  const yyyy = String(year).padStart(4, '0');
  const mm = String(month).padStart(2, '0');
  const dd = String(day).padStart(2, '0');
  const hh = String(hour).padStart(2, '0');
  const mi = String(minute).padStart(2, '0');
  return `${yyyy}${mm}${dd}T${hh}${mi}00`;
}

function formatUtcStamp(epochMillis: number): string {
  const d = new Date(epochMillis);
  const pad = (n: number, len = 2) => String(n).padStart(len, '0');
  return `${pad(d.getUTCFullYear(), 4)}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}T${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())}Z`;
}

function nextOccurrenceIso(fromIso: string, targetIsoDow: number): string {
  for (let offset = 0; offset < 7; offset += 1) {
    const candidate = addDaysIso(fromIso, offset);
    if (parseIsoDate(candidate).dayOfWeekIso === targetIsoDow) {
      return candidate;
    }
  }
  return fromIso;
}

/**
 * Exporta grade semanal de aulas e/ou lembretes do RU em formato iCalendar (.ics)
 * compativel com o Calendario nativo do iOS (Apple Calendar) com alarmes exatos (VALARM).
 */
export const IcsCalendarExporter = {
  buildClassesScheduleIcs(
    turmas: readonly TurmaResumo[],
    leadMinutes: number = 30,
    nowEpochMillis: number = Date.now(),
  ): string {
    const todayIso = getBrasiliaParts(nowEpochMillis).isoDate;
    const dtstamp = formatUtcStamp(nowEpochMillis);
    const lines: string[] = [
      'BEGIN:VCALENDAR',
      'VERSION:2.0',
      'PRODID:-//UnB Pocket//Grade Semanal//PT-BR',
      'CALSCALE:GREGORIAN',
      'X-WR-CALNAME:Aulas UnB Pocket',
      'X-WR-TIMEZONE:America/Sao_Paulo',
    ];

    for (const turma of turmas) {
      const byDayMap = new Map<DiaSemanaSigaa, typeof turma.horarioSlots[number][]>();
      for (const slot of turma.horarioSlots) {
        const list = byDayMap.get(slot.diaSemana) ?? [];
        list.push(slot);
        byDayMap.set(slot.diaSemana, list);
      }

      for (const [dia, slots] of byDayMap.entries()) {
        const dayMeta = SIGAA_TO_ICS_BYDAY[dia];
        const firstDateIso = nextOccurrenceIso(todayIso, dayMeta.isoDow);
        const blocks = mergeContiguousClassBlocks(slots);

        for (const block of blocks) {
          const dtStart = formatIcsLocalDateTime(firstDateIso, block.start.hour, block.start.minute);
          const dtEnd = formatIcsLocalDateTime(firstDateIso, block.end.hour, block.end.minute);
          const uid = `aula-${turma.idTurma || turma.frontEndIdTurma}-${dia}-${block.start.hour}${block.start.minute}@unbpocket.local`;
          const summary = turma.codigoDisciplina
            ? `${turma.codigoDisciplina} · ${turma.nomeDisciplina}`
            : turma.nomeDisciplina;
          const descParts = [
            turma.docenteNome ? `Docente: ${turma.docenteNome}` : null,
            turma.codigoHorarioRaw ? `Horário SIGAA: ${turma.codigoHorarioRaw}` : null,
          ].filter(Boolean);

          lines.push('BEGIN:VEVENT');
          lines.push(`UID:${uid}`);
          lines.push(`DTSTAMP:${dtstamp}`);
          lines.push(`DTSTART;TZID=America/Sao_Paulo:${dtStart}`);
          lines.push(`DTEND;TZID=America/Sao_Paulo:${dtEnd}`);
          lines.push(`RRULE:FREQ=WEEKLY;COUNT=18;BYDAY=${dayMeta.byDay}`);
          lines.push(`SUMMARY:${escapeIcsText(summary)}`);
          if (turma.local.trim()) {
            lines.push(`LOCATION:${escapeIcsText(turma.local.trim())}`);
          }
          if (descParts.length > 0) {
            lines.push(`DESCRIPTION:${escapeIcsText(descParts.join(' | '))}`);
          }
          lines.push('BEGIN:VALARM');
          lines.push(`TRIGGER:-PT${Math.max(5, leadMinutes)}M`);
          lines.push('ACTION:DISPLAY');
          lines.push(`DESCRIPTION:${escapeIcsText(`Aula em ${leadMinutes} min: ${turma.nomeDisciplina}`)}`);
          lines.push('END:VALARM');
          lines.push('END:VEVENT');
        }
      }
    }

    lines.push('END:VCALENDAR');
    return `${lines.join('\r\n')}\r\n`;
  },

  buildRuMealsScheduleIcs(
    config: RuNotificacaoConfig,
    campus: CampusRu,
    nowEpochMillis: number = Date.now(),
  ): string {
    const todayIso = getBrasiliaParts(nowEpochMillis).isoDate;
    const dtstamp = formatUtcStamp(nowEpochMillis);
    const campusName = CAMPUS_RU_BY_ID[campus].displayName;
    const lines: string[] = [
      'BEGIN:VCALENDAR',
      'VERSION:2.0',
      'PRODID:-//UnB Pocket//Lembretes RU//PT-BR',
      'CALSCALE:GREGORIAN',
      `X-WR-CALNAME:RU ${campusName} · UnB Pocket`,
      'X-WR-TIMEZONE:America/Sao_Paulo',
    ];

    const mealTimes = {
      CAFE_DA_MANHA: { startH: 7, startM: 0, endH: 8, endM: 0 },
      ALMOCO: { startH: 11, startM: 0, endH: 12, endM: 0 },
      JANTAR: { startH: 17, startM: 0, endH: 18, endM: 0 },
    } as const;

    for (const dayEntry of DIA_SEMANA_RU_LIST) {
      const meals = config.refeicoesPorDia[dayEntry.id] ?? [];
      if (meals.length === 0) continue;
      const dayMeta = RU_TO_ICS_BYDAY[dayEntry.id];
      const firstDateIso = nextOccurrenceIso(todayIso, dayMeta.isoDow);

      for (const meal of meals) {
        const tm = mealTimes[meal];
        const mealLabel = TIPO_REFEICAO_BY_ID[meal].displayName;
        const dtStart = formatIcsLocalDateTime(firstDateIso, tm.startH, tm.startM);
        const dtEnd = formatIcsLocalDateTime(firstDateIso, tm.endH, tm.endM);
        const uid = `ru-${campus}-${dayEntry.id}-${meal}@unbpocket.local`;

        lines.push('BEGIN:VEVENT');
        lines.push(`UID:${uid}`);
        lines.push(`DTSTAMP:${dtstamp}`);
        lines.push(`DTSTART;TZID=America/Sao_Paulo:${dtStart}`);
        lines.push(`DTEND;TZID=America/Sao_Paulo:${dtEnd}`);
        lines.push(`RRULE:FREQ=WEEKLY;COUNT=16;BYDAY=${dayMeta.byDay}`);
        lines.push(`SUMMARY:${escapeIcsText(`${mealLabel} · RU ${campusName}`)}`);
        lines.push(`LOCATION:${escapeIcsText(`Restaurante Universitário UnB - ${campusName}`)}`);
        lines.push('BEGIN:VALARM');
        lines.push('TRIGGER:-PT60M');
        lines.push('ACTION:DISPLAY');
        lines.push(`DESCRIPTION:${escapeIcsText(`Confira o cardápio de ${mealLabel.toLowerCase()} no UnB Pocket`)}`);
        lines.push('END:VALARM');
        lines.push('END:VEVENT');
      }
    }

    lines.push('END:VCALENDAR');
    return `${lines.join('\r\n')}\r\n`;
  },
};
