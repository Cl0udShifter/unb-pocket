package com.unbpocket.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unbpocket.app.ui.common.CoreErrorMapper
import com.unbpocket.app.ui.common.UiErrorBanner
import com.unbpocket.app.ui.common.withoutStaleAuth
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.auth.CredentialsInput
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.PreloadProgress
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.model.notification.SigaaSyncIntervalHours
import com.unbpocket.core.model.turmas.TurmaResumo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val authState: AuthState = AuthState.Unauthenticated,
    val hasSavedCredentials: Boolean = false,
    val isBootstrapping: Boolean = false,
    val matriculaInput: String = "",
    val senhaInput: String = "",
    val isSubmittingLogin: Boolean = false,
    val isSyncingAll: Boolean = false,
    val isLoggingOut: Boolean = false,
    val preloadProgress: PreloadProgress = PreloadProgress(),
    val loginError: UiErrorBanner? = null,
    val syncError: UiErrorBanner? = null,
    val syncStatusMessage: String? = null,
    val sigaaNotificacoes: SigaaNotificacaoConfig = SigaaNotificacaoConfig(),
    val turmasParaNotificacao: List<TurmaResumo> = emptyList(),
)

private data class AuthFormState(
    val hasSavedCredentials: Boolean = false,
    val isBootstrapping: Boolean = false,
    val matriculaInput: String = "",
    val senhaInput: String = "",
    val isSubmittingLogin: Boolean = false,
    val isSyncingAll: Boolean = false,
    val isLoggingOut: Boolean = false,
    val loginError: UiErrorBanner? = null,
    val syncError: UiErrorBanner? = null,
    val syncStatusMessage: String? = null,
    val sigaaNotificacoes: SigaaNotificacaoConfig = SigaaNotificacaoConfig(),
    val turmasParaNotificacao: List<TurmaResumo> = emptyList(),
)

class AuthViewModel(
    private val core: UnbPocketCore,
) : ViewModel() {

    private val formState = MutableStateFlow(AuthFormState())

    val uiState: StateFlow<AuthUiState> = combine(
        core.auth.authState,
        core.preloadProgress,
        core.observeSigaaNotificacoesConfig(),
        core.turmas.observeTurmasSemestre(),
        formState,
    ) { auth, preload, sigaaNotifs, turmasSynced, form ->
        AuthUiState(
            authState = auth,
            hasSavedCredentials = form.hasSavedCredentials,
            isBootstrapping = form.isBootstrapping,
            matriculaInput = form.matriculaInput,
            senhaInput = form.senhaInput,
            isSubmittingLogin = form.isSubmittingLogin,
            isSyncingAll = form.isSyncingAll,
            isLoggingOut = form.isLoggingOut,
            preloadProgress = preload,
            loginError = form.loginError.withoutStaleAuth(auth),
            syncError = form.syncError.withoutStaleAuth(auth),
            syncStatusMessage = form.syncStatusMessage,
            sigaaNotificacoes = sigaaNotifs,
            turmasParaNotificacao = turmasSynced?.value.orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthUiState(
            authState = core.auth.authState.value,
            preloadProgress = core.preloadProgress.value,
        ),
    )

    init {
        viewModelScope.launch {
            core.auth.authState.collect { auth ->
                if (auth is AuthState.Authenticated) {
                    formState.update { state ->
                        state.copy(
                            loginError = state.loginError.withoutStaleAuth(auth),
                            syncError = state.syncError.withoutStaleAuth(auth),
                        )
                    }
                }
            }
        }
        bootstrapSessionIfSaved()
    }

    private fun bootstrapSessionIfSaved() {
        viewModelScope.launch {
            val saved = core.auth.hasSavedCredentials()
            formState.update { it.copy(hasSavedCredentials = saved) }
            if (!saved) return@launch

            when (val result = core.silentRefreshAll(includeTurmaDetails = true)) {
                is CoreResult.Success -> {
                    formState.update {
                        it.copy(
                            isBootstrapping = false,
                            hasSavedCredentials = true,
                            syncError = null,
                        )
                    }
                }

                is CoreResult.Failure -> {
                    val stillHasSaved = core.auth.hasSavedCredentials()
                    formState.update {
                        it.copy(
                            isBootstrapping = false,
                            hasSavedCredentials = stillHasSaved,
                            syncError = if (result.error is CoreError.InvalidCredentials) {
                                CoreErrorMapper.toUiError(result.error)
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun onMatriculaChange(value: String) {
        formState.update { it.copy(matriculaInput = value, loginError = null) }
    }

    fun onSenhaChange(value: String) {
        formState.update { it.copy(senhaInput = value, loginError = null) }
    }

    fun submitLogin(onSuccess: () -> Unit = {}) {
        val current = formState.value
        if (current.isSubmittingLogin) return

        val matricula = current.matriculaInput.trim()
        val senha = current.senhaInput
        if (matricula.isBlank() || senha.isBlank()) {
            formState.update {
                it.copy(
                    loginError = UiErrorBanner(
                        title = "Campos obrigatórios",
                        message = "Informe sua matrícula da UnB e a senha do SIGAA/CAS.",
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            formState.update {
                it.copy(
                    isSubmittingLogin = true,
                    loginError = null,
                    syncStatusMessage = "Conectando ao SIGAA...",
                )
            }
            when (val result = core.auth.login(CredentialsInput(matricula = matricula, password = senha))) {
                is CoreResult.Success -> {
                    formState.update {
                        it.copy(
                            hasSavedCredentials = true,
                            senhaInput = "",
                            loginError = null,
                            syncError = null,
                            syncStatusMessage = "Carregando turmas e dados acadêmicos...",
                        )
                    }
                    when (val essentialResult = core.syncPortalHome(includeSaldoRu = true)) {
                        is CoreResult.Success -> {
                            val summary = essentialResult.value
                            core.startBackgroundPreload(includeTurmaDetails = true)
                            formState.update {
                                it.copy(
                                    isSubmittingLogin = false,
                                    syncError = null,
                                    syncStatusMessage = if (summary.turmasCount == 0) {
                                        "Dados acadêmicos carregados."
                                    } else {
                                        "Sincronizado (${summary.turmasCount} turmas)."
                                    },
                                )
                            }
                            onSuccess()
                        }

                        is CoreResult.Failure -> {
                            if (essentialResult.error is CoreError.InvalidCredentials) {
                                formState.update {
                                    it.copy(
                                        isSubmittingLogin = false,
                                        loginError = CoreErrorMapper.toUiError(essentialResult.error),
                                        syncStatusMessage = null,
                                    )
                                }
                            } else {
                                formState.update {
                                    it.copy(
                                        isSubmittingLogin = false,
                                        syncError = CoreErrorMapper.toUiError(essentialResult.error),
                                        syncStatusMessage = null,
                                    )
                                }
                                onSuccess()
                            }
                        }
                    }
                }

                is CoreResult.Failure -> {
                    formState.update {
                        it.copy(
                            isSubmittingLogin = false,
                            loginError = CoreErrorMapper.toUiError(result.error),
                            syncStatusMessage = null,
                        )
                    }
                }
            }
        }
    }

    fun syncAll() {
        if (formState.value.isSyncingAll) return
        viewModelScope.launch {
            syncAllInternal(silentNetworkFailure = false)
        }
    }

    private suspend fun syncAllInternal(silentNetworkFailure: Boolean) {
        formState.update { it.copy(isSyncingAll = true, syncError = null, syncStatusMessage = null) }
        when (val result = core.syncPortalHome(includeSaldoRu = true)) {
            is CoreResult.Success -> {
                val summary = result.value
                core.startBackgroundPreload(includeTurmaDetails = true)
                formState.update {
                    it.copy(
                        isSyncingAll = false,
                        syncError = null,
                        syncStatusMessage = if (summary.turmasCount == 0) {
                            "Dados acadêmicos sincronizados."
                        } else {
                            "Sincronizado (${summary.turmasCount} turmas)."
                        },
                    )
                }
            }

            is CoreResult.Failure -> {
                formState.update {
                    it.copy(
                        isSyncingAll = false,
                        syncError = if (silentNetworkFailure) {
                            null
                        } else {
                            CoreErrorMapper.toUiError(result.error)
                        },
                    )
                }
            }
        }
    }

    fun logout(clearCachedData: Boolean = true, onDone: () -> Unit = {}) {
        if (formState.value.isLoggingOut) return
        viewModelScope.launch {
            formState.update { it.copy(isLoggingOut = true) }
            val result = core.auth.logout(clearCachedData = clearCachedData)
            val saved = core.auth.hasSavedCredentials()
            formState.update {
                it.copy(
                    isLoggingOut = false,
                    hasSavedCredentials = saved,
                    senhaInput = "",
                    loginError = (result as? CoreResult.Failure)?.let { err ->
                        CoreErrorMapper.toUiError(err.error)
                    },
                    syncError = null,
                    syncStatusMessage = null,
                )
            }
            onDone()
        }
    }

    fun dismissLoginError() {
        formState.update { it.copy(loginError = null) }
    }

    fun dismissSyncError() {
        formState.update { it.copy(syncError = null) }
    }

    fun clearSyncStatusMessage() {
        formState.update { it.copy(syncStatusMessage = null) }
    }

    fun autoEnableSigaaNotificationsIfPermitted() {
        viewModelScope.launch {
            core.autoEnableSigaaNotificacoesIfEligible()
        }
    }

    fun toggleSigaaTurmasSecao(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            if (enabled) {
                current.copy(habilitado = true, turmasHabilitado = true)
            } else {
                current.copy(turmasHabilitado = false)
            }
        }
    }

    fun toggleSigaaDiscenteSecao(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            if (enabled) {
                current.copy(habilitado = true, discenteHabilitado = true)
            } else {
                current.copy(discenteHabilitado = false)
            }
        }
    }

    fun setAulasLembrete(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                turmasHabilitado = current.turmasHabilitado || enabled,
                aulasLembrete = enabled,
            )
        }
    }

    fun setAntecedenciaAulaMinutos(minutes: Int) {
        updateSigaaNotificacoes { current ->
            current.copy(antecedenciaAulaMinutos = minutes)
        }
    }

    fun setAvisosEMateriaisNovos(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                turmasHabilitado = current.turmasHabilitado || enabled,
                avisosEMateriaisNovos = enabled,
            )
        }
    }

    fun setProvasETarefasLembrete(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                turmasHabilitado = current.turmasHabilitado || enabled,
                provasETarefasLembrete = enabled,
            )
        }
    }

    fun setNotasParciaisAlteradas(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                turmasHabilitado = current.turmasHabilitado || enabled,
                notasParciaisAlteradas = enabled,
            )
        }
    }

    fun setRiscoFaltaAlerta(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                turmasHabilitado = current.turmasHabilitado || enabled,
                riscoFaltaAlerta = enabled,
            )
        }
    }

    fun toggleTurmaSilenciada(turma: TurmaResumo) {
        updateSigaaNotificacoes { current ->
            val muted = current.turmasSilenciadas.toMutableSet()
            val isMutedNow = turma.frontEndIdTurma in muted || turma.idTurma in muted
            if (isMutedNow) {
                muted.remove(turma.frontEndIdTurma)
                muted.remove(turma.idTurma)
            } else {
                muted.add(turma.frontEndIdTurma)
            }
            current.copy(turmasSilenciadas = muted)
        }
    }

    fun setSaldoRuBaixoAlerta(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                discenteHabilitado = current.discenteHabilitado || enabled,
                saldoRuBaixoAlerta = enabled,
            )
        }
    }

    fun setLimiteRefeicoesRuBaixo(meals: Int) {
        updateSigaaNotificacoes { current ->
            current.copy(limiteRefeicoesRuBaixo = meals.coerceAtLeast(1))
        }
    }

    fun setExtratoRuMovimentacaoAlerta(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                discenteHabilitado = current.discenteHabilitado || enabled,
                extratoRuMovimentacaoAlerta = enabled,
            )
        }
    }

    fun setBoletimAlteradoAlerta(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                discenteHabilitado = current.discenteHabilitado || enabled,
                boletimAlteradoAlerta = enabled,
            )
        }
    }

    fun setIndicesAlteradosAlerta(enabled: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(
                habilitado = current.habilitado || enabled,
                discenteHabilitado = current.discenteHabilitado || enabled,
                indicesAlteradosAlerta = enabled,
            )
        }
    }

    fun setSigaaSyncInterval(interval: SigaaSyncIntervalHours) {
        updateSigaaNotificacoes { current ->
            current.copy(syncIntervalHours = interval)
        }
    }

    fun setSigaaSomenteWifi(onlyWifi: Boolean) {
        updateSigaaNotificacoes { current ->
            current.copy(somenteWifi = onlyWifi)
        }
    }

    private fun updateSigaaNotificacoes(
        transform: (SigaaNotificacaoConfig) -> SigaaNotificacaoConfig,
    ) {
        val current = uiState.value.sigaaNotificacoes
        val updated = transform(current).copy(userConfigured = true)
        viewModelScope.launch {
            core.setSigaaNotificacoesConfig(updated)
        }
    }
}
