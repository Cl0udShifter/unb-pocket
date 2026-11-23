package com.unbpocket.core.parser

import com.unbpocket.core.model.ru.Alergenico
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Buffer RGB puro (3 bytes por pixel) extraido de um XObject do PDF do RU.
 */
class RgbImageBuffer(
    val width: Int,
    val height: Int,
    val rgbBytes: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "Dimensoes de imagem invalidas: ${width}x$height" }
        require(rgbBytes.size >= width * height * 3) {
            "Tamanho do buffer RGB insuficiente para ${width}x$height"
        }
    }
}

/**
 * Classificador vetorial/raster dos 10 icones de alergenicos do RU via
 * fatiamento horizontal e Erro Quadratico Medio (MSE) em 32x32 RGB.
 */
object RuAllergenClassifier {
    private const val TARGET_SIZE = 32

    fun to32x32Vector(image: RgbImageBuffer, xStart: Int = 0, sliceWidth: Int = image.width): FloatArray {
        val vector = FloatArray(TARGET_SIZE * TARGET_SIZE * 3)
        val maxW = (xStart + sliceWidth).coerceAtMost(image.width)
        val effectiveW = (maxW - xStart).coerceAtLeast(1)

        for (ty in 0 until TARGET_SIZE) {
            val sy = ((ty + 0.5f) * image.height / TARGET_SIZE - 0.5f)
                .coerceIn(0f, (image.height - 1).toFloat())
            val y0 = sy.toInt()
            val y1 = (y0 + 1).coerceAtMost(image.height - 1)
            val wy = sy - y0

            for (tx in 0 until TARGET_SIZE) {
                val sx = xStart + ((tx + 0.5f) * effectiveW / TARGET_SIZE - 0.5f)
                    .coerceIn(0f, (effectiveW - 1).toFloat())
                val x0 = sx.toInt().coerceIn(0, image.width - 1)
                val x1 = (x0 + 1).coerceAtMost(image.width - 1)
                val wx = sx - x0

                val outOffset = (ty * TARGET_SIZE + tx) * 3
                for (c in 0 until 3) {
                    val p00 = image.rgbBytes[(y0 * image.width + x0) * 3 + c].toInt() and 0xFF
                    val p10 = image.rgbBytes[(y0 * image.width + x1) * 3 + c].toInt() and 0xFF
                    val p01 = image.rgbBytes[(y1 * image.width + x0) * 3 + c].toInt() and 0xFF
                    val p11 = image.rgbBytes[(y1 * image.width + x1) * 3 + c].toInt() and 0xFF
                    val top = p00 * (1f - wx) + p10 * wx
                    val bottom = p01 * (1f - wx) + p11 * wx
                    vector[outOffset + c] = top * (1f - wy) + bottom * wy
                }
            }
        }
        return vector
    }

    fun classifyStrip(
        strip: RgbImageBuffer,
        legendReferences: Map<Alergenico, FloatArray>,
    ): List<Alergenico> {
        if (legendReferences.isEmpty()) return emptyList()
        val iconCount = max(1, (strip.width.toFloat() / strip.height.toFloat()).roundToInt())
        val sliceWidth = strip.width.toFloat() / iconCount
        val detected = mutableListOf<Alergenico>()

        for (i in 0 until iconCount) {
            val x0 = (i * sliceWidth).toInt()
            val x1 = ((i + 1) * sliceWidth).toInt()
            val vec = to32x32Vector(strip, xStart = x0, sliceWidth = (x1 - x0).coerceAtLeast(1))
            val best = legendReferences.minByOrNull { (_, ref) -> mse(vec, ref) }?.key
            if (best != null && best !in detected) {
                detected += best
            }
        }
        return detected
    }

    private fun mse(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum / a.size
    }
}
