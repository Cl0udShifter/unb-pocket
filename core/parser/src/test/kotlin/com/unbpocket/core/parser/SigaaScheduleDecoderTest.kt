package com.unbpocket.core.parser

import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.TurnoSigaa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SigaaScheduleDecoderTest {
    @Test
    fun decodesStandardCodeWithMultipleDaysAndTimes() {
        val slots = SigaaScheduleDecoder.decode("24M12")
        assertEquals(4, slots.size)
        assertEquals(DiaSemanaSigaa.SEGUNDA, slots[0].diaSemana)
        assertEquals(TurnoSigaa.MANHA, slots[0].turno)
        assertEquals("08:00", slots[0].inicio)
        assertEquals("08:55", slots[0].fim)
        assertEquals(DiaSemanaSigaa.QUARTA, slots[3].diaSemana)
        assertEquals("08:55", slots[3].inicio)
        assertEquals("09:50", slots[3].fim)
    }

    @Test
    fun ignoresDateRangeSuffixAndDecodesNightShift() {
        val slots = SigaaScheduleDecoder.decode("35N12 (10/08/2026 - 14/12/2026)")
        assertEquals(4, slots.size)
        assertEquals(TurnoSigaa.NOITE, slots[0].turno)
        assertEquals("19:00", slots[0].inicio)
        assertEquals("20:40", slots[3].fim)
    }

    @Test
    fun returnsEmptyForInvalidInput() {
        assertTrue(SigaaScheduleDecoder.decode("A combinar").isEmpty())
    }
}
