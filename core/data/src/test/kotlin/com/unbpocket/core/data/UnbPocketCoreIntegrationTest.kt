package com.unbpocket.core.data

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.auth.CredentialsInput
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.PreloadPhase
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import java.time.ZoneId
import java.time.ZonedDateTime
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

    @Test
    fun loginReusesFreshPortalHtmlAndCompletesPhase2BackgroundPreload() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        val verPortalHits = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                return when {
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "GET" ->
                        redirect("$baseUrl/sso-server/login?service=sigaa")

                    path.startsWith("/sso-server/login") && request.method == "GET" ->
                        latin1Response(casLoginHtml())

                    path.startsWith("/sso-server/login") && request.method == "POST" ->
                        redirect("$baseUrl/sigaa/verPortalDiscente.do")

                    path.startsWith("/sigaa/verPortalDiscente.do") -> {
                        verPortalHits.incrementAndGet()
                        latin1Response(discenteHomeHtml())
                    }

                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "POST" -> {
                        when {
                            body.contains("formExibirExtrato") -> latin1Response(extratoRuHtml())
                            body.contains("atestadoMatriculaUnb") -> latin1Response(atestadoHtml())
                            body.contains("frontEndIdTurma") -> latin1Response(avaHomeHtml())
                            body.contains("relatorioNotasAluno") -> latin1Response(boletimHtml())
                            body.contains("emitiCarteiraEstudantilUnb") -> pdfResponse("%PDF-1.4 carteirinha")
                            else -> MockResponse().setResponseCode(400)
                        }
                    }

                    path.startsWith("/sigaa/ava/index.jsf") && request.method == "POST" -> {
                        when {
                            body.contains("formMenu%3Afreq") -> latin1Response(frequenciaHtml())
                            body.contains("formMenu%3Anotas") -> latin1Response(notasIndisponiveisHtml())
                            else -> MockResponse().setResponseCode(400)
                        }
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        pdfExtractor.carteirinhaResult = CoreResult.Success(buildSyntheticCarteirinhaInput())

        assertTrue(core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = true)) is CoreResult.Success)
        assertEquals(1, verPortalHits.get())

        val essential = core.syncPortalHome(includeSaldoRu = true)
        assertTrue(essential is CoreResult.Success)
        // Reutiliza o HTML do login na Fase 1 (apenas +1 hit para ViewState fresco do Atestado)
        assertEquals(2, verPortalHits.get())

        core.startBackgroundPreload(includeTurmaDetails = true)
        val completed = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                core.preloadProgress.first { it.phase == PreloadPhase.COMPLETED && it.totalSteps > 0 }
            }
        }
        assertEquals(PreloadPhase.COMPLETED, completed.phase)
        assertEquals(7, completed.totalSteps)
        assertNotNull(core.discente.observeCarteirinha().first())
        assertNotNull(core.discente.observeBoletimGeral().first())
        assertNotNull(core.turmas.observeTurmaDetalhe(frontEndId).first())
        assertNotNull(core.turmas.observeFrequencia(frontEndId).first())
        assertNotNull(core.turmas.observeNotasParciais(frontEndId).first())

        assertTrue(core.auth.logout(clearCachedData = true) is CoreResult.Success)
        assertEquals(PreloadPhase.IDLE, core.preloadProgress.value.phase)
    }

    @Test
    fun silentRefreshAllSkipsRedundantWritesUpdatesOnDiffAndPreservesCacheOnError() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        var currentExtratoSaldo = "R$ 8,65"
        var failPortal = false

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                return when {
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "GET" ->
                        redirect("$baseUrl/sso-server/login?service=sigaa")

                    path.startsWith("/sso-server/login") && request.method == "GET" ->
                        latin1Response(casLoginHtml())

                    path.startsWith("/sso-server/login") && request.method == "POST" ->
                        redirect("$baseUrl/sigaa/verPortalDiscente.do")

                    path.startsWith("/sigaa/verPortalDiscente.do") ->
                        if (failPortal) MockResponse().setResponseCode(503) else latin1Response(discenteHomeHtml())

                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "POST" -> {
                        if (failPortal) {
                            MockResponse().setResponseCode(503)
                        } else {
                            when {
                                body.contains("formExibirExtrato") -> latin1Response(
                                    extratoRuHtml().replace("R$ 8,65", currentExtratoSaldo),
                                )
                                body.contains("atestadoMatriculaUnb") -> latin1Response(atestadoHtml())
                                body.contains("relatorioNotasAluno") -> latin1Response(boletimHtml())
                                body.contains("emitiCarteiraEstudantilUnb") -> pdfResponse("%PDF-1.4 carteirinha")
                                else -> MockResponse().setResponseCode(400)
                            }
                        }
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        pdfExtractor.carteirinhaResult = CoreResult.Success(buildSyntheticCarteirinhaInput())
        assertTrue(core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = true)) is CoreResult.Success)
        assertTrue(core.silentRefreshAll(includeTurmaDetails = false) is CoreResult.Success)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                core.preloadProgress.first { it.phase == PreloadPhase.COMPLETED && it.totalSteps == 2 }
            }
        }

        val upsertsAfterInitialLoad = dao.upsertCount

        // Payload identico: nao dispara novas gravacoes no Room e sinaliza anyDataChanged = false
        val sameRes = core.silentRefreshAll(includeTurmaDetails = false)
        assertTrue(sameRes is CoreResult.Success)
        assertFalse((sameRes as CoreResult.Success).value.anyDataChanged)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                core.preloadProgress.first { it.phase == PreloadPhase.COMPLETED && it.totalSteps == 2 }
            }
        }
        assertEquals(upsertsAfterInitialLoad, dao.upsertCount)
        assertEquals(865L, core.ru.observeSaldoExtrato().first()?.value?.saldoAtualCentavos)

        // Saldo alterado no SIGAA: detecta diff, persiste e atualiza fluxo reativo
        currentExtratoSaldo = "R$ 15,20"
        val changedRes = core.silentRefreshAll(includeTurmaDetails = false)
        assertTrue(changedRes is CoreResult.Success)
        assertTrue((changedRes as CoreResult.Success).value.anyDataChanged)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                core.preloadProgress.first { it.phase == PreloadPhase.COMPLETED && it.totalSteps == 2 }
            }
        }
        assertTrue(dao.upsertCount > upsertsAfterInitialLoad)
        assertEquals(1520L, core.ru.observeSaldoExtrato().first()?.value?.saldoAtualCentavos)

        // Erro HTTP na sincronizacao silenciosa preserva integralmente o cache anterior
        failPortal = true
        val offlineRes = core.silentRefreshAll(includeTurmaDetails = false)
        assertTrue(offlineRes is CoreResult.Failure)
        assertEquals(1520L, core.ru.observeSaldoExtrato().first()?.value?.saldoAtualCentavos)
        assertNotNull(core.discente.observeDiscenteResumo().first())
    }

    @Test
    fun logoutClearsSigaaUserDataWhilePreservingPublicRuMenuPricesNoticesAndPdfs() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
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
                    path.contains("slug=precos-e-grupos") -> MockResponse().setResponseCode(200).setBody(
                        """
                        [
                          {
                            "id": 400,
                            "modified_gmt": "2026-11-01T10:00:00",
                            "content": { "rendered": "<table><tr><th>Grupo</th><th>Café</th><th>Almoço</th><th>Jantar</th></tr><tr><td>Grupo IV</td><td>R$ 2,50</td><td>R$ 6,10</td><td>R$ 6,10</td></tr></table>" }
                          }
                        ]
                        """.trimIndent(),
                    )
                    path.contains("/uploads/darcy.pdf") -> pdfResponse("%PDF-1.4 cardapio")
                    path.contains("/wp-json/wp/v2/media") -> MockResponse().setResponseCode(200).setBody("[]")
                    path.contains("/wp-json/wp/v2/posts") -> MockResponse().setResponseCode(200).setBody(
                        """[{"id":23408,"date":"2026-05-28T10:00:00","link":"https://ru.unb.br/n1","title":{"rendered":"Edital 07"},"excerpt":{"rendered":"Resumo"},"content":{"rendered":"<p>Texto</p>"}}]""",
                    )

                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "GET" ->
                        redirect("$baseUrl/sso-server/login?service=sigaa")
                    path.startsWith("/sso-server/login") && request.method == "GET" ->
                        latin1Response(casLoginHtml())
                    path.startsWith("/sso-server/login") && request.method == "POST" ->
                        redirect("$baseUrl/sigaa/verPortalDiscente.do")
                    path.startsWith("/sigaa/verPortalDiscente.do") ->
                        latin1Response(discenteHomeHtml())
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "POST" -> {
                        when {
                            body.contains("formExibirExtrato") -> latin1Response(extratoRuHtml())
                            body.contains("atestadoMatriculaUnb") -> latin1Response(atestadoHtml())
                            body.contains("frontEndIdTurma") -> latin1Response(avaHomeHtml())
                            else -> MockResponse().setResponseCode(400)
                        }
                    }
                    path.startsWith("/sigaa/ava/index.jsf") && request.method == "POST" ->
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/pdf")
                            .setHeader("Content-Disposition", "attachment; filename=\"roteiro.pdf\"")
                            .setBody("PDF_MATERIAL_BYTES")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        pdfExtractor.ruPagesResult = CoreResult.Success(listOf(buildSyntheticRuMenuPage()))

        // 1. Carrega dados publicos do RU (cardapio, tabela de precos, noticias e PDF local)
        val menu = (core.ru.refreshCardapio(CampusRu.DARCY_RIBEIRO) as CoreResult.Success).value
        assertNotNull(core.ru.observeCardapio(CampusRu.DARCY_RIBEIRO).first())
        assertTrue(core.ru.refreshTabelaPrecos() is CoreResult.Success)
        assertTrue(core.ru.refreshNoticias() is CoreResult.Success)
        val menuPdfPath = menu.localPdfPath
        assertNotNull(menuPdfPath)
        assertTrue(File(menuPdfPath!!).exists())

        // 2. Conecta ao SIGAA, sincroniza perfil/turmas/saldo RU e baixa arquivo de turma
        assertTrue(core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = true)) is CoreResult.Success)
        assertTrue(core.syncPortalHome(includeSaldoRu = true) is CoreResult.Success)
        val material = (core.turmas.downloadMaterial(frontEndId, "HASH_MAT_1") as CoreResult.Success).value
        assertNotNull(core.ru.observeSaldoExtrato().first())
        assertTrue(File(material.localFilePath).exists())

        // 3. Desconectar manualmente limpa dados SIGAA + saldo RU, mas preserva cardapio/precos/noticias/PDF publicos
        assertTrue(core.auth.logout(clearCachedData = true) is CoreResult.Success)
        assertFalse(core.auth.hasSavedCredentials())
        assertNull(core.discente.observeDiscenteResumo().first())
        assertNull(core.turmas.observeTurmasSemestre().first())
        assertNull(core.ru.observeSaldoExtrato().first())
        assertFalse(File(material.localFilePath).exists())

        assertNotNull(core.ru.observeCardapio(CampusRu.DARCY_RIBEIRO).first())
        assertEquals(DataOrigin.REMOTE, core.ru.observeTabelaPrecos().first()?.origin)
        assertNotNull(core.ru.observeNoticias().first())
        assertTrue(File(menuPdfPath).exists())

        // Confirma regra de filtro de subdiretorios usada pelo DownloadedFileStore no Android
        assertTrue(com.unbpocket.core.data.local.DownloadedFileStore.isSigaaUserDir("carteirinha"))
        assertTrue(com.unbpocket.core.data.local.DownloadedFileStore.isSigaaUserDir("documentos"))
        assertTrue(com.unbpocket.core.data.local.DownloadedFileStore.isSigaaUserDir("materiais_$frontEndId"))
        assertFalse(com.unbpocket.core.data.local.DownloadedFileStore.isSigaaUserDir("pdfs"))
    }

    @Test
    fun ruAutoSyncRespectsCooldownWeeklyRolloverDelayedPublishAndPartialFailures() = runTest {
        val brasilia = ZoneId.of("America/Sao_Paulo")
        fun brtMillis(day: Int, hour: Int, minute: Int = 0): Long =
            ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, brasilia).toInstant().toEpochMilli()

        val pagesHits = AtomicInteger(0)
        val pdfHits = AtomicInteger(0)
        var includeW2Links = false
        var failPlanaltinaW2 = false

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/wp-json/wp/v2/pages/12853") -> {
                        pagesHits.incrementAndGet()
                        val html = buildString {
                            for (campus in CampusRu.entries) {
                                append("<h3>Cardápio ${campus.displayName}</h3>")
                                append("<p><a href=\"${server.url("/uploads/${campus.slug}-w1.pdf")}\">23/11/2026 à 29/11/2026</a></p>")
                                if (includeW2Links) {
                                    append("<p><a href=\"${server.url("/uploads/${campus.slug}-w2.pdf")}\">30/11/2026 à 06/12/2026</a></p>")
                                }
                            }
                        }.replace("\"", "\\\"")
                        MockResponse().setResponseCode(200).setBody(
                            """{"id":12853,"modified_gmt":"2026-11-23T08:00:00","content":{"rendered":"$html"}}""",
                        )
                    }
                    path.contains("-w1.pdf") -> {
                        pdfHits.incrementAndGet()
                        pdfResponse("%PDF-1.4 w1")
                    }
                    path.contains("-w2.pdf") -> {
                        pdfHits.incrementAndGet()
                        if (failPlanaltinaW2 && path.contains("planaltina")) {
                            MockResponse().setResponseCode(500)
                        } else {
                            pdfResponse("%PDF-1.4 w2")
                        }
                    }
                    path.contains("slug=precos-e-grupos") -> MockResponse().setResponseCode(200).setBody(
                        """[{"id":400,"modified_gmt":"2026-11-01T10:00:00","content":{"rendered":"<table><tr><th>Grupo</th><th>Café</th></tr><tr><td>Grupo IV</td><td>R$ 2,50</td></tr></table>"}}]""",
                    )
                    path.contains("/wp-json/wp/v2/media") -> MockResponse().setResponseCode(200).setBody("[]")
                    path.contains("/wp-json/wp/v2/posts") -> MockResponse().setResponseCode(200).setBody(
                        """[{"id":23408,"date":"2026-05-28T10:00:00","link":"https://ru.unb.br/n1","title":{"rendered":"Edital 07"},"excerpt":{"rendered":"Resumo"},"content":{"rendered":"<p>Texto</p>"}}]""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        pdfExtractor.ruPagesResult = CoreResult.Success(listOf(buildSyntheticRuMenuPage()))

        // 1. Primeira sincronizacao na quarta 25/11 busca pages/12853 1 vez e os 5 PDFs
        fakeClock = brtMillis(25, 10, 0)
        val initial = (core.ru.syncCardapiosIfNeeded(forceCheck = false, includeExtras = true) as CoreResult.Success).value
        assertFalse(initial.skippedDueToCooldown)
        assertEquals(CampusRu.entries.toSet(), initial.updatedCampi)
        assertTrue(initial.failedCampi.isEmpty())
        assertTrue(initial.tabelaPrecosAtualizada)
        assertTrue(initial.noticiasAtualizadas)
        assertEquals(1, pagesHits.get())
        assertEquals(5, pdfHits.get())

        // 2. Dentro de 6h e semana vigente, nenhuma requisicao de rede e feita (mesmo apos logout do SIGAA)
        fakeClock = brtMillis(25, 12, 0)
        assertTrue(core.auth.logout(clearCachedData = true) is CoreResult.Success)
        val withinCooldown = (core.ru.syncCardapiosIfNeeded(forceCheck = false) as CoreResult.Success).value
        assertTrue(withinCooldown.skippedDueToCooldown)
        assertEquals(CampusRu.entries.toSet(), withinCooldown.upToDateCampi)
        assertEquals(1, pagesHits.get())
        assertEquals(5, pdfHits.get())
        assertNotNull(core.ru.observeCardapio(CampusRu.GAMA).first())

        // 3. No domingo 22:00 BRT forcamos verificacao com semana ainda valida
        fakeClock = brtMillis(29, 22, 0)
        val sundayCheck = (core.ru.syncCardapiosIfNeeded(forceCheck = true) as CoreResult.Success).value
        assertFalse(sundayCheck.skippedDueToCooldown)
        assertTrue(sundayCheck.updatedCampi.isEmpty())
        assertEquals(2, pagesHits.get())
        assertEquals(5, pdfHits.get())

        // 4. Segunda 00:30 BRT (2h30 depois, menor que 6h): semana expirou e RU atrasou a publicacao
        fakeClock = brtMillis(30, 0, 30)
        val delayedCheck = (core.ru.syncCardapiosIfNeeded(forceCheck = false) as CoreResult.Success).value
        assertFalse(delayedCheck.skippedDueToCooldown)
        assertTrue(delayedCheck.updatedCampi.isEmpty())
        assertEquals(CampusRu.entries.toSet(), delayedCheck.upToDateCampi)
        assertEquals(3, pagesHits.get())
        assertEquals(5, pdfHits.get())
        assertEquals("23/11/2026 à 29/11/2026", core.ru.observeCardapio(CampusRu.DARCY_RIBEIRO).first()?.value?.semanaLabel)

        // 5. 15 min depois respeita o cooldown curto de 30 min para cardapio expirado
        fakeClock = brtMillis(30, 0, 45)
        val throttledRetry = (core.ru.syncCardapiosIfNeeded(forceCheck = false) as CoreResult.Success).value
        assertTrue(throttledRetry.skippedDueToCooldown)
        assertEquals(3, pagesHits.get())

        // 6. Apos 35 min (01:05 BRT), novo PDF semanal entra no ar com falha parcial em Planaltina
        fakeClock = brtMillis(30, 1, 5)
        includeW2Links = true
        failPlanaltinaW2 = true
        val partialUpdate = (core.ru.syncCardapiosIfNeeded(forceCheck = false) as CoreResult.Success).value
        assertFalse(partialUpdate.skippedDueToCooldown)
        assertEquals(
            setOf(
                CampusRu.DARCY_RIBEIRO,
                CampusRu.CEILANDIA,
                CampusRu.GAMA,
                CampusRu.FAZENDA_AGUA_LIMPA,
            ),
            partialUpdate.updatedCampi,
        )
        assertEquals(setOf(CampusRu.PLANALTINA), partialUpdate.failedCampi.keys)
        assertEquals("30/11/2026 à 06/12/2026", core.ru.observeCardapio(CampusRu.DARCY_RIBEIRO).first()?.value?.semanaLabel)
        assertEquals("23/11/2026 à 29/11/2026", core.ru.observeCardapio(CampusRu.PLANALTINA).first()?.value?.semanaLabel)
        assertEquals(4, pagesHits.get())

        // 7. IDs de noticias vistas sao persistidos e sobrevivem ao logout SIGAA
        assertTrue(core.ru.observePreferencias().first().noticiasVistasIds.isEmpty())
        assertTrue(core.ru.markNoticiasVistas(setOf(23408L)) is CoreResult.Success)
        assertTrue(core.auth.logout(clearCachedData = true) is CoreResult.Success)
        assertEquals(setOf(23408L), core.ru.observePreferencias().first().noticiasVistasIds)
    }

    @Test
    fun getNoticiaCompletaUsesFastPathAfterAvaHomeAndCachesInRoom() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        val discentePosts = AtomicInteger(0)
        val avaPosts = AtomicInteger(0)

        val avaHtmlWithVisualizar = """
            <html><body>
              <div id="nomeTurma"><span id="linkCodigoTurma">ENE0064 - </span><span id="linkNomeTurma">REDES SEM FIO</span><span id="linkPeriodoTurma">- T01</span></div>
              <input type="hidden" name="idTurma" value="1614167" />
              <input type="hidden" name="javax.faces.ViewState" value="j_id2" />
              <div id="barraDireita">
                <div>Aulas (Ministradas/Total): <i>40 / 64</i></div>
                <div class="rich-panel-body">
                  20/11/2026 10:00<br/>
                  <i>Aviso Sem Anexo</i>
                  <form id="f1">
                    <input type="hidden" name="id" value="24442200" />
                    <a onclick="jsfcljs(document.getElementById('f1'),{'f1:ver':'f1:ver'},'')">(Visualizar)</a>
                  </form>
                  15/11/2026 08:30<br/>
                  <i>Aviso Anterior</i>
                  <form id="f2">
                    <input type="hidden" name="id" value="24441000" />
                    <a onclick="jsfcljs(document.getElementById('f2'),{'f2:ver':'f2:ver'},'')">(Visualizar)</a>
                  </form>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                return when {
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "GET" ->
                        redirect("$baseUrl/sso-server/login?service=sigaa")
                    path.startsWith("/sso-server/login") && request.method == "GET" ->
                        latin1Response(casLoginHtml())
                    path.startsWith("/sso-server/login") && request.method == "POST" ->
                        redirect("$baseUrl/sigaa/verPortalDiscente.do")
                    path.startsWith("/sigaa/verPortalDiscente.do") ->
                        latin1Response(discenteHomeHtml())
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "POST" -> {
                        discentePosts.incrementAndGet()
                        latin1Response(avaHtmlWithVisualizar)
                    }
                    path.startsWith("/sigaa/ava/index.jsf") && request.method == "POST" -> {
                        avaPosts.incrementAndGet()
                        val titulo = if (body.contains("24442200")) "Aviso Sem Anexo" else "Aviso Anterior"
                        val conteudo = if (body.contains("24442200")) "Conteudo completo 1" else "Conteudo completo 2"
                        latin1Response(
                            """
                            <html><body>
                              <div id="ultimaNoticia">
                                <h4>$titulo - 20/11/2026 10:00</h4>
                                <span class="conteudoNoticia"><p>$conteudo</p></span>
                              </div>
                            </body></html>
                            """.trimIndent(),
                        )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        assertTrue(core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = true)) is CoreResult.Success)
        val detalhe = (core.turmas.refreshTurmaDetalhe(frontEndId) as CoreResult.Success).value
        assertEquals(listOf("Aviso Sem Anexo", "Aviso Anterior"), detalhe.historicoNoticiasResumo.map { it.titulo })

        discentePosts.set(0)
        avaPosts.set(0)

        // 1. Primeira leitura usa fast-path direto no AVA (1 POST, sem reentrar em discente.jsf)
        val first = (core.turmas.getNoticiaCompleta(frontEndId, "24442200") as CoreResult.Success).value
        assertEquals("Conteudo completo 1", first.conteudoTexto)
        assertEquals(0, discentePosts.get())
        assertEquals(1, avaPosts.get())

        // 2. Segunda leitura do mesmo aviso vem do Room (0 requisicoes de rede)
        val cached = (core.turmas.getNoticiaCompleta(frontEndId, "24442200") as CoreResult.Success).value
        assertEquals("Conteudo completo 1", cached.conteudoTexto)
        assertEquals(0, discentePosts.get())
        assertEquals(1, avaPosts.get())

        // 3. Outro aviso nao em cache reentra na turma e persiste no Room
        val second = (core.turmas.getNoticiaCompleta(frontEndId, "24441000") as CoreResult.Success).value
        assertEquals("Conteudo completo 2", second.conteudoTexto)
        assertEquals(1, discentePosts.get())
        assertEquals(2, avaPosts.get())
    }

    @Test
    fun downloadMaterialExportsToDownloadsAndRejectsUnexpectedHtmlPage() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        val discentePosts = AtomicInteger(0)
        val avaPosts = AtomicInteger(0)
        var forceUnexpectedHtml = false

        val avaHtmlWithMaterial = """
            <html><body>
              <div id="nomeTurma"><span id="linkCodigoTurma">ENE0295 - </span><span id="linkNomeTurma">LAB</span><span id="linkPeriodoTurma">- T04</span></div>
              <input type="hidden" name="idTurma" value="1616625" />
              <input type="hidden" name="javax.faces.ViewState" value="j_id4" />
              <form id="formAva">
                <div class="topico-aula">
                  <div class="titulo">Experimentos (10/08/2026 - 14/12/2026)</div>
                  <div class="conteudotopico">
                    <div class="item">
                      <img src="/sigaa/img/indicator.gif" />
                      <img src="/sigaa/img/porta_arquivos/icones/zip.png" />
                      <a onclick="jsfcljs(document.getElementById('formAva'),{'formAva:mat1':'formAva:mat1','id':'HASH_ZIP_1'},'_blank')">Experimento 01</a>
                    </div>
                  </div>
                </div>
              </form>
            </body></html>
        """.trimIndent()

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
                    path.startsWith("/sigaa/portais/discente/discente.jsf") && request.method == "POST" -> {
                        discentePosts.incrementAndGet()
                        latin1Response(avaHtmlWithMaterial)
                    }
                    path.startsWith("/sigaa/ava/index.jsf") && request.method == "POST" -> {
                        avaPosts.incrementAndGet()
                        if (forceUnexpectedHtml) {
                            latin1Response("<html><body><div>ViewExpired ou erro HTML</div></body></html>")
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/octet-stream")
                                .setHeader("Content-Disposition", "attachment; filename*=UTF-8''Exp%2001.zip")
                                .setBody("PK_FAKE_ZIP_BYTES")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val core = createCore()
        assertTrue(core.auth.login(CredentialsInput("190026308", "secret", rememberCredentials = true)) is CoreResult.Success)
        val detalhe = (core.turmas.refreshTurmaDetalhe(frontEndId) as CoreResult.Success).value
        assertEquals("zip", detalhe.topicosAula.single().materiais.single().tipoExtensao)

        discentePosts.set(0)
        avaPosts.set(0)

        // 1. Download via fast-path (1 POST no AVA), inferindo MIME zip, decodificando nome e salvando em Downloads/UnB Pocket
        val downloaded = (core.turmas.downloadMaterial(frontEndId, "HASH_ZIP_1") as CoreResult.Success).value
        assertEquals("Exp 01.zip", downloaded.nomeArquivo)
        assertEquals("application/zip", downloaded.mimeType)
        assertEquals("Downloads/UnB Pocket/Exp 01.zip", downloaded.downloadPublicoInfo)
        assertEquals(0, discentePosts.get())
        assertEquals(1, avaPosts.get())
        assertTrue(File(downloaded.localFilePath).exists())
        assertTrue(File(fileStore.rootDir, "public_downloads/UnB Pocket/Exp 01.zip").exists())

        // 2. Resposta HTML inesperada no lugar de arquivo aciona reentrada e falha explicitamente sem corromper cache
        forceUnexpectedHtml = true
        val htmlErr = core.turmas.downloadMaterial(frontEndId, "HASH_ZIP_1")
        assertTrue(htmlErr is CoreResult.Failure && htmlErr.error is CoreError.ParseError)
        assertEquals(1, discentePosts.get())
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
