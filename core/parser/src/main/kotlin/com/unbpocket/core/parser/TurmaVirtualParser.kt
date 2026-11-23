package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.turmas.AnexoArquivoSigaa
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.MaterialAulaAva
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.NoticiaTurmaResumoAva
import com.unbpocket.core.model.turmas.RegistroFrequenciaDia
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TopicoAulaAva
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.UnidadeNotaTurma
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.math.floor

data class AtestadoTurmaItem(
    val codigoDisciplina: String,
    val nomeDisciplina: String,
    val docenteNome: String?,
    val local: String?,
    val numeroTurma: String?,
    val horarioRaw: String,
)

data class ParsedAtestadoMatricula(
    val turmas: List<AtestadoTurmaItem>,
    val codigoVerificacao: String?,
)

data class ParsedAvaFormTurmaItem(
    val frontEndIdTurma: String,
    val codigoDisciplina: String,
    val nomeDisciplina: String,
    val cargaHorariaHoras: Int?,
    val semestre: String,
)

data class ParsedAvaMaterialRef(
    val material: MaterialAulaAva,
    val formParams: Map<String, String>,
)

data class ParsedAvaAnexoRef(
    val anexo: AnexoArquivoSigaa,
    val downloadUrl: String?,
    val formParams: Map<String, String>?,
)

data class ParsedAvaNoticiaRef(
    val resumo: NoticiaTurmaResumoAva,
    val formParams: Map<String, String>,
)

data class ParsedAvaHome(
    val detalhe: TurmaDetalheAva,
    val formTurmaItems: List<ParsedAvaFormTurmaItem>,
    val menuActions: Map<String, Map<String, String>>,
    val materialRefs: Map<String, ParsedAvaMaterialRef>,
    val anexoRefs: Map<String, ParsedAvaAnexoRef>,
    val noticiaRefs: Map<String, ParsedAvaNoticiaRef>,
)

data class ParsedAvaNoticiaCompleta(
    val noticia: NoticiaTurmaAva,
    val anexoRefs: Map<String, ParsedAvaAnexoRef>,
)

/**
 * Parsers do interior da Turma Virtual (`/sigaa/ava/index.jsf`) e do Atestado de Matricula.
 */
object TurmaVirtualParser {
    private val aulasCountRegex = Regex("""(\d+)\s*/\s*(\d+)""")
    private val progressRegex = Regex("""(\d+)\s*%""")
    private val formTurmaRegex = Regex("""^([A-Z]{2,}\d+)\s*-\s*(.+?)\s*\((\d+)h\)\s*\(([^)]+)\)$""")
    private val idArquivoRegex = Regex("""idArquivo=(\d+)""")
    private val verifCodeRegex = Regex("""(?:código de verificação|codigo de verificacao)\s*:?\s*([a-zA-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)
    private val digitsRegex = Regex("""\d+""")
    private val noticiaDateRegex = Regex("""\d{2}/\d{2}/\d{4}(?:\s+\d{2}:\d{2})?""")
    private val genericNoticeActions = setOf(
        "visualizar",
        "ver",
        "abrir",
        "ler",
        "detalhes",
        "ver mais",
        "saiba mais",
        "acessar",
    )
    private val unregisteredFrequenciaMarkers = listOf(
        "nao registrad",
        "nao lancad",
        "nao informad",
        "sem registr",
        "sem chamad",
        "nao houve",
        "nao ministrad",
        "feriado",
        "cancelad",
        "suspens",
    )

    fun parseAtestadoMatricula(html: String): ParsedAtestadoMatricula {
        val doc = Jsoup.parse(html)
        val rows = doc.select("table.listagem tbody tr, table#matriculas tbody tr")
        val items = mutableListOf<AtestadoTurmaItem>()

        for (row in rows) {
            val cols = row.select("td")
            if (cols.size < 5) continue
            val codigo = cols[0].text().trim()
            if (codigo.isEmpty() || !codigo.any { it.isDigit() }) continue

            val discHtml = cols[1].html()
            val discTextLines = discHtml
                .split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
                .map { Jsoup.parse(it).text().trim() }
                .filter { it.isNotEmpty() }

            val nomeDisciplina = discTextLines.firstOrNull().orEmpty()
            val docente = discTextLines.getOrNull(1)
                ?.takeUnless { it.startsWith("Tipo:", ignoreCase = true) || it.startsWith("Local:", ignoreCase = true) }
            val localLine = discTextLines.firstOrNull { it.contains("Local:", ignoreCase = true) }
                ?.substringAfter("Local:")
                ?.trim()

            val numeroTurma = cols[2].text().trim().takeIf { it.isNotEmpty() }
            val horarioRaw = cols[4].text().trim()

            items += AtestadoTurmaItem(
                codigoDisciplina = codigo,
                nomeDisciplina = nomeDisciplina,
                docenteNome = docente,
                local = localLine,
                numeroTurma = numeroTurma,
                horarioRaw = horarioRaw,
            )
        }

        val verif = verifCodeRegex.find(doc.text())?.groupValues?.getOrNull(1)
        return ParsedAtestadoMatricula(turmas = items, codigoVerificacao = verif)
    }

    fun parseAvaHome(html: String, frontEndIdTurma: String): CoreResult<ParsedAvaHome> {
        val doc = Jsoup.parse(html)
        val codigo = doc.selectFirst("#linkCodigoTurma")?.text()?.trim()?.trimEnd('-', ' ').orEmpty()
        val nome = doc.selectFirst("#linkNomeTurma")?.text()?.trim().orEmpty()
        if (codigo.isEmpty() && nome.isEmpty()) {
            return CoreResult.Failure(
                CoreError.ParseError(
                    source = "ava/index.jsf",
                    reason = "Cabecalho da Turma Virtual (#nomeTurma) nao encontrado.",
                ),
            )
        }

        val periodoTexto = doc.selectFirst("#linkPeriodoTurma")?.text()?.trim().orEmpty()
        val viewState = CasAndJsfParser.extractViewState(doc).orEmpty()

        val barraDireitaText = doc.selectFirst("#barraDireita")?.text().orEmpty()
        val aulasMatch = aulasCountRegex.find(barraDireitaText)
        val aulasMinistradas = aulasMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val aulasTotal = aulasMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        val percentualBarra = doc.selectFirst("#barraDireita .progress-bar")
            ?.let { el ->
                progressRegex.find(el.attr("style") + " " + el.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            ?: if (aulasTotal > 0) ((aulasMinistradas * 100.0) / aulasTotal).toInt() else 0

        val anexoRefs = linkedMapOf<String, ParsedAvaAnexoRef>()
        val ultimaNoticia = parseUltimaNoticia(doc, anexoRefs)
        val (noticiasResumo, noticiaRefs) = parseHistoricoNoticias(doc, viewState, ultimaNoticia)

        val materialRefs = linkedMapOf<String, ParsedAvaMaterialRef>()
        val topicos = parseTopicosAula(doc, viewState, materialRefs)
        val menuActions = parseFormMenuActions(doc, viewState)
        val formTurmaItems = parseFormTurmaItems(doc)

        val idTurma = doc.selectFirst("input[name=idTurma]")?.attr("value")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: frontEndIdTurma

        return CoreResult.Success(
            ParsedAvaHome(
                detalhe = TurmaDetalheAva(
                    idTurma = idTurma,
                    frontEndIdTurma = frontEndIdTurma,
                    codigoDisciplina = codigo,
                    nomeDisciplina = nome,
                    periodoTurmaTexto = periodoTexto,
                    aulasMinistradas = aulasMinistradas,
                    aulasTotal = aulasTotal,
                    percentualAndamento = percentualBarra,
                    ultimaNoticia = ultimaNoticia,
                    historicoNoticiasResumo = noticiasResumo,
                    topicosAula = topicos,
                ),
                formTurmaItems = formTurmaItems,
                menuActions = menuActions,
                materialRefs = materialRefs,
                anexoRefs = anexoRefs,
                noticiaRefs = noticiaRefs,
            ),
        )
    }

    fun parseAvaNoticiaCompleta(
        html: String,
        fallbackIdNoticia: String? = null,
    ): CoreResult<ParsedAvaNoticiaCompleta> {
        val doc = Jsoup.parse(html)
        val container = doc.selectFirst("#ultimaNoticia")
            ?: doc.selectFirst(".conteudoNoticia")?.parent()
            ?: doc.selectFirst("fieldset")
            ?: return CoreResult.Failure(
                CoreError.ParseError("ava/noticia", "Conteudo completo da noticia nao encontrado."),
            )

        val headerText = container.selectFirst("h4, h3, legend")?.text()?.trim().orEmpty()
        val parts = headerText.split(" - ")
        val dataHora = parts.lastOrNull()?.takeIf { it.contains("/") }?.trim().orEmpty()
        val titulo = if (dataHora.isNotEmpty() && parts.size > 1) {
            parts.dropLast(1).joinToString(" - ").trim()
        } else {
            headerText
        }

        val contentSpan = container.selectFirst("span.conteudoNoticia, .conteudoNoticia, p")
        val contentHtml = contentSpan?.html().orEmpty()
        val contentText = contentSpan?.text()?.trim().orEmpty()
        val autor = container.select("small i").lastOrNull()?.text()?.trim()

        val anexoRefs = linkedMapOf<String, ParsedAvaAnexoRef>()
        val anexos = container.select("a[href*='/sigaa/verArquivo']").mapIndexed { idx, link ->
            val href = link.attr("href").trim()
            val id = idArquivoRegex.find(href)?.groupValues?.getOrNull(1) ?: "anexo_$idx"
            val anexo = AnexoArquivoSigaa(id = id, nomeArquivo = link.text().trim())
            anexoRefs[id] = ParsedAvaAnexoRef(anexo = anexo, downloadUrl = href, formParams = null)
            anexo
        }

        return CoreResult.Success(
            ParsedAvaNoticiaCompleta(
                noticia = NoticiaTurmaAva(
                    idNoticia = fallbackIdNoticia,
                    titulo = titulo,
                    dataHoraTexto = dataHora,
                    conteudoHtml = contentHtml,
                    conteudoTexto = contentText,
                    autor = autor,
                    anexos = anexos,
                ),
                anexoRefs = anexoRefs,
            ),
        )
    }

    fun parseAvaFrequencia(html: String): CoreResult<MapaFrequenciaTurma> {
        val doc = Jsoup.parse(html)
        val bodyText = doc.body().text()
        if (!CasAndJsfParser.normalizeLabel(bodyText).contains("frequencia")) {
            return CoreResult.Failure(
                CoreError.ParseError("ava/frequencia", "Mapa de frequencias nao encontrado."),
            )
        }

        val registros = mutableListOf<RegistroFrequenciaDia>()
        for (row in doc.select("table.listagem tbody tr, fieldset table tbody tr")) {
            val cols = row.select("td")
            if (cols.size < 2) continue
            val data = cols[0].text().trim()
            val situacao = cols[1].text().trim()
            if (!data.contains("/")) continue
            val normSit = CasAndJsfParser.normalizeLabel(situacao)
            val presente = normSit.contains("presente") || normSit.contains("presenca")
            val unregistered = normSit.isBlank() ||
                normSit.all { it == '-' || it == '_' } ||
                unregisteredFrequenciaMarkers.any { normSit.contains(it) }
            val faltasDia = when {
                presente || unregistered -> 0
                else -> {
                    val parsedDigits = digitsRegex.find(situacao)?.value?.toIntOrNull()
                    when {
                        parsedDigits != null -> parsedDigits
                        normSit.contains("falta") || normSit.contains("ausent") -> 1
                        else -> 0
                    }
                }
            }
            registros += RegistroFrequenciaDia(
                dataTexto = data,
                situacaoTexto = situacao.ifBlank { "Não registrado" },
                faltasNoDia = faltasDia,
                presente = presente,
            )
        }

        val presencas = extractMetricInt(bodyText, "Presenças Registradas") ?: 0
        val comRegistro = extractMetricInt(bodyText, "Número de Aulas com Registro de Frequência") ?: 0
        val percRegistradas = extractMetricInt(bodyText, "Porcentagem de Frequência em relação as Aulas com Registro de Frequência") ?: 100
        val aulasCh = extractMetricInt(bodyText, "Número de Aulas definidas pela CH do Componente") ?: 0
        val percCh = extractMetricInt(bodyText, "Porcentagem de Frequência em relação a CH") ?: 100

        val faltasComputadas = (comRegistro - presencas).coerceAtLeast(
            registros.sumOf { it.faltasNoDia },
        )
        val limiteMaximo = floor(aulasCh * 0.25).toInt()
        val restantes = (limiteMaximo - faltasComputadas).coerceAtLeast(0)
        val emRisco = aulasCh > 0 && (faltasComputadas > limiteMaximo || restantes <= 2)

        return CoreResult.Success(
            MapaFrequenciaTurma(
                presencasRegistradas = presencas,
                aulasComRegistro = comRegistro,
                percentualSobreRegistradas = percRegistradas,
                aulasDefinidasCh = aulasCh,
                percentualSobreCh = percCh,
                faltasComputadas = faltasComputadas,
                limiteMaximoFaltas = limiteMaximo,
                faltasRestantes = restantes,
                emRiscoReprovacaoPorFalta = emRisco,
                registrosDiarios = registros,
            ),
        )
    }

    fun parseAvaNotasParciais(html: String): CoreResult<NotasParciaisTurma> {
        val doc = Jsoup.parse(html)
        val normalizedBody = CasAndJsfParser.normalizeLabel(doc.text())
        if (normalizedBody.contains("ainda nao foram lancadas notas")) {
            val aviso = doc.select(".aviso, .painelUsuario, fieldset p, #mensagens p")
                .firstOrNull { CasAndJsfParser.normalizeLabel(it.text()).contains("ainda nao foram lancadas notas") }
                ?.text()?.trim()
                ?: "Ainda não foram lançadas notas."
            return CoreResult.Success(
                NotasParciaisTurma(
                    notasDisponiveis = false,
                    mensagemAviso = aviso,
                    unidades = emptyList(),
                    mediaFinal = null,
                    faltas = null,
                    situacao = null,
                ),
            )
        }

        val table = doc.selectFirst("table.tabelaRelatorio, table.listagem")
            ?: return CoreResult.Failure(
                CoreError.ParseError("ava/notas", "Tabela de notas parciais nao encontrada."),
            )

        val headers = table.select("thead th, tr:first-child th").map { it.text().trim() }
        val dataRow = table.select("tbody tr").firstOrNull { it.select("td").isNotEmpty() }
            ?: return CoreResult.Success(
                NotasParciaisTurma(
                    notasDisponiveis = false,
                    mensagemAviso = "Sem notas lancadas.",
                    unidades = emptyList(),
                    mediaFinal = null,
                    faltas = null,
                    situacao = null,
                ),
            )

        val cells = dataRow.select("td").map { it.text().trim() }
        val unidades = mutableListOf<UnidadeNotaTurma>()
        var mediaFinal: String? = null
        var faltas: Int? = null
        var situacao: String? = null

        cells.forEachIndexed { idx, value ->
            val header = headers.getOrElse(idx) { "Coluna ${idx + 1}" }
            val normHeader = CasAndJsfParser.normalizeLabel(header)
            when {
                normHeader.contains("media") || normHeader.contains("resultado") -> mediaFinal = value.ifEmpty { null }
                normHeader.contains("falta") -> faltas = value.toIntOrNull()
                normHeader.contains("situacao") -> situacao = value.ifEmpty { null }
                normHeader.contains("matricula") || normHeader.contains("nome") || normHeader.contains("discente") -> Unit
                else -> unidades += UnidadeNotaTurma(nome = header, valor = value.ifEmpty { null })
            }
        }

        return CoreResult.Success(
            NotasParciaisTurma(
                notasDisponiveis = true,
                mensagemAviso = null,
                unidades = unidades,
                mediaFinal = mediaFinal,
                faltas = faltas,
                situacao = situacao,
            ),
        )
    }

    fun parseAvaAvaliacoes(html: String): CoreResult<List<AvaliacaoTurma>> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table.listagem")
        if (table == null) {
            return if (CasAndJsfParser.normalizeLabel(doc.text()).contains("avaliac")) {
                CoreResult.Success(emptyList())
            } else {
                CoreResult.Failure(CoreError.ParseError("ava/avaliacoes", "Pagina de avaliacoes invalida."))
            }
        }
        val items = table.select("tbody tr").mapNotNull { row ->
            val cols = row.select("td")
            if (cols.size < 2) return@mapNotNull null
            val data = cols[0].text().trim()
            val hora = if (cols.size >= 3) cols[1].text().trim().ifEmpty { null } else null
            val desc = cols.lastOrNull()?.text()?.trim().orEmpty()
            if (desc.isEmpty()) return@mapNotNull null
            AvaliacaoTurma(dataTexto = data, horaTexto = hora, descricao = desc)
        }
        return CoreResult.Success(items)
    }

    fun parseAvaTarefas(html: String): CoreResult<List<TarefaTurma>> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table.listagem")
        if (table == null) {
            return if (CasAndJsfParser.normalizeLabel(doc.text()).contains("tarefa")) {
                CoreResult.Success(emptyList())
            } else {
                CoreResult.Failure(CoreError.ParseError("ava/tarefas", "Pagina de tarefas invalida."))
            }
        }
        val items = table.select("tbody tr").mapNotNull { row ->
            val cols = row.select("td")
            if (cols.size < 2) return@mapNotNull null
            val titulo = cols[0].text().trim()
            if (titulo.isEmpty()) return@mapNotNull null
            val periodo = cols.getOrNull(1)?.text()?.trim().orEmpty()
            val notaMax = cols.getOrNull(2)?.text()?.trim()?.ifEmpty { null }
            val possuiArquivo = cols.getOrNull(3)?.let { cell ->
                CasAndJsfParser.normalizeLabel(cell.text()).contains("sim") || cell.selectFirst("img, a") != null
            } ?: false
            val emGrupo = cols.getOrNull(4)?.let { cell ->
                CasAndJsfParser.normalizeLabel(cell.text()).contains("sim")
            } ?: false
            TarefaTurma(
                titulo = titulo,
                periodoEntrega = periodo,
                notaMaxima = notaMax,
                possuiArquivo = possuiArquivo,
                emGrupo = emGrupo,
            )
        }
        return CoreResult.Success(items)
    }

    private fun parseUltimaNoticia(
        doc: Document,
        anexoRefs: MutableMap<String, ParsedAvaAnexoRef>,
    ): NoticiaTurmaAva? {
        val box = doc.selectFirst("#ultimaNoticia") ?: return null
        val header = box.selectFirst("h4")?.text()?.trim().orEmpty()
        val parts = header.split(" - ")
        val dataHora = parts.lastOrNull()?.takeIf { it.contains("/") }?.trim().orEmpty()
        val titulo = if (dataHora.isNotEmpty() && parts.size > 1) {
            parts.dropLast(1).joinToString(" - ").trim()
        } else {
            header
        }
        val span = box.selectFirst("span.conteudoNoticia")
        val autor = box.select("small i").lastOrNull()?.text()?.trim()
        val anexos = box.select("a[href*='/sigaa/verArquivo']").mapIndexed { idx, link ->
            val href = link.attr("href").trim()
            val id = idArquivoRegex.find(href)?.groupValues?.getOrNull(1) ?: "ultima_$idx"
            val anexo = AnexoArquivoSigaa(id = id, nomeArquivo = link.text().trim())
            anexoRefs[id] = ParsedAvaAnexoRef(anexo = anexo, downloadUrl = href, formParams = null)
            anexo
        }
        if (titulo.isEmpty() && span == null) return null
        return NoticiaTurmaAva(
            idNoticia = null,
            titulo = titulo,
            dataHoraTexto = dataHora,
            conteudoHtml = span?.html().orEmpty(),
            conteudoTexto = span?.text()?.trim().orEmpty(),
            autor = autor,
            anexos = anexos,
        )
    }

    private fun parseHistoricoNoticias(
        doc: Document,
        viewState: String,
        ultimaNoticia: NoticiaTurmaAva? = null,
    ): Pair<List<NoticiaTurmaResumoAva>, Map<String, ParsedAvaNoticiaRef>> {
        val list = mutableListOf<NoticiaTurmaResumoAva>()
        val refs = linkedMapOf<String, ParsedAvaNoticiaRef>()
        for (form in doc.select("#barraDireita form")) {
            val idInput = form.selectFirst("input[name=id]")?.attr("value")?.trim() ?: continue
            val link = form.selectFirst("a[onclick]") ?: continue
            val (dataHora, titulo) = extractNoticiaResumoHeader(form, link, ultimaNoticia)
            val formId = form.attr("id").trim()
            val params = linkedMapOf<String, String>()
            if (formId.isNotEmpty()) params[formId] = formId
            params.putAll(CasAndJsfParser.extractJsfCljsParams(link.attr("onclick")))
            params["id"] = idInput
            if (viewState.isNotEmpty()) params["javax.faces.ViewState"] = viewState

            val resumo = NoticiaTurmaResumoAva(idNoticia = idInput, dataHoraTexto = dataHora, titulo = titulo)
            list += resumo
            refs[idInput] = ParsedAvaNoticiaRef(resumo = resumo, formParams = params)
        }
        return list to refs
    }

    private fun extractNoticiaResumoHeader(
        form: Element,
        link: Element,
        ultimaNoticia: NoticiaTurmaAva?,
    ): Pair<String, String> {
        val segmentNodes = collectPrecedingNoticeNodes(form)
        val precedingText = renderNodesText(segmentNodes)
        val dataHora = noticiaDateRegex.findAll(precedingText).lastOrNull()?.value
            ?: noticiaDateRegex.find(form.text())?.value.orEmpty()

        val styledCandidates = (segmentNodes + form.children().filter { !it.tagName().equals("a", ignoreCase = true) })
            .flatMap { node ->
                when (node) {
                    is Element -> {
                        val own = if (node.tagName().lowercase() in setOf("i", "b", "strong")) listOf(node) else emptyList()
                        own + node.select("i, b, strong")
                    }
                    else -> emptyList()
                }
            }
            .map { it.text().trim() }
            .filter { it.isNotEmpty() && !isGenericNoticeAction(it) }

        val linkTitle = link.text().trim().takeUnless(::isGenericNoticeAction)
        val plainLeftover = precedingText
            .let { text -> noticiaDateRegex.replace(text, " ") }
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', '|', '\u2013', '\u2022')
            .takeUnless { it.isEmpty() || isGenericNoticeAction(it) }

        val titulo = styledCandidates.lastOrNull()
            ?: linkTitle
            ?: plainLeftover
            ?: ultimaNoticia?.takeIf { dataHora.isNotEmpty() && it.dataHoraTexto == dataHora }?.titulo?.takeIf { it.isNotBlank() }
            ?: "Comunicado"

        return dataHora to titulo
    }

    private fun collectPrecedingNoticeNodes(form: Element): List<Node> {
        val nodes = ArrayList<Node>()
        var cursor = form.previousSibling()
        while (cursor != null) {
            if (cursor is Element && isNoticeBoundary(cursor)) {
                break
            }
            nodes.add(0, cursor)
            cursor = cursor.previousSibling()
        }

        val hasMetadataInSiblings = renderNodesText(nodes).isNotBlank()
        if (!hasMetadataInSiblings && noticiaDateRegex.find(form.text()) == null) {
            val parent = form.parent()
            if (parent != null && parent.id() != "barraDireita" && parent.select("form").size == 1) {
                val prevContainer = parent.previousElementSibling()
                if (prevContainer != null && !isNoticeBoundary(prevContainer) && prevContainer.text().isNotBlank()) {
                    return listOf(prevContainer)
                }
            }
        }
        return nodes
    }

    private fun isNoticeBoundary(element: Element): Boolean {
        val tag = element.tagName().lowercase()
        if (tag in setOf("form", "hr", "h1", "h2", "h3", "h4", "h5", "h6", "table")) return true
        if (element.selectFirst("form") != null) return true
        if (element.hasClass("rich-panel") || element.hasClass("rich-panel-header") || element.hasClass("progress-bar")) return true
        val norm = CasAndJsfParser.normalizeLabel(element.text())
        return norm.contains("ministradas") || norm.contains("carga horaria")
    }

    private fun renderNodesText(nodes: List<Node>): String =
        nodes.joinToString(" ") { node ->
            when (node) {
                is TextNode -> node.text()
                is Element -> node.text()
                else -> ""
            }
        }.replace(Regex("""\s+"""), " ").trim()

    private fun isGenericNoticeAction(text: String): Boolean {
        val cleaned = CasAndJsfParser.normalizeLabel(text)
            .trim()
            .trim('(', ')', '[', ']', '.', ':')
            .trim()
        return cleaned.isEmpty() || cleaned in genericNoticeActions
    }

    private fun parseTopicosAula(
        doc: Document,
        viewState: String,
        materialRefs: MutableMap<String, ParsedAvaMaterialRef>,
    ): List<TopicoAulaAva> {
        val avaHiddenInputs = doc.select("form#formAva input[type=hidden]")
            .mapNotNull { input ->
                val name = input.attr("name").trim()
                if (name.isEmpty()) null else name to input.attr("value")
            }
            .toMap()

        val containers = doc.select("#formAva div.topico-aula, div.topico-aula")
        return containers.mapIndexed { index, el ->
            val tituloRaw = el.selectFirst("div.titulo")?.text()?.trim().orEmpty()
            val periodoMatch = Regex("""\(([^)]+\d{4}[^)]*)\)\s*$""").find(tituloRaw)
            val periodo = periodoMatch?.groupValues?.getOrNull(1)?.trim()
            val titulo = if (periodoMatch != null) {
                tituloRaw.removeRange(periodoMatch.range).trim()
            } else {
                tituloRaw
            }
            val conteudo = el.select("div.conteudotopico > p").text().trim()
            val materiais = el.select("div.conteudotopico div.item a[onclick]").mapNotNull { link ->
                val onClick = link.attr("onclick")
                val paramsMap = CasAndJsfParser.extractJsfCljsParams(onClick)
                val hashId = paramsMap["id"] ?: return@mapNotNull null
                val itemContainer = link.closest("div.item")
                val linkNome = link.text().trim()
                val ext = resolveMaterialExtension(itemContainer, linkNome)

                val fullParams = linkedMapOf<String, String>()
                fullParams.putAll(avaHiddenInputs)
                fullParams["formAva"] = "formAva"
                fullParams["formAva:idTopicoSelecionado"] = index.toString()
                fullParams.putAll(paramsMap)
                if (viewState.isNotEmpty()) fullParams["javax.faces.ViewState"] = viewState

                val material = MaterialAulaAva(
                    materialId = hashId,
                    nome = linkNome,
                    tipoExtensao = ext,
                    topicoIndex = index,
                )
                materialRefs[hashId] = ParsedAvaMaterialRef(material = material, formParams = fullParams)
                material
            }
            TopicoAulaAva(
                index = index,
                titulo = titulo,
                periodoTexto = periodo,
                conteudoTexto = conteudo,
                materiais = materiais,
            )
        }
    }

    private fun resolveMaterialExtension(itemContainer: Element?, linkNome: String): String {
        val iconImg = itemContainer?.selectFirst("img[src*='/porta_arquivos/icones/'], img[src*='/icones/']")
            ?: itemContainer?.select("img")?.firstOrNull { img ->
                val src = img.attr("src").lowercase()
                src.isNotBlank() &&
                    !src.contains("indicator") &&
                    !src.contains("spacer") &&
                    !src.contains("loading") &&
                    !src.contains("spinner") &&
                    !src.contains("ajax")
            }
        val iconSrc = iconImg?.attr("src").orEmpty()
        val rawIconExt = iconSrc
            .substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('.')
            .trim()
            .lowercase()
        val sanitizedIconExt = rawIconExt.takeUnless {
            it.isEmpty() || it in setOf("indicator", "spacer", "loading", "spinner", "ajax", "blank")
        }
        if (sanitizedIconExt != null) return sanitizedIconExt

        return linkNome
            .substringAfterLast('.', "")
            .trim()
            .lowercase()
            .takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
            .orEmpty()
    }

    private fun parseFormMenuActions(doc: Document, viewState: String): Map<String, Map<String, String>> {
        val form = doc.selectFirst("form#formMenu") ?: return emptyMap()
        val formId = form.attr("id").ifBlank { "formMenu" }
        val result = linkedMapOf<String, Map<String, String>>()
        for (link in form.select("a[onclick]")) {
            val label = link.selectFirst("div.itemMenu")?.text()?.trim() ?: link.text().trim()
            if (label.isEmpty()) continue
            val params = linkedMapOf(formId to formId)
            params.putAll(CasAndJsfParser.extractJsfCljsParams(link.attr("onclick")))
            if (viewState.isNotEmpty()) params["javax.faces.ViewState"] = viewState
            result[CasAndJsfParser.normalizeLabel(label)] = params
        }
        return result
    }

    private fun parseFormTurmaItems(doc: Document): List<ParsedAvaFormTurmaItem> {
        val form = doc.selectFirst("form#formTurma") ?: return emptyList()
        return form.select("a.linkTurma").mapNotNull { link ->
            val text = link.selectFirst("span")?.text()?.trim() ?: link.text().trim()
            val m = formTurmaRegex.find(text) ?: return@mapNotNull null
            val params = CasAndJsfParser.extractJsfCljsParams(link.attr("onclick"))
            val frontEndId = params["frontEndIdTurma"] ?: return@mapNotNull null
            ParsedAvaFormTurmaItem(
                frontEndIdTurma = frontEndId,
                codigoDisciplina = m.groupValues[1].trim(),
                nomeDisciplina = m.groupValues[2].trim(),
                cargaHorariaHoras = m.groupValues[3].toIntOrNull(),
                semestre = m.groupValues[4].trim(),
            )
        }
    }

    private fun extractMetricInt(bodyText: String, label: String): Int? {
        val escaped = Regex.escape(label)
        val regex = Regex("""$escaped\s*:\s*(\d+)\s*%?""", RegexOption.IGNORE_CASE)
        return regex.find(bodyText)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
