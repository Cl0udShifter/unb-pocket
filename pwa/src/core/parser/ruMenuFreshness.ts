import {
  CAMPUS_RU_LIST,
  type CampusRu,
  type CardapioDiaRu,
  type CardapioSemanalRu,
  type DiaSemanaSigaa,
  type RuSelecaoInicial,
  type TipoRefeicao,
} from '../model/models';
import {
  daysBetweenIso,
  formatIsoDate,
  getBrasiliaParts,
  isValidCalendarDate,
  parseIsoDate,
} from './brasiliaTime';
import { CasAndJsfParser } from './casAndJsfParser';

const FULL_DATE_REGEX = /(\d{2})\/(\d{2})\/(\d{4})/g;
const SHORT_DATE_REGEX = /(\d{2})\/(\d{2})(?!\/\d)/g;

const BREAKFAST_END_MINUTES = 9 * 60 + 30;
const LUNCH_END_MINUTES = 14 * 60 + 30;

export const RuMenuFreshness = {
  NORMAL_COOLDOWN_MILLIS: 6 * 60 * 60 * 1000,
  EXPIRED_RETRY_COOLDOWN_MILLIS: 30 * 60 * 1000,

  localDateInBrasilia(epochMillis: number): string {
    return getBrasiliaParts(epochMillis).isoDate;
  },

  resolveMenuEndDate(cardapio: CardapioSemanalRu, referenceIsoDate: string): string | null {
    const labelDates = this.extractDatesFromText(cardapio.semanaLabel, referenceIsoDate);
    const dayDates = cardapio.dias.flatMap((d) =>
      this.extractDatesFromText(d.dataTexto, referenceIsoDate),
    );
    const all = [...labelDates, ...dayDates].sort();
    return all.length > 0 ? all[all.length - 1] : null;
  },

  isMenuWeekExpired(cardapio: CardapioSemanalRu, nowEpochMillis: number): boolean {
    const today = this.localDateInBrasilia(nowEpochMillis);
    const endDate = this.resolveMenuEndDate(cardapio, today);
    if (!endDate) return false;
    return today > endDate;
  },

  shouldCheckRemote(
    cachedByCampus: Partial<Record<CampusRu, CardapioSemanalRu | null | undefined>>,
    lastCheckEpochMillis: number | null,
    nowEpochMillis: number,
    forceCheck: boolean,
  ): boolean {
    if (forceCheck || lastCheckEpochMillis === null) return true;

    const anyMissingOrExpired = CAMPUS_RU_LIST.some((c) => {
      const menu = cachedByCampus[c.id];
      return !menu || this.isMenuWeekExpired(menu, nowEpochMillis);
    });

    const elapsed = Math.max(0, nowEpochMillis - lastCheckEpochMillis);
    const requiredCooldown = anyMissingOrExpired
      ? this.EXPIRED_RETRY_COOLDOWN_MILLIS
      : this.NORMAL_COOLDOWN_MILLIS;
    return elapsed >= requiredCooldown;
  },

  extractDatesFromText(text: string, referenceIsoDate: string): string[] {
    const fullDates: string[] = [];
    FULL_DATE_REGEX.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = FULL_DATE_REGEX.exec(text)) !== null) {
      const day = Number.parseInt(m[1], 10);
      const month = Number.parseInt(m[2], 10);
      const year = Number.parseInt(m[3], 10);
      if (isValidCalendarDate(year, month, day)) {
        fullDates.push(formatIsoDate(year, month, day));
      }
    }
    if (fullDates.length > 0) return fullDates;

    const shortDates: string[] = [];
    const { year: refYear } = parseIsoDate(referenceIsoDate);
    SHORT_DATE_REGEX.lastIndex = 0;
    while ((m = SHORT_DATE_REGEX.exec(text)) !== null) {
      const day = Number.parseInt(m[1], 10);
      const month = Number.parseInt(m[2], 10);
      const candidates = [refYear - 1, refYear, refYear + 1]
        .filter((y) => isValidCalendarDate(y, month, day))
        .map((y) => formatIsoDate(y, month, day));
      if (candidates.length > 0) {
        candidates.sort(
          (a, b) =>
            Math.abs(daysBetweenIso(referenceIsoDate, a)) -
            Math.abs(daysBetweenIso(referenceIsoDate, b)),
        );
        shortDates.push(candidates[0]);
      }
    }
    return shortDates;
  },
};

export const ScheduleSelectionResolver = {
  resolveRuMeal(nowEpochMillis: number): TipoRefeicao {
    const parts = getBrasiliaParts(nowEpochMillis);
    const minutesOfDay = parts.hour * 60 + parts.minute;
    if (minutesOfDay <= BREAKFAST_END_MINUTES) return 'CAFE_DA_MANHA';
    if (minutesOfDay <= LUNCH_END_MINUTES) return 'ALMOCO';
    return 'JANTAR';
  },

  resolveRuDayIndex(dias: readonly CardapioDiaRu[], nowEpochMillis: number): number {
    if (dias.length === 0) return 0;
    const parts = getBrasiliaParts(nowEpochMillis);
    const todayIso = parts.isoDate;

    const exactIdx = dias.findIndex((dia) =>
      RuMenuFreshness.extractDatesFromText(dia.dataTexto, todayIso).includes(todayIso),
    );
    if (exactIdx >= 0) return exactIdx;

    const ddMmPrefix = `${String(parts.day).padStart(2, '0')}/${String(parts.month).padStart(2, '0')}`;
    const prefixIdx = dias.findIndex((dia) => dia.dataTexto.trim().startsWith(ddMmPrefix));
    if (prefixIdx >= 0) return prefixIdx;

    const dowIdx = dias.findIndex((dia) => matchesDayOfWeek(dia.diaSemana, parts.dayOfWeekIso));
    return dowIdx >= 0 ? dowIdx : 0;
  },

  resolveRuInitialSelection(
    cardapio: CardapioSemanalRu | null,
    nowEpochMillis: number,
  ): RuSelecaoInicial {
    const dias = cardapio?.dias ?? [];
    const dayIndex = this.resolveRuDayIndex(dias, nowEpochMillis);
    const timeMeal = this.resolveRuMeal(nowEpochMillis);
    const selectedDay = dias[dayIndex];
    if (!selectedDay) {
      return { dayIndex, refeicao: timeMeal };
    }
    if ((selectedDay.refeicoes[timeMeal]?.length ?? 0) > 0) {
      return { dayIndex, refeicao: timeMeal };
    }
    const fallbackOrder: readonly TipoRefeicao[] = ['ALMOCO', 'JANTAR', 'CAFE_DA_MANHA'];
    const availableMeal = fallbackOrder.find(
      (meal) => (selectedDay.refeicoes[meal]?.length ?? 0) > 0,
    );
    return {
      dayIndex,
      refeicao: availableMeal ?? timeMeal,
    };
  },

  resolveCurrentSigaaDay(nowEpochMillis: number): DiaSemanaSigaa {
    const dow = getBrasiliaParts(nowEpochMillis).dayOfWeekIso;
    switch (dow) {
      case 1:
        return 'SEGUNDA';
      case 2:
        return 'TERCA';
      case 3:
        return 'QUARTA';
      case 4:
        return 'QUINTA';
      case 5:
        return 'SEXTA';
      case 6:
        return 'SABADO';
      default:
        return 'SEGUNDA';
    }
  },
};

function matchesDayOfWeek(diaSemanaLabel: string, dayOfWeekIso: number): boolean {
  const norm = CasAndJsfParser.normalizeLabel(diaSemanaLabel);
  if (!norm) return false;
  switch (dayOfWeekIso) {
    case 1:
      return norm.includes('seg') || norm.startsWith('2');
    case 2:
      return norm.includes('ter') || norm.startsWith('3');
    case 3:
      return norm.includes('qua') || norm.startsWith('4');
    case 4:
      return norm.includes('qui') || norm.startsWith('5');
    case 5:
      return norm.includes('sex') || norm.startsWith('6');
    case 6:
      return norm.includes('sab');
    case 7:
      return norm.includes('dom');
    default:
      return false;
  }
}
