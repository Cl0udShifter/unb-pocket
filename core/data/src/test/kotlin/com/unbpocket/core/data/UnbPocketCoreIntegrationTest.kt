package com.unbpocket.core.data

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.auth.CredentialsInput
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.GrupoAcessoRu
import com.unbpocket.core.network.InMemoryCookieJar
import com.unbpocket.core.network.NetworkEndpoints
import com.unbpocket.core.network.UnbHttpSession
import com.unbpocket.core.network.UnbHttpTransport
import com.unbpocket.core.parser.CarteirinhaPdfExtractedInput
import com.unbpocket.core.parser.PdfLineSegment
import com.unbpocket.core.parser.PdfLocatedImage
import com.unbpocket.core.parser.PdfMenuPageData
import com.unbpocket.core.parser.PdfTextSpan
import com.unbpocket.core.parser.RgbImageBuffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

class UnbPocketCoreIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var vault: InMemoryCredentialVault
    private lateinit var dao: InMemorySnapshotDao
    private lateinit var fileStore: TempFileStore
    private lateinit var pdfExtractor: FakePdfExtractor
    private lateinit var prefs: InMemoryRuPrefsSource
    private var fakeClock = 1_700_000_000_000L

    private val latin1 = Charset.forName("ISO-8859-1")
    private val frontEndId = "7D4F1E83699CC81005304EC1877AE1F7888882CD"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        vault = InMemoryCredentialVault()
        dao = InMemorySnapshotDao()
        fileStore = TempFileStore()
        pdfExtractor = FakePdfExtractor()
        prefs = InMemoryRuPrefsSource()
    }

    @After
    fun tearDown() {
        server.shutdown()
        fileStore.rootDir.deleteRecursively()
    }

    private fun createCore(): UnbPocketCore {
        val baseUrl = server.url("").toString().trimEnd('/')
        val jar = InMemoryCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val noRedirect = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val session = UnbHttpSession(cookieJar = jar, sigaaClient = client, noRedirectClient = noRedirect)
        val transport = UnbHttpTransport(
            session = session,
            endpoints = NetworkEndpoints(
                sigaaBaseUrl = baseUrl,
                casBaseUrl = baseUrl,
                sigBaseUrl = baseUrl,
                ruBaseUrl = baseUrl,
            ),
        )
        return UnbPocketCoreFactory.createWithPorts(
            transport = transport,
            snapshotDao = dao,
            vault = vault,
            ruPrefs = prefs,
            fileStore = fileStore,
            pdfExtractor = pdfExtractor,
            clockMillis = { fakeClock },
        )
    }

    @Test
    fun casLoginSyncPortalHomeTurmasDiscenteAndLogoutFlow() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        val casHits = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                return when {
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "GET" ->
                        redirect("$baseUrl/sso-server/login?service=sigaa")

                    path.startsWith("/sso-server/login") && request.method == "GET" -> {
                        casHits.incrementAndGet()
                        latin1Response(casLoginHtml())
                    }

                    path.startsWith("/sso-server/login") && request.method == "POST" -> {
                        if (body.contains("password=wrong")) {
                            latin1Response(casLoginHtml())
                        } else {
                            redirect("$baseUrl/sigaa/login/cas?ticket=ST-1")
                        }
                    }

                    path.startsWith("/sigaa/login/cas") ->
                        redirect("$baseUrl/sigaa/paginaInicial.do")

                    path.startsWith("/sigaa/paginaInicial.do") ->
                        redirect("$baseUrl/sigaa/verPortalDiscente.do")

                    path.startsWith("/sigaa/verPortalDiscente.do") ->
                        latin1Response(discenteHomeHtml())

                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "POST" -> {
                        when {
                            body.contains("formExibirExtrato") -> latin1Response(extratoRuHtml())
                            body.contains("atestadoMatriculaUnb") -> latin1Response(atestadoHtml())
                            body.contains("frontEndIdTurma") -> latin1Response(avaHomeHtml())
                            body.contains("relatorioNotasAluno") -> latin1Response(boletimHtml())
                            body.contains("emitiCarteiraEstudantilUnb") -> pdfResponse("%PDF-1.4 carteirinha")
                            body.contains("emitirDeclaracao") -> pdfResponse("%PDF-1.4 declaracao")
                            else -> MockResponse().setResponseCode(400)
                        }
                    }

                    path.startsWith("/sigaa/ava/index.jsf") && request.method == "POST" -> {
                        when {
                            body.contains("formMenu%3Afreq") -> latin1Response(frequenciaHtml())
                            body.contains("formMenu%3Anotas") -> latin1Response(notasIndisponiveisHtml())
                            body.contains("formAva%3Amat1") -> MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/pdf")
                                .setHeader("Content-Disposition", "attachment; filename=\"roteiro.pdf\"")
                                .setBody("PDF_MATERIAL_BYTES")
                            else -> MockResponse().setResponseCode(400)
                        }
                    }

                    path.startsWith("/sigaa/verArquivo") ->
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/zip")
                            .setHeader("Content-Disposition", "attachment; filename=\"Exp11.zip\"")
                            .setBody("ZIP_ANEXO_BYTES")

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()

        // 1. Senha errada -> InvalidCredentials
        val badLogin = core.auth.login(CredentialsInput("190026308", "wrong", rememberCredentials = true))
        assertTrue(badLogin is CoreResult.Failure)
        assertEquals(CoreError.InvalidCredentials, (badLogin as CoreResult.Failure).error)
        assertEquals(AuthState.Unauthenticated, core.auth.authState.value)

        // 2. Login valido -> Authenticated + credencial salva
        val goodLogin = core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = true))
        assertTrue(goodLogin is CoreResult.Success)
        assertEquals(2, casHits.get())
        assertTrue(core.auth.hasSavedCredentials())
        assertTrue(core.auth.authState.value is AuthState.Authenticated)

        // 3. syncPortalHome unificado popula Discente, Turmas (enriquecidas), Feed e Saldo RU
        val syncSummary = core.syncPortalHome(includeSaldoRu = true)
        assertTrue(syncSummary is CoreResult.Success)
        val summary = (syncSummary as CoreResult.Success).value
        assertEquals(1, summary.turmasCount)
        assertEquals(1, summary.atualizacoesCount)
        assertTrue(summary.saldoRuAtualizado)

        val resumo = core.discente.observeDiscenteResumo().first()
        assertNotNull(resumo)
        assertEquals("190026308", resumo?.value?.perfil?.matricula)
        assertEquals(DataOrigin.REMOTE, resumo?.origin)

        val turmas = core.turmas.observeTurmasSemestre().first()?.value.orEmpty()
        assertEquals(1, turmas.size)
        assertEquals("ENE0064", turmas[0].codigoDisciplina)
        assertEquals("JOAO GONDIM", turmas[0].docenteNome)

        val saldo = core.ru.observeSaldoExtrato().first()?.value
        assertEquals(865L, saldo?.saldoAtualCentavos)
        assertEquals(GrupoAcessoRu.GRUPO_4, saldo?.grupoAcesso)

        // 4. AVA: detalhe (enriquece CH), frequencia, notas indisponiveis, download material e anexo
        val avaRes = core.turmas.refreshTurmaDetalhe(frontEndId)
        assertTrue(avaRes is CoreResult.Success)
        val enrichedTurmas = core.turmas.observeTurmasSemestre().first()?.value.orEmpty()
        assertEquals(60, enrichedTurmas[0].cargaHorariaHoras)

        val freq = (core.turmas.refreshFrequencia(frontEndId) as CoreResult.Success).value
        assertEquals(8, freq.limiteMaximoFaltas)

        val notas = (core.turmas.refreshNotasParciais(frontEndId) as CoreResult.Success).value
        assertFalse(notas.notasDisponiveis)

        val mat = (core.turmas.downloadMaterial(frontEndId, "HASH_MAT_1") as CoreResult.Success).value
        assertEquals("roteiro.pdf", mat.nomeArquivo)
        assertTrue(File(mat.localFilePath).exists())

        val anexo = (core.turmas.downloadAnexoNoticia(frontEndId, "4245329") as CoreResult.Success).value
        assertEquals("Exp11.zip", anexo.nomeArquivo)
        assertTrue(File(anexo.localFilePath).exists())

        // 5. Discente: Carteirinha, Boletim e Declaracao
        pdfExtractor.carteirinhaResult = CoreResult.Success(buildSyntheticCarteirinhaInput())
        val carteirinha = (core.discente.refreshCarteirinha() as CoreResult.Success).value
        assertEquals("7744502674419362", carteirinha.qrPayload16Digitos)
        assertTrue(File(carteirinha.qrImageLocalPath).exists())

        val boletim = (core.discente.refreshBoletimGeral() as CoreResult.Success).value
        assertEquals(1, boletim.size)

        val docEmitido = (core.discente.emitirDocumentoOficial(TipoDocumentoOficial.DECLARACAO_VINCULO) as CoreResult.Success).value
        assertEquals("5c49c94b22", docEmitido.codigoVerificacao)
        assertEquals(1, core.discente.observeDocumentosEmitidos().first().size)

        // 6. Logout limpa vault, Room e arquivos
        assertTrue(core.auth.logout(clearCachedData = true) is CoreResult.Success)
        assertFalse(core.auth.hasSavedCredentials())
        assertNull(core.discente.observeDiscenteResumo().first())
        assertFalse(File(mat.localFilePath).exists())
    }

    @Test
    fun silentReloginRecoversWhenSigaaExpiresSessionEarly() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        val verPortalHits = AtomicInteger(0)
        val loginCount = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "GET" ->
                        redirect("$baseUrl/sso-server/login?service=sigaa")
                    path.startsWith("/sso-server/login") && request.method == "GET" ->
                        latin1Response(casLoginHtml())
                    path.startsWith("/sso-server/login") && request.method == "POST" -> {
                        loginCount.incrementAndGet()
                        redirect("$baseUrl/sigaa/verPortalDiscente.do")
                    }
                    path.startsWith("/sigaa/verPortalDiscente.do") -> {
                        if (verPortalHits.incrementAndGet() == 2) {
                            redirect("$baseUrl/sso-server/login?service=expired")
                        } else {
                            latin1Response(discenteHomeHtml())
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        assertTrue(core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = true)) is CoreResult.Success)

        val resumo = core.discente.refreshDiscenteResumo()
        assertTrue(resumo is CoreResult.Success)
        assertEquals(2, loginCount.get())
    }

    @Test
    fun returnsSessionExpiredWhenSessionTimesOutWithoutStoredCredentials() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "GET" ->
                        redirect("$baseUrl/sso-server/login?service=sigaa")
                    path.startsWith("/sso-server/login") && request.method == "GET" ->
                        latin1Response(casLoginHtml())
                    path.startsWith("/sso-server/login") && request.method == "POST" ->
                        redirect("$baseUrl/sigaa/verPortalDiscente.do")
                    path.startsWith("/sigaa/verPortalDiscente.do") ->
                        latin1Response(discenteHomeHtml())
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        assertTrue(core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = false)) is CoreResult.Success)
        assertFalse(core.auth.hasSavedCredentials())

        fakeClock += 41 * 60 * 1000L
        val res = core.discente.refreshDiscenteResumo()
        assertTrue(res is CoreResult.Failure)
        assertEquals(CoreError.SessionExpired, (res as CoreResult.Failure).error)
    }

    @Test
    fun ruRepositoryUsesModifiedGmtCacheAndActivatesImageFallbackOnPdfParseFailure() = runTest {
        val mediaDownloads = AtomicInteger(0)
        val pdfDownloads = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/wp-json/wp/v2/pages/12853") -> MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "id": 12853,
                          "modified_gmt": "2026-11-19T11:19:39",
                          "content": {
                            "rendered": "<h3>Cardápio Darcy Ribeiro</h3><p><a href=\"${server.url("/uploads/darcy.pdf")}\">23/11 à 29/11</a></p>"
                          }
                        }
                        """.trimIndent(),
                    )
                    path.contains("/uploads/darcy.pdf") -> {
                        pdfDownloads.incrementAndGet()
                        pdfResponse("%PDF-1.4 cardapio")
                    }
                    path.contains("/wp-json/wp/v2/media") -> {
                        mediaDownloads.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(
                            """
                            [
                              {
                                "date_gmt": "2026-11-19T11:19:00",
                                "title": { "rendered": "Darcy Ribeiro - Semana 02" },
                                "source_url": "${server.url("/uploads/darcy.pdf")}",
                                "media_details": { "sizes": { "full": { "source_url": "${server.url("/uploads/darcy-pdf.jpg")}" } } }
                              }
                            ]
                            """.trimIndent(),
                        )
                    }
                    path.contains("/wp-json/wp/v2/posts") -> MockResponse().setResponseCode(200).setBody(
                        """[{"id":23408,"date":"2026-05-28T10:00:00","link":"https://ru.unb.br/n1","title":{"rendered":"Edital 07"},"excerpt":{"rendered":"Resumo"},"content":{"rendered":"<p>Texto</p>"}}]""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()

        // 1. Falha no parser de PDF ativa fallback FALLBACK_IMAGE_ONLY automaticamente
        val fallbackMenu = (core.ru.refreshCardapio(CampusRu.DARCY_RIBEIRO) as CoreResult.Success).value
        assertEquals(CardapioParserStatus.FALLBACK_IMAGE_ONLY, fallbackMenu.parserStatus)
        assertTrue(fallbackMenu.fallbackImageUrl?.endsWith("/uploads/darcy-pdf.jpg") == true)
        assertEquals(1, pdfDownloads.get())

        // 2. Agora PDF extractor entrega pagina valida -> parse estruturado
        pdfExtractor.ruPagesResult = CoreResult.Success(listOf(buildSyntheticRuMenuPage()))
        val structuredMenu = (core.ru.refreshCardapio(CampusRu.DARCY_RIBEIRO) as CoreResult.Success).value
        assertEquals(CardapioParserStatus.PARSED_STRUCTURED, structuredMenu.parserStatus)
        assertEquals(1, structuredMenu.dias.size)
        assertEquals(2, pdfDownloads.get())

        // 3. Mesmo modified_gmt + !force usa cache local sem baixar PDF novamente
        val cachedSkip = (core.ru.refreshCardapio(CampusRu.DARCY_RIBEIRO, force = false) as CoreResult.Success).value
        assertEquals(CardapioParserStatus.PARSED_STRUCTURED, cachedSkip.parserStatus)
        assertEquals(2, pdfDownloads.get())

        // 4. Noticias e preferencias
        val noticias = (core.ru.refreshNoticias() as CoreResult.Success).value
        assertEquals(1, noticias.size)
        core.ru.setCampusSelecionado(CampusRu.GAMA)
        core.ru.setAlergenicosRestritos(setOf(Alergenico.GLUTEN), ocultarPratos = true)
        val updatedPrefs = core.ru.observePreferencias().first()
        assertEquals(CampusRu.GAMA, updatedPrefs.campusSelecionado)
        assertTrue(updatedPrefs.ocultarPratosComAlergenicos)
    }

    private fun redirect(location: String) =
        MockResponse().setResponseCode(302).setHeader("Location", location)

    private fun latin1Response(html: String): MockResponse {
        val bytes = html.toByteArray(latin1)
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/html;charset=ISO-8859-1")
            .setBody(Buffer().write(bytes))
    }

    private fun pdfResponse(content: String) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/pdf")
            .setBody(content)

    private fun casLoginHtml() = """
        <html><body>
          <form id="login-form" action="/sso-server/login;jsessionid=S1?service=sigaa" method="post">
            <input type="hidden" name="lt" value="LT-123" />
            <input type="hidden" name="execution" value="e1s1" />
            <input type="hidden" name="_eventId" value="submit" />
          </form>
        </body></html>
    """.trimIndent()

    private fun discenteHomeHtml() = """
        <html><body>
          <div id="info-usuario">
            <p class="usuario"><span>Daniel Junior</span></p>
            <p class="unidade">DEPTO ENGENHARIA ELETRICA</p>
            <p class="periodo-atual"><strong>2026.2</strong></p>
          </div>
          <form id="menu:form_menu_discente"><input type="hidden" name="id" value="331956" /></form>
          <div id="perfil-docente"><p class="info-docente"><span class="nome"><b>Daniel Junior</b></span>Bio</p></div>
          <div id="agenda-docente">
            <table>
              <tr><td>Matrícula:</td><td>190026308</td></tr>
              <tr><td>Curso:</td><td>ENGENHARIA DE REDES</td></tr>
              <tr><td>Nível:</td><td>GRADUAÇÃO</td></tr>
              <tr><td>Status:</td><td>ATIVO</td></tr>
              <tr><td>Entrada:</td><td>2019.1</td></tr>
              <tr><td>IRA:</td><td>3.2102</td></tr>
              <tr><td>MP:</td><td>3.4563</td></tr>
            </table>
          </div>
          <form id="formExibirExtrato">
            <a onclick="jsfcljs(document.getElementById('formExibirExtrato'),{'formExibirExtrato:btn':'formExibirExtrato:btn'},'')">Saldo</a>
          </form>
          <input type="hidden" name="javax.faces.ViewState" value="j_id1" />
          <div id="turmas-portal">
            <div id="atualizacoes-turma">
              <div class="rotator">
                <table><tr><td>22/11/2026 - <a onclick="jsfcljs(f,{'idTurma':'1614167'},'')">REDES SEM FIO (2026.2)</a></td></tr><tr><td>Novo aviso</td></tr></table>
              </div>
            </div>
            <table><tbody>
              <tr>
                <td class="descricao">
                  <form id="form_acessarTurmaVirtualj_id_3">
                    <a onclick="jsfcljs(document.getElementById('form_acessarTurmaVirtualj_id_3'),{'form_acessarTurmaVirtualj_id_3:btn':'form_acessarTurmaVirtualj_id_3:btn','frontEndIdTurma':'$frontEndId'},'')">REDES SEM FIO</a>
                  </form>
                </td>
                <td class="info">FT AT-31/12</td>
                <td class="info">24M12</td>
              </tr>
              <tr><td colspan="5" id="linha_1614167"></td></tr>
            </tbody></table>
          </div>
        </body></html>
    """.trimIndent()

    private fun extratoRuHtml() = """
        <html><body>
          <div><h4>Extrato no Restaurante Universitário</h4>
            <table>
              <tr><td>22/11/2026 20:43</td><td>Saldo</td><td>R$ 8,65</td></tr>
              <tr><td>16/11/2026 17:37</td><td>Grupo 4 Jantar</td><td>-R$ 2,50</td></tr>
            </table>
          </div>
        </body></html>
    """.trimIndent()

    private fun atestadoHtml() = """
        <html><body>
          <table class="listagem"><tbody>
            <tr><td>ENE0064</td><td>REDES SEM FIO<br/>JOAO GONDIM<br/>Local: FT AT-31/12</td><td>01</td><td>MATRICULADO(A)</td><td>24M12</td></tr>
          </tbody></table>
        </body></html>
    """.trimIndent()

    private fun avaHomeHtml() = """
        <html><body>
          <div id="nomeTurma"><span id="linkCodigoTurma">ENE0064 - </span><span id="linkNomeTurma">REDES SEM FIO</span><span id="linkPeriodoTurma">- T01</span></div>
          <input type="hidden" name="idTurma" value="1614167" />
          <input type="hidden" name="javax.faces.ViewState" value="j_id2" />
          <form id="formMenu">
            <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:freq':'formMenu:freq'},'')"><div class="itemMenu">Frequência</div></a>
            <a onclick="jsfcljs(document.getElementById('formMenu'),{'formMenu:notas':'formMenu:notas'},'')"><div class="itemMenu">Ver Notas</div></a>
          </form>
          <div id="ultimaNoticia">
            <h4>Roteiro - 22/11/2026 09:34</h4>
            <span class="conteudoNoticia">Texto</span>
            <a href="/sigaa/verArquivo?idArquivo=4245329&key=k1">Exp11.zip</a>
          </div>
          <div id="barraDireita"><div>Aulas (Ministradas/Total): <i>40 / 64</i></div></div>
          <form id="formAva">
            <div class="topico-aula">
              <div class="titulo">Topico 1</div>
              <div class="conteudotopico"><div class="item"><img src="/icones/pdf.png"/><a onclick="jsfcljs(document.getElementById('formAva'),{'formAva:mat1':'formAva:mat1','id':'HASH_MAT_1'},'_blank')">Roteiro</a></div></div>
            </div>
          </form>
          <form id="formTurma">
            <a class="linkTurma" onclick="jsfcljs(document.getElementById('formTurma'),{'frontEndIdTurma':'$frontEndId'},'')"><span>ENE0064 - REDES SEM FIO (60h) (2026.2)</span></a>
          </form>
        </body></html>
    """.trimIndent()

    private fun frequenciaHtml() = """
        <html><body><fieldset><legend>Mapa de Frequências</legend>
          <p>Presenças Registradas: 32</p>
          <p>Número de Aulas com Registro de Frequência: 32</p>
          <p>Porcentagem de Frequência em relação as Aulas com Registro de Frequência: 100%</p>
          <p>Número de Aulas definidas pela CH do Componente: 32</p>
          <p>Porcentagem de Frequência em relação a CH: 100%</p>
        </fieldset></body></html>
    """.trimIndent()

    private fun notasIndisponiveisHtml() =
        "<html><body><p class='aviso'>Ainda não foram lançadas notas.</p></body></html>"

    private fun boletimHtml() = """
        <html><body>
          <table class="tabelaRelatorio"><caption>2026.1</caption>
            <tbody><tr><td>ENE0045</td><td>ELETRÔNICA</td><td>SS</td><td>SS</td><td>0</td><td>APROVADO(A)</td></tr></tbody>
          </table>
        </body></html>
    """.trimIndent()

    private fun buildSyntheticCarteirinhaInput(): CarteirinhaPdfExtractedInput {
        val bitMatrix = com.google.zxing.MultiFormatWriter()
            .encode("7744502674419362", com.google.zxing.BarcodeFormat.QR_CODE, 250, 250)
        val rgb = ByteArray(250 * 250 * 3)
        for (y in 0 until 250) {
            for (x in 0 until 250) {
                val c = if (bitMatrix.get(x, y)) 0.toByte() else 255.toByte()
                val off = (y * 250 + x) * 3
                rgb[off] = c
                rgb[off + 1] = c
                rgb[off + 2] = c
            }
        }
        return CarteirinhaPdfExtractedInput(
            textLines = listOf("DANIEL", "190026308", "VALIDADE Março 2027", "GRADUAÇÃO", "ENGENHARIA"),
            img2Foto = RgbImageBuffer(10, 10, ByteArray(300)),
            img4Qr = RgbImageBuffer(250, 250, rgb),
        )
    }

    private fun buildSyntheticRuMenuPage(): PdfMenuPageData {
        val legend = Alergenico.entries.sortedBy { it.legendOrderIndex }.mapIndexed { idx, alg ->
            val buf = RgbImageBuffer(32, 32, ByteArray(32 * 32 * 3) { (alg.legendOrderIndex * 20).toByte() })
            PdfLocatedImage("leg_$idx", 50f + idx * 60f, 540f, 70f + idx * 60f, 560f, buf)
        }
        val lines = listOf(80f, 100f, 120f, 140f, 160f, 200f).map { y -> PdfLineSegment(20f, y, 400f, y) } +
            listOf(20f, 120f, 250f, 400f).map { x -> PdfLineSegment(x, 80f, x, 200f) }
        val texts = listOf(
            PdfTextSpan("2ª FEIRA", 260f, 125f, 340f, 135f),
            PdfTextSpan("23/11/2026", 260f, 145f, 350f, 155f),
            PdfTextSpan("FRUTA", 130f, 170f, 200f, 185f),
            PdfTextSpan("Banana", 260f, 170f, 320f, 185f),
        )
        return PdfMenuPageData(0, 842f, 595f, texts, lines, legend)
    }
}
