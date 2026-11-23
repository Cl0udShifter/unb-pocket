package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioSemanalRu
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Regras puras de vigencia semanal e cooldown para atualizacao automatica dos cardapios do RU.
 */
object RuMenuFreshness {
    const val NORMAL_COOLDOWN_MILLIS: Long = 6 * 60 * 60 * 1000L
    const val EXPIRED_RETRY_COOLDOWN_MILLIS: Long = 30 * 60 * 1000L

    private val brasiliaZone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val fullDateRegex = Regex("""(\d{2}/\d{2}/\d{4})""")
    private val shortDateRegex = Regex("""(\d{2}/\d{2})(?!/\d)""")
    private val brFullFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun localDateInBrasilia(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(brasiliaZone).toLocalDate()

    fun resolveMenuEndDate(
        cardapio: CardapioSemanalRu,
        referenceDate: LocalDate,
    ): LocalDate? {
        val labelDates = extractDatesFromText(cardapio.semanaLabel, referenceDate)
        val dayDates = cardapio.dias.flatMap { day ->
            extractDatesFromText(day.dataTexto, referenceDate)
        }
        return (labelDates + dayDates).maxOrNull()
    }

    fun isMenuWeekExpired(
        cardapio: CardapioSemanalRu,
        nowEpochMillis: Long,
    ): Boolean {
        val today = localDateInBrasilia(nowEpochMillis)
        val endDate = resolveMenuEndDate(cardapio, today) ?: return false
        return today.isAfter(endDate)
    }

    fun shouldCheckRemote(
        cachedByCampus: Map<CampusRu, CardapioSemanalRu?>,
        lastCheckEpochMillis: Long?,
        nowEpochMillis: Long,
        forceCheck: Boolean,
    ): Boolean {
        if (forceCheck || lastCheckEpochMillis == null) return true

        val anyMissingOrExpired = CampusRu.entries.any { campus ->
            val menu = cachedByCampus[campus]
            menu == null || isMenuWeekExpired(menu, nowEpochMillis)
        }

        val elapsed = (nowEpochMillis - lastCheckEpochMillis).coerceAtLeast(0L)
        val requiredCooldown = if (anyMissingOrExpired) {
            EXPIRED_RETRY_COOLDOWN_MILLIS
        } else {
            NORMAL_COOLDOWN_MILLIS
        }
        return elapsed >= requiredCooldown
    }

    private fun extractDatesFromText(
        text: String,
        referenceDate: LocalDate,
    ): List<LocalDate> {
        val fullDates = fullDateRegex.findAll(text).mapNotNull { match ->
            runCatching { LocalDate.parse(match.groupValues[1], brFullFormatter) }.getOrNull()
        }.toList()
        if (fullDates.isNotEmpty()) return fullDates

        return shortDateRegex.findAll(text).mapNotNull { match ->
            parseShortDateClosestTo(match.groupValues[1], referenceDate)
        }.toList()
    }

    private fun parseShortDateClosestTo(
        ddMm: String,
        referenceDate: LocalDate,
    ): LocalDate? {
        val parts = ddMm.split('/')
        if (parts.size != 2) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null

        val candidateYears = listOf(referenceDate.year - 1, referenceDate.year, referenceDate.year + 1)
        val candidates = candidateYears.mapNotNull { year ->
            runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        return candidates.minByOrNull { date ->
            abs(ChronoUnit.DAYS.between(referenceDate, date))
        }
    }
}
