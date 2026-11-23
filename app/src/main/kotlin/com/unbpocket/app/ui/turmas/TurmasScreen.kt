package com.unbpocket.app.ui.turmas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unbpocket.app.ui.common.FocusClassState
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.common.ScheduledClassItem
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.EditorialHeader
import com.unbpocket.app.ui.components.EmptyStateCard
import com.unbpocket.app.ui.components.ErrorBanner
import com.unbpocket.app.ui.components.HairlineDivider
import com.unbpocket.app.ui.components.HeaderActionIconButton
import com.unbpocket.app.ui.components.OfflineOriginStrip
import com.unbpocket.app.ui.components.SegmentedPillBar
import com.unbpocket.app.ui.components.SkeletonCard
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.theme.UnbShapes
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.viewmodel.TurmasListTab
import com.unbpocket.app.ui.viewmodel.TurmasUiState
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.turmas.AtualizacaoTurmaFeed
import com.unbpocket.core.model.turmas.DiaSemanaSigaa
import com.unbpocket.core.model.turmas.TurmaResumo
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurmasScreen(
    uiState: TurmasUiState,
    bottomContentPadding: Dp,
    onSelectListTab: (TurmasListTab) -> Unit,
    onSelectDay: (DiaSemanaSigaa) -> Unit,
    onToggleExpandClass: (String) -> Unit,
    onOpenTurma: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    val turmasList = uiState.turmas?.value.orEmpty()
    val atualizacoes = uiState.atualizacoes?.value.orEmpty()
    val semestreHeader = remember(turmasList) {
        turmasList.firstOrNull { it.semestre.isNotBlank() }?.semestre
    }
    val hasOfflineOrAuthData = turmasList.isNotEmpty() || uiState.authState is AuthState.Authenticated

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        EditorialHeader(
            title = "Aulas & Turmas",
            subtitle = {
                val subtitleText = when {
                    semestreHeader != null -> "Semestre $semestreHeader · Horário Oficial"
                    uiState.authState is AuthState.Authenticated -> "Semestre Atual · SIGAA"
                    else -> "Conecte sua conta SIGAA para sincronizar"
                }
                Text(
                    text = subtitleText,
                    style = typography.label,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                if (uiState.authState is AuthState.Authenticated) {
                    HeaderActionIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Atualizar turmas",
                        isLoading = uiState.isRefreshingList,
                        onClick = onRefresh,
                    )
                }
                HeaderActionIconButton(
                    icon = if (uiState.authState is AuthState.Authenticated) {
                        Icons.Rounded.Person
                    } else {
                        Icons.Rounded.PersonOutline
                    },
                    contentDescription = "Conta UnB",
                    showDot = uiState.authState is AuthState.Authenticated,
                    onClick = onOpenAccountSheet,
                )
            },
        )

        if (!hasOfflineOrAuthData && !uiState.isRefreshingList) {
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
                if (uiState.listError != null) {
                    item(key = "auth_gate_error") {
                        ErrorBanner(
                            banner = uiState.listError,
                            onRetry = if (uiState.listError.canRetry) onRefresh else null,
                            onLoginClick = if (uiState.listError.requiresLogin) onOpenLogin else null,
                            onDismiss = onDismissError,
                        )
                    }
                }
                item(key = "turmas_login_gate") {
                    EmptyStateCard(
                        icon = Icons.Rounded.Lock,
                        title = "Entre com sua conta do SIGAA",
                        body = "Conecte sua matrícula UnB para acompanhar seu horário semanal oficial, aulas do dia, materiais, frequências e avisos das turmas.",
                        actionLabel = "Entrar no SIGAA",
                        onAction = onOpenLogin,
                    )
                }
            }
            return
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshingList,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val nowMinutes = remember(uiState.selectedDay, uiState.turmas) {
                val cal = Calendar.getInstance()
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            }
            val dayClasses = remember(turmasList, uiState.selectedDay) {
                Formatters.classesForDay(turmasList, uiState.selectedDay)
            }
            val focusClass = remember(dayClasses, uiState.selectedDay, uiState.todayDay, nowMinutes) {
                Formatters.resolveFocusClass(
                    dayClasses = dayClasses,
                    isSelectedDayToday = uiState.selectedDay == uiState.todayDay,
                    nowMinutesOfDay = nowMinutes,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = UnbSpacing.lg,
                    end = UnbSpacing.lg,
                    top = UnbSpacing.xs,
                    bottom = bottomContentPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(UnbSpacing.md),
            ) {
                if (uiState.listError != null) {
                    item(key = "list_error") {
                        ErrorBanner(
                            banner = uiState.listError,
                            onRetry = if (uiState.listError.canRetry) onRefresh else null,
                            onLoginClick = if (uiState.listError.requiresLogin) onOpenLogin else null,
                            onDismiss = onDismissError,
                        )
                    }
                }

                item(key = "list_tabs") {
                    SegmentedPillBar(
                        options = listOf("Aulas do Dia", "Turmas", "Avisos"),
                        selectedIndex = uiState.selectedListTab.ordinal,
                        onSelect = { idx ->
                            onSelectListTab(TurmasListTab.entries[idx])
                        },
                    )
                }

                when (uiState.selectedListTab) {
                    TurmasListTab.AULAS_DO_DIA -> {
                        item(key = "sigaa_day_strip") {
                            SigaaDaySelectorRow(
                                selectedDay = uiState.selectedDay,
                                todayDay = uiState.todayDay,
                                onSelectDay = onSelectDay,
                            )
                        }

                        when {
                            uiState.turmas == null && uiState.isRefreshingList -> {
                                item(key = "turmas_skeleton") { SkeletonCard(lineCount = 5) }
                            }

                            turmasList.isEmpty() -> {
                                item(key = "turmas_empty_all") {
                                    EmptyStateCard(
                                        icon = Icons.Rounded.CalendarMonth,
                                        title = "Nenhuma turma encontrada",
                                        body = "Não há disciplinas ativas registradas para o semestre atual.",
                                        actionLabel = "Sincronizar SIGAA",
                                        onAction = onRefresh,
                                    )
                                }
                            }

                            dayClasses.isEmpty() -> {
                                item(key = "day_no_classes") {
                                    EmptyStateCard(
                                        icon = Icons.Rounded.Schedule,
                                        title = "Dia livre (${uiState.selectedDay.sigla})",
                                        body = "Você não possui aulas presenciais com horário marcado para ${uiState.selectedDay.nomeCompleto.lowercase()}.",
                                        actionLabel = "Ver todas as turmas",
                                        onAction = { onSelectListTab(TurmasListTab.TURMAS_SEMESTRE) },
                                    )
                                }
                            }

                            else -> {
                                if (focusClass != null) {
                                    item(key = "focus_hero_card") {
                                        FocusClassHeroCard(
                                            focus = focusClass,
                                            onOpenTurma = {
                                                onOpenTurma(focusClass.item.turma.frontEndIdTurma)
                                            },
                                        )
                                    }
                                }

                                item(key = "day_list_overline") {
                                    Text(
                                        text = "GRADE DE ${uiState.selectedDay.nomeCompleto.uppercase()}",
                                        style = typography.overline,
                                        color = colors.muted,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }

                                items(
                                    items = dayClasses,
                                    key = { "${it.turma.frontEndIdTurma}_${it.startMinutes}" },
                                ) { scheduled ->
                                    ScheduledClassRowCard(
                                        scheduled = scheduled,
                                        isExpanded = uiState.expandedClassTurmaId == scheduled.turma.frontEndIdTurma,
                                        onToggleExpand = {
                                            onToggleExpandClass(scheduled.turma.frontEndIdTurma)
                                        },
                                        onOpenTurma = {
                                            onOpenTurma(scheduled.turma.frontEndIdTurma)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    TurmasListTab.TURMAS_SEMESTRE -> {
                        if (uiState.turmas == null && uiState.isRefreshingList) {
                            item(key = "semestre_skeleton") { SkeletonCard(lineCount = 5) }
                        } else if (turmasList.isEmpty()) {
                            item(key = "semestre_empty") {
                                EmptyStateCard(
                                    icon = Icons.Rounded.School,
                                    title = "Sem turmas no semestre",
                                    body = "Toque em atualizar para sincronizar as turmas do Portal do Discente.",
                                    actionLabel = "Atualizar turmas",
                                    onAction = onRefresh,
                                )
                            }
                        } else {
                            items(
                                items = turmasList,
                                key = { it.frontEndIdTurma },
                            ) { turma ->
                                SemesterTurmaCard(
                                    turma = turma,
                                    onClick = { onOpenTurma(turma.frontEndIdTurma) },
                                )
                            }
                        }
                    }

                    TurmasListTab.ATUALIZACOES -> {
                        if (atualizacoes.isEmpty()) {
                            item(key = "feed_empty") {
                                EmptyStateCard(
                                    icon = Icons.Rounded.NotificationsNone,
                                    title = "Sem atualizações recentes",
                                    body = "Quando docentes publicarem notícias ou arquivos nas suas turmas, eles aparecerão aqui.",
                                    actionLabel = "Atualizar",
                                    onAction = onRefresh,
                                )
                            }
                        } else {
                            items(
                                items = atualizacoes,
                                key = { "${it.idTurma}_${it.dataTexto}_${it.tituloNoticia}" },
                            ) { update ->
                                TurmaFeedUpdateCard(
                                    item = update,
                                    onClick = {
                                        val matchingTurma = turmasList.firstOrNull {
                                            it.idTurma == update.idTurma ||
                                                it.frontEndIdTurma == update.idTurma
                                        }
                                        val targetId = matchingTurma?.frontEndIdTurma ?: update.idTurma
                                        if (targetId.isNotBlank()) onOpenTurma(targetId)
                                    },
                                )
                            }
                        }
                    }
                }

                uiState.turmas?.let { synced ->
                    item(key = "turmas_origin") {
                        OfflineOriginStrip(
                            origin = synced.origin,
                            syncedAtEpochMillis = synced.syncedAtEpochMillis,
                            extraNote = "${turmasList.size} turma(s)",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SigaaDaySelectorRow(
    selectedDay: DiaSemanaSigaa,
    todayDay: DiaSemanaSigaa,
    onSelectDay: (DiaSemanaSigaa) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DiaSemanaSigaa.entries.forEach { day ->
            val selected = day == selectedDay
            val isToday = day == todayDay
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .clip(UnbShapes.inner)
                    .clickable(role = Role.Tab) { onSelectDay(day) },
                shape = UnbShapes.inner,
                color = if (selected) colors.pillActiveBg else colors.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) colors.pillActiveBg else colors.border,
                ),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = day.sigla,
                        style = typography.label,
                        color = if (selected) colors.pillActiveInk else colors.ink,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isToday && selected -> colors.pillActiveInk
                                    isToday -> colors.ink
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusClassHeroCard(
    focus: FocusClassState,
    onOpenTurma: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val turma = focus.item.turma

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(UnbShapes.card)
            .clickable(role = Role.Button, onClick = onOpenTurma),
        shape = UnbShapes.card,
        color = colors.pillActiveBg,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(UnbSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (focus.isLiveNow) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.pillActiveInk),
                        )
                    }
                    Text(
                        text = focus.statusLabel,
                        style = typography.label,
                        color = colors.pillActiveInk.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val turmaCode = turma.codigoDisciplina
                if (!turmaCode.isNullOrBlank()) {
                    Text(
                        text = turmaCode,
                        style = typography.mono,
                        color = colors.pillActiveInk.copy(alpha = 0.78f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(UnbSpacing.sm))

            Text(
                text = turma.nomeDisciplina,
                style = typography.title.copy(fontWeight = FontWeight.Bold),
                color = colors.pillActiveInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            val location = turma.local.ifBlank { "Local não informado" }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = colors.pillActiveInk.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = location,
                    style = typography.body,
                    color = colors.pillActiveInk.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(UnbSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val footerLeft = when {
                    !turma.docenteNome.isNullOrBlank() -> "Prof. ${turma.docenteNome}"
                    !turma.numeroTurma.isNullOrBlank() -> "Turma ${turma.numeroTurma}"
                    else -> ""
                }
                Text(
                    text = footerLeft,
                    style = typography.label,
                    color = colors.pillActiveInk.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Turma Virtual",
                        style = typography.label,
                        color = colors.pillActiveInk,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.pillActiveInk,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledClassRowCard(
    scheduled: ScheduledClassItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenTurma: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val turma = scheduled.turma

    EditorialCard(onClick = onOpenTurma) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "${scheduled.startText} - ${scheduled.endText}",
                    style = typography.bodyTabular.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
            }
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Recolher detalhes" else "Expandir detalhes",
                    tint = colors.muted,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = turma.nomeDisciplina,
            style = typography.titleStrong,
            color = colors.ink,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = turma.local.ifBlank { "Local a definir no SIGAA" },
                style = typography.body,
                color = colors.muted,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(top = UnbSpacing.md)) {
                HairlineDivider()
                Spacer(modifier = Modifier.height(UnbSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    turma.codigoDisciplina?.takeIf { it.isNotBlank() }?.let { StatusTag(text = it) }
                    turma.numeroTurma?.takeIf { it.isNotBlank() }?.let { StatusTag(text = "Turma $it") }
                    if (turma.codigoHorarioRaw.isNotBlank()) {
                        StatusTag(text = turma.codigoHorarioRaw)
                    }
                }
                turma.docenteNome?.takeIf { it.isNotBlank() }?.let { docente ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Docente: $docente",
                        style = typography.label,
                        color = colors.muted,
                    )
                }
                Spacer(modifier = Modifier.height(UnbSpacing.md))
                Button(
                    onClick = onOpenTurma,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = UnbShapes.inner,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceRaised,
                        contentColor = colors.ink,
                    ),
                ) {
                    Text(text = "Abrir Turma Virtual", style = typography.label)
                }
            }
        }
    }
}

@Composable
private fun SemesterTurmaCard(
    turma: TurmaResumo,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                turma.codigoDisciplina?.takeIf { it.isNotBlank() }?.let { StatusTag(text = it) }
                turma.numeroTurma?.takeIf { it.isNotBlank() }?.let { StatusTag(text = "T$it") }
                turma.cargaHorariaHoras?.let { ch ->
                    Text(
                        text = "${ch}h",
                        style = typography.mono,
                        color = colors.muted,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Abrir turma",
                tint = colors.muted,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = turma.nomeDisciplina,
            style = typography.titleStrong,
            color = colors.ink,
        )

        turma.docenteNome?.takeIf { it.isNotBlank() }?.let { docente ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = docente,
                style = typography.label,
                color = colors.muted,
            )
        }

        val scheduleDisplay = when {
            turma.horarioExtenso.isNotEmpty() -> turma.horarioExtenso.joinToString(" · ")
            turma.codigoHorarioRaw.isNotBlank() -> turma.codigoHorarioRaw
            else -> null
        }

        if (scheduleDisplay != null || turma.local.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            HairlineDivider()
            Spacer(modifier = Modifier.height(10.dp))
            if (scheduleDisplay != null) {
                Text(
                    text = scheduleDisplay,
                    style = typography.label,
                    color = colors.ink,
                )
            }
            if (turma.local.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = turma.local,
                    style = typography.label,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun TurmaFeedUpdateCard(
    item: AtualizacaoTurmaFeed,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusTag(
                text = item.dataTexto.ifBlank { "Atualização" },
                tone = StatusTagTone.INFO,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.muted,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.disciplinaSemestre,
            style = typography.label,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.tituloNoticia,
            style = typography.title,
            color = colors.ink,
        )
    }
}
