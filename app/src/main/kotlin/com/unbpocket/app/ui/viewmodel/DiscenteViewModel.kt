package com.unbpocket.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unbpocket.app.ui.common.CoreErrorMapper
import com.unbpocket.app.ui.common.UiErrorBanner
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.CarteirinhaEstudantil
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.discente.DocumentoOficialEmitido
import com.unbpocket.core.model.discente.TipoDocumentoOficial
import com.unbpocket.core.model.ru.SaldoExtratoRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscenteUiState(
    val authState: AuthState = AuthState.Unauthenticated,
    val discenteResumo: SyncedData<DiscenteResumo>? = null,
    val saldoExtrato: SyncedData<SaldoExtratoRu>? = null,
    val carteirinha: SyncedData<CarteirinhaEstudantil>? = null,
    val boletim: SyncedData<List<BoletimSemestre>>? = null,
    val documentosEmitidos: List<DocumentoOficialEmitido> = emptyList(),
    val isFlippedToQr: Boolean = false,
    val boostQrBrightness: Boolean = true,
    val isRefreshingProfile: Boolean = false,
    val isRefreshingCarteirinha: Boolean = false,
    val isRefreshingBoletim: Boolean = false,
    val emittingDocumentoTipo: TipoDocumentoOficial? = null,
    val lastEmittedDocumentoEvent: DocumentoOficialEmitido? = null,
    val profileError: UiErrorBanner? = null,
    val carteirinhaError: UiErrorBanner? = null,
    val boletimError: UiErrorBanner? = null,
    val documentosError: UiErrorBanner? = null,
)

private data class DiscenteControlState(
    val isFlippedToQr: Boolean = false,
    val boostQrBrightness: Boolean = true,
    val isRefreshingProfile: Boolean = false,
    val isRefreshingCarteirinha: Boolean = false,
    val isRefreshingBoletim: Boolean = false,
    val emittingDocumentoTipo: TipoDocumentoOficial? = null,
    val lastEmittedDocumentoEvent: DocumentoOficialEmitido? = null,
    val profileError: UiErrorBanner? = null,
    val carteirinhaError: UiErrorBanner? = null,
    val boletimError: UiErrorBanner? = null,
    val documentosError: UiErrorBanner? = null,
)

class DiscenteViewModel(
    private val core: UnbPocketCore,
) : ViewModel() {

    private val controlState = MutableStateFlow(DiscenteControlState())

    val uiState: StateFlow<DiscenteUiState> = combine(
        core.auth.authState,
        core.discente.observeDiscenteResumo(),
        core.ru.observeSaldoExtrato(),
        core.discente.observeCarteirinha(),
        core.discente.observeBoletimGeral(),
        core.discente.observeDocumentosEmitidos(),
        controlState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val auth = values[0] as AuthState
        @Suppress("UNCHECKED_CAST")
        val resumo = values[1] as SyncedData<DiscenteResumo>?
        @Suppress("UNCHECKED_CAST")
        val saldo = values[2] as SyncedData<SaldoExtratoRu>?
        @Suppress("UNCHECKED_CAST")
        val carteirinha = values[3] as SyncedData<CarteirinhaEstudantil>?
        @Suppress("UNCHECKED_CAST")
        val boletim = values[4] as SyncedData<List<BoletimSemestre>>?
        @Suppress("UNCHECKED_CAST")
        val docs = values[5] as List<DocumentoOficialEmitido>
        val ctrl = values[6] as DiscenteControlState

        DiscenteUiState(
            authState = auth,
            discenteResumo = resumo,
            saldoExtrato = saldo,
            carteirinha = carteirinha,
            boletim = boletim,
            documentosEmitidos = docs,
            isFlippedToQr = ctrl.isFlippedToQr,
            boostQrBrightness = ctrl.boostQrBrightness,
            isRefreshingProfile = ctrl.isRefreshingProfile,
            isRefreshingCarteirinha = ctrl.isRefreshingCarteirinha,
            isRefreshingBoletim = ctrl.isRefreshingBoletim,
            emittingDocumentoTipo = ctrl.emittingDocumentoTipo,
            lastEmittedDocumentoEvent = ctrl.lastEmittedDocumentoEvent,
            profileError = ctrl.profileError,
            carteirinhaError = ctrl.carteirinhaError,
            boletimError = ctrl.boletimError,
            documentosError = ctrl.documentosError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscenteUiState(authState = core.auth.authState.value),
    )

    fun toggleQrCardFace() {
        val nextFlipped = !controlState.value.isFlippedToQr
        controlState.update { it.copy(isFlippedToQr = nextFlipped, carteirinhaError = null) }
        if (nextFlipped && uiState.value.carteirinha == null) {
            refreshCarteirinha(force = false)
        }
    }

    fun resetQrCardToBalance() {
        controlState.update { it.copy(isFlippedToQr = false) }
    }

    fun toggleBrightnessBoost() {
        controlState.update { it.copy(boostQrBrightness = !it.boostQrBrightness) }
    }

    fun refreshProfileAndSaldo() {
        if (controlState.value.isRefreshingProfile) return
        viewModelScope.launch {
            controlState.update { it.copy(isRefreshingProfile = true, profileError = null) }
            val profileRes = core.discente.refreshDiscenteResumo()
            val saldoRes = core.ru.refreshSaldoExtrato()
            val err = (profileRes as? CoreResult.Failure)?.error
                ?: (saldoRes as? CoreResult.Failure)?.error
            controlState.update {
                it.copy(
                    isRefreshingProfile = false,
                    profileError = err?.let(CoreErrorMapper::toUiError),
                )
            }
        }
    }

    fun refreshCarteirinha(force: Boolean = true) {
        if (controlState.value.isRefreshingCarteirinha) return
        viewModelScope.launch {
            controlState.update { it.copy(isRefreshingCarteirinha = true, carteirinhaError = null) }
            when (val res = core.discente.refreshCarteirinha(force = force)) {
                is CoreResult.Success -> controlState.update {
                    it.copy(isRefreshingCarteirinha = false, carteirinhaError = null)
                }

                is CoreResult.Failure -> controlState.update {
                    it.copy(
                        isRefreshingCarteirinha = false,
                        carteirinhaError = CoreErrorMapper.toUiError(res.error),
                    )
                }
            }
        }
    }

    fun refreshBoletim() {
        if (controlState.value.isRefreshingBoletim) return
        viewModelScope.launch {
            controlState.update { it.copy(isRefreshingBoletim = true, boletimError = null) }
            when (val res = core.discente.refreshBoletimGeral()) {
                is CoreResult.Success -> controlState.update {
                    it.copy(isRefreshingBoletim = false, boletimError = null)
                }

                is CoreResult.Failure -> controlState.update {
                    it.copy(
                        isRefreshingBoletim = false,
                        boletimError = CoreErrorMapper.toUiError(res.error),
                    )
                }
            }
        }
    }

    fun emitirDocumento(tipo: TipoDocumentoOficial) {
        if (tipo == TipoDocumentoOficial.CARTEIRINHA_ESTUDANTIL) {
            return
        }
        if (controlState.value.emittingDocumentoTipo != null) return
        viewModelScope.launch {
            controlState.update { it.copy(emittingDocumentoTipo = tipo, documentosError = null) }
            when (val res = core.discente.emitirDocumentoOficial(tipo)) {
                is CoreResult.Success -> controlState.update {
                    it.copy(
                        emittingDocumentoTipo = null,
                        lastEmittedDocumentoEvent = res.value,
                        documentosError = null,
                    )
                }

                is CoreResult.Failure -> controlState.update {
                    it.copy(
                        emittingDocumentoTipo = null,
                        documentosError = CoreErrorMapper.toUiError(res.error),
                    )
                }
            }
        }
    }

    fun consumeEmittedDocumento() {
        controlState.update { it.copy(lastEmittedDocumentoEvent = null) }
    }

    fun dismissProfileError() {
        controlState.update { it.copy(profileError = null) }
    }

    fun dismissCarteirinhaError() {
        controlState.update { it.copy(carteirinhaError = null) }
    }

    fun dismissBoletimError() {
        controlState.update { it.copy(boletimError = null) }
    }

    fun dismissDocumentosError() {
        controlState.update { it.copy(documentosError = null) }
    }
}
