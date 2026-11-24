package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.RuSelecaoInicial
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Resolvedor canonico de dia e refeicao atuais no fuso de Brasilia (`America/Sao_Paulo`)
 * para abertura automatica das telas de Cardapio do RU e Aulas do Dia.
 */
object ScheduleSelectionResolver {
    val BRASILIA_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

    private const val BREAKFAST_END_MINUTES = 9 * 60 + 30
    private const val LUNCH_END_MINUTES = 14 * 60 + 30

    /**
     * Resolve a refeicao ativa do RU conforme o horario atual em Brasilia:
     * - 00:00..09:30 -> CAFE_DA_MANHA
     * - 09:31..14:30 -> ALMOCO
     * - 14:31..23:59 -> JANTAR
     */
    fun resolveRuMeal(nowEpochMillis: Long): TipoRefeicao {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMillis), BRASILIA_ZONE)
        val minutesOfDay = zdt.hour * 60 + zdt.minute
        return when {
            minutesOfDay <= BREAKFAST_END_MINUTES -> TipoRefeicao.CAFE_DA_MANHA
            minutesOfDay <= LUNCH_END_MINUTES -> TipoRefeicao.ALMOCO
            else -> TipoRefeicao.JANTAR
        }
    }

    /**
     * Resolve o indice do dia atual dentro da lista de dias do cardapio do RU.
     * Prioriza data exata (`dd/MM/yyyy`), depois prefixo `dd/MM`, depois dia da semana e por fim indice `0`.
     */
    fun resolveRuDayIndex(
        dias: List<CardapioDiaRu>,
        nowEpochMillis: Long,
    ): Int {
        if (dias.isEmpty()) return 0
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(BRASILIA_ZONE).toLocalDate()

        val exactDateIdx = dias.indexOfFirst { dia ->
            today in RuMenuFreshness.extractDatesFromText(dia.dataTexto, today)
        }
        if (exactDateIdx >= 0) return exactDateIdx

        val dayStr = today.dayOfMonth.toString().padStart(2, '0')
        val monthStr = today.monthValue.toString().padStart(2, '0')
        val ddMmPrefix = "$dayStr/$monthStr"
        val prefixIdx = dias.indexOfFirst { dia ->
            dia.dataTexto.trim().startsWith(ddMmPrefix)
        }
        if (prefixIdx >= 0) return prefixIdx

        val dowIdx = dias.indexOfFirst { dia ->
            matchesDayOfWeek(dia.diaSemana, today.dayOfWeek)
        }
        return if (dowIdx >= 0) dowIdx else 0
    }

    fun resolveRuInitialSelection(
        cardapio: CardapioSemanalRu?,
        nowEpochMillis: Long,
    ): RuSelecaoInicial = RuSelecaoInicial(
        dayIndex = resolveRuDayIndex(cardapio?.dias.orEmpty(), nowEpochMillis),
        refeicao = resolveRuMeal(nowEpochMillis),
    )

    /**
     * Resolve o dia letivo atual no SIGAA em Brasilia (`SUNDAY` abre em `SEGUNDA`).
     */
    fun resolveCurrentSigaaDay(nowEpochMillis: Long): DiaSemanaSigaa {
        val dow = Instant.ofEpochMilli(nowEpochMillis).atZone(BRASILIA_ZONE).dayOfWeek
        return when (dow) {
            DayOfWeek.MONDAY -> DiaSemanaSigaa.SEGUNDA
            DayOfWeek.TUESDAY -> DiaSemanaSigaa.TERCA
            DayOfWeek.WEDNESDAY -> DiaSemanaSigaa.QUARTA
            DayOfWeek.THURSDAY -> DiaSemanaSigaa.QUINTA
            DayOfWeek.FRIDAY -> DiaSemanaSigaa.SEXTA
            DayOfWeek.SATURDAY -> DiaSemanaSigaa.SABADO
            DayOfWeek.SUNDAY -> DiaSemanaSigaa.SEGUNDA
        }
    }

    private fun matchesDayOfWeek(diaSemanaLabel: String, dayOfWeek: DayOfWeek): Boolean {
        val norm = CasAndJsfParser.normalizeLabel(diaSemanaLabel)
        if (norm.isBlank()) return false
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> norm.contains("seg") || norm.startsWith("2")
            DayOfWeek.TUESDAY -> norm.contains("ter") || norm.startsWith("3")
            DayOfWeek.WEDNESDAY -> norm.contains("qua") || norm.startsWith("4")
            DayOfWeek.THURSDAY -> norm.contains("qui") || norm.startsWith("5")
            DayOfWeek.FRIDAY -> norm.contains("sex") || norm.startsWith("6")
            DayOfWeek.SATURDAY -> norm.contains("sab")
            DayOfWeek.SUNDAY -> norm.contains("dom")
        }
    }
}
