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

    fun notificationTimeFor(refeicao: TipoRefeicao): LocalTime = when (refeicao) {
        TipoRefeicao.CAFE_DA_MANHA -> LocalTime.of(6, 0)
        TipoRefeicao.ALMOCO -> LocalTime.of(10, 0)
        TipoRefeicao.JANTAR -> LocalTime.of(16, 0)
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
