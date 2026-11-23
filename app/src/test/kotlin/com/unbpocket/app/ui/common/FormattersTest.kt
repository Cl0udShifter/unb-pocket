package com.unbpocket.app.ui.common

import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.HorarioSlot
import com.unbpocket.core.model.turmas.TurmaResumo
import com.unbpocket.core.model.turmas.TurnoSigaa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatCentavosBrl formats positive, zero, negative and thousands accurately`() {
        assertEquals("R$ 0,00", Formatters.formatCentavosBrl(0L))
        assertEquals("R$ 8,65", Formatters.formatCentavosBrl(865L))
        assertEquals("-R$ 1,50", Formatters.formatCentavosBrl(-150L))
        assertEquals("R$ 1.234,05", Formatters.formatCentavosBrl(123405L))
    }

    @Test
    fun `buildRuMenuSections groups items cleanly and respects hideRestricted flag`() {
        val items = listOf(
            ItemCardapioRu("Salada 1", "Alface", emptyList()),
            ItemCardapioRu("Prato Principal", "Bife acebolado", listOf(Alergenico.SOJA)),
            ItemCardapioRu("Vegetariano", "Quinoa com legumes", listOf(Alergenico.GLUTEN)),
            ItemCardapioRu("Ovolacto", "Omelete", listOf(Alergenico.OVO, Alergenico.LEITE)),
            ItemCardapioRu("Guarnição", "Purê de batata", listOf(Alergenico.LEITE)),
        )

        val (warningSections, hiddenZero) = Formatters.buildRuMenuSections(
            items = items,
            restrictedAllergens = setOf(Alergenico.GLUTEN, Alergenico.LEITE),
            hideRestricted = false,
        )
        assertEquals(0, hiddenZero)
        assertEquals(RuMenuSectionKind.PRATOS_PRINCIPAIS, warningSections[0].kind)
        assertEquals(RuMenuSectionKind.GUARNICAO, warningSections[1].kind)
        assertEquals(RuMenuSectionKind.SALADAS, warningSections[2].kind)

        val veganRow = warningSections[0].rows.first { it.dietaryBadge == DietaryBadgeKind.VEGANO }
        assertEquals(listOf(Alergenico.GLUTEN), veganRow.restrictedMatches)

        val (filteredSections, hiddenCount) = Formatters.buildRuMenuSections(
            items = items,
            restrictedAllergens = setOf(Alergenico.GLUTEN, Alergenico.LEITE),
            hideRestricted = true,
        )
        assertEquals(3, hiddenCount)
        assertEquals(listOf("Bife acebolado", "Alface"), filteredSections.flatMap { s -> s.rows.map { it.item.prato } })
    }

    @Test
    fun `classesForDay merges contiguous blocks and resolves live focus class`() {
        val turma = TurmaResumo(
            idTurma = "10",
            frontEndIdTurma = "fe_10",
            semestre = "2026.2",
            codigoDisciplina = "CIC0104",
            nomeDisciplina = "Software Básico",
            numeroTurma = "01",
            cargaHorariaHoras = 60,
            docenteNome = "Prof. Teste",
            local = "PAT AT-012",
            codigoHorarioRaw = "24M12",
            horarioSlots = listOf(
                HorarioSlot(DiaSemanaSigaa.SEGUNDA, TurnoSigaa.MANHA, 1, "08:00", "08:55", "2M1"),
                HorarioSlot(DiaSemanaSigaa.SEGUNDA, TurnoSigaa.MANHA, 2, "09:00", "09:50", "2M2"),
                HorarioSlot(DiaSemanaSigaa.QUARTA, TurnoSigaa.MANHA, 1, "08:00", "08:55", "4M1"),
            ),
            horarioExtenso = listOf("Segunda e Quarta 08:00-09:50"),
        )

        val monday = Formatters.classesForDay(listOf(turma), DiaSemanaSigaa.SEGUNDA)
        assertEquals(1, monday.size)
        assertEquals("08:00", monday[0].startText)
        assertEquals("09:50", monday[0].endText)

        val focusLive = Formatters.resolveFocusClass(
            dayClasses = monday,
            isSelectedDayToday = true,
            nowMinutesOfDay = 8 * 60 + 30,
        )
        assertNotNull(focusLive)
        assertTrue(focusLive!!.isLiveNow)
        assertTrue(focusLive.statusLabel.contains("Em andamento"))

        val focusOtherDay = Formatters.resolveFocusClass(
            dayClasses = monday,
            isSelectedDayToday = false,
            nowMinutesOfDay = 8 * 60 + 30,
        )
        assertNotNull(focusOtherDay)
        assertFalse(focusOtherDay!!.isLiveNow)
        assertTrue(focusOtherDay.statusLabel.contains("Primeira aula"))
    }

    @Test
    fun `parseDayChipMeta extracts weekday abbreviation and day number`() {
        val seg = CardapioDiaRu("16/11/2026", "2ª FEIRA", emptyMap())
        val qui = CardapioDiaRu("19/11/2026", "5ª FEIRA", emptyMap())
        val dom = CardapioDiaRu("22/11/2026", "DOMINGO", emptyMap())

        val metaSeg = Formatters.parseDayChipMeta(0, seg)
        assertEquals("SEG", metaSeg.shortDow)
        assertEquals("16", metaSeg.dayNumber)

        val metaQui = Formatters.parseDayChipMeta(3, qui)
        assertEquals("QUI", metaQui.shortDow)
        assertEquals("19", metaQui.dayNumber)

        val metaDom = Formatters.parseDayChipMeta(6, dom)
        assertEquals("DOM", metaDom.shortDow)
        assertEquals("22", metaDom.dayNumber)

        val fallbackFromDateOnly = CardapioDiaRu("23/11/2026", "", emptyMap())
        val metaDateOnly = Formatters.parseDayChipMeta(0, fallbackFromDateOnly)
        assertEquals("SEG", metaDateOnly.shortDow)
        assertEquals("23", metaDateOnly.dayNumber)
    }

    @Test
    fun `buildRuMenuSections labels real Darcy categories cleanly without truncation`() {
        val items = listOf(
            ItemCardapioRu("MOLHO PARA SALADA", "Alho e limão", emptyList()),
            ItemCardapioRu("BEBIDA (Refresco de)", "Maracujá", emptyList()),
            ItemCardapioRu("SOBREMESA", "Melancia", emptyList()),
            ItemCardapioRu("PANIFICAÇÃO", "Pão francês", emptyList()),
            ItemCardapioRu("PRATO PRINCIPAL PADRÃO", "Almôndegas de carne sugo", emptyList()),
        )

        val (sections, _) = Formatters.buildRuMenuSections(items, emptySet(), hideRestricted = false)
        val labels = sections.flatMap { s -> s.rows.mapNotNull { it.rowLabel } }
        assertEquals(listOf("Pães", "Molho", "Bebida", "Sobremesa"), labels)
    }

    @Test
    fun `htmlToPlainText removes markup and decodes basic entities`() {
        val raw = "<p>Aten&ccedil;&atilde;o: aula &amp; prova.<br/>Sala PJC.</p>"
        assertEquals("Atenção: aula & prova.\nSala PJC.", Formatters.htmlToPlainText(raw))
    }
}
