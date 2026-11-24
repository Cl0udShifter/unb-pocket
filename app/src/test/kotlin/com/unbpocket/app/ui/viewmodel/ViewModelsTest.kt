package com.unbpocket.app.ui.viewmodel

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.PreloadPhase
import com.unbpocket.core.model.common.PreloadProgress
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.notification.AulaLeadMinutes
import com.unbpocket.core.model.notification.SigaaSyncIntervalHours
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuAutoSyncSummary
import com.unbpocket.core.model.ru.RuDeepLinkTarget
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.NoticiaTurmaResumoAva
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `AuthViewModel validates empty inputs and propagates login success and errors`() = runTest {
        val core = FakeUnbPocketCore()
        val vm = AuthViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }

        vm.submitLogin()
        assertEquals("Campos obrigatórios", vm.uiState.value.loginError?.title)

        core.authFake.nextLoginResult = CoreResult.Failure(CoreError.InvalidCredentials)
        vm.onMatriculaChange("231012345")
        vm.onSenhaChange("wrong")
        vm.submitLogin()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loginError?.requiresLogin == true)
        assertTrue(vm.uiState.value.authState is AuthState.Unauthenticated)

        var successCalled = false
        core.authFake.nextLoginResult = CoreResult.Success(
            AuthState.Authenticated("231012345", "100", 9999L, true),
        )
        vm.onSenhaChange("correct")
        vm.submitLogin { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertEquals("", vm.uiState.value.senhaInput)
        assertTrue(vm.uiState.value.authState is AuthState.Authenticated)
        assertNull(vm.uiState.value.loginError)

        collector.cancel()
    }

    @Test
    fun `AuthViewModel bootstraps silently when saved credentials exist`() = runTest {
        val core = FakeUnbPocketCore()
        core.authFake.savedCredentials = true
        val vm = AuthViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasSavedCredentials)
        assertTrue(vm.uiState.value.authState is AuthState.Authenticated)

        vm.logout(clearCachedData = true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasSavedCredentials)
        assertTrue(vm.uiState.value.authState is AuthState.Unauthenticated)

        collector.cancel()
    }

    @Test
    fun `RuViewModel keeps cached cardapio on network error and updates campus and allergens`() = runTest {
        val core = FakeUnbPocketCore()
        val cachedDarcy = CardapioSemanalRu(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "Semana em cache",
            pdfUrl = "https://ru.unb.br/darcy.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "",
            parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
            dias = listOf(
                CardapioDiaRu(
                    dataTexto = "16/11",
                    diaSemana = "Segunda-feira",
                    refeicoes = mapOf(
                        TipoRefeicao.ALMOCO to listOf(
                            ItemCardapioRu("Prato Principal", "Frango", emptyList()),
                        ),
                    ),
                ),
            ),
        )
        core.ruFake.cardapios.value = mapOf(
            CampusRu.DARCY_RIBEIRO to SyncedData(cachedDarcy, DataOrigin.CACHE, 500L),
        )
        core.ruFake.nextRefreshCardapioResult = CoreResult.Failure(CoreError.NetworkUnavailable)

        val vm = RuViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()

        assertEquals("Semana em cache", vm.uiState.value.cardapio?.value?.semanaLabel)
        assertTrue(vm.uiState.value.hasResolvedInitialMenu)
        assertFalse(vm.uiState.value.isMenuLoading)
        assertTrue(vm.uiState.value.menuError?.canRetry == true)

        core.ruFake.nextRefreshCardapioResult = null
        vm.selectCampus(CampusRu.GAMA)
        advanceUntilIdle()
        assertEquals(CampusRu.GAMA, vm.uiState.value.preferencias.campusSelecionado)
        assertTrue(CampusRu.GAMA in core.ruFake.refreshedCampuses)

        vm.toggleAllergen(Alergenico.GLUTEN)
        vm.setHideRestricted(true)
        advanceUntilIdle()
        assertEquals(setOf(Alergenico.GLUTEN), vm.uiState.value.preferencias.alergenicosRestritos)
        assertTrue(vm.uiState.value.preferencias.ocultarPratosComAlergenicos)

        collector.cancel()
    }

    @Test
    fun `RuViewModel refreshIfStaleSilently never surfaces errors or loading spinners`() = runTest {
        val core = FakeUnbPocketCore()
        val cachedDarcy = CardapioSemanalRu(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "23/11/2026 a 29/11/2026",
            pdfUrl = "https://ru.unb.br/darcy-w1.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "t1",
            parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
            dias = emptyList(),
        )
        core.ruFake.cardapios.value = mapOf(
            CampusRu.DARCY_RIBEIRO to SyncedData(cachedDarcy, DataOrigin.CACHE, 500L),
        )
        core.ruFake.nextRefreshCardapioResult = CoreResult.Success(cachedDarcy)

        val vm = RuViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()

        core.ruFake.nextSyncCardapiosResult = CoreResult.Failure(CoreError.NetworkUnavailable)
        vm.refreshIfStaleSilently()
        advanceUntilIdle()

        assertEquals(1, core.ruFake.syncCardapiosCalls)
        assertNull(vm.uiState.value.menuError)
        assertFalse(vm.uiState.value.isRefreshingMenu)
        assertEquals("23/11/2026 a 29/11/2026", vm.uiState.value.cardapio?.value?.semanaLabel)

        val updatedDarcy = cachedDarcy.copy(semanaLabel = "30/11/2026 a 06/12/2026")
        core.ruFake.nextSyncCardapiosResult = CoreResult.Success(
            RuAutoSyncSummary(
                checkedAtEpochMillis = 3000L,
                skippedDueToCooldown = false,
                updatedCampi = setOf(CampusRu.DARCY_RIBEIRO),
                upToDateCampi = emptySet(),
                failedCampi = emptyMap(),
            ),
        )
        vm.refreshIfStaleSilently()
        core.ruFake.cardapios.value = mapOf(
            CampusRu.DARCY_RIBEIRO to SyncedData(updatedDarcy, DataOrigin.REMOTE, 3000L),
        )
        advanceUntilIdle()

        assertEquals(2, core.ruFake.syncCardapiosCalls)
        assertNull(vm.uiState.value.menuError)
        assertEquals("30/11/2026 a 06/12/2026", vm.uiState.value.cardapio?.value?.semanaLabel)

        collector.cancel()
    }

    @Test
    fun `RuViewModel clears unread notices indicator on open and restores it only for new notice IDs`() = runTest {
        val core = FakeUnbPocketCore()
        val initialNotices = listOf(
            NoticiaRu(101L, "2026-11-20", "Edital 01", "Resumo 1", "<p>1</p>", "https://ru.unb.br/101", null, emptyList()),
            NoticiaRu(102L, "2026-11-22", "Edital 02", "Resumo 2", "<p>2</p>", "https://ru.unb.br/102", null, emptyList()),
        )
        core.ruFake.noticias.value = SyncedData(initialNotices, DataOrigin.CACHE, 1000L)

        val vm = RuViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasUnreadNoticias)

        vm.setNoticesAndPricesSheetOpen(true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasUnreadNoticias)
        assertEquals(setOf(101L, 102L), vm.uiState.value.preferencias.noticiasVistasIds)

        vm.setNoticesAndPricesSheetOpen(false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasUnreadNoticias)

        // Quando surge uma nova noticia com ID inedito, o indicador volta a aparecer
        val updatedNotices = initialNotices + NoticiaRu(
            103L,
            "2026-11-23",
            "Edital 03",
            "Resumo 3",
            "<p>3</p>",
            "https://ru.unb.br/103",
            null,
            emptyList(),
        )
        core.ruFake.nextRefreshNoticiasResult = CoreResult.Success(updatedNotices)
        vm.refreshPricesAndNotices(force = true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasUnreadNoticias)

        // Abrir novamente marca inclusive o novo ID como visto
        vm.setNoticesAndPricesSheetOpen(true)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasUnreadNoticias)
        assertEquals(setOf(101L, 102L, 103L), vm.uiState.value.preferencias.noticiasVistasIds)

        collector.cancel()
    }

    @Test
    fun `TurmasViewModel lazily loads detail tabs and emits material download event`() = runTest {
        val core = FakeUnbPocketCore()
        val vm = TurmasViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.detailUiState.collect() }

        vm.openTurma("fe_10")
        advanceUntilIdle()
        assertEquals(1, core.turmasFake.refreshDetalheCalls)

        vm.selectDetailTab(TurmaDetailTab.FREQUENCIA)
        advanceUntilIdle()
        assertEquals(1, core.turmasFake.refreshFrequenciaCalls)

        vm.selectDetailTab(TurmaDetailTab.NOTAS_AVALIACOES)
        advanceUntilIdle()
        assertEquals(1, core.turmasFake.refreshNotasCalls)

        vm.downloadMaterial("mat_42")
        advanceUntilIdle()
        assertEquals("slides.pdf", vm.detailUiState.value.downloadedFileEvent?.nomeArquivo)
        vm.consumeDownloadedFile()
        assertNull(vm.detailUiState.value.downloadedFileEvent)

        collector.cancel()
    }

    @Test
    fun `TurmasViewModel shows immediate per-notice loading and resolves matching latest notice instantly`() = runTest {
        val core = FakeUnbPocketCore()
        val latest = NoticiaTurmaAva(
            idNoticia = null,
            titulo = "Roteiro 11",
            dataHoraTexto = "22/11/2026 09:34",
            conteudoHtml = "<p>Leitura</p>",
            conteudoTexto = "Leitura",
            autor = "DOCENTE",
            anexos = emptyList(),
        )
        val detalhe = TurmaDetalheAva(
            idTurma = "10",
            frontEndIdTurma = "fe_10",
            codigoDisciplina = "ENE0295",
            nomeDisciplina = "LAB",
            periodoTurmaTexto = "2026.2",
            aulasMinistradas = 24,
            aulasTotal = 32,
            percentualAndamento = 75,
            ultimaNoticia = latest,
            historicoNoticiasResumo = listOf(
                NoticiaTurmaResumoAva("n_latest", "22/11/2026 09:34", "Roteiro 11"),
                NoticiaTurmaResumoAva("n1", "20/11/2026 10:00", "Aviso 1"),
                NoticiaTurmaResumoAva("n2", "18/11/2026 08:00", "Aviso 2"),
            ),
            topicosAula = emptyList(),
        )
        core.turmasFake.detalhe.value = SyncedData(detalhe, DataOrigin.REMOTE, 1000L)

        val d1 = CompletableDeferred<CoreResult<NoticiaTurmaAva>>()
        val d2 = CompletableDeferred<CoreResult<NoticiaTurmaAva>>()
        core.turmasFake.onGetNoticiaCompleta = { _, id ->
            when (id) {
                "n1" -> d1.await()
                "n2" -> d2.await()
                else -> CoreResult.Failure(CoreError.NetworkUnavailable)
            }
        }

        val vm = TurmasViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.detailUiState.collect() }
        vm.openTurma("fe_10")
        advanceUntilIdle()

        // Clicar na ultima noticia que ja corresponde ao detalhe exibido resolve em 0ms sem chamada de rede
        vm.toggleNoticia("n_latest")
        assertEquals("n_latest", vm.detailUiState.value.expandedNoticiaId)
        assertTrue(vm.detailUiState.value.loadingNoticiaIds.isEmpty())
        assertEquals(0, core.turmasFake.getNoticiaCompletaCalls)
        assertEquals("Leitura", vm.detailUiState.value.fullNoticiaById["n_latest"]?.conteudoTexto)

        // Clicar em comunicados antigos marca loading imediatamente e nao perde estado ao alternar entre avisos
        vm.toggleNoticia("n1")
        assertEquals("n1", vm.detailUiState.value.expandedNoticiaId)
        assertTrue("n1" in vm.detailUiState.value.loadingNoticiaIds)

        vm.toggleNoticia("n2")
        assertEquals("n2", vm.detailUiState.value.expandedNoticiaId)
        assertEquals(setOf("n1", "n2"), vm.detailUiState.value.loadingNoticiaIds)

        d1.complete(
            CoreResult.Success(
                NoticiaTurmaAva("n1", "Aviso 1", "20/11/2026 10:00", "<p>Corpo 1</p>", "Corpo 1", null, emptyList()),
            ),
        )
        advanceUntilIdle()
        assertEquals(setOf("n2"), vm.detailUiState.value.loadingNoticiaIds)
        assertEquals("Corpo 1", vm.detailUiState.value.fullNoticiaById["n1"]?.conteudoTexto)

        d2.complete(
            CoreResult.Success(
                NoticiaTurmaAva("n2", "Aviso 2", "18/11/2026 08:00", "<p>Corpo 2</p>", "Corpo 2", null, emptyList()),
            ),
        )
        advanceUntilIdle()
        assertTrue(vm.detailUiState.value.loadingNoticiaIds.isEmpty())
        assertEquals("Corpo 2", vm.detailUiState.value.fullNoticiaById["n2"]?.conteudoTexto)

        collector.cancel()
    }

    @Test
    fun `DiscenteViewModel loads QR on flip and rejects CARTEIRINHA_ESTUDANTIL PDF emission`() = runTest {
        val core = FakeUnbPocketCore()
        val vm = DiscenteViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()

        vm.toggleQrCardFace()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isFlippedToQr)
        assertEquals(1, core.discenteFake.refreshCarteirinhaCalls)
        assertEquals("1234567890123456", vm.uiState.value.carteirinha?.value?.qrPayload16Digitos)

        vm.resetQrCardToBalance()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isFlippedToQr)

        vm.emitirDocumento(TipoDocumentoOficial.CARTEIRINHA_ESTUDANTIL)
        advanceUntilIdle()
        assertTrue(core.discenteFake.emittedRequestedTypes.isEmpty())

        vm.emitirDocumento(TipoDocumentoOficial.ATESTADO_MATRICULA)
        advanceUntilIdle()
        assertEquals(listOf(TipoDocumentoOficial.ATESTADO_MATRICULA), core.discenteFake.emittedRequestedTypes)
        assertNotNull(vm.uiState.value.lastEmittedDocumentoEvent)
        vm.consumeEmittedDocumento()
        assertNull(vm.uiState.value.lastEmittedDocumentoEvent)

        collector.cancel()
    }

    @Test
    fun `AuthViewModel executes phase 1 before login callback starts background preload and silences startup network errors`() = runTest {
        val core = FakeUnbPocketCore()
        val vm = AuthViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }

        var syncCountAtCallback = -1
        var preloadCountAtCallback = -1
        vm.onMatriculaChange("231012345")
        vm.onSenhaChange("correct")
        vm.submitLogin {
            syncCountAtCallback = core.syncPortalHomeCalls
            preloadCountAtCallback = core.startBackgroundPreloadCalls
        }
        advanceUntilIdle()

        assertEquals(1, syncCountAtCallback)
        assertEquals(1, preloadCountAtCallback)
        assertEquals(PreloadPhase.COMPLETED, vm.uiState.value.preloadProgress.phase)

        collector.cancel()

        // Startup silencioso com erro de rede nao exibe erro nem skeleton/spinner de boot
        val offlineCore = FakeUnbPocketCore().apply {
            authFake.savedCredentials = true
            silentRefreshResult = CoreResult.Failure(CoreError.NetworkUnavailable)
        }
        val offlineVm = AuthViewModel(offlineCore)
        val offlineCollector = backgroundScope.launch(testDispatcher) { offlineVm.uiState.collect() }
        advanceUntilIdle()

        assertEquals(1, offlineCore.silentRefreshCalls)
        assertFalse(offlineVm.uiState.value.isBootstrapping)
        assertFalse(offlineVm.uiState.value.isSyncingAll)
        assertNull(offlineVm.uiState.value.syncError)
        offlineCollector.cancel()

        // Startup com credencial invalida informa necessidade de novo login
        val badCredsCore = FakeUnbPocketCore().apply {
            authFake.savedCredentials = true
            authFake.nextEnsureResult = CoreResult.Failure(CoreError.InvalidCredentials)
        }
        val badCredsVm = AuthViewModel(badCredsCore)
        val badCredsCollector = backgroundScope.launch(testDispatcher) { badCredsVm.uiState.collect() }
        advanceUntilIdle()

        assertTrue(badCredsVm.uiState.value.syncError?.requiresLogin == true)
        badCredsCollector.cancel()
    }

    @Test
    fun `TurmasViewModel silently refreshes preloaded detail without skeleton or error disruption`() = runTest {
        val core = FakeUnbPocketCore()
        val cachedDetail = TurmaDetalheAva(
            idTurma = "10",
            frontEndIdTurma = "fe_10",
            codigoDisciplina = "ENE0064",
            nomeDisciplina = "REDES SEM FIO",
            periodoTurmaTexto = "T01",
            aulasMinistradas = 10,
            aulasTotal = 30,
            percentualAndamento = 33,
            ultimaNoticia = null,
            historicoNoticiasResumo = emptyList(),
            topicosAula = emptyList(),
        )
        core.turmasFake.detalhe.value = SyncedData(cachedDetail, DataOrigin.CACHE, 500L)
        core.turmasFake.nextRefreshDetalheResult = CoreResult.Failure(CoreError.NetworkUnavailable)

        val vm = TurmasViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.detailUiState.collect() }

        vm.openTurma("fe_10")
        advanceUntilIdle()

        assertEquals(1, core.turmasFake.refreshDetalheCalls)
        assertFalse(vm.detailUiState.value.isRefreshingDetail)
        assertFalse(vm.detailUiState.value.isLoadingDetail)
        assertNull(vm.detailUiState.value.detailError)
        assertEquals("REDES SEM FIO", vm.detailUiState.value.detalhe?.value?.nomeDisciplina)

        collector.cancel()
    }

    @Test
    fun `ViewModels expose initial cache resolution flags once Room flows emit`() = runTest {
        val core = FakeUnbPocketCore()
        val turmasVm = TurmasViewModel(core)
        val discenteVm = DiscenteViewModel(core)
        val turmasCollector = backgroundScope.launch(testDispatcher) { turmasVm.listUiState.collect() }
        val discenteCollector = backgroundScope.launch(testDispatcher) { discenteVm.uiState.collect() }
        advanceUntilIdle()

        assertTrue(turmasVm.listUiState.value.hasResolvedInitialCache)
        assertTrue(discenteVm.uiState.value.hasResolvedInitialCache)
        assertTrue(discenteVm.uiState.value.hasResolvedCarteirinhaCache)
        assertTrue(discenteVm.uiState.value.hasResolvedBoletimCache)

        turmasCollector.cancel()
        discenteCollector.cancel()
    }

    @Test
    fun `DiscenteViewModel and TurmasViewModel clear stale disconnected banners once authenticated`() = runTest {
        val core = FakeUnbPocketCore()
        val authVm = AuthViewModel(core)
        val discenteVm = DiscenteViewModel(core)
        val turmasVm = TurmasViewModel(core)

        val authCollector = backgroundScope.launch(testDispatcher) { authVm.uiState.collect() }
        val discenteCollector = backgroundScope.launch(testDispatcher) { discenteVm.uiState.collect() }
        val turmasListCollector = backgroundScope.launch(testDispatcher) { turmasVm.listUiState.collect() }
        val turmaDetailCollector = backgroundScope.launch(testDispatcher) { turmasVm.detailUiState.collect() }

        core.turmasFake.nextRefreshSemestreResult = CoreResult.Failure(CoreError.NoStoredCredentials)
        core.turmasFake.nextRefreshDetalheResult = CoreResult.Failure(CoreError.NoStoredCredentials)
        core.discenteFake.nextRefreshCarteirinhaResult = CoreResult.Failure(CoreError.NoStoredCredentials)
        core.discenteFake.nextRefreshBoletimResult = CoreResult.Failure(CoreError.NoStoredCredentials)

        discenteVm.refreshProfileAndSaldo()
        discenteVm.refreshCarteirinha(force = true)
        discenteVm.refreshBoletim()
        turmasVm.refreshSemestre()
        turmasVm.openTurma("fe_10")
        advanceUntilIdle()

        assertEquals("Conta SIGAA desconectada", discenteVm.uiState.value.profileError?.title)
        assertEquals("Conta SIGAA desconectada", discenteVm.uiState.value.carteirinhaError?.title)
        assertEquals("Conta SIGAA desconectada", discenteVm.uiState.value.boletimError?.title)
        assertEquals("Conta SIGAA desconectada", turmasVm.listUiState.value.listError?.title)
        assertEquals("Conta SIGAA desconectada", turmasVm.detailUiState.value.detailError?.title)

        // Login bem-sucedido limpa todos os avisos antigos de autenticacao automaticamente
        core.turmasFake.nextRefreshSemestreResult = null
        core.turmasFake.nextRefreshDetalheResult = null
        core.discenteFake.nextRefreshCarteirinhaResult = null
        core.discenteFake.nextRefreshBoletimResult = null

        authVm.onMatriculaChange("231012345")
        authVm.onSenhaChange("secret")
        authVm.submitLogin()
        advanceUntilIdle()

        assertTrue(authVm.uiState.value.authState is AuthState.Authenticated)
        assertNull(authVm.uiState.value.syncError)
        assertNull(discenteVm.uiState.value.profileError)
        assertNull(discenteVm.uiState.value.carteirinhaError)
        assertNull(discenteVm.uiState.value.boletimError)
        assertNull(turmasVm.listUiState.value.listError)
        assertNull(turmasVm.detailUiState.value.detailError)

        // Erros nao ligados a login (ex: rede) continuam sendo reportados normalmente
        core.discenteFake.nextRefreshCarteirinhaResult = CoreResult.Failure(CoreError.NetworkUnavailable)
        discenteVm.refreshCarteirinha(force = true)
        advanceUntilIdle()
        assertTrue(discenteVm.uiState.value.carteirinhaError?.canRetry == true)

        authCollector.cancel()
        discenteCollector.cancel()
        turmasListCollector.cancel()
        turmaDetailCollector.cancel()
    }

    @Test
    fun `RuViewModel toggles per-day meal notifications and resolves deep link target`() = runTest {
        val core = FakeUnbPocketCore()
        val menuGama = CardapioSemanalRu(
            campus = CampusRu.GAMA,
            semanaLabel = "23/11/2026 a 27/11/2026",
            pdfUrl = "https://ru.unb.br/gama.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "t1",
            parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
            dias = listOf(
                CardapioDiaRu("23/11/2026", "SEG", emptyMap()),
                CardapioDiaRu("24/11/2026", "TER", emptyMap()),
                CardapioDiaRu("25/11/2026", "QUA", emptyMap()),
            ),
        )
        core.ruFake.cardapios.value = mapOf(
            CampusRu.GAMA to SyncedData(menuGama, DataOrigin.CACHE, 1000L),
        )

        val vm = RuViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.preferencias.notificacoes.habilitado)
        vm.toggleNotificacaoSlot(DiaSemanaRu.SEGUNDA, TipoRefeicao.CAFE_DA_MANHA)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.preferencias.notificacoes.isEnabledFor(DiaSemanaRu.SEGUNDA, TipoRefeicao.CAFE_DA_MANHA))
        assertFalse(vm.uiState.value.preferencias.notificacoes.isEnabledFor(DiaSemanaRu.QUARTA, TipoRefeicao.ALMOCO))

        vm.toggleNotificacaoDia(DiaSemanaRu.TERCA)
        advanceUntilIdle()
        assertEquals(
            TipoRefeicao.entries.toSet(),
            vm.uiState.value.preferencias.notificacoes.refeicoesPorDia[DiaSemanaRu.TERCA],
        )

        vm.setNotificacoesHabilitadas(false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.preferencias.notificacoes.habilitado)
        assertTrue(
            vm.uiState.value.preferencias.notificacoes.refeicoesPorDia[DiaSemanaRu.SEGUNDA]
                ?.contains(TipoRefeicao.CAFE_DA_MANHA) == true,
        )

        vm.applyDeepLink(
            RuDeepLinkTarget(
                campus = CampusRu.GAMA,
                dataTexto = "25/11/2026",
                dayIndex = 0,
                refeicao = TipoRefeicao.JANTAR,
            ),
        )
        advanceUntilIdle()
        assertEquals(CampusRu.GAMA, vm.uiState.value.preferencias.campusSelecionado)
        assertEquals(2, vm.uiState.value.selectedDayIndex)
        assertEquals(TipoRefeicao.JANTAR, vm.uiState.value.selectedMeal)

        collector.cancel()
    }

    @Test
    fun `AuthViewModel updates sigaa notification preferences, mutes turmas and handles autoEnable`() = runTest {
        val core = FakeUnbPocketCore()
        val turma = TurmaResumo(
            idTurma = "1614",
            frontEndIdTurma = "fe_1614",
            semestre = "2026.2",
            codigoDisciplina = "ENE0064",
            nomeDisciplina = "REDES SEM FIO",
            numeroTurma = "01",
            cargaHorariaHoras = 60,
            docenteNome = "Joao",
            local = "FT AT-31/12",
            codigoHorarioRaw = "24M12",
            horarioSlots = emptyList(),
            horarioExtenso = emptyList(),
        )
        core.turmasFake.turmas.value = SyncedData(listOf(turma), DataOrigin.CACHE, 1000L)

        val vm = AuthViewModel(core)
        val collector = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.sigaaNotificacoes.habilitado)
        assertEquals(1, vm.uiState.value.turmasParaNotificacao.size)

        vm.autoEnableSigaaNotificationsIfPermitted()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sigaaNotificacoes.habilitado)
        assertFalse(vm.uiState.value.sigaaNotificacoes.userConfigured)

        vm.setAntecedenciaAulaMinutos(AulaLeadMinutes.MIN_15.minutes)
        vm.setSigaaSyncInterval(SigaaSyncIntervalHours.H2)
        vm.setSigaaSomenteWifi(true)
        vm.setLimiteRefeicoesRuBaixo(3)
        vm.toggleTurmaSilenciada(turma)
        advanceUntilIdle()

        val cfg = vm.uiState.value.sigaaNotificacoes
        assertTrue(cfg.userConfigured)
        assertEquals(15, cfg.antecedenciaAulaMinutos)
        assertEquals(SigaaSyncIntervalHours.H2, cfg.syncIntervalHours)
        assertTrue(cfg.somenteWifi)
        assertEquals(3, cfg.limiteRefeicoesRuBaixo)
        assertTrue("fe_1614" in cfg.turmasSilenciadas)

        // Toggle off explicitly preserves userConfigured so autoEnable won't force re-enabling
        vm.toggleSigaaTurmasSecao(false)
        vm.toggleSigaaDiscenteSecao(false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.sigaaNotificacoes.isTurmasAtivo)
        assertFalse(vm.uiState.value.sigaaNotificacoes.isDiscenteAtivo)

        vm.autoEnableSigaaNotificationsIfPermitted()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.sigaaNotificacoes.isTurmasAtivo)

        collector.cancel()
    }

    @Test
    fun `scoped pull-to-refresh and cold start avoid full app sync`() = runTest {
        val core = FakeUnbPocketCore()
        core.authFake.savedCredentials = true

        // 1. Cold start com credenciais salvas faz silentRefreshAll sem varrer todas as turmas
        val authVm = AuthViewModel(core)
        val authCol = backgroundScope.launch(testDispatcher) { authVm.uiState.collect() }
        advanceUntilIdle()
        assertEquals(1, core.silentRefreshCalls)
        assertEquals(false, core.lastSilentRefreshIncludeTurmaDetails)
        authCol.cancel()

        // 2. Pull-to-refresh na tela Discente usa chamada combinada sem rebaixar foto
        val discenteVm = DiscenteViewModel(core)
        val discenteCol = backgroundScope.launch(testDispatcher) { discenteVm.uiState.collect() }
        advanceUntilIdle()
        discenteVm.refreshProfileAndSaldo()
        advanceUntilIdle()
        assertEquals(1, core.refreshDiscentePerfilESaldoCalls)
        assertEquals(false, core.lastRefreshPerfilForceFoto)
        discenteCol.cancel()

        // 3. Pull-to-refresh em Turmas pula atestado quando na aba de Avisos
        val turmasVm = TurmasViewModel(core)
        val turmasCol = backgroundScope.launch(testDispatcher) { turmasVm.listUiState.collect() }
        advanceUntilIdle()
        turmasVm.selectListTab(TurmasListTab.ATUALIZACOES)
        turmasVm.refreshSemestre()
        advanceUntilIdle()
        turmasVm.selectListTab(TurmasListTab.AULAS_DO_DIA)
        turmasVm.refreshSemestre()
        advanceUntilIdle()
        assertEquals(listOf(false, true), core.turmasFake.refreshSemestreEnriquecerHistory)
        turmasCol.cancel()
    }

    @Test
    fun `RuViewModel and TurmasViewModel auto-select current day and meal on launch and resume`() = runTest {
        val core = FakeUnbPocketCore()
        val menu = CardapioSemanalRu(
            campus = CampusRu.DARCY_RIBEIRO,
            semanaLabel = "23/11 a 27/11",
            pdfUrl = "https://ru.unb.br/darcy.pdf",
            fallbackImageUrl = null,
            localPdfPath = null,
            modifiedGmt = "2026-11-23T08:00:00",
            parserStatus = CardapioParserStatus.PARSED_STRUCTURED,
            dias = listOf(
                CardapioDiaRu("23/11/2026", "Segunda-feira", emptyMap()),
                CardapioDiaRu("24/11/2026", "Terça-feira", emptyMap()),
                CardapioDiaRu("25/11/2026", "Quarta-feira", emptyMap()),
            ),
        )
        core.ruFake.cardapios.value = mapOf(
            CampusRu.DARCY_RIBEIRO to SyncedData(menu, DataOrigin.CACHE, 1000L),
        )
        core.ruFake.nextSelecaoInicial = com.unbpocket.core.model.ru.RuSelecaoInicial(
            dayIndex = 1,
            refeicao = TipoRefeicao.CAFE_DA_MANHA,
        )
        core.turmasFake.nextDiaAtualSigaa = DiaSemanaSigaa.TERCA

        val ruVm = RuViewModel(core)
        val ruCol = backgroundScope.launch(testDispatcher) { ruVm.uiState.collect() }
        val turmasVm = TurmasViewModel(core)
        val turmasCol = backgroundScope.launch(testDispatcher) { turmasVm.listUiState.collect() }
        advanceUntilIdle()

        assertEquals(1, ruVm.uiState.value.selectedDayIndex)
        assertEquals(TipoRefeicao.CAFE_DA_MANHA, ruVm.uiState.value.selectedMeal)
        assertEquals(DiaSemanaSigaa.TERCA, turmasVm.listUiState.value.selectedDay)
        assertEquals(DiaSemanaSigaa.TERCA, turmasVm.listUiState.value.todayDay)

        // Same day resume updates automatic meal window without losing manual day override
        core.ruFake.nextSelecaoInicial = com.unbpocket.core.model.ru.RuSelecaoInicial(
            dayIndex = 1,
            refeicao = TipoRefeicao.ALMOCO,
        )
        ruVm.syncAutomaticSelectionOnResume(today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")))
        advanceUntilIdle()
        assertEquals(TipoRefeicao.ALMOCO, ruVm.uiState.value.selectedMeal)

        // Manual override preserved on same day, reset when calendar day rolls over
        ruVm.selectDay(0)
        ruVm.selectMeal(TipoRefeicao.JANTAR)
        turmasVm.selectDay(DiaSemanaSigaa.SEXTA)
        advanceUntilIdle()
        assertEquals(0, ruVm.uiState.value.selectedDayIndex)
        assertEquals(TipoRefeicao.JANTAR, ruVm.uiState.value.selectedMeal)
        assertEquals(DiaSemanaSigaa.SEXTA, turmasVm.listUiState.value.selectedDay)

        val tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1)
        core.ruFake.nextSelecaoInicial = com.unbpocket.core.model.ru.RuSelecaoInicial(
            dayIndex = 2,
            refeicao = TipoRefeicao.CAFE_DA_MANHA,
        )
        core.turmasFake.nextDiaAtualSigaa = DiaSemanaSigaa.QUARTA
        ruVm.syncAutomaticSelectionOnResume(today = tomorrow)
        turmasVm.syncAutomaticDayOnResume(today = tomorrow)
        advanceUntilIdle()

        assertEquals(2, ruVm.uiState.value.selectedDayIndex)
        assertEquals(TipoRefeicao.CAFE_DA_MANHA, ruVm.uiState.value.selectedMeal)
        assertEquals(DiaSemanaSigaa.QUARTA, turmasVm.listUiState.value.selectedDay)
        assertEquals(DiaSemanaSigaa.QUARTA, turmasVm.listUiState.value.todayDay)

        ruCol.cancel()
        turmasCol.cancel()
    }
}
