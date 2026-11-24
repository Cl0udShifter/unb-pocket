package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleSelectionResolverTest {
    private val brasilia = ZoneId.of("America/Sao_Paulo")

    private fun epochBrasilia(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, brasilia).toInstant().toEpochMilli()

    @Test
    fun resolveRuMealSwitchesAtExactWindowBoundariesInBrasilia() {
        // 00:00 .. 09:30 -> CAFE_DA_MANHA
        assertEquals(TipoRefeicao.CAFE_DA_MANHA, ScheduleSelectionResolver.resolveRuMeal(epochBrasilia(2026, 11, 24, 0, 0)))
        assertEquals(TipoRefeicao.CAFE_DA_MANHA, ScheduleSelectionResolver.resolveRuMeal(epochBrasilia(2026, 11, 24, 9, 30)))

        // 09:31 .. 14:30 -> ALMOCO
        assertEquals(TipoRefeicao.ALMOCO, ScheduleSelectionResolver.resolveRuMeal(epochBrasilia(2026, 11, 24, 9, 31)))
        assertEquals(TipoRefeicao.ALMOCO, ScheduleSelectionResolver.resolveRuMeal(epochBrasilia(2026, 11, 24, 14, 30)))

        // 14:31 .. 23:59 -> JANTAR
        assertEquals(TipoRefeicao.JANTAR, ScheduleSelectionResolver.resolveRuMeal(epochBrasilia(2026, 11, 24, 14, 31)))
        assertEquals(TipoRefeicao.JANTAR, ScheduleSelectionResolver.resolveRuMeal(epochBrasilia(2026, 11, 24, 23, 59)))
    }

    @Test
    fun resolveRuInitialSelectionMatchesExactDateThenWeekdayThenZeroFallback() {
        val dias = listOf(
            CardapioDiaRu("23/11/2026", "Segunda-feira", emptyMap()),
            CardapioDiaRu("24/11/2026", "Terça-feira", emptyMap()),
            CardapioDiaRu("25/11/2026", "Quarta-feira", emptyMap()),
            CardapioDiaRu("26/11/2026", "Quinta-feira", emptyMap()),
            CardapioDiaRu("27/11/2026", "Sexta-feira", emptyMap()),
        )
        val menu = CardapioSemanalRu(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "23/11 a 27/11",
            pdfUrl = "https://ru.unb.br/darcy.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "2026-11-23T08:00:00",
            parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
            dias = dias,
        )

        // Exact date match: Tuesday 24/11/2026 at 12:15 -> index 1, ALMOCO
        val tuesdayLunch = ScheduleSelectionResolver.resolveRuInitialSelection(
            cardapio = menu,
            nowEpochMillis = epochBrasilia(2026, 11, 24, 12, 15),
        )
        assertEquals(1, tuesdayLunch.dayIndex)
        assertEquals(TipoRefeicao.ALMOCO, tuesdayLunch.refeicao)

        // Weekday fallback when menu is from a previous week: Thursday 03/12/2026 at 18:00 -> index 3 (Quinta), JANTAR
        val thursdayDinner = ScheduleSelectionResolver.resolveRuInitialSelection(
            cardapio = menu,
            nowEpochMillis = epochBrasilia(2026, 12, 3, 18, 0),
        )
        assertEquals(3, thursdayDinner.dayIndex)
        assertEquals(TipoRefeicao.JANTAR, thursdayDinner.refeicao)

        // Sunday not present on Mon-Fri campus -> falls back to index 0, CAFE_DA_MANHA
        val sundayMorning = ScheduleSelectionResolver.resolveRuInitialSelection(
            cardapio = menu,
            nowEpochMillis = epochBrasilia(2026, 11, 29, 8, 0),
        )
        assertEquals(0, sundayMorning.dayIndex)
        assertEquals(TipoRefeicao.CAFE_DA_MANHA, sundayMorning.refeicao)
    }

    @Test
    fun resolveCurrentSigaaDayMapsAllWeekdaysAndSundayToSegunda() {
        assertEquals(DiaSemanaSigaa.SEGUNDA, ScheduleSelectionResolver.resolveCurrentSigaaDay(epochBrasilia(2026, 11, 23, 10, 0)))
        assertEquals(DiaSemanaSigaa.TERCA, ScheduleSelectionResolver.resolveCurrentSigaaDay(epochBrasilia(2026, 11, 24, 10, 0)))
        assertEquals(DiaSemanaSigaa.QUARTA, ScheduleSelectionResolver.resolveCurrentSigaaDay(epochBrasilia(2026, 11, 25, 10, 0)))
        assertEquals(DiaSemanaSigaa.QUINTA, ScheduleSelectionResolver.resolveCurrentSigaaDay(epochBrasilia(2026, 11, 26, 10, 0)))
        assertEquals(DiaSemanaSigaa.SEXTA, ScheduleSelectionResolver.resolveCurrentSigaaDay(epochBrasilia(2026, 11, 27, 10, 0)))
        assertEquals(DiaSemanaSigaa.SABADO, ScheduleSelectionResolver.resolveCurrentSigaaDay(epochBrasilia(2026, 11, 28, 10, 0)))
        assertEquals(DiaSemanaSigaa.SEGUNDA, ScheduleSelectionResolver.resolveCurrentSigaaDay(epochBrasilia(2026, 11, 29, 10, 0)))
    }
}
