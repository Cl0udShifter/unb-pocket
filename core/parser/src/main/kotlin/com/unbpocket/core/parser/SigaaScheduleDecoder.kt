package com.unbpocket.core.parser

import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.HorarioSlot
import com.unbpocket.core.model.turmas.TurnoSigaa

/**
 * Decodificador da gramatica oficial de horarios do SIGAA UnB: `([2-7]+)([MTN])([1-6]+)`.
 */
object SigaaScheduleDecoder {
    private val blockRegex = Regex("""([2-7]+)([MTN])([1-6]+)""")

    private val timeTable: Map<Pair<TurnoSigaa, Int>, Pair<String, String>> = mapOf(
        (TurnoSigaa.MANHA to 1) to ("08:00" to "08:55"),
        (TurnoSigaa.MANHA to 2) to ("08:55" to "09:50"),
        (TurnoSigaa.MANHA to 3) to ("10:00" to "10:55"),
        (TurnoSigaa.MANHA to 4) to ("10:55" to "11:50"),
        (TurnoSigaa.MANHA to 5) to ("12:00" to "12:55"),
        (TurnoSigaa.TARDE to 1) to ("12:55" to "13:50"),
        (TurnoSigaa.TARDE to 2) to ("14:00" to "14:55"),
        (TurnoSigaa.TARDE to 3) to ("14:55" to "15:50"),
        (TurnoSigaa.TARDE to 4) to ("16:00" to "16:55"),
        (TurnoSigaa.TARDE to 5) to ("16:55" to "17:50"),
        (TurnoSigaa.TARDE to 6) to ("18:00" to "18:55"),
        (TurnoSigaa.NOITE to 1) to ("19:00" to "19:50"),
        (TurnoSigaa.NOITE to 2) to ("19:50" to "20:40"),
        (TurnoSigaa.NOITE to 3) to ("20:50" to "21:40"),
        (TurnoSigaa.NOITE to 4) to ("21:40" to "22:30"),
    )

    fun decode(rawSchedule: String): List<HorarioSlot> {
        val sanitized = rawSchedule.substringBefore('(').trim()
        if (sanitized.isEmpty()) return emptyList()

        val result = mutableListOf<HorarioSlot>()
        for (match in blockRegex.findAll(sanitized)) {
            val daysPart = match.groupValues[1]
            val shiftChar = match.groupValues[2].first()
            val timesPart = match.groupValues[3]
            val shift = TurnoSigaa.entries.firstOrNull { it.letra == shiftChar } ?: continue

            for (dayChar in daysPart) {
                val dayNum = dayChar.digitToIntOrNull() ?: continue
                val day = DiaSemanaSigaa.entries.firstOrNull { it.digito == dayNum } ?: continue

                for (timeChar in timesPart) {
                    val slotNum = timeChar.digitToIntOrNull() ?: continue
                    val range = timeTable[shift to slotNum] ?: continue
                    result += HorarioSlot(
                        diaSemana = day,
                        turno = shift,
                        tempoNumero = slotNum,
                        inicio = range.first,
                        fim = range.second,
                        codigoOriginal = match.value,
                    )
                }
            }
        }
        return result
    }
}
