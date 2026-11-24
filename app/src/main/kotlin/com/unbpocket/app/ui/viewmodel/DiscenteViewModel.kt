package com.unbpocket.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unbpocket.app.ui.common.CoreErrorMapper
import com.unbpocket.app.ui.common.UiErrorBanner
import com.unbpocket.app.ui.common.withoutStaleAuth
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
import com.unbpocket.core.model.turmas.ArquivoBaixado
import com.unbpocket.core.model.update.AppReleaseInfo
import com.unbpocket.core.model.update.AppUpdateCheckResult
import com.unbpocket.core.model.update.AppUpdateDownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    val hasResolvedInitialCache: Boolean = false,
    val hasResolvedCarteirinhaCache: Boolean = false,
    val hasResolvedBoletimCache: Boolean = false,
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
    val updateCheck: SyncedData<AppUpdateCheckResult>? = null,
    val updateDownloadProgress: AppUpdateDownloadProgress? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val pendingApkInstallEvent: ArquivoBaixado? = null,
    val updateError: UiErrorBanner? = null,
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
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val pendingApkInstallEvent: ArquivoBaixado? = null,
    val updateError: UiErrorBanner? = null,
)

class DiscenteViewModel(
    private val core: UnbPocketCore,
) : ViewModel() {

    private val controlState = MutableStateFlow(DiscenteControlState())

    private val resolvedResumoFlow = core.discente.observeDiscenteResumo()
        .map { true to it }
        .onStart { emit(false to null) }

    private val resolvedCarteirinhaFlow = core.discente.observeCarteirinha()
        .map { true to it }
        .onStart { emit(false to null) }

    private val resolvedBoletimFlow = core.discente.observeBoletimGeral()
        .map { true to it }
        .onStart { emit(false to null) }

    val uiState: StateFlow<DiscenteUiState> = combine(
        core.auth.authState,
        resolvedResumoFlow,
        core.ru.observeSaldoExtrato(),
        resolvedCarteirinhaFlow,
        resolvedBoletimFlow,
        core.discente.observeDocumentosEmitidos(),
        core.updates.observeLatestUpdateCheck(),
        core.updates.downloadProgress,
        controlState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val auth = values[0] as AuthState
        @Suppress("UNCHECKED_CAST")
        val resolvedResumo = values[1] as Pair<Boolean, SyncedData<DiscenteResumo>?>
        @Suppress("UNCHECKED_CAST")
        val saldo = values[2] as SyncedData<SaldoExtratoRu>?
        @Suppress("UNCHECKED_CAST")
        val resolvedCarteirinha = values[3] as Pair<Boolean, SyncedData<CarteirinhaEstudantil>?>
        @Suppress("UNCHECKED_CAST")
        val resolvedBoletim = values[4] as Pair<Boolean, SyncedData<List<BoletimSemestre>>?>
        @Suppress("UNCHECKED_CAST")
        val docs = values[5] as List<DocumentoOficialEmitido>
        @Suppress("UNCHECKED_CAST")
        val updateCheck = values[6] as SyncedData<AppUpdateCheckResult>?
        @Suppress("UNCHECKED_CAST")
        val downloadProgress = values[7] as AppUpdateDownloadProgress?
        val ctrl = values[8] as DiscenteControlState

        DiscenteUiState(
            authState = auth,
            discenteResumo = resolvedResumo.second,
            saldoExtrato = saldo,
            carteirinha = resolvedCarteirinha.second,
            boletim = resolvedBoletim.second,
            documentosEmitidos = docs,
            hasResolvedInitialCache = resolvedResumo.first,
            hasResolvedCarteirinhaCache = resolvedCarteirinha.first,
            hasResolvedBoletimCache = resolvedBoletim.first,
            isFlippedToQr = ctrl.isFlippedToQr,
            boostQrBrightness = ctrl.boostQrBrightness,
            isRefreshingProfile = ctrl.isRefreshingProfile,
            isRefreshingCarteirinha = ctrl.isRefreshingCarteirinha,
            isRefreshingBoletim = ctrl.isRefreshingBoletim,
            emittingDocumentoTipo = ctrl.emittingDocumentoTipo,
            lastEmittedDocumentoEvent = ctrl.lastEmittedDocumentoEvent,
            profileError = ctrl.profileError.withoutStaleAuth(auth),
            carteirinhaError = ctrl.carteirinhaError.withoutStaleAuth(auth),
            boletimError = ctrl.boletimError.withoutStaleAuth(auth),
            documentosError = ctrl.documentosError.withoutStaleAuth(auth),
            updateCheck = updateCheck,
            updateDownloadProgress = downloadProgress,
            isCheckingUpdate = ctrl.isCheckingUpdate,
            isDownloadingUpdate = ctrl.isDownloadingUpdate,
            pendingApkInstallEvent = ctrl.pendingApkInstallEvent,
            updateError = ctrl.updateError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscenteUiState(authState = core.auth.authState.value),
    )

    init {
        viewModelScope.launch {
            core.auth.authState.collect { auth ->
                if (auth is AuthState.Authenticated) {
                    controlState.update { state ->
                        state.copy(
                            profileError = state.profileError.withoutStaleAuth(auth),
                            carteirinhaError = state.carteirinhaError.withoutStaleAuth(auth),
                            boletimError = state.boletimError.withoutStaleAuth(auth),
                            documentosError = state.documentosError.withoutStaleAuth(auth),
                        )
                    }
                }
            }
        }
    }

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
            val res = core.refreshDiscentePerfilESaldo(forceDownloadFoto = false)
            val err = (res as? CoreResult.Failure)?.error
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

    fun checkForUpdates(
        currentVersionName: String,
        force: Boolean = true,
    ) {
        if (controlState.value.isCheckingUpdate) return
        viewModelScope.launch {
            controlState.update {
                it.copy(
                    isCheckingUpdate = force,
                    updateError = if (force) null else it.updateError,
                )
            }
            when (val res = core.updates.checkForUpdates(currentVersionName = currentVersionName, force = force)) {
                is CoreResult.Success -> controlState.update {
                    it.copy(isCheckingUpdate = false, updateError = null)
                }

                is CoreResult.Failure -> controlState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateError = if (force) CoreErrorMapper.toUiError(res.error) else it.updateError,
                    )
                }
            }
        }
    }

    fun downloadUpdate(release: AppReleaseInfo) {
        if (controlState.value.isDownloadingUpdate) return
        viewModelScope.launch {
            controlState.update { it.copy(isDownloadingUpdate = true, updateError = null) }
            when (val res = core.updates.downloadReleaseApk(release)) {
                is CoreResult.Success -> controlState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        pendingApkInstallEvent = res.value,
                        updateError = null,
                    )
                }

                is CoreResult.Failure -> controlState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateError = CoreErrorMapper.toUiError(res.error),
                    )
                }
            }
        }
    }

    fun consumePendingApkInstall() {
        controlState.update { it.copy(pendingApkInstallEvent = null) }
    }

    fun dismissUpdateError() {
        controlState.update { it.copy(updateError = null) }
    }
}
