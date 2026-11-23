package com.unbpocket.app.ui.ru

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unbpocket.app.ui.common.DietaryBadgeKind
import com.unbpocket.app.ui.common.FileOpener
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.common.RuMenuDisplayRow
import com.unbpocket.app.ui.common.RuMenuSectionKind
import com.unbpocket.app.ui.components.DietaryBadge
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.EditorialHeader
import com.unbpocket.app.ui.components.EmptyStateCard
import com.unbpocket.app.ui.components.ErrorBanner
import com.unbpocket.app.ui.components.HairlineDivider
import com.unbpocket.app.ui.components.HeaderActionIconButton
import com.unbpocket.app.ui.components.InfoCalloutRow
import com.unbpocket.app.ui.components.InnerTile
import com.unbpocket.app.ui.components.OfflineOriginStrip
import com.unbpocket.app.ui.components.RemoteBitmapImage
import com.unbpocket.app.ui.components.SegmentedPillBar
import com.unbpocket.app.ui.components.SkeletonBox
import com.unbpocket.app.ui.components.SkeletonInlineBar
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.components.UnbPullToRefreshBox
import com.unbpocket.app.ui.theme.UnbShapes
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.viewmodel.RuUiState
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.CardapioDiaRu
import com.unbpocket.core.model.ru.CardapioParserStatus
import com.unbpocket.core.model.ru.CardapioSemanalRu
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.NoticiaRu
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.TabelaPrecosRu
import com.unbpocket.core.model.ru.TipoRefeicao

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuScreen(
    uiState: RuUiState,
    authState: AuthState,
    bottomContentPadding: Dp,
    onSelectCampus: (CampusRu) -> Unit,
    onSelectDay: (Int) -> Unit,
    onSelectMeal: (TipoRefeicao) -> Unit,
    onToggleAllergen: (Alergenico) -> Unit,
    onSetHideRestricted: (Boolean) -> Unit,
    onClearAllergens: () -> Unit,
    onSetNotificacoesHabilitadas: (Boolean) -> Unit,
    onToggleNotificacaoSlot: (DiaSemanaRu, TipoRefeicao) -> Unit,
    onToggleNotificacaoDia: (DiaSemanaRu) -> Unit,
    onRefreshMenu: () -> Unit,
    onRefreshExtras: () -> Unit,
    onSetCampusSheetOpen: (Boolean) -> Unit,
    onSetAllergenSheetOpen: (Boolean) -> Unit,
    onSetNoticesSheetOpen: (Boolean) -> Unit,
    onOpenAccountSheet: () -> Unit,
    onOpenLogin: () -> Unit,
    onDismissMenuError: () -> Unit,
    onDismissExtrasError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val context = LocalContext.current

    val cardapioSynced = uiState.cardapio
    val cardapio = cardapioSynced?.value
    val dias = cardapio?.dias.orEmpty()
    val selectedDay = dias.getOrNull(uiState.selectedDayIndex)
    val isLoadingMenu = uiState.isMenuLoading

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        EditorialHeader(
            title = "Cardápio RU",
            subtitle = {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(UnbShapes.tag)
                        .clickable(role = Role.Button) { onSetCampusSheetOpen(true) }
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "UnB",
                        style = typography.label,
                        color = colors.muted,
                    )
                    Text(
                        text = "·",
                        style = typography.label,
                        color = colors.muted,
                    )
                    Text(
                        text = uiState.preferencias.campusSelecionado.displayName.uppercase(),
                        style = typography.label,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Selecionar campus",
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            actions = {
                HeaderActionIconButton(
                    icon = Icons.Rounded.NotificationsNone,
                    contentDescription = "Avisos e preços do RU",
                    showDot = uiState.hasUnreadNoticias,
                    onClick = { onSetNoticesSheetOpen(true) },
                )
                HeaderActionIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "Alergênicos e notificações do RU",
                    showDot = uiState.preferencias.alergenicosRestritos.isNotEmpty() ||
                        uiState.preferencias.notificacoes.hasAnySlotSelected(),
                    onClick = { onSetAllergenSheetOpen(true) },
                )
                HeaderActionIconButton(
                    icon = if (authState is AuthState.Authenticated) {
                        Icons.Rounded.Person
                    } else {
                        Icons.Rounded.PersonOutline
                    },
                    contentDescription = if (authState is AuthState.Authenticated) {
                        "Conta UnB conectada"
                    } else {
                        "Entrar na conta UnB"
                    },
                    onClick = onOpenAccountSheet,
                )
            },
        )

        if (dias.isNotEmpty() || isLoadingMenu) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UnbSpacing.lg, vertical = UnbSpacing.xs),
            ) {
                if (dias.isNotEmpty()) {
                    RuDayChipsStrip(
                        dias = dias,
                        selectedIndex = uiState.selectedDayIndex,
                        onSelectDay = onSelectDay,
                    )
                } else {
                    RuDayChipsSkeletonStrip()
                }
            }
        }

        UnbPullToRefreshBox(
            isRefreshing = uiState.isRefreshingMenu,
            onRefresh = onRefreshMenu,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = UnbSpacing.lg,
                    end = UnbSpacing.lg,
                    top = UnbSpacing.sm,
                    bottom = bottomContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(UnbSpacing.md),
            ) {
                if (uiState.menuError != null) {
                    item(key = "menu_error") {
                        ErrorBanner(
                            banner = uiState.menuError,
                            onRetry = if (uiState.menuError.canRetry) onRefreshMenu else null,
                            onLoginClick = if (uiState.menuError.requiresLogin) onOpenLogin else null,
                            onDismiss = onDismissMenuError,
                        )
                    }
                }

                item(key = "meal_selector") {
                    RuMealSelectorRow(
                        selectedMeal = uiState.selectedMeal,
                        onSelectMeal = onSelectMeal,
                    )
                }

                if (uiState.preferencias.alergenicosRestritos.isNotEmpty()) {
                    item(key = "active_allergen_strip") {
                        ActiveAllergenSummaryPill(
                            selected = uiState.preferencias.alergenicosRestritos,
                            hiding = uiState.preferencias.ocultarPratosComAlergenicos,
                            onClick = { onSetAllergenSheetOpen(true) },
                        )
                    }
                }

                item(key = "menu_content") {
                    when {
                        isLoadingMenu -> {
                            RuMenuSkeletonCard()
                        }

                        cardapio == null -> {
                            EmptyStateCard(
                                icon = Icons.Rounded.Restaurant,
                                title = "Cardápio indisponível no momento",
                                body = "Ainda não há refeições salvas para ${uiState.preferencias.campusSelecionado.displayName}. Tente atualizar ou verificar os avisos do RU.",
                                actionLabel = "Atualizar cardápio",
                                onAction = onRefreshMenu,
                                secondaryLabel = "Ver avisos e preços",
                                onSecondaryAction = { onSetNoticesSheetOpen(true) },
                            )
                        }

                        cardapio.parserStatus == CardapioParserStatus.FALLBACK_IMAGE_ONLY -> {
                            FallbackImageMenuCard(
                                cardapio = cardapio,
                                onOpenInBrowser = { url -> FileOpener.openExternalUrl(context, url) },
                                onRetry = onRefreshMenu,
                            )
                        }

                        cardapio.parserStatus == CardapioParserStatus.FALLBACK_PDF_ONLY && dias.isEmpty() -> {
                            EmptyStateCard(
                                icon = Icons.Rounded.Restaurant,
                                title = "Formato não estruturado no portal",
                                body = "O cardápio oficial de ${uiState.preferencias.campusSelecionado.displayName} nesta semana foi publicado em PDF sem texto extraível ou imagem direta.",
                                actionLabel = "Tentar novamente",
                                onAction = onRefreshMenu,
                                secondaryLabel = "Ver avisos do RU",
                                onSecondaryAction = { onSetNoticesSheetOpen(true) },
                            )
                        }

                        selectedDay == null -> {
                            EmptyStateCard(
                                icon = Icons.Rounded.Restaurant,
                                title = "Dia sem cardápio",
                                body = "Não encontramos refeições listadas para o dia selecionado em ${uiState.preferencias.campusSelecionado.displayName}.",
                                actionLabel = "Atualizar cardápio",
                                onAction = onRefreshMenu,
                            )
                        }

                        else -> {
                            AnimatedContent(
                                targetState = selectedDay to uiState.selectedMeal,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(160)) togetherWith
                                        fadeOut(animationSpec = tween(120))
                                },
                                label = "menuDayMealTransition",
                            ) { (day, meal) ->
                                StructuredMenuContentCard(
                                    day = day,
                                    meal = meal,
                                    restrictedAllergens = uiState.preferencias.alergenicosRestritos,
                                    hideRestricted = uiState.preferencias.ocultarPratosComAlergenicos,
                                    onEditAllergens = { onSetAllergenSheetOpen(true) },
                                    onRefresh = onRefreshMenu,
                                )
                            }
                        }
                    }
                }

                if (cardapioSynced != null) {
                    item(key = "origin_footer") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            OfflineOriginStrip(
                                origin = cardapioSynced.origin,
                                syncedAtEpochMillis = cardapioSynced.syncedAtEpochMillis,
                                extraNote = cardapioSynced.value.semanaLabel.takeIf { it.isNotBlank() },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onRefreshMenu,
                                enabled = !uiState.isRefreshingMenu,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = colors.ink,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Atualizar",
                                    style = typography.label,
                                    color = colors.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isCampusSheetOpen) {
        CampusPickerBottomSheet(
            selected = uiState.preferencias.campusSelecionado,
            onSelect = onSelectCampus,
            onDismiss = { onSetCampusSheetOpen(false) },
        )
    }

    if (uiState.isAllergenSheetOpen) {
        AllergenFilterBottomSheet(
            selected = uiState.preferencias.alergenicosRestritos,
            hideRestricted = uiState.preferencias.ocultarPratosComAlergenicos,
            notificacoes = uiState.preferencias.notificacoes,
            onToggleAllergen = onToggleAllergen,
            onSetHideRestricted = onSetHideRestricted,
            onClearAll = onClearAllergens,
            onSetNotificacoesHabilitadas = onSetNotificacoesHabilitadas,
            onToggleNotificacaoSlot = onToggleNotificacaoSlot,
            onToggleNotificacaoDia = onToggleNotificacaoDia,
            onDismiss = { onSetAllergenSheetOpen(false) },
        )
    }

    if (uiState.isNoticesAndPricesSheetOpen) {
        NoticesAndPricesBottomSheet(
            notices = uiState.noticias?.value.orEmpty(),
            tabelaPrecos = uiState.tabelaPrecos?.value,
            isLoading = uiState.isRefreshingExtras,
            error = uiState.extrasError,
            onRetry = onRefreshExtras,
            onDismissError = onDismissExtrasError,
            onOpenUrl = { url -> FileOpener.openExternalUrl(context, url) },
            onDismiss = { onSetNoticesSheetOpen(false) },
        )
    }
}

@Composable
private fun RuDayChipsStrip(
    dias: List<CardapioDiaRu>,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit,
) {
    val fitSingleRow = dias.size <= 7 && LocalDensity.current.fontScale <= 1.15f
    if (fitSingleRow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dias.forEachIndexed { index, dia ->
                RuDayChipItem(
                    index = index,
                    dia = dia,
                    selected = index == selectedIndex,
                    onClick = { onSelectDay(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = dias,
                key = { idx, item -> "${idx}_${item.dataTexto}_${item.diaSemana}" },
            ) { index, dia ->
                RuDayChipItem(
                    index = index,
                    dia = dia,
                    selected = index == selectedIndex,
                    onClick = { onSelectDay(index) },
                    modifier = Modifier.widthIn(min = 54.dp),
                )
            }
        }
    }
}

@Composable
private fun RuDayChipsSkeletonStrip() {
    val colors = UnbTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(7) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 60.dp),
                shape = UnbShapes.inner,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(24.dp)
                            .height(10.dp),
                        shape = RoundedCornerShape(3.dp),
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .width(20.dp)
                            .height(16.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RuMenuSkeletonCard() {
    EditorialCard {
        SkeletonBox(
            modifier = Modifier
                .width(128.dp)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.76f, 0.58f, 0.72f).forEach { fraction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(76.dp)
                            .height(26.dp),
                        shape = UnbShapes.tag,
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(16.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(UnbSpacing.lg))
        HairlineDivider()
        Spacer(modifier = Modifier.height(UnbSpacing.lg))

        SkeletonBox(
            modifier = Modifier
                .width(164.dp)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.82f, 0.54f).forEach { fraction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(76.dp)
                            .height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(15.dp),
                        shape = RoundedCornerShape(5.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(UnbSpacing.lg))
        HairlineDivider()
        Spacer(modifier = Modifier.height(UnbSpacing.lg))

        SkeletonBox(
            modifier = Modifier
                .width(116.dp)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.68f, 0.74f, 0.46f).forEach { fraction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(76.dp)
                            .height(14.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(15.dp),
                        shape = RoundedCornerShape(5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RuDayChipItem(
    index: Int,
    dia: CardapioDiaRu,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val meta = remember(index, dia) { Formatters.parseDayChipMeta(index, dia) }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 60.dp)
            .clip(UnbShapes.inner)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = meta.fullLabel },
        shape = UnbShapes.inner,
        color = if (selected) colors.pillActiveBg else colors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) colors.pillActiveBg else colors.border,
        ),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = meta.shortDow,
                style = typography.overline,
                color = if (selected) colors.pillActiveInk.copy(alpha = 0.75f) else colors.muted,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = meta.dayNumber,
                style = typography.titleStrong,
                color = if (selected) colors.pillActiveInk else colors.ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun RuMealSelectorRow(
    selectedMeal: TipoRefeicao,
    onSelectMeal: (TipoRefeicao) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedMeal.displayName,
                style = typography.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Formatters.mealScheduleWindow(selectedMeal),
                style = typography.label,
                color = colors.muted,
            )
        }

        Spacer(modifier = Modifier.width(UnbSpacing.sm))

        Surface(
            shape = CircleShape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MealIconPill(
                    selected = selectedMeal == TipoRefeicao.CAFE_DA_MANHA,
                    icon = Icons.Rounded.FreeBreakfast,
                    label = TipoRefeicao.CAFE_DA_MANHA.displayName,
                    onClick = { onSelectMeal(TipoRefeicao.CAFE_DA_MANHA) },
                )
                MealIconPill(
                    selected = selectedMeal == TipoRefeicao.ALMOCO,
                    icon = Icons.Rounded.Restaurant,
                    label = TipoRefeicao.ALMOCO.displayName,
                    onClick = { onSelectMeal(TipoRefeicao.ALMOCO) },
                )
                MealIconPill(
                    selected = selectedMeal == TipoRefeicao.JANTAR,
                    icon = Icons.Rounded.SoupKitchen,
                    label = TipoRefeicao.JANTAR.displayName,
                    onClick = { onSelectMeal(TipoRefeicao.JANTAR) },
                )
            }
        }
    }
}

@Composable
private fun MealIconPill(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
            .clip(CircleShape)
            .background(if (selected) colors.pillActiveBg else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) colors.pillActiveInk else colors.muted,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ActiveAllergenSummaryPill(
    selected: Set<Alergenico>,
    hiding: Boolean,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val names = selected.sortedBy { it.legendOrderIndex }.joinToString(", ") { it.labelPtBr }
    val modeText = if (hiding) "ocultando incompatíveis" else "alertando incompatíveis"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(UnbShapes.inner)
            .clickable(role = Role.Button, onClick = onClick),
        shape = UnbShapes.inner,
        color = colors.warningBg,
        border = BorderStroke(1.dp, colors.warningInk.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UnbSpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Restrições ($modeText): $names",
                style = typography.label,
                color = colors.warningInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(UnbSpacing.sm))
            Text(
                text = "Editar",
                style = typography.label,
                color = colors.warningInk,
            )
        }
    }
}

@Composable
private fun StructuredMenuContentCard(
    day: CardapioDiaRu,
    meal: TipoRefeicao,
    restrictedAllergens: Set<Alergenico>,
    hideRestricted: Boolean,
    onEditAllergens: () -> Unit,
    onRefresh: () -> Unit,
) {
    val rawItems = day.refeicoes[meal].orEmpty()
    if (rawItems.isEmpty()) {
        EmptyStateCard(
            icon = Icons.Rounded.Restaurant,
            title = "Sem itens para ${meal.displayName.lowercase()}",
            body = "Esta unidade não divulgou refeição para este turno na data selecionada.",
            actionLabel = "Atualizar",
            onAction = onRefresh,
        )
        return
    }

    val (sections, hiddenCount) = remember(rawItems, restrictedAllergens, hideRestricted) {
        Formatters.buildRuMenuSections(
            items = rawItems,
            restrictedAllergens = restrictedAllergens,
            hideRestricted = hideRestricted,
        )
    }

    if (sections.isEmpty() && hiddenCount > 0) {
        EmptyStateCard(
            icon = Icons.Rounded.Tune,
            title = "Todos os pratos filtrados",
            body = "Os $hiddenCount itens deste turno contêm alérgenos da sua lista de restrição.",
            actionLabel = "Ajustar filtros de alérgenos",
            onAction = onEditAllergens,
        )
        return
    }

    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        sections.forEachIndexed { sectionIndex, section ->
            if (sectionIndex > 0) {
                Spacer(modifier = Modifier.height(UnbSpacing.md))
                HairlineDivider()
                Spacer(modifier = Modifier.height(UnbSpacing.md))
            }

            Text(
                text = section.kind.heading,
                style = typography.overline,
                color = colors.muted,
            )
            Spacer(modifier = Modifier.height(UnbSpacing.md))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                section.rows.forEach { row ->
                    MenuItemRow(
                        row = row,
                        isPrimarySection = section.kind == RuMenuSectionKind.PRATOS_PRINCIPAIS,
                    )
                }
            }
        }

        if (hiddenCount > 0) {
            Spacer(modifier = Modifier.height(UnbSpacing.md))
            HairlineDivider()
            Spacer(modifier = Modifier.height(UnbSpacing.sm))
            Text(
                text = "$hiddenCount prato(s) oculto(s) pelos seus filtros de alérgenos.",
                style = typography.label,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun MenuItemRow(
    row: RuMenuDisplayRow,
    isPrimarySection: Boolean,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (row.dietaryBadge != null) {
            DietaryBadge(kind = row.dietaryBadge)
        } else if (!row.rowLabel.isNullOrBlank()) {
            Text(
                text = row.rowLabel,
                style = typography.body,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(84.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            val isDefaultDish = row.dietaryBadge == DietaryBadgeKind.PADRAO && isPrimarySection
            Text(
                text = row.item.prato,
                style = if (isDefaultDish) {
                    typography.body.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    typography.body
                },
                color = colors.ink,
            )
            if (row.restrictedMatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                StatusTag(
                    text = "Contém ${row.restrictedMatches.joinToString(", ") { it.labelPtBr }}",
                    tone = StatusTagTone.WARNING,
                )
            }
        }
    }
}

@Composable
private fun FallbackImageMenuCard(
    cardapio: CardapioSemanalRu,
    onOpenInBrowser: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        StatusTag(text = "IMAGEM OFICIAL DO PORTAL", tone = StatusTagTone.INFO)
        Spacer(modifier = Modifier.height(UnbSpacing.sm))
        Text(
            text = "Leitura estruturada indisponível para esta semana; exibindo a arte oficial publicada pelo RU.",
            style = typography.body,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(UnbSpacing.md))

        val url = cardapio.fallbackImageUrl
        RemoteBitmapImage(
            imageUrl = url,
            contentDescription = "Imagem oficial do cardápio semanal",
            onFailed = {
                InnerTile(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Não foi possível carregar a imagem oficial agora.",
                        style = typography.body,
                        color = colors.muted,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(UnbSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm)) {
            if (!url.isNullOrBlank()) {
                Button(
                    onClick = { onOpenInBrowser(url) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.pillActiveBg,
                        contentColor = colors.pillActiveInk,
                    ),
                    shape = UnbShapes.inner,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Abrir imagem", style = typography.label)
                }
            }
            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, colors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                shape = UnbShapes.inner,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = "Recarregar", style = typography.label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusPickerBottomSheet(
    selected: CampusRu,
    onSelect: (CampusRu) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.ink,
        shape = UnbShapes.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = UnbSpacing.lg, vertical = UnbSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
        ) {
            Text(
                text = "Selecionar Campus",
                style = typography.displayEditorial,
                color = colors.ink,
            )
            Text(
                text = "Escolha a unidade do Restaurante Universitário",
                style = typography.label,
                color = colors.muted,
            )
            Spacer(modifier = Modifier.height(4.dp))

            CampusRu.entries.forEach { campus ->
                val active = campus == selected
                InnerTile(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(campus) },
                    containerColor = if (active) colors.pillActiveBg else colors.surfaceRaised,
                    borderColor = if (active) colors.pillActiveBg else Color.Transparent,
                    contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = campus.displayName,
                                style = typography.title,
                                color = if (active) colors.pillActiveInk else colors.ink,
                            )
                            Text(
                                text = campus.operatingDaysSummary,
                                style = typography.label,
                                color = if (active) colors.pillActiveInk.copy(alpha = 0.78f) else colors.muted,
                            )
                        }
                        if (active) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selecionado",
                                tint = colors.pillActiveInk,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(UnbSpacing.sm))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AllergenFilterBottomSheet(
    selected: Set<Alergenico>,
    hideRestricted: Boolean,
    notificacoes: RuNotificacaoConfig,
    onToggleAllergen: (Alergenico) -> Unit,
    onSetHideRestricted: (Boolean) -> Unit,
    onClearAll: () -> Unit,
    onSetNotificacoesHabilitadas: (Boolean) -> Unit,
    onToggleNotificacaoSlot: (DiaSemanaRu, TipoRefeicao) -> Unit,
    onToggleNotificacaoDia: (DiaSemanaRu) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedAllergens = remember { Alergenico.entries.sortedBy { it.legendOrderIndex } }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPermissionDeniedBanner by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingNotificationAction
        pendingNotificationAction = null
        if (granted) {
            showPermissionDeniedBanner = false
            action?.invoke()
        } else {
            showPermissionDeniedBanner = true
        }
    }

    val runWithNotificationPermission: (() -> Unit) -> Unit = { action ->
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showPermissionDeniedBanner = false
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.ink,
        shape = UnbShapes.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = UnbSpacing.lg, vertical = UnbSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Alergênicos",
                    style = typography.displayEditorial,
                    color = colors.ink,
                )
                if (selected.isNotEmpty() || hideRestricted) {
                    TextButton(onClick = onClearAll) {
                        Text(text = "Limpar", style = typography.label, color = colors.muted)
                    }
                }
            }

            Text(
                text = "Classificação MSE automática sobre os pratos do cardápio oficial.",
                style = typography.body,
                color = colors.muted,
            )

            InnerTile(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ocultar pratos com restrição",
                            style = typography.title,
                            color = colors.ink,
                        )
                        Text(
                            text = "Quando desligado, exibe alerta sem esconder o prato",
                            style = typography.label,
                            color = colors.muted,
                        )
                    }
                    Spacer(modifier = Modifier.width(UnbSpacing.sm))
                    Switch(
                        checked = hideRestricted,
                        onCheckedChange = onSetHideRestricted,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.pillActiveInk,
                            checkedTrackColor = colors.pillActiveBg,
                            uncheckedThumbColor = colors.muted,
                            uncheckedTrackColor = colors.surface,
                            uncheckedBorderColor = colors.border,
                        ),
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sortedAllergens.forEach { allergen ->
                    val isSelected = allergen in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleAllergen(allergen) },
                        label = {
                            Text(
                                text = allergen.labelPtBr,
                                style = typography.label,
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.pillActiveBg,
                            selectedLabelColor = colors.pillActiveInk,
                            selectedLeadingIconColor = colors.pillActiveInk,
                            containerColor = colors.surfaceRaised,
                            labelColor = colors.ink,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = colors.border,
                            selectedBorderColor = colors.pillActiveBg,
                        ),
                    )
                }
            }

            HairlineDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "NOTIFICAÇÕES DO CARDÁPIO",
                    style = typography.overline,
                    color = colors.muted,
                )
                Text(
                    text = "Envio 1h antes de cada refeição escolhida (06h, 10h e 16h), mesmo com o app fechado.",
                    style = typography.label,
                    color = colors.muted,
                )
            }

            InnerTile(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ativar notificações",
                            style = typography.title,
                            color = colors.ink,
                        )
                        Text(
                            text = if (notificacoes.habilitado) {
                                "Selecione abaixo os dias e refeições desejados"
                            } else {
                                "Desativado; sua grade fica salva ao religar"
                            },
                            style = typography.label,
                            color = colors.muted,
                        )
                    }
                    Spacer(modifier = Modifier.width(UnbSpacing.sm))
                    Switch(
                        checked = notificacoes.habilitado,
                        onCheckedChange = { checked ->
                            if (checked) {
                                runWithNotificationPermission { onSetNotificacoesHabilitadas(true) }
                            } else {
                                showPermissionDeniedBanner = false
                                onSetNotificacoesHabilitadas(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.pillActiveInk,
                            checkedTrackColor = colors.pillActiveBg,
                            uncheckedThumbColor = colors.muted,
                            uncheckedTrackColor = colors.surface,
                            uncheckedBorderColor = colors.border,
                        ),
                    )
                }
            }

            if (showPermissionDeniedBanner) {
                InnerTile(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = colors.warningBg,
                    borderColor = colors.warningInk.copy(alpha = 0.25f),
                    contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Permissão de notificações bloqueada no sistema.",
                            style = typography.label,
                            color = colors.warningInk,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(UnbSpacing.sm))
                        TextButton(
                            onClick = {
                                val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                runCatching { context.startActivity(settingsIntent) }
                            },
                        ) {
                            Text(
                                text = "Ajustes",
                                style = typography.label,
                                color = colors.warningInk,
                            )
                        }
                    }
                }
            }

            RuNotificationScheduleGrid(
                notificacoes = notificacoes,
                onToggleSlot = { dia, refeicao ->
                    val currentlySelected = refeicao in notificacoes.refeicoesPorDia[dia].orEmpty()
                    if (!currentlySelected || !notificacoes.habilitado) {
                        runWithNotificationPermission { onToggleNotificacaoSlot(dia, refeicao) }
                    } else {
                        onToggleNotificacaoSlot(dia, refeicao)
                    }
                },
                onToggleDay = { dia ->
                    val currentMeals = notificacoes.refeicoesPorDia[dia].orEmpty()
                    val enablingDay = currentMeals.size < TipoRefeicao.entries.size
                    if (enablingDay || !notificacoes.habilitado) {
                        runWithNotificationPermission { onToggleNotificacaoDia(dia) }
                    } else {
                        onToggleNotificacaoDia(dia)
                    }
                },
            )

            Spacer(modifier = Modifier.height(UnbSpacing.sm))
        }
    }
}

@Composable
internal fun RuNotificationScheduleGrid(
    notificacoes: RuNotificacaoConfig,
    onToggleSlot: (DiaSemanaRu, TipoRefeicao) -> Unit,
    onToggleDay: (DiaSemanaRu) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val mealColumns = remember {
        listOf(
            TipoRefeicao.CAFE_DA_MANHA to Pair("CAFÉ", "06h"),
            TipoRefeicao.ALMOCO to Pair("ALMOÇO", "10h"),
            TipoRefeicao.JANTAR to Pair("JANTAR", "16h"),
        )
    }

    InnerTile(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "DIA",
                    style = typography.overline,
                    color = colors.muted,
                    modifier = Modifier.width(44.dp),
                )
                mealColumns.forEach { (_, labels) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = labels.first,
                            style = typography.overline,
                            color = colors.ink,
                            maxLines = 1,
                        )
                        Text(
                            text = labels.second,
                            style = typography.label,
                            color = colors.muted,
                        )
                    }
                }
            }

            DiaSemanaRu.entries.forEach { dia ->
                val dayMeals = notificacoes.refeicoesPorDia[dia].orEmpty()
                val hasAnyForDay = dayMeals.isNotEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(UnbShapes.tag)
                            .clickable(role = Role.Button) { onToggleDay(dia) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = dia.shortLabel,
                            style = typography.label.copy(
                                fontWeight = if (hasAnyForDay) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                            color = if (hasAnyForDay && notificacoes.habilitado) colors.ink else colors.muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    mealColumns.forEach { (meal, _) ->
                        val isSelected = meal in dayMeals
                        val isActive = isSelected && notificacoes.habilitado
                        val containerColor = when {
                            isActive -> colors.pillActiveBg
                            isSelected -> colors.surfaceRaised
                            else -> colors.surface
                        }
                        val borderColor = when {
                            isActive -> colors.pillActiveBg
                            else -> colors.border
                        }
                        val slotDesc = "${dia.displayName}, ${meal.displayName}"

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(UnbShapes.tag)
                                .background(containerColor)
                                .border(1.dp, borderColor, UnbShapes.tag)
                                .clickable(role = Role.Checkbox) { onToggleSlot(dia, meal) }
                                .semantics { contentDescription = slotDesc },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (isActive) colors.pillActiveInk else colors.muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Text(
                                    text = "-",
                                    style = typography.label,
                                    color = colors.muted.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticesAndPricesBottomSheet(
    notices: List<NoticiaRu>,
    tabelaPrecos: TabelaPrecosRu?,
    isLoading: Boolean,
    error: com.unbpocket.app.ui.common.UiErrorBanner?,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tabIndex by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.ink,
        shape = UnbShapes.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = UnbSpacing.lg, vertical = UnbSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Portal RU",
                    style = typography.displayEditorial,
                    color = colors.ink,
                )
                if (isLoading) {
                    SkeletonInlineBar(
                        width = 24.dp,
                        height = 7.dp,
                    )
                }
            }

            SegmentedPillBar(
                options = listOf("Avisos do RU", "Tabela de Preços"),
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
            )

            if (error != null) {
                ErrorBanner(
                    banner = error,
                    onRetry = if (error.canRetry) onRetry else null,
                    onDismiss = onDismissError,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
            ) {
                if (tabIndex == 0) {
                    if (notices.isEmpty() && isLoading) {
                        items(count = 3, key = { "ru_notice_skel_$it" }) {
                            InnerTile(modifier = Modifier.fillMaxWidth()) {
                                SkeletonBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.84f)
                                        .height(16.dp),
                                    shape = RoundedCornerShape(5.dp),
                                    color = colors.border,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonBox(
                                    modifier = Modifier
                                        .width(88.dp)
                                        .height(11.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.border,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                SkeletonBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.95f)
                                        .height(13.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.border,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .height(13.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.border,
                                )
                            }
                        }
                    } else if (notices.isEmpty()) {
                        item(key = "notices_empty") {
                            EmptyStateCard(
                                icon = Icons.Rounded.NotificationsNone,
                                title = "Nenhum aviso carregado",
                                body = "Toque abaixo para buscar comunicados recentes de ru.unb.br.",
                                actionLabel = "Atualizar avisos",
                                onAction = onRetry,
                            )
                        }
                    } else {
                        items(items = notices, key = { it.id }) { notice ->
                            InnerTile(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (notice.link.isNotBlank()) onOpenUrl(notice.link)
                                },
                            ) {
                                Text(
                                    text = notice.title,
                                    style = typography.title,
                                    color = colors.ink,
                                )
                                if (notice.dateIso.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notice.dateIso.take(10),
                                        style = typography.label,
                                        color = colors.muted,
                                    )
                                }
                                val preview = remember(notice.excerptText, notice.contentHtml) {
                                    notice.excerptText.ifBlank {
                                        Formatters.htmlToPlainText(notice.contentHtml)
                                    }
                                }
                                if (preview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = preview,
                                        style = typography.body,
                                        color = colors.muted,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (tabelaPrecos == null && isLoading) {
                        items(count = 3, key = { "prices_skel_$it" }) {
                            InnerTile(modifier = Modifier.fillMaxWidth()) {
                                SkeletonBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.56f)
                                        .height(16.dp),
                                    shape = RoundedCornerShape(5.dp),
                                    color = colors.border,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.74f)
                                        .height(12.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.border,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    repeat(3) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            SkeletonBox(
                                                modifier = Modifier
                                                    .width(44.dp)
                                                    .height(10.dp),
                                                shape = RoundedCornerShape(3.dp),
                                                color = colors.border,
                                            )
                                            SkeletonBox(
                                                modifier = Modifier
                                                    .width(56.dp)
                                                    .height(14.dp),
                                                shape = RoundedCornerShape(4.dp),
                                                color = colors.border,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (tabelaPrecos == null) {
                        item(key = "prices_empty") {
                            EmptyStateCard(
                                icon = Icons.Rounded.Restaurant,
                                title = "Tabela não carregada",
                                body = "Consulte os valores oficiais das refeições por grupo de acesso.",
                                actionLabel = "Carregar tabela",
                                onAction = onRetry,
                            )
                        }
                    } else {
                        item(key = "prices_ref") {
                            InfoCalloutRow(
                                text = "Referência: ${tabelaPrecos.resolucaoReferencia}",
                            )
                        }
                        items(items = tabelaPrecos.grupos, key = { it.grupo.name }) { preco ->
                            InnerTile(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${preco.grupo.roman} · ${preco.grupo.descricao}",
                                        style = typography.title,
                                        color = colors.ink,
                                    )
                                }
                                Text(
                                    text = preco.publicoAlvo,
                                    style = typography.label,
                                    color = colors.muted,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PriceMetricColumn(
                                        label = "Café",
                                        centavos = preco.cafeCentavos,
                                        modifier = Modifier.weight(1f),
                                    )
                                    PriceMetricColumn(
                                        label = "Almoço",
                                        centavos = preco.almocoCentavos,
                                        modifier = Modifier.weight(1f),
                                    )
                                    PriceMetricColumn(
                                        label = "Jantar",
                                        centavos = preco.jantarCentavos,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                preco.porKgCentavos?.let { kg ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Por quilo (visitante/kg): ${Formatters.formatCentavosBrl(kg)}",
                                        style = typography.label,
                                        color = colors.muted,
                                    )
                                }
                            }
                        }
                        if (tabelaPrecos.formasPagamento.isNotEmpty()) {
                            item(key = "payment_methods") {
                                InfoCalloutRow(
                                    text = "Formas de pagamento: ${tabelaPrecos.formasPagamento.joinToString(", ")}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceMetricColumn(
    label: String,
    centavos: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    Column(modifier = modifier) {
        Text(
            text = label,
            style = typography.overline,
            color = colors.muted,
        )
        Text(
            text = centavos?.let(Formatters::formatCentavosBrl) ?: "-",
            style = typography.bodyTabular,
            color = colors.ink,
        )
    }
}
