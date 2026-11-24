package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.TipoRefeicao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class RuNotificationPlannerTest {
    private fun millisAtBrasilia(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(
            LocalDate.of(year, month, day),
            LocalTime.of(hour, minute),
            RuMenuFreshness.brasiliaZone,
        ).toInstant().toEpochMilli()

    private fun sampleWeekMenu(
        status: CardapioParserStatus = CardapioParserStatus.PARSED_STRUCTURED,
        wednesdayHasLunch: Boolean = true,
    ): CardapioSemanalRu {
        val mondayMeals = mapOf(
            TipoRefeicao.CAFE_DA_MANHA to listOf(
                ItemCardapioRu("COMPLEMENTO PADRÃO", "Salsicha ao molho", listOf(Alergenico.SOJA)),
                ItemCardapioRu("COMPLEMENTO OVOLACTO", "Queijo muçarela", listOf(Alergenico.LEITE)),
                ItemCardapioRu("COMPLEMENTO VEGANO", "Pasta de ervilha", emptyList()),
            ),
            TipoRefeicao.ALMOCO to listOf(
                ItemCardapioRu("PRATO PRINCIPAL PADRÃO", "Frango assado", emptyList()),
                ItemCardapioRu("PRATO PRINCIPAL OVOLACTO", "Omelete de ervas", listOf(Alergenico.OVO)),
                ItemCardapioRu("PRATO PRINCIPAL VEGANO", "Grão-de-bico refogado", emptyList()),
            ),
        )
        val wednesdayMeals = if (wednesdayHasLunch) {
            mapOf(
                TipoRefeicao.ALMOCO to listOf(
                    ItemCardapioRu("PRATO PRINCIPAL PADRÃO", "Bife acebolado", emptyList()),
                ),
            )
        } else {
            emptyMap()
        }
        return CardapioSemanalRu(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "23/11/2026 a 29/11/2026",
            pdfUrl = "https://ru.unb.br/darcy.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "2026-11-23T08:00:00",
            parserStatus = status,
            dias = listOf(
                CardapioDiaRu("23/11/2026", "SEGUNDA-FEIRA", mondayMeals),
                CardapioDiaRu("24/11/2026", "TERÇA-FEIRA", emptyMap()),
                CardapioDiaRu("25/11/2026", "QUARTA-FEIRA", wednesdayMeals),
            ),
        )
    }

    @Test
    fun findNextTriggerRespectsSelectedDaysAndMealHours() {
        // Segunda 23/11/2026 09:30 -> deve agendar Segunda 10:00 (Almoco) se marcado, pulando Quarta desmarcada
        val config = RuNotificacaoConfig(
            habilitado = true,
            refeicoesPorDia = mapOf(
                DiaSemanaRu.SEGUNDA to setOf(TipoRefeicao.CAFE_DA_MANHA, TipoRefeicao.ALMOCO),
                DiaSemanaRu.QUINTA to setOf(TipoRefeicao.JANTAR),
            ),
        )

        val next = RuNotificationPlanner.findNextTrigger(
            config = config,
            nowEpochMillis = millisAtBrasilia(2026, 11, 23, 9, 30),
        )
        assertNotNull(next)
        assertEquals(DiaSemanaRu.SEGUNDA, next!!.dia)
        assertEquals(TipoRefeicao.ALMOCO, next.refeicao)
        assertEquals(millisAtBrasilia(2026, 11, 23, 10, 0), next.triggerEpochMillis)

        // Domingo 29/11/2026 17:00 -> deve enrolar para proxima Segunda 06:00
        val nextWeek = RuNotificationPlanner.findNextTrigger(
            config = config,
            nowEpochMillis = millisAtBrasilia(2026, 11, 29, 17, 0),
        )
        assertNotNull(nextWeek)
        assertEquals(DiaSemanaRu.SEGUNDA, nextWeek!!.dia)
        assertEquals(TipoRefeicao.CAFE_DA_MANHA, nextWeek.refeicao)
        assertEquals(millisAtBrasilia(2026, 11, 30, 6, 0), nextWeek.triggerEpochMillis)

        // Desabilitado -> null
        assertNull(
            RuNotificationPlanner.findNextTrigger(
                config = config.copy(habilitado = false),
                nowEpochMillis = millisAtBrasilia(2026, 11, 23, 9, 30),
            ),
        )
    }

    @Test
    fun buildNotificationContentAppliesAllergenFiltersAndSkipsMissingOrClosedDays() {
        val prefs = RuPreferencias(
            campusSelecionado = CampusRu.DARCY_RIBEIRO,
            alergenicosRestritos = setOf(Alergenico.OVO),
            ocultarPratosComAlergenicos = true,
            notificacoes = RuNotificacaoConfig(
                habilitado = true,
                refeicoesPorDia = mapOf(
                    DiaSemanaRu.SEGUNDA to setOf(TipoRefeicao.ALMOCO),
                ),
            ),
        )
        val menu = sampleWeekMenu()
        val mondayLunchMillis = millisAtBrasilia(2026, 11, 23, 10, 0)

        val content = RuNotificationPlanner.buildNotificationContent(
            cardapio = menu,
            preferencias = prefs,
            refeicao = TipoRefeicao.ALMOCO,
            nowEpochMillis = mondayLunchMillis,
        )
        assertNotNull(content)
        assertEquals("Almoço · RU Darcy Ribeiro", content!!.titulo)
        assertEquals("23/11/2026", content.dataTexto)
        assertEquals(0, content.dayIndex)
        assertEquals(TipoRefeicao.ALMOCO, content.refeicao)
        assertTrue(content.mensagem.contains("Frango assado"))
        assertFalse(content.mensagem.contains("Omelete"))

        // Quando ocultar = false, mostra o prato e adiciona alerta
        val warnContent = RuNotificationPlanner.buildNotificationContent(
            cardapio = menu,
            preferencias = prefs.copy(ocultarPratosComAlergenicos = false),
            refeicao = TipoRefeicao.ALMOCO,
            nowEpochMillis = mondayLunchMillis,
        )
        assertNotNull(warnContent)
        assertTrue(warnContent!!.mensagem.contains("Omelete"))
        assertTrue(warnContent.mensagem.contains("Atenção: Ovo"))

        // Dia nao habilitado (Quarta) ou horario nao habilitado (Jantar na Segunda) -> null
        assertNull(
            RuNotificationPlanner.buildNotificationContent(
                cardapio = menu,
                preferencias = prefs,
                refeicao = TipoRefeicao.JANTAR,
                nowEpochMillis = mondayLunchMillis,
            ),
        )
        assertNull(
            RuNotificationPlanner.buildNotificationContent(
                cardapio = menu,
                preferencias = prefs,
                refeicao = TipoRefeicao.ALMOCO,
                nowEpochMillis = millisAtBrasilia(2026, 11, 25, 10, 0),
            ),
        )

        // Fallback image/PDF-only -> null em buildNotificationContent, mas fallback garantido em buildNotificationContentOrFallback
        assertNull(
            RuNotificationPlanner.buildNotificationContent(
                cardapio = sampleWeekMenu(status = CardapioParserStatus.FALLBACK_IMAGE_ONLY),
                preferencias = prefs,
                refeicao = TipoRefeicao.ALMOCO,
                nowEpochMillis = mondayLunchMillis,
            ),
        )
        val fallbackContent = RuNotificationPlanner.buildNotificationContentOrFallback(
            cardapio = sampleWeekMenu(status = CardapioParserStatus.FALLBACK_IMAGE_ONLY),
            preferencias = prefs,
            refeicao = TipoRefeicao.ALMOCO,
            nowEpochMillis = mondayLunchMillis,
        )
        assertNotNull(fallbackContent)
        assertEquals(CampusRu.DARCY_RIBEIRO, fallbackContent!!.campus)
        assertEquals(TipoRefeicao.ALMOCO, fallbackContent.refeicao)
        assertTrue(fallbackContent.titulo.contains(CampusRu.DARCY_RIBEIRO.displayName))
        assertTrue(fallbackContent.mensagem.isNotBlank())

        val offlineFallback = RuNotificationPlanner.buildNotificationContentOrFallback(
            cardapio = null,
            preferencias = prefs,
            refeicao = TipoRefeicao.ALMOCO,
            nowEpochMillis = mondayLunchMillis,
        )
        assertNotNull(offlineFallback)
    }

    @Test
    fun `findCatchUpMeal recovers missed meal within window and skips past deadline or already sent`() {
        val mondayConfig = RuNotificacaoConfig(
            habilitado = true,
            refeicoesPorDia = mapOf(
                DiaSemanaRu.SEGUNDA to setOf(TipoRefeicao.ALMOCO, TipoRefeicao.JANTAR),
            ),
        )

        // Alarme das 16:00 atrasou e sistema acordou as 17:38 (ainda antes das 19:30)
        val mondayLateAfternoon = millisAtBrasilia(2026, 11, 23, 17, 38)
        val missedDinner = RuNotificationPlanner.findCatchUpMeal(
            config = mondayConfig,
            alreadySentKeys = emptySet(),
            nowEpochMillis = mondayLateAfternoon,
        )
        assertNotNull(missedDinner)
        assertEquals(TipoRefeicao.JANTAR, missedDinner!!.refeicao)

        // Depois de marcado como enviado no mesmo dia, nao repete
        val today = LocalDate.of(2026, 11, 23)
        val sentDinner = RuNotificationPlanner.findCatchUpMeal(
            config = mondayConfig,
            alreadySentKeys = setOf(RuNotificationPlanner.sentSlotKey(today, TipoRefeicao.JANTAR)),
            nowEpochMillis = mondayLateAfternoon,
        )
        assertNull(sentDinner)

        // Apos encerrar janela do jantar (20:15), nao dispara mais hoje
        val pastDeadline = millisAtBrasilia(2026, 11, 23, 20, 15)
        assertNull(
            RuNotificationPlanner.findCatchUpMeal(
                config = mondayConfig,
                alreadySentKeys = emptySet(),
                nowEpochMillis = pastDeadline,
            ),
        )
    }
}
