package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.Alergenico
import org.junit.Assert.assertEquals
import org.junit.Test

class RuAllergenClassifierTest {
    @Test
    fun classifiesConcatenatedTwoIconStripUsingMse() {
        val legends = Alergenico.entries.associateWith { alergenico ->
            val v = (alergenico.legendOrderIndex * 25).toByte()
            val buf = RgbImageBuffer(
                width = 32,
                height = 32,
                rgbBytes = ByteArray(32 * 32 * 3) { idx ->
                    when (idx % 3) {
                        0 -> v
                        1 -> (255 - (v.toInt() and 0xFF)).toByte()
                        else -> ((alergenico.legendOrderIndex * 17) % 255).toByte()
                    }
                },
            )
            RuAllergenClassifier.to32x32Vector(buf)
        }

        val leftColor = legends.getValue(Alergenico.LEITE)
        val rightColor = legends.getValue(Alergenico.GLUTEN)
        val stripBytes = ByteArray(64 * 32 * 3)
        for (y in 0 until 32) {
            for (x in 0 until 64) {
                val src = if (x < 32) leftColor else rightColor
                val srcX = if (x < 32) x else x - 32
                val srcOff = (y * 32 + srcX) * 3
                val dstOff = (y * 64 + x) * 3
                stripBytes[dstOff] = src[srcOff].toInt().toByte()
                stripBytes[dstOff + 1] = src[srcOff + 1].toInt().toByte()
                stripBytes[dstOff + 2] = src[srcOff + 2].toInt().toByte()
            }
        }

        val detected = RuAllergenClassifier.classifyStrip(
            strip = RgbImageBuffer(width = 64, height = 32, rgbBytes = stripBytes),
            legendReferences = legends,
        )
        assertEquals(listOf(Alergenico.LEITE, Alergenico.GLUTEN), detected)
    }
}
