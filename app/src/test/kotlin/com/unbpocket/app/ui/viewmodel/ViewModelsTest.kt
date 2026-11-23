package com.unbpocket.app.ui.viewmodel

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.PreloadPhase
import com.unbpocket.core.model.common.PreloadProgress
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.turmas.TurmaDetalheAva
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
            silentRefreshResult = CoreResult.Failure(CoreError.InvalidCredentials)
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
}
