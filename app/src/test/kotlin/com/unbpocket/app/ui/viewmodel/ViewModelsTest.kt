package com.unbpocket.app.ui.viewmodel

import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.ItemCardapioRu
import com.unbpocket.core.model.ru.TipoRefeicao
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
}
