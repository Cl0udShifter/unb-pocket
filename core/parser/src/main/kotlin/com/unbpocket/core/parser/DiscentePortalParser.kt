package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.discente.IndicesIntegralizacao
import com.unbpocket.core.model.discente.PerfilDiscente
import com.unbpocket.core.model.ru.GrupoAcessoRu
import com.unbpocket.core.model.ru.SaldoExtratoRu
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.ru.TransacaoRu
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.TurmaResumo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedHomeTurma(
    val resumo: TurmaResumo,
    val formId: String,
    val formParams: Map<String, String>,
)

/**
 * Parsers do HTML da tela inicial do Portal Discente (`discente.jsf`).
 */
object DiscentePortalParser {
    private val publicCourseRegex = Regex("""curso/portal\.jsf\?id=(\d+)""")
    private val progressWidthRegex = Regex("""width:\s*([\d.,]+)%""")
    private val idTurmaOnclickRegex = Regex("""'idTurma'\s*:\s*'(\d+)'""")
    private val frontEndIdRegex = Regex("""'frontEndIdTurma'\s*:\s*'([A-F0-9]{40})'""")
    private val digitsRegex = Regex("""\d+""")

    fun parseDiscenteResumo(html: String): CoreResult<DiscenteResumo> {
        val doc = Jsoup.parse(html)
        val agendaRows = parseAgendaRows(doc)
        val idDiscente = CasAndJsfParser.extractDiscenteId(doc).orEmpty()
        val matricula = agendaRows["matricula"]
        val nomeCompleto = doc.selectFirst("#perfil-docente p.info-docente span.nome b")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("#info-usuario p.usuario span")?.text()?.trim()

        if (matricula.isNullOrBlank() || nomeCompleto.isNullOrBlank()) {
            return CoreResult.Failure(
                CoreError.ParseError(
                    source = "discente.jsf",
                    reason = "Campos obrigatorios de matricula ou nome nao encontrados no portal discente.",
                ),
            )
        }

        val nomeAbreviado = doc.selectFirst("#info-usuario p.usuario span")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: nomeCompleto

        val infoDocenteEl = doc.selectFirst("#perfil-docente p.info-docente")
        val bioPerfil = infoDocenteEl?.clone()?.apply {
            select("span.nome").remove()
        }?.text()?.trim()?.takeIf { it.isNotEmpty() }

        val fotoUrl = doc.selectFirst("#perfil-docente div.foto img")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.contains("no_picture", ignoreCase = true) }

        val idCursoPublico = publicCourseRegex.find(html)?.groupValues?.getOrNull(1)

        val perfil = PerfilDiscente(
            idDiscente = idDiscente,
            matricula = matricula,
            nomeCompleto = nomeCompleto,
            nomeAbreviado = nomeAbreviado,
            bioPerfil = bioPerfil,
            fotoUrl = fotoUrl,
            semestreAtual = doc.selectFirst("#info-usuario p.periodo-atual strong")?.text()?.trim().orEmpty(),
            semestreEntrada = agendaRows["entrada"].orEmpty(),
            unidadeDepartamento = doc.selectFirst("#info-usuario p.unidade")?.text()?.trim().orEmpty(),
            curso = agendaRows["curso"].orEmpty(),
            nivel = agendaRows["nivel"].orEmpty(),
            status = agendaRows["status"].orEmpty(),
            emailTruncado = agendaRows["e-mail"],
            idCursoPortalPublico = idCursoPublico,
        )

        val progressDiv = doc.selectFirst("#agenda-docente div[style*='width:'], #agenda-docente .progress-bar")
        val percentualExato = progressDiv?.attr("style")
            ?.let { progressWidthRegex.find(it)?.groupValues?.getOrNull(1) }
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        val integralizacaoCellText = doc.select("#agenda-docente td")
            .lastOrNull { it.text().contains("Integralizado", ignoreCase = true) }
            ?.text()
            ?.trim()
        val percentualTexto = integralizacaoCellText?.let { text ->
            Regex("""\d+(?:[.,]\d+)?%\s*Integralizado""", RegexOption.IGNORE_CASE)
                .find(text)
                ?.value
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                ?: text
        }

        val indices = IndicesIntegralizacao(
            ira = parseDoubleOrNull(agendaRows["ira"]),
            mp = parseDoubleOrNull(agendaRows["mp"]),
            chObrigatoriaPendente = parseIntOrNull(agendaRows["ch. obrigatoria pendente"]),
            chOptativaPendente = parseIntOrNull(agendaRows["ch. optativa pendente"]),
            chComplementarPendente = parseIntOrNull(agendaRows["ch. complementar pendente"]),
            chTotalCurriculo = parseIntOrNull(agendaRows["ch. total curriculo"]),
            percentualIntegralizadoExato = percentualExato,
            percentualIntegralizadoTexto = percentualTexto,
        )

        return CoreResult.Success(DiscenteResumo(perfil = perfil, indices = indices))
    }

    fun parseAtualizacoesFeed(html: String): List<AtualizacaoTurmaFeed> {
        val doc = Jsoup.parse(html)
        val tables = doc.select("#atualizacoes-turma div.rotator > table")
        return tables.mapNotNull { table ->
            val rows = table.select("tr")
            if (rows.size < 2) return@mapNotNull null
            val firstCell = rows[0].selectFirst("td") ?: return@mapNotNull null
            val link = firstCell.selectFirst("a") ?: return@mapNotNull null
            val fullFirstText = firstCell.text().trim()
            val dataTexto = fullFirstText.substringBefore('-').trim()
            val disciplinaSemestre = link.text().trim()
            val idTurma = idTurmaOnclickRegex.find(link.attr("onclick"))?.groupValues?.getOrNull(1).orEmpty()
            val tituloNoticia = rows[1].selectFirst("td")?.text()?.trim().orEmpty()
            if (tituloNoticia.isEmpty() && disciplinaSemestre.isEmpty()) return@mapNotNull null
            AtualizacaoTurmaFeed(
                dataTexto = dataTexto,
                disciplinaSemestre = disciplinaSemestre,
                idTurma = idTurma,
                tituloNoticia = tituloNoticia,
            )
        }
    }

    fun parseTurmasDaHome(html: String): List<ParsedHomeTurma> {
        val doc = Jsoup.parse(html)
        val semestreAtual = doc.selectFirst("#info-usuario p.periodo-atual strong")?.text()?.trim().orEmpty()
        val portal = doc.selectFirst("#turmas-portal") ?: return emptyList()
        val tables = portal.select("table")
        val turmaTable = tables.firstOrNull { it.select("td.descricao form").isNotEmpty() } ?: return emptyList()
        val rows = turmaTable.select("tbody > tr")
        val viewState = CasAndJsfParser.extractViewState(doc).orEmpty()

        val result = mutableListOf<ParsedHomeTurma>()
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            val descForm = row.selectFirst("td.descricao form")
            if (descForm == null) {
                i++
                continue
            }

            val link = descForm.selectFirst("a[onclick]")
            val nomeDisciplina = (link?.text() ?: descForm.text()).trim()
            val onClick = link?.attr("onclick").orEmpty()
            val formId = descForm.attr("id").trim()
            val frontEndIdTurma = frontEndIdRegex.find(onClick)?.groupValues?.getOrNull(1).orEmpty()

            val infoCells = row.select("td.info")
            val local = infoCells.getOrNull(0)?.text()?.trim().orEmpty()
            val horarioCell = infoCells.getOrNull(1)
            val horarioExtenso = horarioCell?.select("div.popUp")?.firstOrNull()
                ?.html()
                ?.split(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE))
                ?.map { Jsoup.parse(it).text().trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            val horarioRaw = horarioCell?.clone()?.apply {
                select("div.popUp, img").remove()
            }?.text()?.trim().orEmpty()

            var idTurma = ""
            if (i + 1 < rows.size) {
                val hiddenTd = rows[i + 1].selectFirst("td[id^=linha_]")
                if (hiddenTd != null) {
                    idTurma = hiddenTd.attr("id").removePrefix("linha_")
                    i++
                }
            }

            if (frontEndIdTurma.isNotEmpty()) {
                val params = linkedMapOf<String, String>()
                if (formId.isNotEmpty()) params[formId] = formId
                params.putAll(CasAndJsfParser.extractJsfCljsParams(onClick))
                if (viewState.isNotEmpty()) params["javax.faces.ViewState"] = viewState

                result += ParsedHomeTurma(
                    resumo = TurmaResumo(
                        idTurma = idTurma,
                        frontEndIdTurma = frontEndIdTurma,
                        semestre = semestreAtual,
                        codigoDisciplina = null,
                        nomeDisciplina = nomeDisciplina,
                        numeroTurma = null,
                        cargaHorariaHoras = null,
                        docenteNome = null,
                        local = local,
                        codigoHorarioRaw = horarioRaw,
                        horarioSlots = SigaaScheduleDecoder.decode(horarioRaw),
                        horarioExtenso = horarioExtenso,
                    ),
                    formId = formId,
                    formParams = params,
                )
            }
            i++
        }
        return result
    }

    fun parseExtratoRu(
        html: String,
        tabelaPrecos: TabelaPrecosRu = RuCalculator.defaultPrecosResolucao0037,
    ): CoreResult<SaldoExtratoRu> {
        val doc = Jsoup.parse(html)
        val heading = doc.select("h4").firstOrNull {
            CasAndJsfParser.normalizeLabel(it.text()).contains("extrato no restaurante universitario")
        }
        if (heading == null) {
            return if (doc.selectFirst("#agenda-docente") != null && doc.selectFirst("#formExibirExtrato") == null) {
                CoreResult.Failure(
                    CoreError.UnavailableForProfile(
                        feature = "Extrato RU",
                        reason = "Formulario de extrato RU indisponivel para este perfil discente.",
                    ),
                )
            } else {
                CoreResult.Failure(
                    CoreError.ParseError(
                        source = "discente.jsf#extratoRu",
                        reason = "Bloco de extrato RU nao encontrado na resposta.",
                    ),
                )
            }
        }

        val container = heading.parent() ?: return CoreResult.Failure(
            CoreError.ParseError("discente.jsf#extratoRu", "Container do extrato RU ausente."),
        )

        val rows = container.select("table tr")
        var saldoAtual: Long? = null
        var saldoAnterior: Long? = null
        val transacoes = mutableListOf<TransacaoRu>()
        var grupoDetectado = GrupoAcessoRu.DESCONHECIDO

        for (row in rows) {
            val cols = row.select("td")
            if (cols.size < 3) continue
            val dataHora = cols[0].text().trim()
            val descricao = cols[1].text().trim()
            val valorRaw = cols[2].text().trim()
            val valorCentavos = RuCalculator.parseBrlToCentavos(valorRaw) ?: continue

            val normDesc = CasAndJsfParser.normalizeLabel(descricao)
            when (normDesc) {
                "saldo" -> saldoAtual = valorCentavos
                "saldo anterior" -> saldoAnterior = valorCentavos
                else -> {
                    transacoes += TransacaoRu(
                        dataHoraTexto = dataHora,
                        descricao = descricao,
                        valorCentavos = valorCentavos,
                    )
                    if (grupoDetectado == GrupoAcessoRu.DESCONHECIDO) {
                        grupoDetectado = RuCalculator.detectGrupo(descricao)
                    }
                }
            }
        }

        if (saldoAtual == null && transacoes.isEmpty()) {
            return CoreResult.Success(
                SaldoExtratoRu(
                    saldoAtualCentavos = 0L,
                    saldoAnterior7DiasCentavos = saldoAnterior,
                    grupoAcesso = GrupoAcessoRu.DESCONHECIDO,
                    transacoesUltimos7Dias = emptyList(),
                    refeicoesRestantes = null,
                ),
            )
        }

        val finalSaldo = saldoAtual ?: 0L
        val refeicoes = if (grupoDetectado != GrupoAcessoRu.DESCONHECIDO) {
            RuCalculator.calculateRemainingMeals(finalSaldo, grupoDetectado, tabelaPrecos)
        } else {
            null
        }

        return CoreResult.Success(
            SaldoExtratoRu(
                saldoAtualCentavos = finalSaldo,
                saldoAnterior7DiasCentavos = saldoAnterior,
                grupoAcesso = grupoDetectado,
                transacoesUltimos7Dias = transacoes,
                refeicoesRestantes = refeicoes,
            ),
        )
    }

    private fun parseAgendaRows(doc: Document): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val rows = doc.select("#agenda-docente table tr")
        for (row in rows) {
            val cells = row.select("td")
            if (cells.size < 2) continue
            val key = CasAndJsfParser.normalizeLabel(cells[0].text()).trimEnd(':')
            val value = cells[1].text().trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun parseDoubleOrNull(raw: String?): Double? {
        if (raw.isNullOrBlank() || raw.trim() == "-" || raw.trim() == "--") return null
        return raw.trim().replace(',', '.').toDoubleOrNull()
    }

    private fun parseIntOrNull(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return digitsRegex.find(raw)?.value?.toIntOrNull()
    }
}
