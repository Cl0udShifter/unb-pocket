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
}
