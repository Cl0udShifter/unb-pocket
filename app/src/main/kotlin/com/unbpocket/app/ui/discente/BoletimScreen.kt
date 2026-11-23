package com.unbpocket.app.ui.discente

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unbpocket.app.ui.components.BackHeaderButton
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.EditorialHeader
import com.unbpocket.app.ui.components.EmptyStateCard
import com.unbpocket.app.ui.components.ErrorBanner
import com.unbpocket.app.ui.components.HeaderActionIconButton
import com.unbpocket.app.ui.components.InnerTile
import com.unbpocket.app.ui.components.OfflineOriginStrip
import com.unbpocket.app.ui.components.SkeletonCard
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.components.UnbPullToRefreshBox
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.viewmodel.DiscenteUiState
import com.unbpocket.core.model.discente.BoletimSemestre
import com.unbpocket.core.model.discente.NotaDisciplinaBoletim

@Composable
fun BoletimScreen(
    uiState: DiscenteUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val semestres = uiState.boletim?.value.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        EditorialHeader(
            title = "Boletim & Menções",
            navigationIcon = {
                BackHeaderButton(onClick = onBack, contentDescription = "Voltar ao perfil")
            },
            subtitle = {
                Text(
                    text = "Histórico por período letivo no SIGAA",
                    style = typography.label,
                    color = colors.muted,
                )
            },
            actions = {
                HeaderActionIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Atualizar boletim",
                    isLoading = uiState.isRefreshingBoletim,
                    onClick = onRefresh,
                )
            },
        )

        UnbPullToRefreshBox(
            isRefreshing = uiState.isRefreshingBoletim,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = UnbSpacing.lg,
                    end = UnbSpacing.lg,
                    top = UnbSpacing.xs,
                    bottom = UnbSpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(UnbSpacing.md),
            ) {
                uiState.boletimError?.let { err ->
                    item(key = "boletim_error") {
                        ErrorBanner(
                            banner = err,
                            onRetry = if (err.canRetry) onRefresh else null,
                            onLoginClick = if (err.requiresLogin) onOpenLogin else null,
                            onDismiss = onDismissError,
                        )
                    }
                }

                if (uiState.boletim == null && uiState.isRefreshingBoletim) {
                    item(key = "boletim_skeleton") { SkeletonCard(lineCount = 6) }
                } else if (semestres.isEmpty()) {
                    item(key = "boletim_empty") {
                        EmptyStateCard(
                            icon = Icons.Rounded.School,
                            title = "Boletim não carregado",
                            body = "Toque em atualizar para buscar menções e notas por semestre do SIGAA.",
                            actionLabel = "Carregar boletim",
                            onAction = onRefresh,
                        )
                    }
                } else {
                    items(
                        items = semestres,
                        key = { it.semestre },
                    ) { sem ->
                        BoletimSemestreCard(semestre = sem)
                    }
                }

                uiState.boletim?.let { synced ->
                    item(key = "boletim_origin") {
                        OfflineOriginStrip(
                            origin = synced.origin,
                            syncedAtEpochMillis = synced.syncedAtEpochMillis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoletimSemestreCard(semestre: BoletimSemestre) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PERÍODO ${semestre.semestre}",
                style = typography.overline,
                color = colors.muted,
            )
            Text(
                text = "${semestre.disciplinas.size} disciplina(s)",
                style = typography.label,
                color = colors.muted,
            )
        }
        Spacer(modifier = Modifier.height(UnbSpacing.md))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            semestre.disciplinas.forEach { disc ->
                BoletimDisciplinaTile(disc = disc)
            }
        }
    }
}

@Composable
private fun BoletimDisciplinaTile(disc: NotaDisciplinaBoletim) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    val mencao = disc.resultadoMencao?.trim().orEmpty()
    val mencaoTone = when (mencao.uppercase()) {
        "SS", "MS", "MM" -> StatusTagTone.INFO
        "MI", "II", "SR" -> StatusTagTone.DANGER
        "TR", "CC", "DP" -> StatusTagTone.WARNING
        else -> StatusTagTone.NEUTRAL
    }

    InnerTile(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = disc.codigo,
                    style = typography.mono,
                    color = colors.muted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = disc.disciplina,
                    style = typography.title,
                    color = colors.ink,
                )
            }
            Spacer(modifier = Modifier.width(UnbSpacing.sm))
            if (mencao.isNotBlank()) {
                StatusTag(text = mencao, tone = mencaoTone)
            }
        }

        val details = listOfNotNull(
            disc.nota1?.takeIf { it.isNotBlank() }?.let { "Nota: $it" },
            disc.faltas?.let { "Faltas: $it" },
            disc.situacao?.takeIf { it.isNotBlank() },
        )
        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = details.joinToString(" · "),
                style = typography.label,
                color = colors.muted,
            )
        }
    }
}
