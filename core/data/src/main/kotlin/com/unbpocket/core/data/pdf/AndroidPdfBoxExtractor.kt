package com.unbpocket.core.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.unbpocket.core.data.local.PdfExtractor
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.parser.CarteirinhaPdfExtractedInput
import com.unbpocket.core.parser.PdfLineSegment
import com.unbpocket.core.parser.PdfLocatedImage
import com.unbpocket.core.parser.PdfMenuPageData
import com.unbpocket.core.parser.PdfTextSpan
import com.unbpocket.core.parser.RgbImageBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.math.abs

/**
 * Adaptador Android baseado em `pdfbox-android` para extrair texto vetorial,
 * linhas de grade e XObjects de imagem RGB da Carteirinha e dos PDFs do RU.
 */
class AndroidPdfBoxExtractor(
    context: Context,
) : PdfExtractor {
    private val appContext = context.applicationContext

    @Volatile
    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    PDFBoxResourceLoader.init(appContext)
                    initialized = true
                }
            }
        }
    }

    override suspend fun extractCarteirinha(pdfBytes: ByteArray): CoreResult<CarteirinhaPdfExtractedInput> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureInitialized()
                PDDocument.load(ByteArrayInputStream(pdfBytes)).use { doc ->
                    if (doc.numberOfPages == 0) {
                        return@withContext CoreResult.Failure(
                            CoreError.PdfExtractionError("Carteirinha Estudantil", "PDF sem paginas."),
                        )
                    }
                    val text = PDFTextStripper().getText(doc)
                    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

                    val page = doc.getPage(0)
                    val resources = page.resources
                    var img2Foto: RgbImageBuffer? = null
                    var img4Qr: RgbImageBuffer? = null

                    if (resources != null) {
                        for (xName in resources.xObjectNames) {
                            val xObj = resources.getXObject(xName)
                            if (xObj is PDImageXObject) {
                                val rgb = bitmapToRgb(xObj.image)
                                when {
                                    (rgb.width == 250 && rgb.height == 250) || xName.name == "img4" -> img4Qr = rgb
                                    (rgb.width == 500 && rgb.height == 625) || xName.name == "img2" -> img2Foto = rgb
                                }
                            }
                        }
                    }

                    CoreResult.Success(
                        CarteirinhaPdfExtractedInput(
                            textLines = lines,
                            img2Foto = img2Foto,
                            img4Qr = img4Qr,
                        ),
                    )
                }
            }.getOrElse { error ->
                CoreResult.Failure(
                    CoreError.PdfExtractionError(
                        documentType = "Carteirinha Estudantil",
                        reason = error.message ?: "Erro ao ler PDF da carteirinha.",
                    ),
                )
            }
        }

    override suspend fun extractTextLines(pdfBytes: ByteArray, documentType: String): CoreResult<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureInitialized()
                PDDocument.load(ByteArrayInputStream(pdfBytes)).use { doc ->
                    val text = PDFTextStripper().getText(doc)
                    CoreResult.Success(text.lines().map { it.trim() }.filter { it.isNotEmpty() })
                }
            }.getOrElse { error ->
                CoreResult.Failure(
                    CoreError.PdfExtractionError(
                        documentType = documentType,
                        reason = error.message ?: "Erro ao extrair texto do PDF.",
                    ),
                )
            }
        }

    override suspend fun extractRuMenuPages(pdfBytes: ByteArray): CoreResult<List<PdfMenuPageData>> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureInitialized()
                PDDocument.load(ByteArrayInputStream(pdfBytes)).use { doc ->
                    val pages = mutableListOf<PdfMenuPageData>()
                    for (pageIndex in 0 until doc.numberOfPages) {
                        val page = doc.getPage(pageIndex)
                        val mediaBox = page.mediaBox
                        val pageWidth = mediaBox.width
                        val pageHeight = mediaBox.height

                        val textCollector = PositionedTextCollector(pageIndex + 1)
                        textCollector.getText(doc)

                        val graphicsCollector = MenuGraphicsCollector(page, pageHeight)
                        graphicsCollector.processPage(page)

                        pages += PdfMenuPageData(
                            pageIndex = pageIndex,
                            pageWidth = pageWidth,
                            pageHeight = pageHeight,
                            texts = textCollector.spans,
                            lines = graphicsCollector.lines,
                            images = graphicsCollector.images,
                        )
                    }
                    CoreResult.Success(pages)
                }
            }.getOrElse { error ->
                CoreResult.Failure(
                    CoreError.PdfExtractionError(
                        documentType = "Cardapio RU",
                        reason = error.message ?: "Erro ao ler PDF do cardapio RU.",
                    ),
                )
            }
        }

    private fun bitmapToRgb(bitmap: Bitmap): RgbImageBuffer {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val rgb = ByteArray(w * h * 3)
        for (i in pixels.indices) {
            val px = pixels[i]
            val out = i * 3
            rgb[out] = ((px shr 16) and 0xFF).toByte()
            rgb[out + 1] = ((px shr 8) and 0xFF).toByte()
            rgb[out + 2] = (px and 0xFF).toByte()
        }
        return RgbImageBuffer(width = w, height = h, rgbBytes = rgb)
    }

    private class PositionedTextCollector(pageNumber: Int) : PDFTextStripper() {
        val spans = mutableListOf<PdfTextSpan>()

        init {
            startPage = pageNumber
            endPage = pageNumber
            sortByPosition = true
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
            var minX = Float.MAX_VALUE
            var minTop = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxBottom = -Float.MAX_VALUE
            for (pos in textPositions) {
                minX = minOf(minX, pos.xDirAdj)
                maxX = maxOf(maxX, pos.xDirAdj + pos.widthDirAdj)
                val top = pos.yDirAdj - pos.heightDir
                minTop = minOf(minTop, top)
                maxBottom = maxOf(maxBottom, pos.yDirAdj)
            }
            spans += PdfTextSpan(
                text = text.trim(),
                x0 = minX,
                top = minTop,
                x1 = maxX,
                bottom = maxBottom,
            )
        }
    }

    private inner class MenuGraphicsCollector(
        page: PDPage,
        private val pageHeight: Float,
    ) : PDFGraphicsStreamEngine(page) {
        val lines = mutableListOf<PdfLineSegment>()
        val images = mutableListOf<PdfLocatedImage>()

        private val pendingSegments = mutableListOf<PdfLineSegment>()
        private var currentPoint = PointF(0f, 0f)
        private var pathStart = PointF(0f, 0f)
        private var imageCounter = 0

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
            val xs = listOf(p0.x, p1.x, p2.x, p3.x)
            val ys = listOf(p0.y, p1.y, p2.y, p3.y)
            val x0 = xs.min()
            val x1 = xs.max()
            val top = pageHeight - ys.max()
            val bottom = pageHeight - ys.min()
            pendingSegments += PdfLineSegment(x0, top, x1, top)
            pendingSegments += PdfLineSegment(x0, bottom, x1, bottom)
            pendingSegments += PdfLineSegment(x0, top, x0, bottom)
            pendingSegments += PdfLineSegment(x1, top, x1, bottom)
        }

        override fun drawImage(pdImage: PDImage) {
            val ctm = graphicsState.currentTransformationMatrix
            val x0 = ctm.translateX
            val w = abs(ctm.scalingFactorX)
            val h = abs(ctm.scalingFactorY)
            if (w <= 0f || h <= 0f) return
            val pdfBottom = ctm.translateY
            val top = pageHeight - (pdfBottom + h)
            val bottom = pageHeight - pdfBottom
            val x1 = x0 + w

            val bitmap = runCatching { pdImage.image }.getOrNull() ?: return
            val rgb = bitmapToRgb(bitmap)
            images += PdfLocatedImage(
                name = "img_${imageCounter++}",
                x0 = x0,
                top = top,
                x1 = x1,
                bottom = bottom,
                rgb = rgb,
            )
        }

        override fun clip(windingRule: Path.FillType) = Unit

        override fun moveTo(x: Float, y: Float) {
            currentPoint = PointF(x, y)
            pathStart = PointF(x, y)
        }

        override fun lineTo(x: Float, y: Float) {
            val top0 = pageHeight - currentPoint.y
            val top1 = pageHeight - y
            pendingSegments += PdfLineSegment(currentPoint.x, top0, x, top1)
            currentPoint = PointF(x, y)
        }

        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            currentPoint = PointF(x3, y3)
        }

        override fun getCurrentPoint(): PointF = currentPoint

        override fun closePath() {
            val top0 = pageHeight - currentPoint.y
            val top1 = pageHeight - pathStart.y
            pendingSegments += PdfLineSegment(currentPoint.x, top0, pathStart.x, top1)
            currentPoint = pathStart
        }

        override fun endPath() {
            pendingSegments.clear()
        }

        override fun strokePath() {
            lines += pendingSegments
            pendingSegments.clear()
        }

        override fun fillPath(windingRule: Path.FillType) {
            for (seg in pendingSegments) {
                if (abs(seg.x1 - seg.x0) >= 15f || abs(seg.bottom - seg.top) >= 15f) {
                    lines += seg
                }
            }
            pendingSegments.clear()
        }

        override fun fillAndStrokePath(windingRule: Path.FillType) {
            lines += pendingSegments
            pendingSegments.clear()
        }

        override fun shadingFill(shadingName: COSName) = Unit
    }
}
