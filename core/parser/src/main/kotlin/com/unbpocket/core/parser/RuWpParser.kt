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

    fun parseMenuPage(jsonBody: String): CoreResult<RuWpMenuPage> = runCatching {
        val page = json.decodeFromString<WpPageDto>(jsonBody)
        val doc = Jsoup.parse(page.content.rendered)
        val links = linkedMapOf<CampusRu, CampusPdfLink>()

        val headings = doc.select("h3.elementor-heading-title, h3, h2")
        for (heading in headings) {
            val normalizedHeading = CasAndJsfParser.normalizeLabel(heading.text())
            val campus = CampusRu.entries.firstOrNull { c ->
                normalizedHeading.contains(CasAndJsfParser.normalizeLabel(c.headingKeyword))
            } ?: continue

            var sibling = heading.nextElementSibling()
            var foundLink: CampusPdfLink? = null
            var hops = 0
            while (sibling != null && hops < 5 && foundLink == null) {
                val anchor = sibling.selectFirst("a[href*='.pdf']")
                    ?: if (sibling.tagName() == "a" && sibling.attr("href").contains(".pdf")) sibling else null
                if (anchor != null) {
                    val href = anchor.attr("href").trim()
                    val label = anchor.text().trim()
                    if (href.isNotEmpty()) {
                        foundLink = CampusPdfLink(campus = campus, semanaLabel = label, pdfUrl = href)
                    }
                }
                sibling = sibling.nextElementSibling()
                hops++
            }
            if (foundLink == null) {
                val parentBlockAnchor = heading.parent()?.parent()?.selectFirst("a[href*='.pdf']")
                if (parentBlockAnchor != null) {
                    foundLink = CampusPdfLink(
                        campus = campus,
                        semanaLabel = parentBlockAnchor.text().trim(),
                        pdfUrl = parentBlockAnchor.attr("href").trim(),
                    )
                }
            }
            if (foundLink != null) {
                links[campus] = foundLink
            }
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
