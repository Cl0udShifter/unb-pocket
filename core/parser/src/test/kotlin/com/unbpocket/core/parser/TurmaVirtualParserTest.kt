package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurmaVirtualParserTest {
    @Test
    fun parsesAvaHomeWithTopicosMaterialsNewsAndFormTurma() {
        val avaHtml = """
            <html><body>
              <div id="nomeTurma">
                <span id="linkCodigoTurma">ENE0295 - </span>
                <span id="linkNomeTurma">LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO</span>
                <span id="linkPeriodoTurma">- T04 (2026.2 - 2T45)</span>
              </div>
              <input type="hidden" name="idTurma" value="1616625" />
              <input type="hidden" name="javax.faces.ViewState" value="j_id4" />
              <form id="formMenu">
                <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:top':'formMenu:sub','formMenu:freq':'formMenu:freq'},'')"><div class="itemMenu">Frequência</div></a>
                <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:top':'formMenu:sub','formMenu:notas':'formMenu:notas'},'')"><div class="itemMenu">Ver Notas</div></a>
              </form>
              <div id="ultimaNoticia">
                <h4>Experimento 11 - Roteiro - 22/11/2026 09:34</h4>
                <span class="conteudoNoticia"><p>Leiam o roteiro antes da aula.</p></span>
                <a href="/sigaa/verArquivo?idArquivo=4245329&key=61b389b1ce00f824fe8986e3b7d2e95e">Exp11_Sincronismo.zip</a>
                <small>Cadastrado por: <i>LEONARDO AGUAYO</i></small>
              </div>
              <div id="barraDireita">
                <div>Aulas (Ministradas/Total): <i>24 / 32</i></div>
                <div class="progress-bar" style="width: 75%">75%</div>
                <form id="formNoticia1">
                  22/11/2026 09:34 <input type="hidden" name="id" value="24442200" />
                  <a onclick="jsfcljs(document.getElementById('formNoticia1'),{'formNoticia1:ver':'formNoticia1:ver'},'')">Experimento 11</a>
                </form>
              </div>
              <form id="formAva">
                <div class="topico-aula">
                  <div class="titulo">Experimentos (10/08/2026 - 14/12/2026)</div>
                  <div class="conteudotopico">
                    <p>Arquivos de laboratorio.</p>
                    <div class="item">
                      <img src="/sigaa/img/porta_arquivos/icones/pdf.png" />
                      <a onclick="jsfcljs(document.getElementById('formAva'),{'formAva:mat1':'formAva:mat1','id':'445F1CEF4EFBE7DDCC8AB673E127D7A5C24C9C3D'},'_blank')">Experimento 01</a>
                    </div>
                  </div>
                </div>
              </form>
              <form id="formTurma">
                <a class="linkTurma" onclick="jsfcljs(document.getElementById('formTurma'),{'frontEndIdTurma':'B23FC005EF33B52CE0E03706BB07F27A40C8BCD0'},'')">
                  <span>ENE0295 - LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO (30h) (2026.2)</span>
                </a>
              </form>
            </body></html>
        """.trimIndent()

        val res = TurmaVirtualParser.parseAvaHome(avaHtml, "B23FC005EF33B52CE0E03706BB07F27A40C8BCD0")
        assertTrue(res is CoreResult.Success)
        val parsed = (res as CoreResult.Success).value
        assertEquals("ENE0295", parsed.detalhe.codigoDisciplina)
        assertEquals(24, parsed.detalhe.aulasMinistradas)
        assertEquals(32, parsed.detalhe.aulasTotal)
        assertEquals(75, parsed.detalhe.percentualAndamento)
        assertNotNull(parsed.detalhe.ultimaNoticia)
        assertEquals("4245329", parsed.detalhe.ultimaNoticia?.anexos?.first()?.id)
        assertEquals(1, parsed.detalhe.topicosAula.size)
        assertEquals("445F1CEF4EFBE7DDCC8AB673E127D7A5C24C9C3D", parsed.detalhe.topicosAula[0].materiais[0].materialId)
        assertEquals("pdf", parsed.detalhe.topicosAula[0].materiais[0].tipoExtensao)
        assertTrue(parsed.menuActions.containsKey("frequencia"))
        assertEquals(30, parsed.formTurmaItems.first().cargaHorariaHoras)
    }

    @Test
    fun parsesFrequenciaWith25PercentLimitAndRiskDetection() {
        val freqHtml = """
            <html><body>
              <fieldset>
                <legend>Mapa de Frequências</legend>
                <table class="listagem">
                  <tbody>
                    <tr><td>10/08/2026</td><td>Presente</td></tr>
                    <tr><td>12/08/2026</td><td>3 Faltas</td></tr>
                    <tr><td>17/08/2026</td><td>4 Faltas</td></tr>
                  </tbody>
                </table>
                <p>Presenças Registradas: 25</p>
                <p>Número de Aulas com Registro de Frequência: 32</p>
                <p>Porcentagem de Frequência em relação as Aulas com Registro de Frequência: 78%</p>
                <p>Número de Aulas definidas pela CH do Componente: 32</p>
                <p>Porcentagem de Frequência em relação a CH: 78%</p>
              </fieldset>
            </body></html>
        """.trimIndent()

        val freq = (TurmaVirtualParser.parseAvaFrequencia(freqHtml) as CoreResult.Success).value
        assertEquals(7, freq.faltasComputadas)
        assertEquals(8, freq.limiteMaximoFaltas)
        assertEquals(1, freq.faltasRestantes)
        assertTrue(freq.emRiscoReprovacaoPorFalta)
        assertEquals(3, freq.registrosDiarios.size)
    }

    @Test
    fun doesNotCountUnregisteredFrequencyRowsAsAbsences() {
        val freqHtml = """
            <html><body>
              <fieldset>
                <legend>Mapa de Frequências</legend>
                <table class="listagem">
                  <tbody>
                    <tr><td>10/08/2026</td><td>Presente</td></tr>
                    <tr><td>12/08/2026</td><td>Não registrado</td></tr>
                    <tr><td>14/08/2026</td><td>Não lançada</td></tr>
                    <tr><td>17/08/2026</td><td>--</td></tr>
                    <tr><td>19/08/2026</td><td>2 Faltas</td></tr>
                    <tr><td>21/08/2026</td><td>Falta</td></tr>
                  </tbody>
                </table>
                <p>Presenças Registradas: 2</p>
                <p>Número de Aulas com Registro de Frequência: 5</p>
                <p>Porcentagem de Frequência em relação as Aulas com Registro de Frequência: 40%</p>
                <p>Número de Aulas definidas pela CH do Componente: 32</p>
                <p>Porcentagem de Frequência em relação a CH: 90%</p>
              </fieldset>
            </body></html>
        """.trimIndent()

        val freq = (TurmaVirtualParser.parseAvaFrequencia(freqHtml) as CoreResult.Success).value
        assertEquals(6, freq.registrosDiarios.size)

        val naoRegistrado = freq.registrosDiarios[1]
        assertEquals("Não registrado", naoRegistrado.situacaoTexto)
        assertFalse(naoRegistrado.presente)
        assertEquals(0, naoRegistrado.faltasNoDia)

        val naoLancada = freq.registrosDiarios[2]
        assertFalse(naoLancada.presente)
        assertEquals(0, naoLancada.faltasNoDia)

        val dash = freq.registrosDiarios[3]
        assertFalse(dash.presente)
        assertEquals(0, dash.faltasNoDia)

        assertEquals(2, freq.registrosDiarios[4].faltasNoDia)
        assertEquals(1, freq.registrosDiarios[5].faltasNoDia)
        assertEquals(3, freq.faltasComputadas)
        assertEquals(5, freq.faltasRestantes)
    }

    @Test
    fun parsesPartialGradesUnavailableAndAvailableStatesAndAtestado() {
        val emptyNotasHtml = "<html><body><p class='aviso'>Ainda não foram lançadas notas.</p></body></html>"
        val emptyNotas = (TurmaVirtualParser.parseAvaNotasParciais(emptyNotasHtml) as CoreResult.Success).value
        assertFalse(emptyNotas.notasDisponiveis)

        val gradesHtml = """
            <html><body>
              <table class="tabelaRelatorio">
                <thead><tr><th>Matrícula</th><th>Discente</th><th>Nota 1</th><th>Nota 2</th><th>Média Final</th><th>Faltas</th><th>Situação</th></tr></thead>
                <tbody><tr><td>190026308</td><td>Daniel</td><td>9,0</td><td>8,5</td><td>SS</td><td>2</td><td>APROVADO</td></tr></tbody>
              </table>
            </body></html>
        """.trimIndent()
        val grades = (TurmaVirtualParser.parseAvaNotasParciais(gradesHtml) as CoreResult.Success).value
        assertTrue(grades.notasDisponiveis)
        assertEquals(2, grades.unidades.size)
        assertEquals("SS", grades.mediaFinal)
        assertEquals(2, grades.faltas)

        val atestadoHtml = """
            <html><body>
              <table class="listagem">
                <tbody>
                  <tr>
                    <td>ENE0295</td>
                    <td>LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO<br/>LEONARDO AGUAYO<br/>Tipo: DISCIPLINA Local: SG-11 A1-09/12</td>
                    <td>04</td>
                    <td>MATRICULADO(A)</td>
                    <td>2T45</td>
                  </tr>
                </tbody>
              </table>
              <p>Código de Verificação: 3c610b1b69</p>
            </body></html>
        """.trimIndent()
        val atestado = TurmaVirtualParser.parseAtestadoMatricula(atestadoHtml)
        assertEquals(1, atestado.turmas.size)
        assertEquals("LEONARDO AGUAYO", atestado.turmas[0].docenteNome)
        assertEquals("3c610b1b69", atestado.codigoVerificacao)
    }

    @Test
    fun extractsRealNoticeTitlesInsteadOfVisualizarFromBarraDireita() {
        val avaHtml = """
            <html><body>
              <div id="nomeTurma">
                <span id="linkCodigoTurma">ENE0295 - </span>
                <span id="linkNomeTurma">LABORATORIO</span>
                <span id="linkPeriodoTurma">- T04 (2026.2)</span>
              </div>
              <input type="hidden" name="idTurma" value="1616625" />
              <input type="hidden" name="javax.faces.ViewState" value="j_id4" />
              <div id="ultimaNoticia">
                <h4>Experimento 11 - Roteiro - 22/11/2026 09:34</h4>
                <span class="conteudoNoticia"><p>Leiam o roteiro.</p></span>
              </div>
              <div id="barraDireita">
                <div class="rich-panel">
                  <div class="rich-panel-header">Andamento das Aulas</div>
                  <div class="rich-panel-body">Aulas (Ministradas/Total): <i>24 / 32</i></div>
                </div>
                <div class="rich-panel">
                  <div class="rich-panel-header">Últimas Notícias</div>
                  <div id="j_id_jsp_1879301362_155_body" class="rich-panel-body">
                    22/11/2026 09:34<br/>
                    <i>Experimento 11 - Roteiro</i>
                    <form id="f1">
                      <input type="hidden" name="id" value="24442200" />
                      <a onclick="jsfcljs(document.getElementById('f1'),{'f1:v':'f1:v'},'')">(Visualizar)</a>
                    </form>
                    15/11/2026 14:20<br/>
                    <i>Alteração na data da prova</i>
                    <form id="f2">
                      <input type="hidden" name="id" value="24441000" />
                      <a onclick="jsfcljs(document.getElementById('f2'),{'f2:v':'f2:v'},'')"> (Visualizar) </a>
                    </form>
                    <table>
                      <tr>
                        <td>10/11/2026 08:00<br/><i>Aviso de laboratório</i></td>
                        <td>
                          <form id="f3">
                            <input type="hidden" name="id" value="24440000" />
                            <a onclick="jsfcljs(document.getElementById('f3'),{'f3:v':'f3:v'},'')">Visualizar</a>
                          </form>
                        </td>
                      </tr>
                    </table>
                  </div>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val parsed = (TurmaVirtualParser.parseAvaHome(avaHtml, "FE_1") as CoreResult.Success).value
        val historico = parsed.detalhe.historicoNoticiasResumo
        assertEquals(3, historico.size)

        assertEquals("24442200", historico[0].idNoticia)
        assertEquals("22/11/2026 09:34", historico[0].dataHoraTexto)
        assertEquals("Experimento 11 - Roteiro", historico[0].titulo)

        assertEquals("24441000", historico[1].idNoticia)
        assertEquals("15/11/2026 14:20", historico[1].dataHoraTexto)
        assertEquals("Alteração na data da prova", historico[1].titulo)

        assertEquals("24440000", historico[2].idNoticia)
        assertEquals("10/11/2026 08:00", historico[2].dataHoraTexto)
        assertEquals("Aviso de laboratório", historico[2].titulo)
    }
}
