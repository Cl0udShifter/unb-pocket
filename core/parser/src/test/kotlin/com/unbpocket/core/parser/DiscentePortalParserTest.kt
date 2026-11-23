package com.unbpocket.core.parser

import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.ru.GrupoAcessoRu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscentePortalParserTest {
    @Test
    fun parsesFullPortalHomeProfileIndicesFeedAndTurmas() {
        val html = """
            <html><body>
              <div id="info-usuario">
                <p class="usuario"><span>Daniel Alexandro C. Junior</span></p>
                <p class="unidade">DEPTO ENGENHARIA ELETRICA (11.01.01.11.03)</p>
                <p class="periodo-atual"><strong>2026.2</strong></p>
              </div>
              <form id="menu:form_menu_discente" action="/sigaa/portais/discente/discente.jsf" method="post">
                <input type="hidden" name="id" value="331956" />
              </form>
              <script>var url = "/sigaa/public/curso/portal.jsf?id=414330&lc=pt_BR";</script>
              <div id="perfil-docente">
                <div class="foto"><img src="https://arquivos.unb.br/arquivos/foto.png" /></div>
                <p class="info-docente">
                  <span class="nome"><b>Daniel Alexandro Contreira Junior</b></span>
                  Conhecimento em lingua inglesa e redes.
                </p>
              </div>
              <div id="agenda-docente">
                <table>
                  <tr><td>Matrícula:</td><td>190026308</td></tr>
                  <tr><td>Curso:</td><td>ENGENHARIA DE REDES DE COMUNICAÇÃO/ENE - Bacharelado - MT</td></tr>
                  <tr><td>Nível:</td><td>GRADUAÇÃO</td></tr>
                  <tr><td>Status:</td><td>ATIVO</td></tr>
                  <tr><td>E-Mail:</td><td>daniel.contreira16@...</td></tr>
                  <tr><td>Entrada:</td><td>2019.1</td></tr>
                  <tr><td><acronym title="IRA">IRA:</acronym></td><td>3.2102</td></tr>
                  <tr><td><acronym title="MP">MP:</acronym></td><td>3.4563</td></tr>
                  <tr><td>CH. Obrigatória Pendente</td><td>420</td></tr>
                  <tr><td>CH. Optativa Pendente</td><td>540</td></tr>
                  <tr><td>CH. Total Currículo</td><td>3750</td></tr>
                  <tr><td>CH. Complementar Pendente</td><td>0</td></tr>
                  <tr><td colspan="2"><div class="progress-bar" style="width: 74.4%"></div></td></tr>
                  <tr><td colspan="2">74% Integralizado</td></tr>
                </table>
              </div>
              <input type="hidden" name="javax.faces.ViewState" id="javax.faces.ViewState" value="j_id1" />
              <div id="turmas-portal">
                <div id="atualizacoes-turma">
                  <div class="rotator">
                    <table style="display: table">
                      <tr><td>22/11/2026 - <a onclick="jsfcljs(document.getElementById('f'),{'idTurma':'1616625'},'')">LABORATÓRIO DE PRINCÍPIOS DE COMUNICAÇÃO (2026.2)</a></td></tr>
                      <tr><td>Nova Notícia: Experimento 11 - Roteiro</td></tr>
                    </table>
                    <table style="display: none">
                      <tr><td>20/11/2026 - <a onclick="jsfcljs(document.getElementById('f'),{'idTurma':'1614167'},'')">REDES SEM FIO (2026.2)</a></td></tr>
                      <tr><td>Arquivo adicionado: Slides Aula 09</td></tr>
                    </table>
                  </div>
                </div>
                <table>
                  <tbody>
                    <tr class="odd">
                      <td class="descricao">
                        <form id="form_acessarTurmaVirtualj_id_3">
                          <a onclick="jsfcljs(document.getElementById('form_acessarTurmaVirtualj_id_3'),{'form_acessarTurmaVirtualj_id_3:btn':'form_acessarTurmaVirtualj_id_3:btn','frontEndIdTurma':'7D4F1E83699CC81005304EC1877AE1F7888882CD'},'')">REDES SEM FIO</a>
                        </form>
                      </td>
                      <td class="info">FT AT-31/12</td>
                      <td class="info">24M12 <div class="popUp">Segunda-feira 08:00 às 09:50<br/>Quarta-feira 08:00 às 09:50</div></td>
                    </tr>
                    <tr><td colspan="5" id="linha_1614167" style="display:none"></td></tr>
                  </tbody>
                </table>
              </div>
            </body></html>
        """.trimIndent()

        val resumoResult = DiscentePortalParser.parseDiscenteResumo(html)
        assertTrue(resumoResult is CoreResult.Success)
        val resumo = (resumoResult as CoreResult.Success).value
        assertEquals("331956", resumo.perfil.idDiscente)
        assertEquals("190026308", resumo.perfil.matricula)
        assertEquals("414330", resumo.perfil.idCursoPortalPublico)
        assertEquals("https://arquivos.unb.br/arquivos/foto.png", resumo.perfil.fotoUrl)
        assertEquals("Conhecimento em lingua inglesa e redes.", resumo.perfil.bioPerfil)
        assertEquals(3.2102, resumo.indices.ira ?: 0.0, 0.0001)
        assertEquals(3.4563, resumo.indices.mp ?: 0.0, 0.0001)
        assertEquals(420, resumo.indices.chObrigatoriaPendente)
        assertEquals(74.4, resumo.indices.percentualIntegralizadoExato ?: 0.0, 0.01)

        val feed = DiscentePortalParser.parseAtualizacoesFeed(html)
        assertEquals(2, feed.size)
        assertEquals("1616625", feed[0].idTurma)
        assertEquals("Nova Notícia: Experimento 11 - Roteiro", feed[0].tituloNoticia)

        val turmas = DiscentePortalParser.parseTurmasDaHome(html)
        assertEquals(1, turmas.size)
        assertEquals("1614167", turmas[0].resumo.idTurma)
        assertEquals("7D4F1E83699CC81005304EC1877AE1F7888882CD", turmas[0].resumo.frontEndIdTurma)
        assertEquals(4, turmas[0].resumo.horarioSlots.size)
        assertEquals(listOf("Segunda-feira 08:00 às 09:50", "Quarta-feira 08:00 às 09:50"), turmas[0].resumo.horarioExtenso)
    }

    @Test
    fun handlesFresherProfileWithMissingIraAndEmptyRuExtract() {
        val fresherHtml = """
            <html><body>
              <div id="info-usuario"><p class="usuario"><span>Calouro UnB</span></p><p class="periodo-atual"><strong>2026.2</strong></p></div>
              <form id="menu:form_menu_discente"><input type="hidden" name="id" value="999001" /></form>
              <div id="perfil-docente">
                <div class="foto"><img src="/shared/img/no_picture.png" /></div>
                <p class="info-docente"><span class="nome"><b>Calouro UnB</b></span></p>
              </div>
              <div id="agenda-docente">
                <table>
                  <tr><td>Matrícula:</td><td>260012345</td></tr>
                  <tr><td>Curso:</td><td>CIÊNCIA DA COMPUTAÇÃO</td></tr>
                  <tr><td>Nível:</td><td>GRADUAÇÃO</td></tr>
                  <tr><td>Status:</td><td>ATIVO</td></tr>
                  <tr><td>Entrada:</td><td>2026.2</td></tr>
                  <tr><td>IRA:</td><td>--</td></tr>
                  <tr><td>MP:</td><td>-</td></tr>
                </table>
              </div>
            </body></html>
        """.trimIndent()

        val resumo = (DiscentePortalParser.parseDiscenteResumo(fresherHtml) as CoreResult.Success).value
        assertNull(resumo.perfil.fotoUrl)
        assertNull(resumo.perfil.bioPerfil)
        assertNull(resumo.indices.ira)
        assertNull(resumo.indices.mp)
        assertTrue(DiscentePortalParser.parseTurmasDaHome(fresherHtml).isEmpty())

        val unavailableRu = DiscentePortalParser.parseExtratoRu(fresherHtml)
        assertTrue(unavailableRu is CoreResult.Failure)
        assertTrue((unavailableRu as CoreResult.Failure).error is CoreError.UnavailableForProfile)
    }

    @Test
    fun parsesRuStatementFromDiscenteJsf() {
        val extratoHtml = """
            <html><body>
              <div style="background-color: #EFF3FA; margin: 5px">
                <h4>Extrato no Restaurante Universitário</h4>
                <p>(Últimos 7 dias)</p>
                <table>
                  <tr><td>22/11/2026 20:43</td><td>Saldo</td><td>R$ 8,65</td></tr>
                  <tr><td>16/11/2026 17:37</td><td>Grupo 4 Jantar</td><td>-R$ 2,50</td></tr>
                  <tr><td>15/11/2026 20:43</td><td>Saldo Anterior</td><td>R$ 11,15</td></tr>
                </table>
              </div>
            </body></html>
        """.trimIndent()

        val res = DiscentePortalParser.parseExtratoRu(extratoHtml)
        assertTrue(res is CoreResult.Success)
        val saldo = (res as CoreResult.Success).value
        assertEquals(865L, saldo.saldoAtualCentavos)
        assertEquals(1115L, saldo.saldoAnterior7DiasCentavos)
        assertEquals(GrupoAcessoRu.GRUPO_4, saldo.grupoAcesso)
        assertEquals(1, saldo.transacoesUltimos7Dias.size)
        assertNotNull(saldo.refeicoesRestantes)
        assertEquals(3, saldo.refeicoesRestantes?.almocosOuJantaresRestantes)
        assertEquals(5, saldo.refeicoesRestantes?.cafesRestantes)
    }

    @Test
    fun preservesMultipleFeedEntriesPublishedOnSameDayAndTitle() {
        val htmlWithRepeatedNotice = """
            <html><body>
              <div id="atualizacoes-turma">
                <div class="rotator">
                  <table>
                    <tr><td>16/10/2026 - <a onclick="jsfcljs(document.getElementById('f'),{'idTurma':'1616625'},'')">ELETRONICA EXPERIMENTAL (2026.2)</a></td></tr>
                    <tr><td>Nova Notícia: Experimento 07 - Roteiro</td></tr>
                  </table>
                  <table>
                    <tr><td>16/10/2026 - <a onclick="jsfcljs(document.getElementById('f'),{'idTurma':'1616625'},'')">ELETRONICA EXPERIMENTAL (2026.2)</a></td></tr>
                    <tr><td>Nova Notícia: Experimento 07 - Roteiro</td></tr>
                  </table>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val feed = DiscentePortalParser.parseAtualizacoesFeed(htmlWithRepeatedNotice)
        assertEquals(2, feed.size)
        assertEquals(feed[0], feed[1])
        assertEquals("1616625", feed[0].idTurma)
        assertEquals("16/10/2026", feed[0].dataTexto)
        assertEquals("Nova Notícia: Experimento 07 - Roteiro", feed[0].tituloNoticia)
    }

    @Test
    fun extractsShortIntegralizacaoTextFromNestedAgendaTable() {
        val html = """
            <html><body>
              <div id="info-usuario">
                <p class="usuario"><span>Daniel</span></p>
                <p class="periodo-atual"><strong>2026.2</strong></p>
              </div>
              <form id="menu:form_menu_discente"><input type="hidden" name="id" value="331956" /></form>
              <div id="agenda-docente">
                <table>
                  <tr><td>Matrícula:</td><td>190026308</td></tr>
                  <tr>
                    <td colspan="2">
                      Integralizações:
                      <table>
                        <tr><td>CH. Obrigatória Pendente</td><td>420</td></tr>
                        <tr><td>CH. Optativa Pendente</td><td>540</td></tr>
                        <tr><td>CH. Total Currículo</td><td>3750</td></tr>
                        <tr><td>CH. Complementar Pendente</td><td>0</td></tr>
                        <tr><td colspan="2"><div class="progress-bar" style="width: 74%"></div></td></tr>
                        <tr><td colspan="2">74% Integralizado</td></tr>
                      </table>
                    </td>
                  </tr>
                </table>
              </div>
            </body></html>
        """.trimIndent()

        val result = DiscentePortalParser.parseDiscenteResumo(html)
        assertTrue(result is CoreResult.Success)
        val indices = (result as CoreResult.Success).value.indices
        assertEquals(74.0, indices.percentualIntegralizadoExato ?: 0.0, 0.01)
        assertEquals("74% Integralizado", indices.percentualIntegralizadoTexto)
    }

    @Test
    fun normalizesProtocolRelativeProfilePhotoAndRejectsPlaceholders() {
        val protocolRelativeHtml = """
            <html><body>
              <div id="info-usuario"><p class="usuario"><span>Daniel</span></p></div>
              <form id="menu:form_menu_discente"><input type="hidden" name="id" value="331956" /></form>
              <div id="perfil-docente">
                <div class="foto"><img src="//arquivos.unb.br/arquivos/12345/foto.jpg" /></div>
                <p class="info-docente"><span class="nome"><b>Daniel Junior</b></span></p>
              </div>
              <div id="agenda-docente"><table><tr><td>Matrícula:</td><td>190026308</td></tr></table></div>
            </body></html>
        """.trimIndent()
        val res1 = (DiscentePortalParser.parseDiscenteResumo(protocolRelativeHtml) as CoreResult.Success).value
        assertEquals("https://arquivos.unb.br/arquivos/12345/foto.jpg", res1.perfil.fotoUrl)

        val placeholderHtml = protocolRelativeHtml.replace(
            "//arquivos.unb.br/arquivos/12345/foto.jpg",
            "/sigaa/img/sem_foto.png",
        )
        val res2 = (DiscentePortalParser.parseDiscenteResumo(placeholderHtml) as CoreResult.Success).value
        assertNull(res2.perfil.fotoUrl)
    }
}
