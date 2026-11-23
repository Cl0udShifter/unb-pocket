package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.TipoRefeicao
import kotlin.math.abs

data class PdfTextSpan(
    val text: String,
    val x0: Float,
    val top: Float,
    val x1: Float,
    val bottom: Float,
)

data class PositionedGlyph(
    val text: String,
    val x0: Float,
    val top: Float,
    val x1: Float,
    val bottom: Float,
    val widthOfSpace: Float,
)

data class FilledPdfRect(
    val x0: Float,
    val top: Float,
    val x1: Float,
    val bottom: Float,
)

data class PdfLineSegment(
    val x0: Float,
    val top: Float,
    val x1: Float,
    val bottom: Float,
)

data class PdfLocatedImage(
    val name: String,
    val x0: Float,
    val top: Float,
    val x1: Float,
    val bottom: Float,
    val rgb: RgbImageBuffer,
)

data class PdfMenuPageData(
    val pageIndex: Int,
    val pageWidth: Float,
    val pageHeight: Float,
    val texts: List<PdfTextSpan>,
    val lines: List<PdfLineSegment>,
    val images: List<PdfLocatedImage>,
)

/**
 * Reconstroi a grade do cardapio semanal do RU a partir dos spans de texto,
 * linhas vetoriais e XObjects de imagem RGB extraidos de cada pagina do PDF.
 */
object RuMenuPdfParser {
    private val dateRegex = Regex("""(\d{2}/\d{2}/\d{4})""")
    private val multiSpaceRegex = Regex("""\s+""")

    fun parsePages(
        campus: CampusRu,
        semanaLabel: String,
        pdfUrl: String,
        fallbackImageUrl: String?,
        localPdfPath: String?,
        modifiedGmt: String,
        pages: List<PdfMenuPageData>,
    ): CoreResult<CardapioSemanalRu> {
        if (pages.isEmpty()) {
            return CoreResult.Failure(
                CoreError.ParseError(
                    source = "ru-pdf:${campus.slug}",
                    reason = "PDF sem paginas extraidas.",
                    recoverableFallbackAvailable = true,
                ),
            )
        }

        val daysMap = linkedMapOf<String, MutableCardapioDia>()

        for (page in pages) {
            val meal = TipoRefeicao.entries.firstOrNull { it.pageIndex == page.pageIndex } ?: continue
            val legendRefs = buildLegendReferences(page.images)
            val grid = reconstructGrid(page.lines) ?: continue

            val cellTexts = Array(grid.rowBounds.size - 1) { row ->
                Array(grid.colBounds.size - 1) { col ->
                    extractCellText(page.texts, grid.cellBbox(row, col))
                }
            }

            val (dayCols, headerRowsEnd) = detectDayColumns(cellTexts)
            if (dayCols.isEmpty()) continue
            val categoryCol = detectCategoryColumn(grid, cellTexts, dayCols.keys.min())

            val classifiedIcons = page.images
                .filter { it.top in 85f..520f }
                .map { im ->
                    ClassifiedIcon(
                        cx = (im.x0 + im.x1) / 2f,
                        cy = (im.top + im.bottom) / 2f,
                        alergenicos = RuAllergenClassifier.classifyStrip(im.rgb, legendRefs),
                    )
                }

            var lastCategory: String? = null
            for (r in headerRowsEnd until cellTexts.size) {
                val rowTexts = cellTexts[r]
                val category = rowTexts.getOrNull(categoryCol)?.takeIf { it.isNotBlank() } ?: lastCategory
                if (category.isNullOrBlank()) continue
                lastCategory = category

                val sharedDish = dayCols.keys
                    .filter { it < rowTexts.size }
                    .firstNotNullOfOrNull { c -> rowTexts[c].takeIf { it.isNotBlank() } }
                    .orEmpty()

                val mergedBbox = if (isHorizontallyMergedDayRow(page.lines, grid, r, dayCols.keys)) {
                    findSingleMergedDayBbox(grid, r, dayCols.keys)
                } else {
                    null
                }
                val mergedDish = mergedBbox?.let { bbox ->
                    extractCellText(page.texts, bbox).takeIf { it.isNotBlank() }
                }

                for ((colIdx, meta) in dayCols) {
                    val dataStr = meta.data ?: "COL_$colIdx"
                    val dayObj = daysMap.getOrPut(dataStr) {
                        MutableCardapioDia(dataTexto = dataStr, diaSemana = meta.diaSemana.orEmpty())
                    }
                    val mealList = dayObj.refeicoes.getOrPut(meal) { mutableListOf() }

                    val rawDish = mergedDish
                        ?: rowTexts.getOrNull(colIdx)?.takeIf { it.isNotBlank() }
                        ?: sharedDish
                    val dish = rawDish.replace(multiSpaceRegex, " ").trim()
                    if (dish.isEmpty()) continue

                    val cellBbox = grid.cellBbox(r, colIdx)
                    val allergens = collectAllergens(classifiedIcons, cellBbox)
                        .ifEmpty { mergedBbox?.let { collectAllergens(classifiedIcons, it) }.orEmpty() }

                    mealList += ItemCardapioRu(
                        categoria = category.replace(multiSpaceRegex, " ").trim(),
                        prato = dish,
                        alergenicos = allergens,
                    )
                }
            }
        }

        val parsedDays = daysMap.values.map {
            CardapioDiaRu(
                dataTexto = it.dataTexto,
                diaSemana = it.diaSemana,
                refeicoes = it.refeicoes,
            )
        }.filter { it.refeicoes.isNotEmpty() }

        if (parsedDays.isEmpty()) {
            return CoreResult.Failure(
                CoreError.ParseError(
                    source = "ru-pdf:${campus.slug}",
                    reason = "Nao foi possivel extrair a tabela semanal do PDF do RU.",
                    recoverableFallbackAvailable = true,
                ),
            )
        }

        return CoreResult.Success(
            CardapioSemanalRu(
                campus = campus,
                semanaLabel = semanaLabel,
                pdfUrl = pdfUrl,
                fallbackImageUrl = fallbackImageUrl,
                localPdfPath = localPdfPath,
                modifiedGmt = modifiedGmt,
                parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
                dias = parsedDays,
            ),
        )
    }

    private fun buildLegendReferences(images: List<PdfLocatedImage>): Map<Alergenico, FloatArray> {
        val legendImages = images.filter { it.top > 530f }.sortedBy { it.x0 }
        val map = linkedMapOf<Alergenico, FloatArray>()
        Alergenico.entries.sortedBy { it.legendOrderIndex }.zip(legendImages).forEach { (alg, img) ->
            map[alg] = RuAllergenClassifier.to32x32Vector(img.rgb)
        }
        return map
    }

    private fun reconstructGrid(lines: List<PdfLineSegment>): PdfGrid? {
        val xCoords = mutableListOf<Float>()
        val yCoords = mutableListOf<Float>()

        for (seg in lines) {
            val dx = abs(seg.x1 - seg.x0)
            val dy = abs(seg.bottom - seg.top)
            if (dx <= 2.5f && dy >= 15f) {
                xCoords += (seg.x0 + seg.x1) / 2f
            } else if (dy <= 2.5f && dx >= 15f) {
                yCoords += (seg.top + seg.bottom) / 2f
            } else if (dx >= 15f && dy >= 15f) {
                xCoords += minOf(seg.x0, seg.x1)
                xCoords += maxOf(seg.x0, seg.x1)
                yCoords += minOf(seg.top, seg.bottom)
                yCoords += maxOf(seg.top, seg.bottom)
            }
        }

        val cols = clusterCoordinates(xCoords, tolerance = 2.5f)
        val rows = clusterCoordinates(yCoords, tolerance = 2.5f)
        if (cols.size < 3 || rows.size < 6) return null
        return PdfGrid(colBounds = cols, rowBounds = rows)
    }

    private fun clusterCoordinates(values: List<Float>, tolerance: Float): List<Float> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        val clusters = mutableListOf<Float>()
        var currentSum = sorted[0]
        var count = 1
        var anchor = sorted[0]

        for (i in 1 until sorted.size) {
            val v = sorted[i]
            if (v - anchor <= tolerance) {
                currentSum += v
                count++
            } else {
                clusters += currentSum / count
                currentSum = v
                count = 1
                anchor = v
            }
        }
        clusters += currentSum / count
        return clusters
    }

    private fun extractCellText(spans: List<PdfTextSpan>, bbox: CellBbox): String {
        val inside = spans.filter { span ->
            val cx = (span.x0 + span.x1) / 2f
            val cy = (span.top + span.bottom) / 2f
            cx in bbox.x0..bbox.x1 && cy in bbox.top..bbox.bottom
        }.sortedWith(compareBy<PdfTextSpan> { (it.top / 4f).toInt() }.thenBy { it.x0 })

        return inside.joinToString(" ") { it.text }.replace(multiSpaceRegex, " ").trim()
    }

    private fun detectDayColumns(cellTexts: Array<Array<String>>): Pair<Map<Int, DayColumnMeta>, Int> {
        val map = linkedMapOf<Int, DayColumnMeta>()
        val headerLimit = minOf(8, cellTexts.size)
        var lastHeaderRow = -1
        for (r in 0 until headerLimit) {
            for (c in cellTexts[r].indices) {
                val text = cellTexts[r][c]
                if (text.isBlank()) continue
                val dateMatch = dateRegex.find(text)
                if (dateMatch != null) {
                    map.getOrPut(c) { DayColumnMeta() }.data = dateMatch.groupValues[1]
                    lastHeaderRow = maxOf(lastHeaderRow, r)
                } else {
                    val norm = CasAndJsfParser.normalizeLabel(text)
                    if (norm.contains("feira") || norm.contains("sabado") || norm.contains("domingo")) {
                        map.getOrPut(c) { DayColumnMeta() }.diaSemana = text
                        lastHeaderRow = maxOf(lastHeaderRow, r)
                    } else if (norm.contains("composicao")) {
                        lastHeaderRow = maxOf(lastHeaderRow, r)
                    }
                }
            }
        }
        val filtered = map.filterValues { it.data != null || it.diaSemana != null }
        val headerRowsEnd = if (lastHeaderRow >= 0) (lastHeaderRow + 1).coerceAtMost(cellTexts.size) else minOf(4, cellTexts.size)
        return filtered to headerRowsEnd
    }

    private fun detectCategoryColumn(
        grid: PdfGrid,
        cellTexts: Array<Array<String>>,
        firstDayCol: Int,
    ): Int {
        if (firstDayCol <= 0) return 0
        for (r in cellTexts.indices) {
            for (c in 0 until firstDayCol) {
                if (CasAndJsfParser.normalizeLabel(cellTexts[r][c]).contains("composicao")) {
                    return c
                }
            }
        }
        return (0 until firstDayCol).maxByOrNull { c -> grid.colBounds[c + 1] - grid.colBounds[c] }
            ?: (firstDayCol - 1)
    }

    private fun isHorizontallyMergedDayRow(
        lines: List<PdfLineSegment>,
        grid: PdfGrid,
        row: Int,
        dayCols: Set<Int>,
    ): Boolean {
        if (dayCols.size <= 1) return false
        val rowTop = grid.rowBounds[row]
        val rowBottom = grid.rowBounds[row + 1]
        val rowMidY = (rowTop + rowBottom) / 2f
        val internalColXs = dayCols.sorted().drop(1).map { grid.colBounds[it] }

        val hasInternalVerticalDivider = internalColXs.any { colX ->
            lines.any { seg ->
                val isVertical = abs(seg.x1 - seg.x0) <= 2.5f
                val segX = (seg.x0 + seg.x1) / 2f
                val segTop = minOf(seg.top, seg.bottom)
                val segBottom = maxOf(seg.top, seg.bottom)
                isVertical && abs(segX - colX) <= 3.0f && segTop <= rowMidY && segBottom >= rowMidY
            }
        }
        return !hasInternalVerticalDivider
    }

    private fun findSingleMergedDayBbox(grid: PdfGrid, row: Int, dayCols: Set<Int>): CellBbox? {
        if (dayCols.isEmpty()) return null
        val minCol = dayCols.min()
        val maxCol = dayCols.max()
        return CellBbox(
            x0 = grid.colBounds[minCol],
            top = grid.rowBounds[row],
            x1 = grid.colBounds[maxCol + 1],
            bottom = grid.rowBounds[row + 1],
        )
    }

    private fun collectAllergens(icons: List<ClassifiedIcon>, bbox: CellBbox): List<Alergenico> {
        val detected = mutableListOf<Alergenico>()
        for (ic in icons) {
            if (ic.cx in bbox.x0..bbox.x1 && ic.cy in bbox.top..bbox.bottom) {
                for (alg in ic.alergenicos) {
                    if (alg !in detected) detected += alg
                }
            }
        }
        return detected
    }

    /**
     * Separa glifos de uma mesma linha visual do PDFTextStripper em spans por palavra/celula
     * para impedir que colunas vizinhas sejam fundidas em um unico bounding box horizontal.
     */
    fun splitLineGlyphsIntoCellSpans(glyphs: List<PositionedGlyph>): List<PdfTextSpan> {
        if (glyphs.isEmpty()) return emptyList()
        val spans = mutableListOf<PdfTextSpan>()
        val current = mutableListOf<PositionedGlyph>()

        fun flushCurrent() {
            if (current.isEmpty()) return
            val joined = buildString {
                current.forEach { append(it.text) }
            }.trim()
            if (joined.isNotEmpty()) {
                spans += PdfTextSpan(
                    text = joined,
                    x0 = current.minOf { it.x0 },
                    top = current.minOf { it.top },
                    x1 = current.maxOf { it.x1 },
                    bottom = current.maxOf { it.bottom },
                )
            }
            current.clear()
        }

        var prevGlyph: PositionedGlyph? = null
        for (glyph in glyphs) {
            val isWhitespace = glyph.text.isBlank()
            val gapThreshold = maxOf(glyph.widthOfSpace * 1.4f, 3.5f)
            val hasColumnGap = prevGlyph != null && (glyph.x0 - prevGlyph.x1) > gapThreshold
            if (isWhitespace || hasColumnGap) {
                flushCurrent()
            }
            if (!isWhitespace) {
                current += glyph
            }
            prevGlyph = glyph
        }
        flushCurrent()
        return spans
    }

    /**
     * Classifica retangulos preenchidos (`re -> f*`): bordas finas de tabela viram segmentos de grade,
     * enquanto caixas internas de destaque de texto ficam isoladas em fallback.
     */
    fun classifyFilledRectSegments(
        rect: FilledPdfRect,
        thinBorderLines: MutableList<PdfLineSegment>,
        fallbackFillLines: MutableList<PdfLineSegment>,
    ) {
        val width = abs(rect.x1 - rect.x0)
        val height = abs(rect.bottom - rect.top)
        when {
            height <= 3.5f && width >= 15f -> {
                val midY = (rect.top + rect.bottom) / 2f
                thinBorderLines += PdfLineSegment(rect.x0, midY, rect.x1, midY)
            }
            width <= 3.5f && height >= 15f -> {
                val midX = (rect.x0 + rect.x1) / 2f
                thinBorderLines += PdfLineSegment(midX, rect.top, midX, rect.bottom)
            }
            width >= 15f || height >= 15f -> {
                fallbackFillLines += PdfLineSegment(rect.x0, rect.top, rect.x1, rect.top)
                fallbackFillLines += PdfLineSegment(rect.x0, rect.bottom, rect.x1, rect.bottom)
                fallbackFillLines += PdfLineSegment(rect.x0, rect.top, rect.x0, rect.bottom)
                fallbackFillLines += PdfLineSegment(rect.x1, rect.top, rect.x1, rect.bottom)
            }
        }
    }

    fun selectGridLineSegments(
        thinOrStroked: List<PdfLineSegment>,
        fallbackFillLines: List<PdfLineSegment>,
    ): List<PdfLineSegment> =
        if (thinOrStroked.size >= 8) thinOrStroked else thinOrStroked + fallbackFillLines

    private data class CellBbox(val x0: Float, val top: Float, val x1: Float, val bottom: Float)

    private class PdfGrid(val colBounds: List<Float>, val rowBounds: List<Float>) {
        fun cellBbox(row: Int, col: Int): CellBbox = CellBbox(
            x0 = colBounds[col],
            top = rowBounds[row],
            x1 = colBounds[col + 1],
            bottom = rowBounds[row + 1],
        )
    }

    private data class DayColumnMeta(var data: String? = null, var diaSemana: String? = null)

    private data class ClassifiedIcon(val cx: Float, val cy: Float, val alergenicos: List<Alergenico>)

    private data class MutableCardapioDia(
        val dataTexto: String,
        val diaSemana: String,
        val refeicoes: MutableMap<TipoRefeicao, MutableList<ItemCardapioRu>> = linkedMapOf(),
    )
}
