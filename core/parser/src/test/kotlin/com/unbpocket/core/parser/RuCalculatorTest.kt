package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.GrupoAcessoRu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuCalculatorTest {
    @Test
    fun parsesBrlAndDetectsGroup4FromStatement() {
        assertEquals(865L, RuCalculator.parseBrlToCentavos("R$ 8,65"))
        assertEquals(-250L, RuCalculator.parseBrlToCentavos("-R$ 2,50"))
        assertEquals(GrupoAcessoRu.GRUPO_4, RuCalculator.detectGrupo("Grupo 4 Jantar"))
        assertEquals(GrupoAcessoRu.GRUPO_2, RuCalculator.detectGrupo("Grupo II Almoco"))
    }

    @Test
    fun calculatesRemainingMealsForGrupo4AndGrupo1() {
        val g4 = RuCalculator.calculateRemainingMeals(865L, GrupoAcessoRu.GRUPO_4)
        assertNotNull(g4)
        assertEquals(5, g4?.cafesRestantes)
        assertEquals(3, g4?.almocosOuJantaresRestantes)
        assertEquals(115L, g4?.trocoCafeCentavos)
        assertEquals(115L, g4?.trocoAlmocoJantarCentavos)

        val g1 = RuCalculator.calculateRemainingMeals(0L, GrupoAcessoRu.GRUPO_1)
        assertNotNull(g1)
        assertTrue(g1?.gratuito == true)
    }
}
