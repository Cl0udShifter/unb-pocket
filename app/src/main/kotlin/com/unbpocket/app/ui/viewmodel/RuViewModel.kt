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
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.ru.TipoRefeicao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class RuUiState(
    val preferencias: RuPreferencias = RuPreferencias(),
    val cardapio: SyncedData<CardapioSemanalRu>? = null,
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
)

private data class RuControlState(
    val selectedDayIndex: Int? = null,
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

    private val preferenciasFlow = core.ru.observePreferencias()

    private val cardapioFlow = preferenciasFlow
        .map { it.campusSelecionado }
        .distinctUntilChanged()
        .flatMapLatest { campus ->
            core.ru.observeCardapio(campus)
        }

    val uiState: StateFlow<RuUiState> = combine(
        preferenciasFlow,
        cardapioFlow,
        core.ru.observeTabelaPrecos(),
        core.ru.observeNoticias(),
        controlState,
    ) { prefs, cardapio, precos, noticias, ctrl ->
        val dias = cardapio?.value?.dias.orEmpty()
        val resolvedDayIndex = when {
            dias.isEmpty() -> 0
            ctrl.selectedDayIndex != null -> ctrl.selectedDayIndex.coerceIn(0, dias.lastIndex)
            else -> resolveTodayIndex(dias.map { it.dataTexto to it.diaSemana })
        }

        RuUiState(
            preferencias = prefs,
            cardapio = cardapio,
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
                    isCampusSheetOpen = false,
                    menuError = null,
                )
            }
            core.ru.setCampusSelecionado(campus)
        }
    }

    fun selectDay(index: Int) {
        controlState.update { it.copy(selectedDayIndex = index) }
    }

    fun selectMeal(meal: TipoRefeicao) {
        controlState.update { it.copy(selectedMeal = meal) }
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

    fun refreshMenu(force: Boolean = true) {
        val campus = uiState.value.preferencias.campusSelecionado
        viewModelScope.launch {
            refreshMenuInternal(campus = campus, force = force)
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
            val precosRes = core.ru.refreshTabelaPrecos(force = force)
            val noticiasRes = core.ru.refreshNoticias(perPage = 10)
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
            refreshPricesAndNotices(force = false)
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
            diaSemana.lowercase().contains(dowKeyword.take(3))
        }
        return if (dowIdx >= 0) dowIdx else 0
    }
}
