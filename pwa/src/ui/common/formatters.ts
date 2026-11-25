import type {
  Alergenico,
  CardapioDiaRu,
  DataOrigin,
  DiaSemanaSigaa,
  GrupoAcessoRu,
  HorarioSlot,
  ItemCardapioRu,
  RefeicoesRestantes,
  TipoRefeicao,
  TurmaResumo,
} from '../../core/model/models';
import { getBrasiliaParts, isValidCalendarDate, parseIsoDate } from '../../core/parser/brasiliaTime';

export type DietaryBadgeKind = 'PADRAO' | 'OVOLACTO' | 'VEGANO';

export const DIETARY_BADGE_LABELS: Readonly<Record<DietaryBadgeKind, string>> = {
  PADRAO: 'Padrão',
  OVOLACTO: 'Ovolacto',
  VEGANO: 'Vegano',
};

export type RuMenuSectionKind =
  | 'PRATOS_PRINCIPAIS'
  | 'PANIFICACAO'
  | 'GUARNICAO'
  | 'SOPA'
  | 'SALADAS'
  | 'SOBREMESA'
  | 'OUTROS';

export const RU_MENU_SECTION_META: Readonly<
  Record<RuMenuSectionKind, { readonly heading: string; readonly sortOrder: number }>
> = {
  PRATOS_PRINCIPAIS: { heading: 'PRATOS PRINCIPAIS', sortOrder: 0 },
  PANIFICACAO: { heading: 'PANIFICAÇÃO & EXTRAS', sortOrder: 1 },
  GUARNICAO: { heading: 'GUARNIÇÃO & ACOMPANHAMENTOS', sortOrder: 2 },
  SOPA: { heading: 'SOPA DO DIA', sortOrder: 3 },
  SALADAS: { heading: 'SALADAS', sortOrder: 4 },
  SOBREMESA: { heading: 'SOBREMESA & BEBIDA', sortOrder: 5 },
  OUTROS: { heading: 'OUTROS ITENS', sortOrder: 6 },
};

export interface RuMenuDisplayRow {
  readonly item: ItemCardapioRu;
  readonly rowLabel: string | null;
  readonly dietaryBadge: DietaryBadgeKind | null;
  readonly restrictedMatches: readonly Alergenico[];
}

export interface RuMenuDisplaySection {
  readonly kind: RuMenuSectionKind;
  readonly heading: string;
  readonly rows: readonly RuMenuDisplayRow[];
}

export interface DayChipMeta {
  readonly index: number;
  readonly shortDow: string;
  readonly dayNumber: string;
  readonly fullLabel: string;
}

export interface ScheduledClassItem {
  readonly turma: TurmaResumo;
  readonly startMinutes: number;
  readonly endMinutes: number;
  readonly startText: string;
  readonly endText: string;
}

export interface FocusClassState {
  readonly item: ScheduledClassItem;
  readonly statusLabel: string;
  readonly isLiveNow: boolean;
}

const DIACRITICS_REGEX = /[\u0300-\u036f]+/g;
const HTML_TAG_REGEX = /<[^>]+>/g;
const MULTI_BLANK_LINE_REGEX = /\n{3,}/g;

function normalize(value: string): string {
  return value.normalize('NFD').replace(DIACRITICS_REGEX, '').toLowerCase().trim();
}

export const Formatters = {
  formatCentavosBrl(centavos: number): string {
    const sign = centavos < 0 ? '-' : '';
    const absValue = Math.abs(Math.trunc(centavos));
    const reais = Math.floor(absValue / 100);
    const cents = absValue % 100;
    const reaisStr = String(reais).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    const centsStr = String(cents).padStart(2, '0');
    return `${sign}R$ ${reaisStr},${centsStr}`;
  },

  formatRuGroupSummary(grupo: GrupoAcessoRu): string {
    switch (grupo) {
      case 'GRUPO_1':
        return 'Grupo I · Assistência estudantil';
      case 'GRUPO_2':
        return 'Grupo II · Graduação e pós-graduação';
      case 'GRUPO_3':
        return 'Grupo III · Servidores e visitantes';
      case 'GRUPO_4':
        return 'Grupo IV · Escola pública';
      case 'EXECUTIVO':
        return 'Restaurante Executivo';
      case 'DESCONHECIDO':
        return 'Grupo não identificado';
    }
  },

  formatRuRemainingMealsSummary(restantes: RefeicoesRestantes | null): string | null {
    if (!restantes) return null;
    if (restantes.gratuito) return 'Gratuidade ativa';
    const count = restantes.almocosOuJantaresRestantes;
    if (count <= 0) return 'Saldo insuficiente para uma refeição';
    if (count === 1) return 'Rende cerca de 1 almoço ou jantar';
    return `Rende cerca de ${count} almoços ou jantares`;
  },

  formatOriginSummary(
    origin: DataOrigin,
    syncedAtEpochMillis: number,
    nowEpochMillis: number = Date.now(),
  ): string {
    const timeText = this.formatSyncTimestamp(syncedAtEpochMillis, nowEpochMillis);
    switch (origin) {
      case 'REMOTE':
        return `Atualizado ${timeText}`;
      case 'CACHE':
        return `Salvo localmente · ${timeText}`;
      case 'FALLBACK':
        return `Contingência · ${timeText}`;
    }
  },

  formatSyncTimestamp(epochMillis: number, nowEpochMillis: number = Date.now()): string {
    if (epochMillis <= 0) return 'sem sincronização';
    const nowParts = getBrasiliaParts(nowEpochMillis);
    const targetParts = getBrasiliaParts(epochMillis);
    const hh = String(targetParts.hour).padStart(2, '0');
    const mm = String(targetParts.minute).padStart(2, '0');
    if (nowParts.isoDate === targetParts.isoDate) {
      return `hoje às ${hh}:${mm}`;
    }
    const dd = String(targetParts.day).padStart(2, '0');
    const mo = String(targetParts.month).padStart(2, '0');
    return `${dd}/${mo} às ${hh}:${mm}`;
  },

  mealScheduleWindow(meal: TipoRefeicao): string {
    switch (meal) {
      case 'CAFE_DA_MANHA':
        return '07h às 09h30';
      case 'ALMOCO':
        return '11h às 14h30';
      case 'JANTAR':
        return '17h às 19h30';
    }
  },

  defaultMealForHour(hourOfDay: number): TipoRefeicao {
    if (hourOfDay < 10) return 'CAFE_DA_MANHA';
    if (hourOfDay < 15) return 'ALMOCO';
    return 'JANTAR';
  },

  parseDayChipMeta(index: number, dia: CardapioDiaRu): DayChipMeta {
    const norm = normalize(dia.diaSemana);
    let fromLabel: string | null = null;
    if (norm.includes('seg') || norm.startsWith('2')) fromLabel = 'SEG';
    else if (norm.includes('ter') || norm.startsWith('3')) fromLabel = 'TER';
    else if (norm.includes('qua') || norm.startsWith('4')) fromLabel = 'QUA';
    else if (norm.includes('qui') || norm.startsWith('5')) fromLabel = 'QUI';
    else if (norm.includes('sex') || norm.startsWith('6')) fromLabel = 'SEX';
    else if (norm.includes('sab')) fromLabel = 'SÁB';
    else if (norm.includes('dom')) fromLabel = 'DOM';

    const shortDow =
      fromLabel ??
      weekdayFromDateText(dia.dataTexto) ??
      (dia.diaSemana.trim().slice(0, 3).toUpperCase() || 'DIA');

    const dayNumMatch = /(\d{1,2})\//.exec(dia.dataTexto);
    const dayNumber = dayNumMatch ? dayNumMatch[1].padStart(2, '0') : String(index + 1);
    const fullLabel =
      dia.diaSemana.trim().length > 0 && !dia.dataTexto.startsWith('COL_')
        ? `${dia.diaSemana} (${dia.dataTexto})`
        : `${shortDow} ${dia.dataTexto}`.trim();

    return { index, shortDow, dayNumber, fullLabel };
  },

  buildRuMenuSections(
    items: readonly ItemCardapioRu[],
    restrictedAllergens: ReadonlySet<Alergenico>,
    hideRestricted: boolean,
  ): { readonly sections: readonly RuMenuDisplaySection[]; readonly hiddenCount: number } {
    let hiddenCount = 0;
    const grouped = new Map<RuMenuSectionKind, RuMenuDisplayRow[]>();

    for (const item of items) {
      const matches = item.alergenicos.filter((a) => restrictedAllergens.has(a));
      if (hideRestricted && matches.length > 0) {
        hiddenCount += 1;
        continue;
      }
      const { section, rowLabel, dietaryBadge } = classifyMenuItem(item);
      const list = grouped.get(section) ?? [];
      list.push({
        item,
        rowLabel,
        dietaryBadge,
        restrictedMatches: matches,
      });
      grouped.set(section, list);
    }

    const sections = Array.from(grouped.entries())
      .sort((a, b) => RU_MENU_SECTION_META[a[0]].sortOrder - RU_MENU_SECTION_META[b[0]].sortOrder)
      .map(([kind, rows]) => ({
        kind,
        heading: RU_MENU_SECTION_META[kind].heading,
        rows,
      }));

    return { sections, hiddenCount };
  },

  currentSigaaDay(nowEpochMillis: number = Date.now()): DiaSemanaSigaa {
    const { dayOfWeekIso } = getBrasiliaParts(nowEpochMillis);
    switch (dayOfWeekIso) {
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

  nowMinutesInBrasilia(nowEpochMillis: number = Date.now()): number {
    const { hour, minute } = getBrasiliaParts(nowEpochMillis);
    return hour * 60 + minute;
  },

  classesForDay(turmas: readonly TurmaResumo[], day: DiaSemanaSigaa): ScheduledClassItem[] {
    const scheduled: ScheduledClassItem[] = [];
    for (const turma of turmas) {
      const daySlots = turma.horarioSlots
        .filter((s) => s.diaSemana === day)
        .sort((a, b) => this.parseMinutes(a.inicio) - this.parseMinutes(b.inicio));
      if (daySlots.length === 0) continue;

      const blocks = mergeContiguousSlots(daySlots, (t) => this.parseMinutes(t));
      for (const block of blocks) {
        const first = block[0];
        const last = block[block.length - 1];
        scheduled.push({
          turma,
          startMinutes: this.parseMinutes(first.inicio),
          endMinutes: this.parseMinutes(last.fim),
          startText: first.inicio,
          endText: last.fim,
        });
      }
    }
    return scheduled.sort((a, b) => a.startMinutes - b.startMinutes);
  },

  resolveFocusClass(
    dayClasses: readonly ScheduledClassItem[],
    isSelectedDayToday: boolean,
    nowMinutesOfDay: number,
  ): FocusClassState | null {
    if (dayClasses.length === 0) return null;
    if (!isSelectedDayToday) {
      const first = dayClasses[0];
      return {
        item: first,
        statusLabel: `Primeira aula · ${first.startText} - ${first.endText}`,
        isLiveNow: false,
      };
    }

    const ongoing = dayClasses.find(
      (c) => nowMinutesOfDay >= c.startMinutes && nowMinutesOfDay <= c.endMinutes,
    );
    if (ongoing) {
      const remaining = Math.max(1, ongoing.endMinutes - nowMinutesOfDay);
      return {
        item: ongoing,
        statusLabel: `Em andamento · termina em ${remaining} min`,
        isLiveNow: true,
      };
    }

    const upcoming = dayClasses.find((c) => c.startMinutes > nowMinutesOfDay);
    if (upcoming) {
      const diff = upcoming.startMinutes - nowMinutesOfDay;
      const label =
        diff <= 60
          ? `Em ${diff} min · ${upcoming.startText}`
          : `Próxima aula · ${upcoming.startText} - ${upcoming.endText}`;
      return {
        item: upcoming,
        statusLabel: label,
        isLiveNow: false,
      };
    }

    const last = dayClasses[dayClasses.length - 1];
    return {
      item: last,
      statusLabel: `Aulas de hoje encerradas · última ${last.startText}`,
      isLiveNow: false,
    };
  },

  parseMinutes(hhMm: string): number {
    const parts = hhMm.trim().split(':');
    const h = Number.parseInt(parts[0] ?? '', 10);
    const m = Number.parseInt(parts[1] ?? '0', 10);
    if (Number.isNaN(h)) return 0;
    return h * 60 + (Number.isNaN(m) ? 0 : m);
  },

  initialsFromName(name: string): string {
    const parts = name
      .trim()
      .split(/\s+/)
      .filter((p) => p.length > 0);
    if (parts.length === 0) return 'UNB';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  },

  formatIntegralizacaoPercent(
    exactPercent: number | null,
    rawText: string | null,
  ): string {
    if (exactPercent !== null && Number.isFinite(exactPercent)) {
      const clamped = Math.min(100, Math.max(0, exactPercent));
      const rounded = Math.round(clamped * 10) / 10;
      return rounded % 1 === 0 ? `${Math.trunc(rounded)}%` : `${rounded.toFixed(1)}%`;
    }
    if (rawText && rawText.trim().length > 0) {
      const match = /\d+(?:[.,]\d+)?%/.exec(rawText)?.[0];
      if (match) return match.replace(',', '.');
    }
    return '-';
  },

  htmlToPlainText(html: string): string {
    if (!html.trim()) return '';
    const normalizedBreaks = html
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<\/(p|div|li|h1|h2|h3|h4|tr)>/gi, '\n')
      .replace(/<li[^>]*>/gi, '- ');
    const stripped = normalizedBreaks.replace(HTML_TAG_REGEX, '');
    return decodeBasicEntities(stripped)
      .split('\n')
      .map((line) => line.trim())
      .join('\n')
      .replace(MULTI_BLANK_LINE_REGEX, '\n\n')
      .trim();
  },
};

function weekdayFromDateText(dataTexto: string): string | null {
  const match = /(\d{1,2})\/(\d{1,2})\/(\d{4})/.exec(dataTexto);
  if (!match) return null;
  const d = Number.parseInt(match[1], 10);
  const m = Number.parseInt(match[2], 10);
  const y = Number.parseInt(match[3], 10);
  if (!isValidCalendarDate(y, m, d)) return null;
  const iso = `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  const { dayOfWeekIso } = parseIsoDate(iso);
  switch (dayOfWeekIso) {
    case 1:
      return 'SEG';
    case 2:
      return 'TER';
    case 3:
      return 'QUA';
    case 4:
      return 'QUI';
    case 5:
      return 'SEX';
    case 6:
      return 'SÁB';
    case 7:
      return 'DOM';
    default:
      return null;
  }
}

function classifyMenuItem(item: ItemCardapioRu): {
  section: RuMenuSectionKind;
  rowLabel: string | null;
  dietaryBadge: DietaryBadgeKind | null;
} {
  const catNorm = normalize(item.categoria);
  if (catNorm.includes('vegetariano') || catNorm.includes('vegano')) {
    return {
      section: 'PRATOS_PRINCIPAIS',
      rowLabel: null,
      dietaryBadge: catNorm.includes('ovolacto') ? 'OVOLACTO' : 'VEGANO',
    };
  }
  if (catNorm.includes('ovolacto')) {
    return { section: 'PRATOS_PRINCIPAIS', rowLabel: null, dietaryBadge: 'OVOLACTO' };
  }
  if (
    catNorm.includes('prato principal') ||
    catNorm.includes('principal') ||
    catNorm.includes('padrao')
  ) {
    return { section: 'PRATOS_PRINCIPAIS', rowLabel: null, dietaryBadge: 'PADRAO' };
  }
  if (catNorm.includes('panificacao') || catNorm.includes('pao')) {
    return { section: 'PANIFICACAO', rowLabel: 'Pães', dietaryBadge: null };
  }
  if (catNorm.includes('gordura')) {
    return { section: 'PANIFICACAO', rowLabel: 'Gordura', dietaryBadge: null };
  }
  if (catNorm.includes('extra')) {
    return { section: 'PANIFICACAO', rowLabel: 'Extra', dietaryBadge: null };
  }
  if (
    catNorm.includes('guarnicao') ||
    catNorm.includes('acompanhamento') ||
    catNorm.includes('arroz') ||
    catNorm.includes('feijao')
  ) {
    return {
      section: 'GUARNICAO',
      rowLabel: catNorm.includes('guarnicao') ? 'Guarnição' : 'Acomp.',
      dietaryBadge: null,
    };
  }
  if (catNorm.includes('sopa') || catNorm.includes('torrada')) {
    return {
      section: 'SOPA',
      rowLabel: catNorm.includes('torrada') ? 'Torrada' : 'Sopa',
      dietaryBadge: null,
    };
  }
  if (catNorm.includes('molho')) {
    return { section: 'SALADAS', rowLabel: 'Molho', dietaryBadge: null };
  }
  if (catNorm.includes('salada')) {
    const label = catNorm.includes('1')
      ? 'Salada 1'
      : catNorm.includes('2')
        ? 'Salada 2'
        : 'Salada';
    return { section: 'SALADAS', rowLabel: label, dietaryBadge: null };
  }
  if (catNorm.includes('sobremesa')) {
    return { section: 'SOBREMESA', rowLabel: 'Sobremesa', dietaryBadge: null };
  }
  if (catNorm.includes('fruta')) {
    return { section: 'SOBREMESA', rowLabel: 'Fruta', dietaryBadge: null };
  }
  if (
    catNorm.includes('bebida') ||
    catNorm.includes('suco') ||
    catNorm.includes('refresco') ||
    catNorm.includes('cafe')
  ) {
    return { section: 'SOBREMESA', rowLabel: 'Bebida', dietaryBadge: null };
  }
  const trimmed = item.categoria.trim();
  return {
    section: 'OUTROS',
    rowLabel: trimmed ? trimmed.slice(0, 14) : null,
    dietaryBadge: null,
  };
}

function mergeContiguousSlots(
  slots: readonly HorarioSlot[],
  parseMin: (hhMm: string) => number,
): HorarioSlot[][] {
  if (slots.length === 0) return [];
  const result: HorarioSlot[][] = [];
  for (const slot of slots) {
    const currentGroup = result[result.length - 1];
    if (!currentGroup) {
      result.push([slot]);
    } else {
      const prevEnd = parseMin(currentGroup[currentGroup.length - 1].fim);
      const nextStart = parseMin(slot.inicio);
      if (nextStart - prevEnd <= 15) {
        currentGroup.push(slot);
      } else {
        result.push([slot]);
      }
    }
  }
  return result;
}

function decodeBasicEntities(text: string): string {
  return text
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&aacute;/g, 'á')
    .replace(/&agrave;/g, 'à')
    .replace(/&atilde;/g, 'ã')
    .replace(/&acirc;/g, 'â')
    .replace(/&eacute;/g, 'é')
    .replace(/&ecirc;/g, 'ê')
    .replace(/&iacute;/g, 'í')
    .replace(/&oacute;/g, 'ó')
    .replace(/&ocirc;/g, 'ô')
    .replace(/&otilde;/g, 'õ')
    .replace(/&uacute;/g, 'ú')
    .replace(/&ccedil;/g, 'ç')
    .replace(/&Aacute;/g, 'Á')
    .replace(/&Eacute;/g, 'É')
    .replace(/&Iacute;/g, 'Í')
    .replace(/&Oacute;/g, 'Ó')
    .replace(/&Uacute;/g, 'Ú')
    .replace(/&Ccedil;/g, 'Ç');
}
