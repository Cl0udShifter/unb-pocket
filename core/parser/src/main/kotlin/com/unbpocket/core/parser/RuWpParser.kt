package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.GrupoAcessoRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.PrecoGrupoRu
import com.unbpocket.core.model.ru.TabelaPrecosRu
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CampusPdfLink(
    val campus: CampusRu,
    val semanaLabel: String,
    val pdfUrl: String,
)

data class RuWpMenuPage(
    val modifiedGmt: String,
    val campusLinks: Map<CampusRu, CampusPdfLink>,
)

data class RuWpMediaItem(
    val sourceUrl: String,
    val title: String,
    val dateGmt: String,
    val fallbackFullImageUrl: String?,
)

@Serializable
private data class WpRenderedDto(
    val rendered: String = "",
)

@Serializable
private data class WpPageDto(
    val id: Long = 0,
    @SerialName("modified_gmt") val modifiedGmt: String = "",
    val content: WpRenderedDto = WpRenderedDto(),
)

@Serializable
private data class WpMediaSizeDto(
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
private data class WpMediaSizesDto(
    val full: WpMediaSizeDto? = null,
)

@Serializable
private data class WpMediaDetailsDto(
    val sizes: WpMediaSizesDto? = null,
)

@Serializable
private data class WpMediaDto(
    @SerialName("date_gmt") val dateGmt: String = "",
    val title: WpRenderedDto = WpRenderedDto(),
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("media_details") val mediaDetails: WpMediaDetailsDto? = null,
)

@Serializable
private data class WpFeaturedMediaDto(
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
private data class WpEmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<WpFeaturedMediaDto> = emptyList(),
)

@Serializable
private data class WpPostDto(
    val id: Long = 0,
    val date: String = "",
    val link: String = "",
    val title: WpRenderedDto = WpRenderedDto(),
    val excerpt: WpRenderedDto = WpRenderedDto(),
    val content: WpRenderedDto = WpRenderedDto(),
    @SerialName("_embedded") val embedded: WpEmbeddedDto? = null,
)

/**
 * Parsers das respostas JSON da WordPress REST API v2 de `ru.unb.br`.
 */
object RuWpParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val dateRangeRegex = Regex("""(\d{2}/\d{2}/\d{4})\s*[aàAÀ\-]+\s*(\d{2}/\d{2}/\d{4})""")
    private val brDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun parseMenuPage(
        jsonBody: String,
        referenceDate: LocalDate = LocalDate.now(),
    ): CoreResult<RuWpMenuPage> = runCatching {
        val page = json.decodeFromString<WpPageDto>(jsonBody)
        val doc = Jsoup.parse(page.content.rendered)
        val candidatesByCampus = linkedMapOf<CampusRu, MutableList<CampusPdfLink>>()
        var currentCampus: CampusRu? = null

        for (element in doc.select("h2, h3, a[href*='.pdf']")) {
            val tag = element.tagName().lowercase()
            if (tag == "h2" || tag == "h3") {
                val normalizedHeading = CasAndJsfParser.normalizeLabel(element.text())
                if (normalizedHeading.contains("ultimas noticias")) {
                    currentCampus = null
                    break
                }
                currentCampus = if (normalizedHeading.contains("cardapio")) {
                    CampusRu.entries.firstOrNull { c ->
                        normalizedHeading.contains(CasAndJsfParser.normalizeLabel(c.headingKeyword))
                    }
                } else {
                    null
                }
            } else if (tag == "a" && currentCampus != null) {
                val href = element.attr("href").trim()
                if (href.isNotEmpty()) {
                    candidatesByCampus
                        .getOrPut(currentCampus) { mutableListOf() }
                        .add(
                            CampusPdfLink(
                                campus = currentCampus,
                                semanaLabel = element.text().trim(),
                                pdfUrl = href,
                            ),
                        )
                }
            }
        }

        val links = linkedMapOf<CampusRu, CampusPdfLink>()
        for ((campus, candidates) in candidatesByCampus) {
            selectActiveWeekLink(candidates, referenceDate)?.let { links[campus] = it }
        }

        if (links.isEmpty()) {
            return@runCatching CoreResult.Failure(
                CoreError.ParseError(
                    source = "wp/v2/pages/12853",
                    reason = "Nenhum link PDF de campus encontrado em content.rendered.",
                ),
            )
        }
        CoreResult.Success(RuWpMenuPage(modifiedGmt = page.modifiedGmt, campusLinks = links))
    }.getOrElse { err ->
        CoreResult.Failure(CoreError.ParseError("wp/v2/pages/12853", err.message ?: "JSON invalido"))
    }

    private fun selectActiveWeekLink(
        candidates: List<CampusPdfLink>,
        referenceDate: LocalDate,
    ): CampusPdfLink? {
        if (candidates.isEmpty()) return null
        val coveringToday = candidates.firstOrNull { link ->
            val match = dateRangeRegex.find(link.semanaLabel) ?: return@firstOrNull false
            val start = runCatching { LocalDate.parse(match.groupValues[1], brDateFormatter) }.getOrNull()
            val end = runCatching { LocalDate.parse(match.groupValues[2], brDateFormatter) }.getOrNull()
            start != null && end != null && !referenceDate.isBefore(start) && !referenceDate.isAfter(end)
        }
        return coveringToday ?: candidates.last()
    }

    fun parseMediaList(jsonBody: String): CoreResult<List<RuWpMediaItem>> = runCatching {
        val dtos = json.decodeFromString<List<WpMediaDto>>(jsonBody)
        CoreResult.Success(
            dtos.map { dto ->
                RuWpMediaItem(
                    sourceUrl = dto.sourceUrl,
                    title = Jsoup.parse(dto.title.rendered).text().trim(),
                    dateGmt = dto.dateGmt,
                    fallbackFullImageUrl = dto.mediaDetails?.sizes?.full?.sourceUrl,
                )
            },
        )
    }.getOrElse { err ->
        CoreResult.Failure(CoreError.ParseError("wp/v2/media", err.message ?: "JSON invalido"))
    }

    fun parseNoticias(jsonBody: String): CoreResult<List<NoticiaRu>> = runCatching {
        val dtos = json.decodeFromString<List<WpPostDto>>(jsonBody)
        CoreResult.Success(
            dtos.map { dto ->
                val contentDoc = Jsoup.parse(dto.content.rendered)
                val pdfLinks = contentDoc.select("a[href*='.pdf']").map { it.attr("href").trim() }.distinct()
                NoticiaRu(
                    id = dto.id,
                    dateIso = dto.date,
                    title = Jsoup.parse(dto.title.rendered).text().trim(),
                    excerptText = Jsoup.parse(dto.excerpt.rendered).text().trim(),
                    contentHtml = dto.content.rendered,
                    link = dto.link,
                    featuredImageUrl = dto.embedded?.featuredMedia?.firstOrNull()?.sourceUrl,
                    anexoPdfUrls = pdfLinks,
                )
            },
        )
    }.getOrElse { err ->
        CoreResult.Failure(CoreError.ParseError("wp/v2/posts", err.message ?: "JSON invalido"))
    }

    fun parsePricesPage(jsonBody: String): TabelaPrecosRu = runCatching {
        val pages = json.decodeFromString<List<WpPageDto>>(jsonBody)
        val page = pages.firstOrNull() ?: return RuCalculator.defaultPrecosResolucao0037
        val doc = Jsoup.parse(page.content.rendered)
        val fallback = RuCalculator.defaultPrecosResolucao0037
        val parsedGroups = mutableListOf<PrecoGrupoRu>()

        for (row in doc.select("table tr")) {
            val cols = row.select("td")
            if (cols.size < 4) continue
            val groupText = cols[0].text().trim()
            val norm = CasAndJsfParser.normalizeLabel(groupText)
            val grupo = when {
                norm.contains("executivo") -> GrupoAcessoRu.EXECUTIVO
                else -> RuCalculator.detectGrupo(groupText)
            }
            if (grupo == GrupoAcessoRu.DESCONHECIDO) continue

            val fallbackGroup = fallback.grupos.firstOrNull { it.grupo == grupo }
            val cafe = parseCellPrice(cols.getOrNull(cols.size - 3)?.text()) ?: fallbackGroup?.cafeCentavos
            val almoco = parseCellPrice(cols.getOrNull(cols.size - 2)?.text()) ?: fallbackGroup?.almocoCentavos
            val jantar = parseCellPrice(cols.getOrNull(cols.size - 1)?.text()) ?: fallbackGroup?.jantarCentavos
            val publico = if (cols.size >= 5) cols[1].text().trim() else grupo.descricao

            parsedGroups += if (grupo == GrupoAcessoRu.EXECUTIVO) {
                PrecoGrupoRu(
                    grupo = grupo,
                    publicoAlvo = publico,
                    cafeCentavos = null,
                    almocoCentavos = null,
                    jantarCentavos = null,
                    porKgCentavos = almoco ?: fallbackGroup?.porKgCentavos,
                )
            } else {
                PrecoGrupoRu(
                    grupo = grupo,
                    publicoAlvo = publico,
                    cafeCentavos = cafe,
                    almocoCentavos = almoco,
                    jantarCentavos = jantar,
                    porKgCentavos = null,
                )
            }
        }

        if (parsedGroups.isEmpty()) {
            fallback.copy(modifiedGmt = page.modifiedGmt.ifEmpty { fallback.modifiedGmt })
        } else {
            fallback.copy(
                modifiedGmt = page.modifiedGmt.ifEmpty { fallback.modifiedGmt },
                grupos = parsedGroups,
            )
        }
    }.getOrDefault(RuCalculator.defaultPrecosResolucao0037)

    private fun parseCellPrice(text: String?): Long? {
        if (text == null) return null
        val norm = CasAndJsfParser.normalizeLabel(text)
        if (norm.contains("gratuit")) return 0L
        return RuCalculator.parseBrlToCentavos(text)
    }
}
