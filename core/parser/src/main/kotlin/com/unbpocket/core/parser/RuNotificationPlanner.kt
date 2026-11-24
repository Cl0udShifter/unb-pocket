package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.RuNotificacaoConteudo
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.TipoRefeicao
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ScheduledMealSlot(
    val dia: DiaSemanaRu,
    val refeicao: TipoRefeicao,
    val triggerEpochMillis: Long,
)

/**
 * Regras puras para planejamento de horarios (06h, 10h e 16h) e composicao
 * de notificacoes do Cardapio RU conforme preferencias de dias, refeicoes e alergenicos.
 */
object RuNotificationPlanner {
    private val mealOrder: List<TipoRefeicao> = listOf(
        TipoRefeicao.CAFE_DA_MANHA,
        TipoRefeicao.ALMOCO,
        TipoRefeicao.JANTAR,
    )

    private val brDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun notificationTimeFor(refeicao: TipoRefeicao): LocalTime = when (refeicao) {
        TipoRefeicao.CAFE_DA_MANHA -> LocalTime.of(6, 0)
        TipoRefeicao.ALMOCO -> LocalTime.of(10, 0)
        TipoRefeicao.JANTAR -> LocalTime.of(16, 0)
    }

    fun catchUpDeadlineFor(refeicao: TipoRefeicao): LocalTime = when (refeicao) {
        TipoRefeicao.CAFE_DA_MANHA -> LocalTime.of(9, 30)
        TipoRefeicao.ALMOCO -> LocalTime.of(14, 0)
        TipoRefeicao.JANTAR -> LocalTime.of(19, 30)
    }

    fun sentSlotKey(date: LocalDate, refeicao: TipoRefeicao): String = "${date}:${refeicao.name}"

    fun findCatchUpMeal(
        config: RuNotificacaoConfig,
        alreadySentKeys: Set<String>,
        nowEpochMillis: Long,
        minTriggerEpochMillis: Long = 0L,
    ): ScheduledMealSlot? {
        if (!config.hasAnySlotSelected()) return null

        val nowZoned = Instant.ofEpochMilli(nowEpochMillis).atZone(RuMenuFreshness.brasiliaZone)
        val today = nowZoned.toLocalDate()
        val dia = DiaSemanaRu.fromDayOfWeekIso(today.dayOfWeek.value) ?: return null
        val enabledMeals = config.refeicoesPorDia[dia].orEmpty()
        if (enabledMeals.isEmpty()) return null

        for (meal in mealOrder) {
            if (meal !in enabledMeals) continue
            if (sentSlotKey(today, meal) in alreadySentKeys) continue

            val triggerMillis = ZonedDateTime.of(
                today,
                notificationTimeFor(meal),
                RuMenuFreshness.brasiliaZone,
            ).toInstant().toEpochMilli()
            val deadlineMillis = ZonedDateTime.of(
                today,
                catchUpDeadlineFor(meal),
                RuMenuFreshness.brasiliaZone,
            ).toInstant().toEpochMilli()

            if (nowEpochMillis in triggerMillis..deadlineMillis && triggerMillis >= minTriggerEpochMillis) {
                return ScheduledMealSlot(
                    dia = dia,
                    refeicao = meal,
                    triggerEpochMillis = triggerMillis,
                )
            }
        }
        return null
    }

    fun findNextTrigger(
        config: RuNotificacaoConfig,
        nowEpochMillis: Long,
    ): ScheduledMealSlot? {
        if (!config.hasAnySlotSelected()) return null

        val nowZoned = Instant.ofEpochMilli(nowEpochMillis).atZone(RuMenuFreshness.brasiliaZone)
        val today = nowZoned.toLocalDate()

        for (dayOffset in 0..7) {
            val candidateDate = today.plusDays(dayOffset.toLong())
            val dia = DiaSemanaRu.fromDayOfWeekIso(candidateDate.dayOfWeek.value) ?: continue
            val enabledMeals = config.refeicoesPorDia[dia].orEmpty()
            if (enabledMeals.isEmpty()) continue

            for (meal in mealOrder) {
                if (meal !in enabledMeals) continue
                val triggerZoned = ZonedDateTime.of(
                    candidateDate,
                    notificationTimeFor(meal),
                    RuMenuFreshness.brasiliaZone,
                )
                val triggerMillis = triggerZoned.toInstant().toEpochMilli()
                if (triggerMillis > nowEpochMillis) {
                    return ScheduledMealSlot(
                        dia = dia,
                        refeicao = meal,
                        triggerEpochMillis = triggerMillis,
                    )
                }
            }
        }
        return null
    }

    fun findDayIndexForDate(
        cardapio: CardapioSemanalRu,
        targetDate: LocalDate,
    ): Int? {
        val index = cardapio.dias.indexOfFirst { day ->
            RuMenuFreshness.extractDatesFromText(day.dataTexto, targetDate).any { it == targetDate }
        }
        return index.takeIf { it >= 0 }
    }

    fun buildNotificationContent(
        cardapio: CardapioSemanalRu?,
        preferencias: RuPreferencias,
        refeicao: TipoRefeicao,
        nowEpochMillis: Long,
    ): RuNotificacaoConteudo? {
        val today = RuMenuFreshness.localDateInBrasilia(nowEpochMillis)
        val diaSemana = DiaSemanaRu.fromDayOfWeekIso(today.dayOfWeek.value) ?: return null
        if (!preferencias.notificacoes.isEnabledFor(diaSemana, refeicao)) return null

        if (cardapio == null || cardapio.parserStatus != CardapioParserStatus.PARSED_STRUCTURED) {
            return null
        }

        val dayIndex = findDayIndexForDate(cardapio, today) ?: return null
        val day = cardapio.dias[dayIndex]
        val items = day.refeicoes[refeicao].orEmpty()
        if (items.isEmpty()) return null

        val restricted = preferencias.alergenicosRestritos
        val visibleItems = if (preferencias.ocultarPratosComAlergenicos && restricted.isNotEmpty()) {
            items.filter { item -> item.alergenicos.none { it in restricted } }
        } else {
            items
        }

        val title = "${refeicao.displayName} · RU ${cardapio.campus.displayName}"
        val body = formatNotificationBody(
            items = visibleItems,
            restricted = restricted,
            hideRestricted = preferencias.ocultarPratosComAlergenicos,
        )

        return RuNotificacaoConteudo(
            campus = cardapio.campus,
            dataTexto = day.dataTexto,
            dayIndex = dayIndex,
            refeicao = refeicao,
            titulo = title,
            mensagem = body,
        )
    }

    /**
     * Compoe a notificacao da refeicao usando o cardapio estruturado quando disponivel, ou uma
     * mensagem de fallback quando o cardapio do dia/campus ainda esta em imagem, PDF ou sem cache.
     */
    fun buildNotificationContentOrFallback(
        cardapio: CardapioSemanalRu?,
        preferencias: RuPreferencias,
        refeicao: TipoRefeicao,
        nowEpochMillis: Long,
    ): RuNotificacaoConteudo? {
        buildNotificationContent(
            cardapio = cardapio,
            preferencias = preferencias,
            refeicao = refeicao,
            nowEpochMillis = nowEpochMillis,
        )?.let { return it }

        val today = RuMenuFreshness.localDateInBrasilia(nowEpochMillis)
        val diaSemana = DiaSemanaRu.fromDayOfWeekIso(today.dayOfWeek.value) ?: return null
        if (!preferencias.notificacoes.isEnabledFor(diaSemana, refeicao)) return null

        val dayIndex = cardapio?.let { findDayIndexForDate(it, today) } ?: 0
        val day = cardapio?.dias?.getOrNull(dayIndex)
        val dataTexto = day?.dataTexto?.takeIf { it.isNotBlank() } ?: brDateFormatter.format(today)
        val campus = cardapio?.campus ?: preferencias.campusSelecionado

        return RuNotificacaoConteudo(
            campus = campus,
            dataTexto = dataTexto,
            dayIndex = dayIndex,
            refeicao = refeicao,
            titulo = "${refeicao.displayName} · RU ${campus.displayName}",
            mensagem = "Hora de conferir o cardápio de ${refeicao.displayName.lowercase()} no UnB Pocket.",
        )
    }

    private fun formatNotificationBody(
        items: List<ItemCardapioRu>,
        restricted: Set<Alergenico>,
        hideRestricted: Boolean,
    ): String {
        if (items.isEmpty()) {
            return "Todos os pratos cadastrados contêm seus alergênicos selecionados."
        }

        val prioritized = items.filter { isPrimaryDishCategory(it.categoria) }
            .ifEmpty { items }
            .take(3)

        val dishSummary = prioritized.joinToString(" · ") { item ->
            val badge = shortDietaryPrefix(item.categoria)
            if (badge != null) "$badge: ${item.prato}" else item.prato
        }

        if (hideRestricted || restricted.isEmpty()) {
            return dishSummary
        }

        val matchedAllergens = prioritized
            .flatMap { it.alergenicos }
            .filter { it in restricted }
            .distinct()
        return if (matchedAllergens.isEmpty()) {
            dishSummary
        } else {
            val alert = matchedAllergens.joinToString(", ") { it.labelPtBr }
            "$dishSummary · Atenção: $alert"
        }
    }

    private fun isPrimaryDishCategory(category: String): Boolean {
        val norm = category.uppercase()
        return norm.contains("PRINCIPAL") || norm.contains("COMPLEMENTO")
    }

    private fun shortDietaryPrefix(category: String): String? {
        val norm = category.uppercase()
        return when {
            norm.contains("VEGANO") || norm.contains("ESTRITO") -> "Vegano"
            norm.contains("OVOLACTO") -> "Ovolacto"
            norm.contains("PADRAO") || norm.contains("PADRÃO") -> "Padrão"
            else -> null
        }
    }
}
