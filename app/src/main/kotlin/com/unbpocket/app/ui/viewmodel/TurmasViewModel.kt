package com.unbpocket.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unbpocket.app.ui.common.CoreErrorMapper
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.common.UiErrorBanner
import com.unbpocket.app.ui.common.withoutStaleAuth
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.turmas.ArquivoBaixado
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TurmaDetalheAva
import com.unbpocket.core.model.turmas.TurmaResumo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TurmasListTab {
    AULAS_DO_DIA,
    TURMAS_SEMESTRE,
    ATUALIZACOES,
}

enum class TurmaDetailTab {
    TOPICOS,
    NOTICIAS,
    FREQUENCIA,
    NOTAS_AVALIACOES,
}

data class TurmasUiState(
    val authState: AuthState = AuthState.Unauthenticated,
    val selectedListTab: TurmasListTab = TurmasListTab.AULAS_DO_DIA,
    val selectedDay: DiaSemanaSigaa = Formatters.currentSigaaDay(),
    val todayDay: DiaSemanaSigaa = Formatters.currentSigaaDay(),
    val expandedClassTurmaId: String? = null,
    val turmas: SyncedData<List<TurmaResumo>>? = null,
    val atualizacoes: SyncedData<List<AtualizacaoTurmaFeed>>? = null,
    val hasResolvedInitialCache: Boolean = false,
    val isRefreshingList: Boolean = false,
    val listError: UiErrorBanner? = null,
)

data class TurmaVirtualUiState(
    val activeTurmaId: String? = null,
    val summary: TurmaResumo? = null,
    val selectedDetailTab: TurmaDetailTab = TurmaDetailTab.TOPICOS,
    val detalhe: SyncedData<TurmaDetalheAva>? = null,
    val frequencia: SyncedData<MapaFrequenciaTurma>? = null,
    val notasParciais: SyncedData<NotasParciaisTurma>? = null,
    val avaliacoes: SyncedData<List<AvaliacaoTurma>>? = null,
    val tarefas: SyncedData<List<TarefaTurma>>? = null,
    val expandedNoticiaId: String? = null,
    val fullNoticiaById: Map<String, NoticiaTurmaAva> = emptyMap(),
    val loadingNoticiaId: String? = null,
    val loadingNoticiaIds: Set<String> = emptySet(),
    val downloadingItemId: String? = null,
    val downloadedFileEvent: ArquivoBaixado? = null,
    val isResolvingCache: Boolean = false,
    val isRefreshingDetail: Boolean = false,
    val detailError: UiErrorBanner? = null,
) {
    val isLoadingDetail: Boolean
        get() = isRefreshingDetail || isResolvingCache
}

private data class TurmasListControl(
    val selectedListTab: TurmasListTab = TurmasListTab.AULAS_DO_DIA,
    val selectedDay: DiaSemanaSigaa = Formatters.currentSigaaDay(),
    val expandedClassTurmaId: String? = null,
    val isRefreshingList: Boolean = false,
    val listError: UiErrorBanner? = null,
)

private data class TurmaDetailControl(
    val selectedDetailTab: TurmaDetailTab = TurmaDetailTab.TOPICOS,
    val expandedNoticiaId: String? = null,
    val fullNoticiaById: Map<String, NoticiaTurmaAva> = emptyMap(),
    val loadingNoticiaIds: Set<String> = emptySet(),
    val downloadingItemId: String? = null,
    val downloadedFileEvent: ArquivoBaixado? = null,
    val isResolvingCache: Boolean = false,
    val isRefreshingDetail: Boolean = false,
    val detailError: UiErrorBanner? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TurmasViewModel(
    private val core: UnbPocketCore,
) : ViewModel() {

    private val listControl = MutableStateFlow(TurmasListControl())
    private val activeTurmaId = MutableStateFlow<String?>(null)
    private val detailControl = MutableStateFlow(TurmaDetailControl())

    private val resolvedTurmasFlow: Flow<Pair<Boolean, SyncedData<List<TurmaResumo>>?>> =
        core.turmas.observeTurmasSemestre()
            .map { true to it }
            .onStart { emit(false to null) }

    init {
        viewModelScope.launch {
            core.auth.authState.collect { auth ->
                if (auth is AuthState.Authenticated) {
                    listControl.update { state ->
                        state.copy(listError = state.listError.withoutStaleAuth(auth))
                    }
                    detailControl.update { state ->
                        state.copy(detailError = state.detailError.withoutStaleAuth(auth))
                    }
                }
            }
        }
    }

    val listUiState: StateFlow<TurmasUiState> = combine(
        core.auth.authState,
        resolvedTurmasFlow,
        core.turmas.observeAtualizacoesFeed(),
        listControl,
    ) { auth, (hasResolvedCache, turmas), atualizacoes, ctrl ->
        TurmasUiState(
            authState = auth,
            selectedListTab = ctrl.selectedListTab,
            selectedDay = ctrl.selectedDay,
            todayDay = Formatters.currentSigaaDay(),
            expandedClassTurmaId = ctrl.expandedClassTurmaId,
            turmas = turmas,
            atualizacoes = atualizacoes,
            hasResolvedInitialCache = hasResolvedCache,
            isRefreshingList = ctrl.isRefreshingList,
            listError = ctrl.listError.withoutStaleAuth(auth),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TurmasUiState(authState = core.auth.authState.value),
    )

    private val detalheFlow: Flow<SyncedData<TurmaDetalheAva>?> = activeTurmaId.flatMapLatest { id ->
        if (id == null) flowOf(null) else core.turmas.observeTurmaDetalhe(id)
    }

    private val frequenciaFlow: Flow<SyncedData<MapaFrequenciaTurma>?> = activeTurmaId.flatMapLatest { id ->
        if (id == null) flowOf(null) else core.turmas.observeFrequencia(id)
    }

    private val notasFlow: Flow<SyncedData<NotasParciaisTurma>?> = activeTurmaId.flatMapLatest { id ->
        if (id == null) flowOf(null) else core.turmas.observeNotasParciais(id)
    }

    private val avaliacoesFlow: Flow<SyncedData<List<AvaliacaoTurma>>?> = activeTurmaId.flatMapLatest { id ->
        if (id == null) flowOf(null) else core.turmas.observeAvaliacoes(id)
    }

    private val tarefasFlow: Flow<SyncedData<List<TarefaTurma>>?> = activeTurmaId.flatMapLatest { id ->
        if (id == null) flowOf(null) else core.turmas.observeTarefas(id)
    }

    val detailUiState: StateFlow<TurmaVirtualUiState> = combine(
        activeTurmaId,
        core.turmas.observeTurmasSemestre(),
        detalheFlow,
        frequenciaFlow,
        notasFlow,
        avaliacoesFlow,
        tarefasFlow,
        detailControl,
        core.auth.authState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val id = values[0] as String?
        @Suppress("UNCHECKED_CAST")
        val turmas = values[1] as SyncedData<List<TurmaResumo>>?
        @Suppress("UNCHECKED_CAST")
        val detalhe = values[2] as SyncedData<TurmaDetalheAva>?
        @Suppress("UNCHECKED_CAST")
        val freq = values[3] as SyncedData<MapaFrequenciaTurma>?
        @Suppress("UNCHECKED_CAST")
        val notas = values[4] as SyncedData<NotasParciaisTurma>?
        @Suppress("UNCHECKED_CAST")
        val avaliacoes = values[5] as SyncedData<List<AvaliacaoTurma>>?
        @Suppress("UNCHECKED_CAST")
        val tarefas = values[6] as SyncedData<List<TarefaTurma>>?
        val ctrl = values[7] as TurmaDetailControl
        val auth = values[8] as AuthState

        TurmaVirtualUiState(
            activeTurmaId = id,
            summary = turmas?.value?.firstOrNull { it.frontEndIdTurma == id },
            selectedDetailTab = ctrl.selectedDetailTab,
            detalhe = detalhe,
            frequencia = freq,
            notasParciais = notas,
            avaliacoes = avaliacoes,
            tarefas = tarefas,
            expandedNoticiaId = ctrl.expandedNoticiaId,
            fullNoticiaById = ctrl.fullNoticiaById,
            loadingNoticiaId = ctrl.loadingNoticiaIds.lastOrNull(),
            loadingNoticiaIds = ctrl.loadingNoticiaIds,
            downloadingItemId = ctrl.downloadingItemId,
            downloadedFileEvent = ctrl.downloadedFileEvent,
            isRefreshingDetail = ctrl.isRefreshingDetail,
            detailError = ctrl.detailError.withoutStaleAuth(auth),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TurmaVirtualUiState(),
    )

    fun selectListTab(tab: TurmasListTab) {
        listControl.update { it.copy(selectedListTab = tab) }
    }

    fun selectDay(day: DiaSemanaSigaa) {
        listControl.update { it.copy(selectedDay = day, expandedClassTurmaId = null) }
    }

    fun toggleExpandedClass(frontEndIdTurma: String) {
        listControl.update {
            val next = if (it.expandedClassTurmaId == frontEndIdTurma) null else frontEndIdTurma
            it.copy(expandedClassTurmaId = next)
        }
    }

    fun refreshSemestre(enriquecerViaAtestado: Boolean = true) {
        if (listControl.value.isRefreshingList) return
        viewModelScope.launch {
            listControl.update { it.copy(isRefreshingList = true, listError = null) }
            when (val res = core.turmas.refreshTurmasSemestre(enriquecerViaAtestado = enriquecerViaAtestado)) {
                is CoreResult.Success -> {
                    listControl.update { it.copy(isRefreshingList = false, listError = null) }
                }

                is CoreResult.Failure -> {
                    listControl.update {
                        it.copy(
                            isRefreshingList = false,
                            listError = CoreErrorMapper.toUiError(res.error),
                        )
                    }
                }
            }
        }
    }

    fun openTurma(frontEndIdTurma: String) {
        activeTurmaId.value = frontEndIdTurma
        detailControl.value = TurmaDetailControl(isResolvingCache = true)
        viewModelScope.launch {
            val hasCached = core.turmas.observeTurmaDetalhe(frontEndIdTurma).first() != null
            detailControl.update { it.copy(isResolvingCache = false) }
            refreshTurmaDetalheInternal(frontEndIdTurma, silent = hasCached)
        }
    }

    fun closeTurma() {
        activeTurmaId.value = null
        detailControl.value = TurmaDetailControl()
    }

    fun selectDetailTab(tab: TurmaDetailTab) {
        val id = activeTurmaId.value ?: return
        detailControl.update { it.copy(selectedDetailTab = tab, detailError = null) }
        val snapshot = detailUiState.value
        viewModelScope.launch {
            when (tab) {
                TurmaDetailTab.TOPICOS,
                TurmaDetailTab.NOTICIAS -> {
                    if (snapshot.detalhe == null) refreshTurmaDetalheInternal(id)
                }

                TurmaDetailTab.FREQUENCIA -> {
                    if (snapshot.frequencia == null) refreshFrequenciaInternal(id)
                }

                TurmaDetailTab.NOTAS_AVALIACOES -> {
                    if (snapshot.notasParciais == null || snapshot.avaliacoes == null || snapshot.tarefas == null) {
                        refreshNotasAvaliacoesTarefasInternal(id)
                    }
                }
            }
        }
    }

    fun refreshActiveDetailTab() {
        val id = activeTurmaId.value ?: return
        if (detailControl.value.isRefreshingDetail) return
        val tab = detailControl.value.selectedDetailTab
        viewModelScope.launch {
            when (tab) {
                TurmaDetailTab.TOPICOS,
                TurmaDetailTab.NOTICIAS -> refreshTurmaDetalheInternal(id)
                TurmaDetailTab.FREQUENCIA -> refreshFrequenciaInternal(id)
                TurmaDetailTab.NOTAS_AVALIACOES -> refreshNotasAvaliacoesTarefasInternal(id)
            }
        }
    }

    private suspend fun refreshTurmaDetalheInternal(id: String, silent: Boolean = false) {
        if (!silent) {
            detailControl.update { it.copy(isRefreshingDetail = true, detailError = null) }
        }
        when (val res = core.turmas.refreshTurmaDetalhe(id)) {
            is CoreResult.Success -> {
                if (!silent) {
                    detailControl.update { it.copy(isRefreshingDetail = false) }
                }
            }
            is CoreResult.Failure -> {
                if (!silent) {
                    detailControl.update {
                        it.copy(
                            isRefreshingDetail = false,
                            detailError = CoreErrorMapper.toUiError(res.error),
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshFrequenciaInternal(id: String) {
        detailControl.update { it.copy(isRefreshingDetail = true, detailError = null) }
        when (val res = core.turmas.refreshFrequencia(id)) {
            is CoreResult.Success -> detailControl.update { it.copy(isRefreshingDetail = false) }
            is CoreResult.Failure -> detailControl.update {
                it.copy(
                    isRefreshingDetail = false,
                    detailError = CoreErrorMapper.toUiError(res.error),
                )
            }
        }
    }

    private suspend fun refreshNotasAvaliacoesTarefasInternal(id: String) {
        detailControl.update { it.copy(isRefreshingDetail = true, detailError = null) }
        val notasRes = core.turmas.refreshNotasParciais(id)
        val avalRes = core.turmas.refreshAvaliacoes(id)
        val tarRes = core.turmas.refreshTarefas(id)
        val firstError = (notasRes as? CoreResult.Failure)?.error
            ?: (avalRes as? CoreResult.Failure)?.error
            ?: (tarRes as? CoreResult.Failure)?.error
        detailControl.update {
            it.copy(
                isRefreshingDetail = false,
                detailError = firstError?.let(CoreErrorMapper::toUiError),
            )
        }
    }

    fun toggleNoticia(idNoticia: String) {
        val idTurma = activeTurmaId.value ?: return
        val currentExpanded = detailControl.value.expandedNoticiaId
        if (currentExpanded == idNoticia) {
            detailControl.update { it.copy(expandedNoticiaId = null) }
            return
        }
        if (detailControl.value.fullNoticiaById.containsKey(idNoticia)) {
            detailControl.update { it.copy(expandedNoticiaId = idNoticia) }
            return
        }

        val currentDetalhe = detailUiState.value.detalhe?.value
        val latest = currentDetalhe?.ultimaNoticia
        val resumo = currentDetalhe?.historicoNoticiasResumo?.firstOrNull { it.idNoticia == idNoticia }
        if (latest != null &&
            resumo != null &&
            latest.titulo.isNotBlank() &&
            latest.titulo.equals(resumo.titulo, ignoreCase = true) &&
            latest.dataHoraTexto.isNotBlank() &&
            latest.dataHoraTexto == resumo.dataHoraTexto
        ) {
            detailControl.update { state ->
                state.copy(
                    expandedNoticiaId = idNoticia,
                    fullNoticiaById = state.fullNoticiaById + (idNoticia to latest.copy(idNoticia = idNoticia)),
                )
            }
            return
        }

        if (idNoticia in detailControl.value.loadingNoticiaIds) {
            detailControl.update { it.copy(expandedNoticiaId = idNoticia) }
            return
        }

        detailControl.update { state ->
            state.copy(
                expandedNoticiaId = idNoticia,
                loadingNoticiaIds = state.loadingNoticiaIds + idNoticia,
                detailError = null,
            )
        }

        viewModelScope.launch {
            when (val res = core.turmas.getNoticiaCompleta(idTurma, idNoticia)) {
                is CoreResult.Success -> detailControl.update { state ->
                    state.copy(
                        loadingNoticiaIds = state.loadingNoticiaIds - idNoticia,
                        fullNoticiaById = state.fullNoticiaById + (idNoticia to res.value),
                    )
                }

                is CoreResult.Failure -> detailControl.update { state ->
                    state.copy(
                        loadingNoticiaIds = state.loadingNoticiaIds - idNoticia,
                        expandedNoticiaId = if (state.expandedNoticiaId == idNoticia) null else state.expandedNoticiaId,
                        detailError = CoreErrorMapper.toUiError(res.error),
                    )
                }
            }
        }
    }

    fun downloadMaterial(materialId: String) {
        val idTurma = activeTurmaId.value ?: return
        if (detailControl.value.downloadingItemId != null) return
        viewModelScope.launch {
            detailControl.update { it.copy(downloadingItemId = materialId, detailError = null) }
            when (val res = core.turmas.downloadMaterial(idTurma, materialId)) {
                is CoreResult.Success -> detailControl.update {
                    it.copy(downloadingItemId = null, downloadedFileEvent = res.value)
                }

                is CoreResult.Failure -> detailControl.update {
                    it.copy(
                        downloadingItemId = null,
                        detailError = CoreErrorMapper.toUiError(res.error),
                    )
                }
            }
        }
    }

    fun downloadAnexoNoticia(anexoId: String) {
        val idTurma = activeTurmaId.value ?: return
        if (detailControl.value.downloadingItemId != null) return
        viewModelScope.launch {
            detailControl.update { it.copy(downloadingItemId = anexoId, detailError = null) }
            when (val res = core.turmas.downloadAnexoNoticia(idTurma, anexoId)) {
                is CoreResult.Success -> detailControl.update {
                    it.copy(downloadingItemId = null, downloadedFileEvent = res.value)
                }

                is CoreResult.Failure -> detailControl.update {
                    it.copy(
                        downloadingItemId = null,
                        detailError = CoreErrorMapper.toUiError(res.error),
                    )
                }
            }
        }
    }

    fun consumeDownloadedFile() {
        detailControl.update { it.copy(downloadedFileEvent = null) }
    }

    fun dismissListError() {
        listControl.update { it.copy(listError = null) }
    }

    fun dismissDetailError() {
        detailControl.update { it.copy(detailError = null) }
    }
}
