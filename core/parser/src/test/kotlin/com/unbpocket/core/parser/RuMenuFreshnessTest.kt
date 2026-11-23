package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RuMenuFreshnessTest {
    private val brasilia = ZoneId.of("America/Sao_Paulo")

    private fun epochBrt(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, brasilia).toInstant().toEpochMilli()

    private fun menu(
        campus: CampusRu = CampusRu.DARCY_RIBEIRO,
        semanaLabel: String = "23/11/2026 a 29/11/2026",
        dias: List<CardapioDiaRu> = emptyList(),
    ) = CardapioSemanalRu(
        campus = campus,
        semanaLabel = semanaLabel,
        pdfUrl = "https://ru.unb.br/${campus.slug}.pdf",
        fallbackImageUrl = null,
        localPdfPath = null,
        modifiedGmt = "2026-11-23T10:00:00",
        parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
        dias = dias,
    )

    @Test
    fun `resolveMenuEndDate handles full dates and dd-MM across year rollover`() {
        val fullRange = menu(semanaLabel = "23/11/2026 à 29/11/2026")
        assertEquals(
            LocalDate.of(2026, 11, 29),
            RuMenuFreshness.resolveMenuEndDate(fullRange, LocalDate.of(2026, 11, 25)),
        )

        val yearRollover = menu(
            semanaLabel = "Cardápio da Semana",
            dias = listOf(
                CardapioDiaRu("29/12", "Segunda", emptyMap()),
                CardapioDiaRu("03/01", "Sábado", emptyMap()),
            ),
        )
        assertEquals(
            LocalDate.of(2026, 1, 3),
            RuMenuFreshness.resolveMenuEndDate(yearRollover, LocalDate.of(2025, 12, 30)),
        )
        assertEquals(
            LocalDate.of(2026, 1, 3),
            RuMenuFreshness.resolveMenuEndDate(yearRollover, LocalDate.of(2026, 1, 4)),
        )
    }

    @Test
    fun `isMenuWeekExpired respects America-Sao_Paulo midnight boundary`() {
        val sample = menu(semanaLabel = "23/11/2026 a 29/11/2026")

        assertFalse(RuMenuFreshness.isMenuWeekExpired(sample, epochBrt(2026, 11, 29, 23, 59)))
        assertTrue(RuMenuFreshness.isMenuWeekExpired(sample, epochBrt(2026, 11, 30, 0, 1)))
    }

    @Test
    fun `shouldCheckRemote applies 6h normal cooldown and 30m retry when expired or missing`() {
        val validAll = CampusRu.entries.associateWith { campus ->
            menu(campus = campus, semanaLabel = "23/11/2026 a 29/11/2026")
        }
        val t0 = epochBrt(2026, 11, 25, 10, 0)

        assertTrue(RuMenuFreshness.shouldCheckRemote(validAll, lastCheckEpochMillis = null, nowEpochMillis = t0, forceCheck = false))
        assertFalse(RuMenuFreshness.shouldCheckRemote(validAll, lastCheckEpochMillis = t0, nowEpochMillis = t0 + 5 * 3600_000L, forceCheck = false))
        assertTrue(RuMenuFreshness.shouldCheckRemote(validAll, lastCheckEpochMillis = t0, nowEpochMillis = t0 + RuMenuFreshness.NORMAL_COOLDOWN_MILLIS, forceCheck = false))
        assertTrue(RuMenuFreshness.shouldCheckRemote(validAll, lastCheckEpochMillis = t0, nowEpochMillis = t0 + 1_000L, forceCheck = true))

        // Semana expirou na segunda-feira 30/11
        val expiredNow = epochBrt(2026, 11, 30, 8, 0)
        assertFalse(
            RuMenuFreshness.shouldCheckRemote(
                cachedByCampus = validAll,
                lastCheckEpochMillis = expiredNow - 15 * 60_000L,
                nowEpochMillis = expiredNow,
                forceCheck = false,
            ),
        )
        assertTrue(
            RuMenuFreshness.shouldCheckRemote(
                cachedByCampus = validAll,
                lastCheckEpochMillis = expiredNow - RuMenuFreshness.EXPIRED_RETRY_COOLDOWN_MILLIS,
                nowEpochMillis = expiredNow,
                forceCheck = false,
            ),
        )

        // Um campus ausente tambem usa janela curta de 30 minutos
        val missingOne = validAll + (CampusRu.GAMA to null)
        assertFalse(
            RuMenuFreshness.shouldCheckRemote(
                cachedByCampus = missingOne,
                lastCheckEpochMillis = t0,
                nowEpochMillis = t0 + 10 * 60_000L,
                forceCheck = false,
            ),
        )
        assertTrue(
            RuMenuFreshness.shouldCheckRemote(
                cachedByCampus = missingOne,
                lastCheckEpochMillis = t0,
                nowEpochMillis = t0 + RuMenuFreshness.EXPIRED_RETRY_COOLDOWN_MILLIS,
                forceCheck = false,
            ),
        )
    }
}
