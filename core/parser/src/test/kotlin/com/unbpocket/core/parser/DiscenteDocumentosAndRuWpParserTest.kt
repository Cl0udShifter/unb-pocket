package com.unbpocket.core.parser

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.TipoRefeicao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiscenteDocumentosAndRuWpParserTest {
    @Test
    fun parsesMultiSemesterBoletimAndEmptyFresherBoletim() {
        val html = """
            <html><body>
              <table class="tabelaRelatorio">
                <caption>2026.1</caption>
                <tbody>
                  <tr><td>ENE0045</td><td>ELETRÔNICA</td><td>SS</td><td>SS</td><td>0</td><td>APROVADO(A)</td></tr>
                  <tr><td>CIC0004</td><td>ALGORITMOS</td><td>-</td><td>SR</td><td>46</td><td>REPROVADO(A) POR FALTAS</td></tr>
                </tbody>
              </table>
            </body></html>
        """.trimIndent()

        val boletim = (DiscenteDocumentosParser.parseBoletimGeral(html) as CoreResult.Success).value
        assertEquals(1, boletim.size)
        assertEquals("2026.1", boletim[0].semestre)
        assertEquals(2, boletim[0].disciplinas.size)
        assertEquals("SS", boletim[0].disciplinas[0].resultadoMencao)
        assertEquals(46, boletim[0].disciplinas[1].faltas)

        val emptyFresher = DiscenteDocumentosParser.parseBoletimGeral(
            "<html><body><h3>Relatório de Notas do(a) Aluno(a)</h3><p>Não possui registros.</p></body></html>",
        )
        assertTrue(emptyFresher is CoreResult.Success)
        assertTrue((emptyFresher as CoreResult.Success).value.isEmpty())
    }

    @Test
    fun decodesReal250x250QrImageFromCarteirinha() {
        val expectedPayload = "7744502674419362"
        val bitMatrix = MultiFormatWriter().encode(expectedPayload, BarcodeFormat.QR_CODE, 250, 250)
        val rgbBytes = ByteArray(250 * 250 * 3)
        for (y in 0 until 250) {
            for (x in 0 until 250) {
                val color = if (bitMatrix.get(x, y)) 0.toByte() else 255.toByte()
                val off = (y * 250 + x) * 3
                rgbBytes[off] = color
                rgbBytes[off + 1] = color
                rgbBytes[off + 2] = color
            }
        }

        val input = CarteirinhaPdfExtractedInput(
            textLines = listOf(
                "Universidade de Brasília",
                "DANIEL ALEXANDRO CONTREIRA JUNIOR",
                "190026308",
                "VALIDADE Março 2027",
                "033.145.241-37",
                "GRADUAÇÃO",
                "ENGENHARIA DE REDES DE COMUNICAÇÃO",
            ),
            img2Foto = RgbImageBuffer(10, 10, ByteArray(10 * 10 * 3)),
            img4Qr = RgbImageBuffer(250, 250, rgbBytes),
        )

        val parsed = (DiscenteDocumentosParser.parseCarteirinha(input) as CoreResult.Success).value
        assertEquals(expectedPayload, parsed.qrPayload16Digitos)
        assertEquals("190026308", parsed.matricula)
        assertEquals("033.145.241-37", parsed.cpfTexto)
        assertEquals("GRADUAÇÃO", parsed.nivel)
    }

    @Test
    fun parsesWpMenuPageForAllFiveCampiAndPosts() {
        val wpMenuJson = """
            {
              "id": 12853,
              "modified_gmt": "2026-11-19T11:19:39",
              "content": {
                "rendered": "<h3 class=\"elementor-heading-title\">Cardápio Darcy Ribeiro</h3><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Darcy.pdf\">23/11/2026 à 29/11/2026</a></p><h3 class=\"elementor-heading-title\">Cardápio Ceilândia</h3><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Ceilandia.pdf\">23/11 à 28/11</a></p><h3 class=\"elementor-heading-title\">Cardápio Gama</h3><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Gama.pdf\">23/11 à 27/11</a></p><h3 class=\"elementor-heading-title\">Cardápio Planaltina</h3><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Planaltina.pdf\">23/11 à 29/11</a></p><h3 class=\"elementor-heading-title\">Cardápio Fazenda Água Limpa</h3><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/FAL.pdf\">23/11 à 27/11</a></p>"
              }
            }
        """.trimIndent()

        val menuPage = (RuWpParser.parseMenuPage(wpMenuJson) as CoreResult.Success).value
        assertEquals("2026-11-19T11:19:39", menuPage.modifiedGmt)
        assertEquals(5, menuPage.campusLinks.size)
        assertEquals("https://ru.unb.br/wp-content/uploads/2026/11/FAL.pdf", menuPage.campusLinks[CampusRu.FAZENDA_AGUA_LIMPA]?.pdfUrl)
    }

    @Test
    fun parsesRealElementorSectionWrappedWpPageAndSelectsActiveWeek() {
        val elementorJson = """
            {
              "id": 12853,
              "modified_gmt": "2026-11-19T11:19:39",
              "content": {
                "rendered": "<section class=\"elementor-section\"><div class=\"elementor-widget-container\"><h2 class=\"elementor-heading-title\">Confira o cardápio da semana:</h2></div></section><section class=\"elementor-section\"><div class=\"elementor-widget-container\"><h3 class=\"elementor-heading-title\">Cardápio Darcy Ribeiro </h3></div></section><section class=\"elementor-section\"><div class=\"elementor-widget-container\"><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Darcy-Semana-01.pdf\">ISM &#8211; 16/11/2026 À 22/11/2026</a></p><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Darcy-Semana-02.pdf\">ISM &#8211; 23/11/2026 À 29/11/2026</a></p></div></section><section class=\"elementor-section\"><div class=\"elementor-widget-container\"><h3 class=\"elementor-heading-title\">Cardápio Ceilândia</h3></div></section><section class=\"elementor-section\"><div class=\"elementor-widget-container\"><p><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Ceilandia-Semana-02.pdf\">ISM &#8211; 23/11/2026 À 29/11/2026</a></p></div></section><section class=\"elementor-section\"><div class=\"elementor-widget-container\"><h3 class=\"title\">Últimas Notícias</h3><a href=\"https://ru.unb.br/wp-content/uploads/2026/11/Edital-Gama.pdf\">Edital Gama</a></div></section>"
              }
            }
        """.trimIndent()

        val week1 = (RuWpParser.parseMenuPage(elementorJson, LocalDate.of(2026, 11, 22)) as CoreResult.Success).value
        assertEquals(
            "https://ru.unb.br/wp-content/uploads/2026/11/Darcy-Semana-01.pdf",
            week1.campusLinks[CampusRu.DARCY_RIBEIRO]?.pdfUrl,
        )
        assertEquals(
            "https://ru.unb.br/wp-content/uploads/2026/11/Ceilandia-Semana-02.pdf",
            week1.campusLinks[CampusRu.CEILANDIA]?.pdfUrl,
        )
        assertEquals(2, week1.campusLinks.size)

        val week2 = (RuWpParser.parseMenuPage(elementorJson, LocalDate.of(2026, 11, 23)) as CoreResult.Success).value
        assertEquals(
            "https://ru.unb.br/wp-content/uploads/2026/11/Darcy-Semana-02.pdf",
            week2.campusLinks[CampusRu.DARCY_RIBEIRO]?.pdfUrl,
        )
    }

    @Test
    fun parsesPdfWithSixHeaderRowsAndVerticalSideBannerWithoutOverwritingCategories() {
        val legendBuffers = Alergenico.entries.sortedBy { it.legendOrderIndex }.associateWith { alg ->
            val v = (alg.legendOrderIndex * 24).toByte()
            RgbImageBuffer(32, 32, ByteArray(32 * 32 * 3) { v })
        }
        val legendImages = legendBuffers.entries.mapIndexed { idx, (_, buf) ->
            PdfLocatedImage("leg_$idx", 50f + idx * 60f, 543f, 70f + idx * 60f, 563f, buf)
        }

        // Simula layout real de Darcy Cafe da Manha: banner vertical (col 0: 21..40), COMPOSICAO (col 1: 46..208), 2a feira (col 2: 209..300)
        // Cabecalho tem 5 faixas horizontais (85, 92, 105, 117, 123), depois BEBIDAS (123..150) e sub-linha Cafe ou cha (150..180)
        val rowYs = listOf(85f, 92f, 105f, 117f, 123f, 150f, 180f)
        val colXs = listOf(21f, 46f, 209f, 300f)
        val lines = rowYs.map { y -> PdfLineSegment(21f, y, 300f, y) } +
            colXs.map { x -> PdfLineSegment(x, 85f, x, 180f) }

        val texts = listOf(
            PdfTextSpan("2ª FEIRA", 215f, 94f, 280f, 103f),
            PdfTextSpan("COMPOSIÇÃO", 60f, 99f, 160f, 112f),
            PdfTextSpan("(23/11/2026)", 215f, 106f, 290f, 115f),
            PdfTextSpan("Café da manhã", 23f, 155f, 38f, 175f),
            PdfTextSpan("BEBIDAS", 60f, 130f, 140f, 145f),
            PdfTextSpan("Leite integral", 215f, 130f, 290f, 145f),
            PdfTextSpan("Café ou chá", 215f, 158f, 285f, 172f),
        )

        val page = PdfMenuPageData(0, 842.04f, 595.56f, texts, lines, legendImages)
        val parsed = (RuMenuPdfParser.parsePages(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "23/11 à 29/11",
            pdfUrl = "https://ru.unb.br/test.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "2026-11-19T11:19:39",
            pages = listOf(page),
        ) as CoreResult.Success).value

        assertEquals(1, parsed.dias.size)
        val cafeItems = parsed.dias[0].refeicoes[TipoRefeicao.CAFE_DA_MANHA].orEmpty()
        assertEquals(2, cafeItems.size)
        assertEquals("BEBIDAS", cafeItems[0].categoria)
        assertEquals("BEBIDAS", cafeItems[1].categoria)
        assertEquals("Café ou chá", cafeItems[1].prato)
    }

    @Test
    fun parsesRuPdfGridWithMergedSharedRowAndPerCellAllergensWithoutLeakingAcrossDays() {
        val legendBuffers = Alergenico.entries.sortedBy { it.legendOrderIndex }.associateWith { alg ->
            val r = (alg.legendOrderIndex * 25).toByte()
            val g = (255 - alg.legendOrderIndex * 20).toByte()
            val b = (alg.legendOrderIndex * 15).toByte()
            RgbImageBuffer(
                width = 32,
                height = 32,
                rgbBytes = ByteArray(32 * 32 * 3) { i ->
                    when (i % 3) {
                        0 -> r
                        1 -> g
                        else -> b
                    }
                },
            )
        }

        val legendImages = legendBuffers.entries.mapIndexed { idx, (_, buf) ->
            val x = 50f + idx * 70f
            PdfLocatedImage(name = "leg_$idx", x0 = x, top = 540f, x1 = x + 20f, bottom = 560f, rgb = buf)
        }

        // Grid cols: [0]=20, [1]=120(cat), [2]=250(Mon), [3]=400(Tue), [4]=550
        // Grid rows: 0..3 headers (80,100,120,140,160), row 4 (160..200) merged BEBIDAS, row 5 (200..240) split COMPLEMENTO
        val rowYs = listOf(80f, 100f, 120f, 140f, 160f, 200f, 240f)
        val lines = mutableListOf<PdfLineSegment>()
        rowYs.forEach { y -> lines += PdfLineSegment(20f, y, 550f, y) }
        // Left boundary & category boundary span full table height
        lines += PdfLineSegment(20f, 80f, 20f, 240f)
        lines += PdfLineSegment(120f, 80f, 120f, 240f)
        lines += PdfLineSegment(250f, 80f, 250f, 240f)
        lines += PdfLineSegment(550f, 80f, 550f, 240f)
        // Internal divider at x=400 exists in header + row 5 (200..240), NOT in merged row 4 (160..200)
        lines += PdfLineSegment(400f, 80f, 400f, 160f)
        lines += PdfLineSegment(400f, 200f, 400f, 240f)

        val texts = listOf(
            PdfTextSpan("2ª FEIRA", 260f, 125f, 340f, 135f),
            PdfTextSpan("23/11/2026", 260f, 145f, 350f, 155f),
            PdfTextSpan("3ª FEIRA", 410f, 125f, 490f, 135f),
            PdfTextSpan("24/11/2026", 410f, 145f, 500f, 155f),
            PdfTextSpan("BEBIDAS", 130f, 170f, 210f, 185f),
            PdfTextSpan("Leite integral ou Bebida de soja", 260f, 170f, 390f, 185f),
            PdfTextSpan("COMPLEMENTO PADRÃO", 130f, 210f, 240f, 225f),
            PdfTextSpan("Salsicha ao molho", 260f, 210f, 370f, 225f),
            PdfTextSpan("Frango desfiado", 410f, 210f, 520f, 225f),
        )

        // Merged row icon at x=510 (inside Tuesday visual area, shared across merged row!) -> LEITE
        val mergedLeiteIcon = PdfLocatedImage(
            name = "merged_leite",
            x0 = 500f,
            top = 170f,
            x1 = 520f,
            bottom = 190f,
            rgb = legendBuffers.getValue(Alergenico.LEITE),
        )
        // Monday-only SUINO icon at x=370 in row 5
        val monSuinoIcon = PdfLocatedImage(
            name = "mon_suino",
            x0 = 365f,
            top = 210f,
            x1 = 385f,
            bottom = 230f,
            rgb = legendBuffers.getValue(Alergenico.SUINO),
        )

        val page = PdfMenuPageData(
            pageIndex = 0,
            pageWidth = 842f,
            pageHeight = 595f,
            texts = texts,
            lines = lines,
            images = legendImages + mergedLeiteIcon + monSuinoIcon,
        )

        val res = RuMenuPdfParser.parsePages(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "23/11 à 29/11",
            pdfUrl = "https://ru.unb.br/test.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "2026-11-19T11:19:39",
            pages = listOf(page),
        )

        assertTrue(res is CoreResult.Success)
        val menu = (res as CoreResult.Success).value
        assertEquals(2, menu.dias.size)

        val monCafe = menu.dias[0].refeicoes[TipoRefeicao.CAFE_DA_MANHA].orEmpty()
        val tueCafe = menu.dias[1].refeicoes[TipoRefeicao.CAFE_DA_MANHA].orEmpty()
        assertEquals(listOf(Alergenico.LEITE), monCafe[0].alergenicos)
        assertEquals(listOf(Alergenico.LEITE), tueCafe[0].alergenicos)
        assertEquals(listOf(Alergenico.SUINO), monCafe[1].alergenicos)
        assertTrue(tueCafe[1].alergenicos.isEmpty())
    }

    @Test
    fun splitsMergedPdfTextStripperRowIntoPerColumnSpansFiltersHighlightRectsAndExtractsFullMergedDish() {
        // Reproduz o bug real de pdfbox-android: PDFTextStripper envia toda a linha visual no mesmo writeString,
        // enquanto re->f* inclui caixas de preenchimento de texto dentro do cabecalho alem das bordas finas (0.96pt).
        fun wordGlyphs(word: String, startX: Float, yTop: Float): List<PositionedGlyph> =
            word.mapIndexed { idx, ch ->
                val gx0 = startX + idx * 5.2f
                PositionedGlyph(
                    text = ch.toString(),
                    x0 = gx0,
                    top = yTop,
                    x1 = gx0 + 4.8f,
                    bottom = yTop + 8f,
                    widthOfSpace = 2.5f,
                )
            }

        val headerDayGlyphs = buildList {
            addAll(wordGlyphs("2a", 230f, 92f))
            add(PositionedGlyph(" ", 241f, 92f, 243f, 100f, Float.NaN))
            addAll(wordGlyphs("FEIRA", 244f, 92f))
            addAll(wordGlyphs("3a", 321f, 92f))
            add(PositionedGlyph(" ", 332f, 92f, 334f, 100f, 2.5f))
            addAll(wordGlyphs("FEIRA", 335f, 92f))
        }
        val dateRowGlyphs = buildList {
            addAll(wordGlyphs("(16/11/2026)", 224f, 106f))
            addAll(wordGlyphs("(17/11/2026)", 315f, 106f))
        }
        val dishRowGlyphs = buildList {
            addAll(wordGlyphs("SALADA", 95f, 128f))
            add(PositionedGlyph(" ", 127f, 128f, 129f, 136f, 2.5f))
            addAll(wordGlyphs("1", 130f, 128f))
            addAll(wordGlyphs("Repolho", 220f, 128f))
            add(PositionedGlyph(" ", 258f, 128f, 260f, 136f, 2.5f))
            addAll(wordGlyphs("roxo", 261f, 128f))
            addAll(wordGlyphs("Alface", 315f, 128f))
        }

        val thinLines = mutableListOf<PdfLineSegment>()
        val fallbackLines = mutableListOf<PdfLineSegment>()
        // Bordas finas reais da tabela (0.96pt): col0=46, col1=208, col2=297, col3=390
        listOf(46f, 208f, 297f, 390f).forEach { x ->
            val topEnd = if (x == 297f) 123f else 220f
            RuMenuPdfParser.classifyFilledRectSegments(
                FilledPdfRect(x - 0.48f, 85f, x + 0.48f, topEnd),
                thinLines,
                fallbackLines,
            )
            if (x == 297f) {
                // col2..col3 dividido em row 145..220 (prato), mesclado em 123..145? Aqui row 145..220 mesclado!
                RuMenuPdfParser.classifyFilledRectSegments(
                    FilledPdfRect(x - 0.48f, 123f, x + 0.48f, 145f),
                    thinLines,
                    fallbackLines,
                )
            }
        }
        listOf(85f, 123f, 145f, 185f, 220f, 255f).forEach { y ->
            RuMenuPdfParser.classifyFilledRectSegments(
                FilledPdfRect(46f, y - 0.48f, 390f, y + 0.48f),
                thinLines,
                fallbackLines,
            )
        }
        // Caixas de destaque de texto internas dentro do cabecalho (h=12.6pt -> NAO podem fragmentar row 0)
        RuMenuPdfParser.classifyFilledRectSegments(
            FilledPdfRect(210.8f, 91.9f, 294.5f, 104.5f),
            thinLines,
            fallbackLines,
        )
        RuMenuPdfParser.classifyFilledRectSegments(
            FilledPdfRect(210.8f, 104.5f, 294.5f, 117.1f),
            thinLines,
            fallbackLines,
        )

        val resolvedLines = RuMenuPdfParser.selectGridLineSegments(thinLines, fallbackLines)

        // Sem quebrar glifos por coluna, uma linha unica do PDFTextStripper colapsa tudo em 1 span central e falha
        val collapsedHeaderSpan = PdfTextSpan("2a FEIRA 3a FEIRA", 230f, 92f, 360f, 100f)
        val unsplitRes = RuMenuPdfParser.parsePages(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "16/11 a 22/11",
            pdfUrl = "https://ru.unb.br/test.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "2026-11-19T11:19:39",
            pages = listOf(
                PdfMenuPageData(
                    1,
                    842.04f,
                    595.56f,
                    listOf(collapsedHeaderSpan),
                    resolvedLines,
                    emptyList(),
                ),
            ),
        )
        assertTrue(unsplitRes is CoreResult.Failure)

        val spans = RuMenuPdfParser.splitLineGlyphsIntoCellSpans(headerDayGlyphs) +
            RuMenuPdfParser.splitLineGlyphsIntoCellSpans(dateRowGlyphs) +
            listOf(PdfTextSpan("COMPOSIÇÃO", 89f, 100f, 168f, 109f)) +
            RuMenuPdfParser.splitLineGlyphsIntoCellSpans(dishRowGlyphs) +
            listOf(
                PdfTextSpan("GORDURA", 88f, 155f, 150f, 165f),
                // Texto de linha mesclada dividido entre col 2 (208..297) e col 3 (297..390)
                PdfTextSpan("Manteiga", 255f, 155f, 295f, 165f),
                PdfTextSpan("ou Creme", 299f, 155f, 345f, 165f),
                PdfTextSpan("Vegetal", 347f, 155f, 385f, 165f),
            )

        val res = RuMenuPdfParser.parsePages(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "16/11 a 22/11",
            pdfUrl = "https://ru.unb.br/test.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "2026-11-19T11:19:39",
            pages = listOf(PdfMenuPageData(1, 842.04f, 595.56f, spans, resolvedLines, emptyList())),
        )

        assertTrue(res is CoreResult.Success)
        val menu = (res as CoreResult.Success).value
        assertEquals(2, menu.dias.size)
        assertEquals("2a FEIRA", menu.dias[0].diaSemana)
        assertEquals("16/11/2026", menu.dias[0].dataTexto)
        assertEquals("3a FEIRA", menu.dias[1].diaSemana)
        assertEquals("17/11/2026", menu.dias[1].dataTexto)
        val mon = menu.dias[0].refeicoes[TipoRefeicao.ALMOCO].orEmpty()
        val tue = menu.dias[1].refeicoes[TipoRefeicao.ALMOCO].orEmpty()
        assertEquals("Repolho roxo", mon[0].prato)
        assertEquals("Alface", tue[0].prato)
        assertEquals("Manteiga ou Creme Vegetal", mon[1].prato)
        assertEquals("Manteiga ou Creme Vegetal", tue[1].prato)
    }
}
