import {
  ALERGENICO_BY_ID,
  CAMPUS_RU_BY_ID,
  TIPO_REFEICAO_BY_ID,
  diaSemanaRuFromIso,
  hasAnyRuSlotSelected,
  isRuSlotEnabled,
  type Alergenico,
  type CardapioSemanalRu,
  type DiaSemanaRu,
  type ItemCardapioRu,
  type RuNotificacaoConfig,
  type RuNotificacaoConteudo,
  type RuPreferencias,
  type TipoRefeicao,
} from '../model/models';
import {
  addDaysIso,
  brasiliaLocalToEpochMillis,
  formatBrDateFromIso,
  getBrasiliaParts,
  parseIsoDate,
} from './brasiliaTime';
import { RuMenuFreshness } from './ruMenuFreshness';

export interface ScheduledMealSlot {
  readonly dia: DiaSemanaRu;
  readonly refeicao: TipoRefeicao;
  readonly triggerEpochMillis: number;
}

const MEAL_ORDER: readonly TipoRefeicao[] = ['CAFE_DA_MANHA', 'ALMOCO', 'JANTAR'];

function notificationHourMinute(refeicao: TipoRefeicao): readonly [number, number] {
  switch (refeicao) {
    case 'CAFE_DA_MANHA':
      return [6, 0];
    case 'ALMOCO':
      return [10, 0];
    case 'JANTAR':
      return [16, 0];
  }
}

function catchUpDeadlineHourMinute(refeicao: TipoRefeicao): readonly [number, number] {
  switch (refeicao) {
    case 'CAFE_DA_MANHA':
      return [9, 30];
    case 'ALMOCO':
      return [14, 0];
    case 'JANTAR':
      return [19, 30];
  }
}

export const RuNotificationPlanner = {
  sentSlotKey(isoDate: string, refeicao: TipoRefeicao): string {
    return `${isoDate}:${refeicao}`;
  },

  findCatchUpMeal(
    config: RuNotificacaoConfig,
    alreadySentKeys: ReadonlySet<string>,
    nowEpochMillis: number,
    minTriggerEpochMillis: number = 0,
  ): ScheduledMealSlot | null {
    if (!hasAnyRuSlotSelected(config)) return null;

    const parts = getBrasiliaParts(nowEpochMillis);
    const todayIso = parts.isoDate;
    const dia = diaSemanaRuFromIso(parts.dayOfWeekIso);
    if (!dia) return null;

    const enabledMeals = config.refeicoesPorDia[dia] ?? [];
    if (enabledMeals.length === 0) return null;

    for (const meal of MEAL_ORDER) {
      if (!enabledMeals.includes(meal)) continue;
      if (alreadySentKeys.has(this.sentSlotKey(todayIso, meal))) continue;

      const [th, tm] = notificationHourMinute(meal);
      const [dh, dm] = catchUpDeadlineHourMinute(meal);
      const triggerMillis = brasiliaLocalToEpochMillis(todayIso, th, tm);
      const deadlineMillis = brasiliaLocalToEpochMillis(todayIso, dh, dm);

      if (
        nowEpochMillis >= triggerMillis &&
        nowEpochMillis <= deadlineMillis &&
        triggerMillis >= minTriggerEpochMillis
      ) {
        return { dia, refeicao: meal, triggerEpochMillis: triggerMillis };
      }
    }
    return null;
  },

  findNextTrigger(config: RuNotificacaoConfig, nowEpochMillis: number): ScheduledMealSlot | null {
    if (!hasAnyRuSlotSelected(config)) return null;

    const todayIso = getBrasiliaParts(nowEpochMillis).isoDate;
    for (let dayOffset = 0; dayOffset <= 7; dayOffset += 1) {
      const candidateIso = addDaysIso(todayIso, dayOffset);
      const { dayOfWeekIso } = parseIsoDate(candidateIso);
      const dia = diaSemanaRuFromIso(dayOfWeekIso);
      if (!dia) continue;

      const enabledMeals = config.refeicoesPorDia[dia] ?? [];
      if (enabledMeals.length === 0) continue;

      for (const meal of MEAL_ORDER) {
        if (!enabledMeals.includes(meal)) continue;
        const [th, tm] = notificationHourMinute(meal);
        const triggerMillis = brasiliaLocalToEpochMillis(candidateIso, th, tm);
        if (triggerMillis > nowEpochMillis) {
          return { dia, refeicao: meal, triggerEpochMillis: triggerMillis };
        }
      }
    }
    return null;
  },

  findDayIndexForDate(cardapio: CardapioSemanalRu, targetIsoDate: string): number | null {
    const idx = cardapio.dias.findIndex((day) =>
      RuMenuFreshness.extractDatesFromText(day.dataTexto, targetIsoDate).includes(targetIsoDate),
    );
    return idx >= 0 ? idx : null;
  },

  buildNotificationContent(
    cardapio: CardapioSemanalRu | null,
    preferencias: RuPreferencias,
    refeicao: TipoRefeicao,
    nowEpochMillis: number,
  ): RuNotificacaoConteudo | null {
    const parts = getBrasiliaParts(nowEpochMillis);
    const diaSemana = diaSemanaRuFromIso(parts.dayOfWeekIso);
    if (!diaSemana || !isRuSlotEnabled(preferencias.notificacoes, diaSemana, refeicao)) {
      return null;
    }

    if (!cardapio || cardapio.parserStatus !== 'PARSED_STRUCTURED') {
      return null;
    }

    const dayIndex = this.findDayIndexForDate(cardapio, parts.isoDate);
    if (dayIndex === null) return null;

    const day = cardapio.dias[dayIndex];
    const items = day.refeicoes[refeicao] ?? [];
    if (items.length === 0) return null;

    const restricted = new Set(preferencias.alergenicosRestritos);
    const visibleItems =
      preferencias.ocultarPratosComAlergenicos && restricted.size > 0
        ? items.filter((item) => item.alergenicos.every((a) => !restricted.has(a)))
        : items;

    const mealMeta = TIPO_REFEICAO_BY_ID[refeicao];
    const campusMeta = CAMPUS_RU_BY_ID[cardapio.campus];
    const titulo = `${mealMeta.displayName} · RU ${campusMeta.displayName}`;
    const mensagem = formatNotificationBody(
      visibleItems,
      restricted,
      preferencias.ocultarPratosComAlergenicos,
    );

    return {
      campus: cardapio.campus,
      dataTexto: day.dataTexto,
      dayIndex,
      refeicao,
      titulo,
      mensagem,
    };
  },

  buildNotificationContentOrFallback(
    cardapio: CardapioSemanalRu | null,
    preferencias: RuPreferencias,
    refeicao: TipoRefeicao,
    nowEpochMillis: number,
  ): RuNotificacaoConteudo | null {
    const structured = this.buildNotificationContent(
      cardapio,
      preferencias,
      refeicao,
      nowEpochMillis,
    );
    if (structured) return structured;

    const parts = getBrasiliaParts(nowEpochMillis);
    const diaSemana = diaSemanaRuFromIso(parts.dayOfWeekIso);
    if (!diaSemana || !isRuSlotEnabled(preferencias.notificacoes, diaSemana, refeicao)) {
      return null;
    }

    const dayIndex = cardapio ? this.findDayIndexForDate(cardapio, parts.isoDate) ?? 0 : 0;
    const day = cardapio?.dias[dayIndex];
    const dataTexto =
      day?.dataTexto && day.dataTexto.trim().length > 0
        ? day.dataTexto
        : formatBrDateFromIso(parts.isoDate);
    const campus = cardapio?.campus ?? preferencias.campusSelecionado;
    const mealMeta = TIPO_REFEICAO_BY_ID[refeicao];
    const campusMeta = CAMPUS_RU_BY_ID[campus];

    return {
      campus,
      dataTexto,
      dayIndex,
      refeicao,
      titulo: `${mealMeta.displayName} · RU ${campusMeta.displayName}`,
      mensagem: `Hora de conferir o cardápio de ${mealMeta.displayName.toLowerCase()} no UnB Pocket.`,
    };
  },
};

function formatNotificationBody(
  items: readonly ItemCardapioRu[],
  restricted: ReadonlySet<Alergenico>,
  hideRestricted: boolean,
): string {
  if (items.length === 0) {
    return 'Todos os pratos cadastrados contêm seus alergênicos selecionados.';
  }

  const primary = items.filter((item) => isPrimaryDishCategory(item.categoria));
  const prioritized = (primary.length > 0 ? primary : items).slice(0, 3);

  const dishSummary = prioritized
    .map((item) => {
      const badge = shortDietaryPrefix(item.categoria);
      return badge ? `${badge}: ${item.prato}` : item.prato;
    })
    .join(' · ');

  if (hideRestricted || restricted.size === 0) {
    return dishSummary;
  }

  const matchedAllergens: Alergenico[] = [];
  for (const item of prioritized) {
    for (const alg of item.alergenicos) {
      if (restricted.has(alg) && !matchedAllergens.includes(alg)) {
        matchedAllergens.push(alg);
      }
    }
  }

  if (matchedAllergens.length === 0) return dishSummary;
  const alert = matchedAllergens.map((a) => ALERGENICO_BY_ID[a].labelPtBr).join(', ');
  return `${dishSummary} · Atenção: ${alert}`;
}

function isPrimaryDishCategory(category: string): boolean {
  const norm = category.toUpperCase();
  return norm.includes('PRINCIPAL') || norm.includes('COMPLEMENTO');
}

function shortDietaryPrefix(category: string): string | null {
  const norm = category.toUpperCase();
  if (norm.includes('VEGANO') || norm.includes('ESTRITO')) return 'Vegano';
  if (norm.includes('OVOLACTO')) return 'Ovolacto';
  if (norm.includes('PADRAO') || norm.includes('PADRÃO')) return 'Padrão';
  return null;
}
