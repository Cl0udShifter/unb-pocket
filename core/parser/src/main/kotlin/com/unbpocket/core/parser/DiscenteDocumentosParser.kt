package com.unbpocket.core.parser

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.NotaDisciplinaBoletim
import org.jsoup.Jsoup

data class CarteirinhaPdfExtractedInput(
    val textLines: List<String>,
    val img2Foto: RgbImageBuffer?,
    val img4Qr: RgbImageBuffer?,
)

data class CarteirinhaParsed(
    val nomeCompleto: String,
    val matricula: String,
    val cpfTexto: String?,
    val curso: String,
    val nivel: String,
    val validadeTexto: String,
    val qrPayload16Digitos: String,
    val qrImage: RgbImageBuffer,
    val foto3x4: RgbImageBuffer?,
)

/**
 * Parsers de boletim geral, codigo de verificacao de documentos e
 * dados/QR Code da Carteirinha Estudantil em PDF.
 */
object DiscenteDocumentosParser {
    private val cpfRegex = Regex("""\d{3}\.\d{3}\.\d{3}-\d{2}""")
    private val matriculaRegex = Regex("""\b\d{9}\b""")
    private val qr16DigitsRegex = Regex("""\d{16}""")
    private val verificationRegex = Regex(
        """(?:código de verificação|codigo de verificacao)\s*:?\s*([a-zA-Z0-9]{6,24})""",
        RegexOption.IGNORE_CASE,
    )

    fun parseBoletimGeral(html: String): CoreResult<List<BoletimSemestre>> {
        val doc = Jsoup.parse(html)
        val tables = doc.select("table.tabelaRelatorio")
        if (tables.isEmpty()) {
            val normalized = CasAndJsfParser.normalizeLabel(doc.text())
            return if (normalized.contains("relatorio de notas") || normalized.contains("nao possui") || normalized.contains("nenhuma")) {
                CoreResult.Success(emptyList())
            } else {
                CoreResult.Failure(
                    CoreError.ParseError(
                        source = "relatorioNotasAluno",
                        reason = "Tabelas de boletim (table.tabelaRelatorio) nao encontradas.",
                    ),
                )
            }
        }

        val semestres = tables.mapNotNull { table ->
            val semestre = table.selectFirst("caption")?.text()?.trim().orEmpty()
            val disciplinas = table.select("tbody tr").mapNotNull { row ->
                val cols = row.select("td")
                if (cols.size < 6) return@mapNotNull null
                val codigo = cols[0].text().trim()
                val nome = cols[1].text().trim()
                if (codigo.isEmpty() && nome.isEmpty()) return@mapNotNull null
                val nota1 = cols[2].text().trim().takeIf { it.isNotEmpty() && it != "-" && it != "--" }
                val mencao = cols[3].text().trim().takeIf { it.isNotEmpty() && it != "-" && it != "--" }
                val faltas = cols[4].text().trim().toIntOrNull()
                val situacao = cols[5].text().trim()
                NotaDisciplinaBoletim(
                    codigo = codigo,
                    disciplina = nome,
                    nota1 = nota1,
                    resultadoMencao = mencao,
                    faltas = faltas,
                    situacao = situacao,
                )
            }
            if (semestre.isEmpty() && disciplinas.isEmpty()) null else BoletimSemestre(semestre, disciplinas)
        }

        return CoreResult.Success(semestres)
    }

    fun extractVerificationCode(htmlOrText: String): String? =
        verificationRegex.find(htmlOrText)?.groupValues?.getOrNull(1)

    fun decodeQrPayloadFromRgb(image: RgbImageBuffer): String? {
        val pixelCount = image.width * image.height
        val argb = IntArray(pixelCount)
        for (i in 0 until pixelCount) {
            val base = i * 3
            val r = image.rgbBytes[base].toInt() and 0xFF
            val g = image.rgbBytes[base + 1].toInt() and 0xFF
            val b = image.rgbBytes[base + 2].toInt() and 0xFF
            argb[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val source = RGBLuminanceSource(image.width, image.height, argb)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        return runCatching {
            MultiFormatReader().decode(bitmap, hints).text?.trim()
        }.getOrNull()
    }

    fun parseCarteirinha(input: CarteirinhaPdfExtractedInput): CoreResult<CarteirinhaParsed> {
        val qrImg = input.img4Qr ?: return CoreResult.Failure(
            CoreError.PdfExtractionError(
                documentType = "Carteirinha Estudantil",
                reason = "Imagem do QR Code (img4 250x250) nao encontrada no PDF.",
            ),
        )

        val decodedRaw = decodeQrPayloadFromRgb(qrImg)
        val qrPayload = decodedRaw?.let { qr16DigitsRegex.find(it)?.value ?: it }
            ?: return CoreResult.Failure(
                CoreError.PdfExtractionError(
                    documentType = "Carteirinha Estudantil",
                    reason = "Nao foi possivel decodificar o QR Code de 16 digitos em img4.",
                ),
            )

        val lines = input.textLines.map { it.trim() }.filter { it.isNotEmpty() }
        val cpf = lines.firstNotNullOfOrNull { cpfRegex.find(it)?.value }
        val matricula = lines.firstNotNullOfOrNull { matriculaRegex.find(it)?.value }.orEmpty()
        val validade = lines.firstOrNull { CasAndJsfParser.normalizeLabel(it).contains("validade") }.orEmpty()
        val nivel = lines.firstOrNull { line ->
            val norm = CasAndJsfParser.normalizeLabel(line)
            norm == "graduacao" || norm == "pos-graduacao" || norm.contains("graduacao")
        }.orEmpty()

        val nome = lines.firstOrNull { line ->
            line != validade &&
                line != nivel &&
                cpfRegex.find(line) == null &&
                matriculaRegex.find(line) == null &&
                !CasAndJsfParser.normalizeLabel(line).contains("universidade de brasilia") &&
                !CasAndJsfParser.normalizeLabel(line).contains("identidade estudantil")
        }.orEmpty()

        val curso = lines.lastOrNull { line ->
            line != nome &&
                line != validade &&
                line != nivel &&
                cpfRegex.find(line) == null &&
                matriculaRegex.find(line) == null &&
                !CasAndJsfParser.normalizeLabel(line).contains("universidade de brasilia")
        }.orEmpty()

        return CoreResult.Success(
            CarteirinhaParsed(
                nomeCompleto = nome,
                matricula = matricula,
                cpfTexto = cpf,
                curso = curso,
                nivel = nivel,
                validadeTexto = validade,
                qrPayload16Digitos = qrPayload,
                qrImage = qrImg,
                foto3x4 = input.img2Foto,
            ),
        )
    }
}
