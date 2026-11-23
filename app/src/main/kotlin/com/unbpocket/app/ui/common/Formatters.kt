package com.unbpocket.app.ui.common

import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.HorarioSlot
import com.unbpocket.core.model.turmas.TurmaResumo
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class DietaryBadgeKind(val label: String) {
    PADRAO("Padrão"),
    OVOLACTO("Ovolacto"),
    VEGANO("Vegano"),
}

enum class RuMenuSectionKind(val heading: String, val sortOrder: Int) {
    PRATOS_PRINCIPAIS("PRATOS PRINCIPAIS", 0),
    GUARNICAO("GUARNIÇÃO & ACOMPANHAMENTOS", 1),
    SOPA("SOPA DO DIA", 2),
    SALADAS("SALADAS", 3),
    SOBREMESA("SOBREMESA & BEBIDA", 4),
    OUTROS("OUTROS ITENS", 5),
}

data class RuMenuDisplayRow(
    val item: ItemCardapioRu,
    val rowLabel: String?,
    val dietaryBadge: DietaryBadgeKind?,
    val restrictedMatches: List<Alergenico>,
)

data class RuMenuDisplaySection(
    val kind: RuMenuSectionKind,
    val rows: List<RuMenuDisplayRow>,
)

data class DayChipMeta(
    val index: Int,
    val shortDow: String,
    val dayNumber: String,
    val fullLabel: String,
)

data class ScheduledClassItem(
    val turma: TurmaResumo,
    val startMinutes: Int,
    val endMinutes: Int,
    val startText: String,
    val endText: String,
)

data class FocusClassState(
    val item: ScheduledClassItem,
    val statusLabel: String,
    val isLiveNow: Boolean,
)

object Formatters {
    private val ptBrLocale = Locale("pt", "BR")
    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val multiBlankLineRegex = Regex("\n{3,}")

    fun formatCentavosBrl(centavos: Long): String {
        val sign = if (centavos < 0L) "-" else ""
        val absValue = abs(centavos)
        val reais = absValue / 100L
        val cents = absValue % 100L
        val reaisStr = reais.toString().reversed().chunked(3).joinToString(".").reversed()
        val centsStr = cents.toString().padStart(2, '0')
        return "${sign}R$ $reaisStr,$centsStr"
    }

    fun formatOriginSummary(origin: DataOrigin, syncedAtEpochMillis: Long): String {
        val timeText = formatSyncTimestamp(syncedAtEpochMillis)
        return when (origin) {
            DataOrigin.REMOTE -> "Atualizado $timeText"
            DataOrigin.CACHE -> "Salvo localmente · $timeText"
            DataOrigin.FALLBACK -> "Contingência · $timeText"
        }
    }

    fun formatSyncTimestamp(epochMillis: Long): String {
        if (epochMillis <= 0L) return "sem sincronização"
        val nowCal = Calendar.getInstance(ptBrLocale)
        val targetCal = Calendar.getInstance(ptBrLocale).apply { timeInMillis = epochMillis }
        val sameDay = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
        val timeFmt = SimpleDateFormat("HH:mm", ptBrLocale)
        return if (sameDay) {
            "hoje às ${timeFmt.format(Date(epochMillis))}"
        } else {
            val dateFmt = SimpleDateFormat("dd/MM 'às' HH:mm", ptBrLocale)
            dateFmt.format(Date(epochMillis))
        }
    }

    fun mealScheduleWindow(meal: TipoRefeicao): String = when (meal) {
        TipoRefeicao.CAFE_DA_MANHA -> "07h às 09h30"
        TipoRefeicao.ALMOCO -> "11h às 14h30"
        TipoRefeicao.JANTAR -> "17h às 19h30"
    }

    fun defaultMealForHour(hourOfDay: Int): TipoRefeicao = when {
        hourOfDay < 10 -> TipoRefeicao.CAFE_DA_MANHA
        hourOfDay < 15 -> TipoRefeicao.ALMOCO
        else -> TipoRefeicao.JANTAR
    }

    fun parseDayChipMeta(index: Int, dia: CardapioDiaRu): DayChipMeta {
        val norm = normalize(dia.diaSemana)
        val shortDow = when {
            norm.contains("seg") -> "SEG"
            norm.contains("ter") -> "TER"
            norm.contains("qua") -> "QUA"
            norm.contains("qui") -> "QUI"
            norm.contains("sex") -> "SEX"
            norm.contains("sab") -> "SÁB"
            norm.contains("dom") -> "DOM"
            else -> dia.diaSemana.take(3).uppercase(ptBrLocale).ifBlank { "DIA" }
        }
        val dayNum = Regex("""(\d{1,2})/""").find(dia.dataTexto)?.groupValues?.getOrNull(1)
            ?.padStart(2, '0')
            ?: (index + 1).toString()
        val label = if (dia.diaSemana.isNotBlank() && !dia.dataTexto.startsWith("COL_")) {
            "${dia.diaSemana} (${dia.dataTexto})"
        } else {
            dia.diaSemana.ifBlank { dia.dataTexto }
        }
        return DayChipMeta(index = index, shortDow = shortDow, dayNumber = dayNum, fullLabel = label)
    }

    fun buildRuMenuSections(
        items: List<ItemCardapioRu>,
        restrictedAllergens: Set<Alergenico>,
        hideRestricted: Boolean,
    ): Pair<List<RuMenuDisplaySection>, Int> {
        var hiddenCount = 0
        val grouped = linkedMapOf<RuMenuSectionKind, MutableList<RuMenuDisplayRow>>()

        for (item in items) {
            val matches = item.alergenicos.filter { it in restrictedAllergens }
            if (hideRestricted && matches.isNotEmpty()) {
                hiddenCount++
                continue
            }
            val (section, rowLabel, dietary) = classifyItem(item)
            grouped.getOrPut(section) { mutableListOf() } += RuMenuDisplayRow(
                item = item,
                rowLabel = rowLabel,
                dietaryBadge = dietary,
                restrictedMatches = matches,
            )
        }

        val sections = grouped.entries
            .sortedBy { it.key.sortOrder }
            .map { RuMenuDisplaySection(kind = it.key, rows = it.value) }
        return sections to hiddenCount
    }

    private fun classifyItem(item: ItemCardapioRu): Triple<RuMenuSectionKind, String?, DietaryBadgeKind?> {
        val catNorm = normalize(item.categoria)
        return when {
            catNorm.contains("vegetariano") || catNorm.contains("vegano") -> Triple(
                RuMenuSectionKind.PRATOS_PRINCIPAIS,
                null,
                if (catNorm.contains("ovolacto")) DietaryBadgeKind.OVOLACTO else DietaryBadgeKind.VEGANO,
            )
            catNorm.contains("ovolacto") -> Triple(
                RuMenuSectionKind.PRATOS_PRINCIPAIS,
                null,
                DietaryBadgeKind.OVOLACTO,
            )
            catNorm.contains("prato principal") || catNorm.contains("principal") || catNorm.contains("padrao") -> Triple(
                RuMenuSectionKind.PRATOS_PRINCIPAIS,
                null,
                DietaryBadgeKind.PADRAO,
            )
            catNorm.contains("guarnicao") || catNorm.contains("acompanhamento") || catNorm.contains("arroz") || catNorm.contains("feijao") -> Triple(
                RuMenuSectionKind.GUARNICAO,
                if (catNorm.contains("guarnicao")) "Guarnição" else "Acomp.",
                null,
            )
            catNorm.contains("sopa") || catNorm.contains("torrada") -> Triple(
                RuMenuSectionKind.SOPA,
                if (catNorm.contains("torrada")) "Torrada" else "Sopa",
                null,
            )
            catNorm.contains("salada") || catNorm.contains("molho") -> Triple(
                RuMenuSectionKind.SALADAS,
                item.categoria.trim().take(12),
                null,
            )
            catNorm.contains("sobremesa") || catNorm.contains("bebida") || catNorm.contains("suco") || catNorm.contains("fruta") || catNorm.contains("cafe") -> Triple(
                RuMenuSectionKind.SOBREMESA,
                item.categoria.trim().take(12),
                null,
            )
            else -> Triple(
                RuMenuSectionKind.OUTROS,
                item.categoria.trim().takeIf { it.isNotBlank() }?.take(14),
                null,
            )
        }
    }

    fun currentSigaaDay(): DiaSemanaSigaa {
        val cal = Calendar.getInstance(ptBrLocale)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> DiaSemanaSigaa.SEGUNDA
            Calendar.TUESDAY -> DiaSemanaSigaa.TERCA
            Calendar.WEDNESDAY -> DiaSemanaSigaa.QUARTA
            Calendar.THURSDAY -> DiaSemanaSigaa.QUINTA
            Calendar.FRIDAY -> DiaSemanaSigaa.SEXTA
            Calendar.SATURDAY -> DiaSemanaSigaa.SABADO
            else -> DiaSemanaSigaa.SEGUNDA
        }
    }

    fun classesForDay(turmas: List<TurmaResumo>, day: DiaSemanaSigaa): List<ScheduledClassItem> {
        val scheduled = mutableListOf<ScheduledClassItem>()
        for (turma in turmas) {
            val daySlots = turma.horarioSlots
                .filter { it.diaSemana == day }
                .sortedBy { parseMinutes(it.inicio) }
            if (daySlots.isEmpty()) continue

            // Merge contiguous time blocks of the same course on the same day
            val blocks = mergeContiguousSlots(daySlots)
            for (block in blocks) {
                val startMin = parseMinutes(block.first().inicio)
                val endMin = parseMinutes(block.last().fim)
                scheduled += ScheduledClassItem(
                    turma = turma,
                    startMinutes = startMin,
                    endMinutes = endMin,
                    startText = block.first().inicio,
                    endText = block.last().fim,
                )
            }
        }
        return scheduled.sortedBy { it.startMinutes }
    }

    private fun mergeContiguousSlots(slots: List<HorarioSlot>): List<List<HorarioSlot>> {
        if (slots.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<HorarioSlot>>()
        for (slot in slots) {
            val currentGroup = result.lastOrNull()
            if (currentGroup == null) {
                result += mutableListOf(slot)
            } else {
                val prevEnd = parseMinutes(currentGroup.last().fim)
                val nextStart = parseMinutes(slot.inicio)
                if (nextStart - prevEnd <= 15) {
                    currentGroup += slot
                } else {
                    result += mutableListOf(slot)
                }
            }
        }
        return result
    }

    fun resolveFocusClass(
        dayClasses: List<ScheduledClassItem>,
        isSelectedDayToday: Boolean,
        nowMinutesOfDay: Int,
    ): FocusClassState? {
        if (dayClasses.isEmpty()) return null
        if (!isSelectedDayToday) {
            val first = dayClasses.first()
            return FocusClassState(
                item = first,
                statusLabel = "Primeira aula · ${first.startText} - ${first.endText}",
                isLiveNow = false,
            )
        }

        val ongoing = dayClasses.firstOrNull { nowMinutesOfDay in it.startMinutes..it.endMinutes }
        if (ongoing != null) {
            val remaining = (ongoing.endMinutes - nowMinutesOfDay).coerceAtLeast(1)
            return FocusClassState(
                item = ongoing,
                statusLabel = "Em andamento · termina em $remaining min",
                isLiveNow = true,
            )
        }

        val upcoming = dayClasses.firstOrNull { it.startMinutes > nowMinutesOfDay }
        if (upcoming != null) {
            val diff = upcoming.startMinutes - nowMinutesOfDay
            val label = if (diff <= 60) {
                "Em $diff min · ${upcoming.startText}"
            } else {
                "Próxima aula · ${upcoming.startText} - ${upcoming.endText}"
            }
            return FocusClassState(
                item = upcoming,
                statusLabel = label,
                isLiveNow = false,
            )
        }

        val last = dayClasses.last()
        return FocusClassState(
            item = last,
            statusLabel = "Aulas de hoje encerradas · última ${last.startText}",
            isLiveNow = false,
        )
    }

    fun parseMinutes(hhMm: String): Int {
        val parts = hhMm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    fun initialsFromName(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "UNB"
            parts.size == 1 -> parts[0].take(2).uppercase(ptBrLocale)
            else -> "${parts.first().first()}${parts.last().first()}".uppercase(ptBrLocale)
        }
    }

    fun htmlToPlainText(html: String): String {
        if (html.isBlank()) return ""
        val normalizedBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|h1|h2|h3|h4|tr)>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "- ")
        val stripped = htmlTagRegex.replace(normalizedBreaks, "")
        return decodeBasicEntities(stripped)
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(multiBlankLineRegex, "\n\n")
            .trim()
    }

    private fun decodeBasicEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&aacute;", "á")
        .replace("&agrave;", "à")
        .replace("&atilde;", "ã")
        .replace("&acirc;", "â")
        .replace("&eacute;", "é")
        .replace("&ecirc;", "ê")
        .replace("&iacute;", "í")
        .replace("&oacute;", "ó")
        .replace("&ocirc;", "ô")
        .replace("&otilde;", "õ")
        .replace("&uacute;", "ú")
        .replace("&ccedil;", "ç")
        .replace("&Aacute;", "Á")
        .replace("&Eacute;", "É")
        .replace("&Iacute;", "Í")
        .replace("&Oacute;", "Ó")
        .replace("&Uacute;", "Ú")
        .replace("&Ccedil;", "Ç")

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return diacriticsRegex.replace(decomposed, "").lowercase(Locale.ROOT).trim()
    }
}
