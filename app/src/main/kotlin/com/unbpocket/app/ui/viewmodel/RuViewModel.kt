package com.unbpocket.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unbpocket.app.ui.common.CoreErrorMapper
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.common.UiErrorBanner
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.SyncedData
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuDeepLinkTarget
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.ru.TipoRefeicao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class RuUiState(
    val preferencias: RuPreferencias = RuPreferencias(),
    val cardapio: SyncedData<CardapioSemanalRu>? = null,
    val hasResolvedInitialMenu: Boolean = false,
    val tabelaPrecos: SyncedData<TabelaPrecosRu>? = null,
    val noticias: SyncedData<List<NoticiaRu>>? = null,
    val selectedDayIndex: Int = 0,
    val selectedMeal: TipoRefeicao = Formatters.defaultMealForHour(
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    ),
    val isRefreshingMenu: Boolean = false,
    val isRefreshingExtras: Boolean = false,
    val menuError: UiErrorBanner? = null,
    val extrasError: UiErrorBanner? = null,
    val isCampusSheetOpen: Boolean = false,
    val isAllergenSheetOpen: Boolean = false,
    val isNoticesAndPricesSheetOpen: Boolean = false,
    val hasUnreadNoticias: Boolean = false,
) {
    val isMenuLoading: Boolean
        get() = cardapio == null && (isRefreshingMenu || !hasResolvedInitialMenu)
}

private data class RuControlState(
    val selectedDayIndex: Int? = null,
    val targetDayDataTexto: String? = null,
    val selectedMeal: TipoRefeicao = Formatters.defaultMealForHour(
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    ),
    val isRefreshingMenu: Boolean = false,
    val isRefreshingExtras: Boolean = false,
    val menuError: UiErrorBanner? = null,
    val extrasError: UiErrorBanner? = null,
    val isCampusSheetOpen: Boolean = false,
    val isAllergenSheetOpen: Boolean = false,
    val isNoticesAndPricesSheetOpen: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RuViewModel(
    private val core: UnbPocketCore,
) : ViewModel() {

    private val controlState = MutableStateFlow(RuControlState())
    private var lastLoadedCampus: CampusRu? = null
    private var silentSyncJob: Job? = null

    private val preferenciasFlow = core.ru.observePreferencias()

    private val resolvedCardapioFlow: Flow<Pair<Boolean, SyncedData<CardapioSemanalRu>?>> = preferenciasFlow
        .map { it.campusSelecionado }
        .distinctUntilChanged()
        .flatMapLatest { campus ->
            core.ru.observeCardapio(campus)
                .map { true to it }
                .onStart { emit(false to null) }
        }

    val uiState: StateFlow<RuUiState> = combine(
        preferenciasFlow,
        resolvedCardapioFlow,
        core.ru.observeTabelaPrecos(),
        core.ru.observeNoticias(),
        controlState,
    ) { prefs, (hasResolvedMenu, cardapio), precos, noticias, ctrl ->
        val dias = cardapio?.value?.dias.orEmpty()
        val matchedDeepLinkIdx = ctrl.targetDayDataTexto?.let { targetText ->
            dias.indexOfFirst { it.dataTexto == targetText }.takeIf { it >= 0 }
        }
        val resolvedDayIndex = when {
            dias.isEmpty() -> 0
            matchedDeepLinkIdx != null -> matchedDeepLinkIdx
            ctrl.selectedDayIndex != null -> ctrl.selectedDayIndex.coerceIn(0, dias.lastIndex)
            else -> resolveTodayIndex(dias.map { it.dataTexto to it.diaSemana })
        }

        val currentNoticias = noticias?.value.orEmpty()
        val hasUnreadNoticias = currentNoticias.any { it.id !in prefs.noticiasVistasIds }

        RuUiState(
            preferencias = prefs,
            cardapio = cardapio,
            hasResolvedInitialMenu = hasResolvedMenu,
            tabelaPrecos = precos,
            noticias = noticias,
            selectedDayIndex = resolvedDayIndex,
            selectedMeal = ctrl.selectedMeal,
            isRefreshingMenu = ctrl.isRefreshingMenu,
            isRefreshingExtras = ctrl.isRefreshingExtras,
            menuError = ctrl.menuError,
            extrasError = ctrl.extrasError,
            isCampusSheetOpen = ctrl.isCampusSheetOpen,
            isAllergenSheetOpen = ctrl.isAllergenSheetOpen,
            isNoticesAndPricesSheetOpen = ctrl.isNoticesAndPricesSheetOpen,
            hasUnreadNoticias = hasUnreadNoticias,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RuUiState(),
    )

    init {
        viewModelScope.launch {
            preferenciasFlow
                .map { it.campusSelecionado }
                .distinctUntilChanged()
                .collect { campus ->
                    if (lastLoadedCampus != campus) {
                        lastLoadedCampus = campus
                        refreshMenuInternal(campus = campus, force = false)
                    }
                }
        }
        refreshPricesAndNotices(force = false)
    }

    fun selectCampus(campus: CampusRu) {
        viewModelScope.launch {
            controlState.update {
                it.copy(
                    selectedDayIndex = null,
                    targetDayDataTexto = null,
                    isCampusSheetOpen = false,
                    menuError = null,
                )
            }
            core.ru.setCampusSelecionado(campus)
        }
    }

    fun selectDay(index: Int) {
        controlState.update { it.copy(selectedDayIndex = index, targetDayDataTexto = null) }
    }

    fun selectMeal(meal: TipoRefeicao) {
        controlState.update { it.copy(selectedMeal = meal) }
    }

    fun applyDeepLink(target: RuDeepLinkTarget) {
        controlState.update {
            it.copy(
                selectedMeal = target.refeicao,
                selectedDayIndex = target.dayIndex,
                targetDayDataTexto = target.dataTexto,
            )
        }
        val targetCampus = target.campus
        if (targetCampus != null && targetCampus != uiState.value.preferencias.campusSelecionado) {
            viewModelScope.launch {
                core.ru.setCampusSelecionado(targetCampus)
            }
        }
    }

    fun toggleAllergen(alergenico: Alergenico) {
        val current = uiState.value.preferencias
        val nextSet = if (alergenico in current.alergenicosRestritos) {
            current.alergenicosRestritos - alergenico
        } else {
            current.alergenicosRestritos + alergenico
        }
        viewModelScope.launch {
            core.ru.setAlergenicosRestritos(nextSet, current.ocultarPratosComAlergenicos)
        }
    }

    fun setHideRestricted(hide: Boolean) {
        val current = uiState.value.preferencias
        viewModelScope.launch {
            core.ru.setAlergenicosRestritos(current.alergenicosRestritos, hide)
        }
    }

    fun clearAllergens() {
        viewModelScope.launch {
            core.ru.setAlergenicosRestritos(emptySet(), ocultarPratos = false)
        }
    }

    fun setNotificacoesHabilitadas(enabled: Boolean) {
        val current = uiState.value.preferencias.notificacoes
        val next = current.copy(habilitado = enabled)
        viewModelScope.launch {
            core.ru.setNotificacoesConfig(next)
        }
    }

    fun toggleNotificacaoSlot(dia: DiaSemanaRu, refeicao: TipoRefeicao) {
        val current = uiState.value.preferencias.notificacoes
        val currentMeals = current.refeicoesPorDia[dia].orEmpty()
        val nextMeals = if (refeicao in currentMeals) currentMeals - refeicao else currentMeals + refeicao
        val nextMap = if (nextMeals.isEmpty()) {
            current.refeicoesPorDia - dia
        } else {
            current.refeicoesPorDia + (dia to nextMeals)
        }
        val next = current.copy(
            habilitado = current.habilitado || refeicao in nextMeals,
            refeicoesPorDia = nextMap,
        )
        viewModelScope.launch {
            core.ru.setNotificacoesConfig(next)
        }
    }

    fun toggleNotificacaoDia(dia: DiaSemanaRu) {
        val current = uiState.value.preferencias.notificacoes
        val allMeals = TipoRefeicao.entries.toSet()
        val currentMeals = current.refeicoesPorDia[dia].orEmpty()
        val enablingDay = currentMeals != allMeals
        val nextMap = if (enablingDay) {
            current.refeicoesPorDia + (dia to allMeals)
        } else {
            current.refeicoesPorDia - dia
        }
        val next = current.copy(
            habilitado = current.habilitado || enablingDay,
            refeicoesPorDia = nextMap,
        )
        viewModelScope.launch {
            core.ru.setNotificacoesConfig(next)
        }
    }

    fun refreshMenu(force: Boolean = true) {
        val campus = uiState.value.preferencias.campusSelecionado
        viewModelScope.launch {
            refreshMenuInternal(campus = campus, force = force)
        }
    }

    /**
     * Verifica e atualiza silenciosamente os cardapios vencidos ou obsoletos sem alterar spinners ou erros visiveis.
     */
    fun refreshIfStaleSilently() {
        if (silentSyncJob?.isActive == true || controlState.value.isRefreshingMenu) return
        silentSyncJob = viewModelScope.launch {
            core.ru.syncCardapiosIfNeeded(
                forceCheck = false,
                includeExtras = false,
            )
        }
    }

    private suspend fun refreshMenuInternal(campus: CampusRu, force: Boolean) {
        controlState.update { it.copy(isRefreshingMenu = true, menuError = null) }
        when (val result = core.ru.refreshCardapio(campus = campus, force = force)) {
            is CoreResult.Success -> {
                controlState.update { it.copy(isRefreshingMenu = false, menuError = null) }
            }

            is CoreResult.Failure -> {
                controlState.update {
                    it.copy(
                        isRefreshingMenu = false,
                        menuError = CoreErrorMapper.toUiError(result.error),
                    )
                }
            }
        }
    }

    fun refreshPricesAndNotices(force: Boolean = true) {
        if (controlState.value.isRefreshingExtras) return
        viewModelScope.launch {
            controlState.update { it.copy(isRefreshingExtras = true, extrasError = null) }
            val (precosRes, noticiasRes) = coroutineScope {
                val precosDeferred = async { core.ru.refreshTabelaPrecos(force = force) }
                val noticiasDeferred = async { core.ru.refreshNoticias(perPage = 10) }
                precosDeferred.await() to noticiasDeferred.await()
            }
            if (controlState.value.isNoticesAndPricesSheetOpen && noticiasRes is CoreResult.Success) {
                val ids = noticiasRes.value.mapTo(linkedSetOf()) { it.id }
                if (ids.isNotEmpty()) {
                    core.ru.markNoticiasVistas(ids)
                }
            }
            val error = (noticiasRes as? CoreResult.Failure)?.error
                ?: (precosRes as? CoreResult.Failure)?.error
            controlState.update {
                it.copy(
                    isRefreshingExtras = false,
                    extrasError = error?.let(CoreErrorMapper::toUiError),
                )
            }
        }
    }

    fun setCampusSheetOpen(open: Boolean) {
        controlState.update { it.copy(isCampusSheetOpen = open) }
    }

    fun setAllergenSheetOpen(open: Boolean) {
        controlState.update { it.copy(isAllergenSheetOpen = open) }
    }

    fun setNoticesAndPricesSheetOpen(open: Boolean) {
        controlState.update { it.copy(isNoticesAndPricesSheetOpen = open) }
        if (open) {
            markLoadedNoticiasAsSeen()
            refreshPricesAndNotices(force = false)
        }
    }

    private fun markLoadedNoticiasAsSeen() {
        viewModelScope.launch {
            val ids = core.ru.observeNoticias().firstOrNull()?.value
                ?.mapTo(linkedSetOf()) { it.id }
                .orEmpty()
            if (ids.isNotEmpty()) {
                core.ru.markNoticiasVistas(ids)
            }
        }
    }

    fun dismissMenuError() {
        controlState.update { it.copy(menuError = null) }
    }

    fun dismissExtrasError() {
        controlState.update { it.copy(extrasError = null) }
    }

    private fun resolveTodayIndex(days: List<Pair<String, String>>): Int {
        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val todayMonth = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val datePrefix = "$todayDay/$todayMonth"
        val exactDateIdx = days.indexOfFirst { (dataTexto, _) -> dataTexto.startsWith(datePrefix) }
        if (exactDateIdx >= 0) return exactDateIdx

        val dowKeyword = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "seg"
            Calendar.TUESDAY -> "ter"
            Calendar.WEDNESDAY -> "qua"
            Calendar.THURSDAY -> "qui"
            Calendar.FRIDAY -> "sex"
            Calendar.SATURDAY -> "sáb"
            Calendar.SUNDAY -> "dom"
            else -> "seg"
        }
        val dowIdx = days.indexOfFirst { (_, diaSemana) ->
            val lower = diaSemana.lowercase().trim()
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> lower.contains("seg") || lower.startsWith("2")
                Calendar.TUESDAY -> lower.contains("ter") || lower.startsWith("3")
                Calendar.WEDNESDAY -> lower.contains("qua") || lower.startsWith("4")
                Calendar.THURSDAY -> lower.contains("qui") || lower.startsWith("5")
                Calendar.FRIDAY -> lower.contains("sex") || lower.startsWith("6")
                Calendar.SATURDAY -> lower.contains("sab") || lower.contains("sáb")
                Calendar.SUNDAY -> lower.contains("dom")
                else -> lower.contains(dowKeyword.take(3))
            }
        }
        return if (dowIdx >= 0) dowIdx else 0
    }
}
