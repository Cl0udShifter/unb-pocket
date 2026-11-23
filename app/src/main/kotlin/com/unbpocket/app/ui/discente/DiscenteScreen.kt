package com.unbpocket.app.ui.discente

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unbpocket.app.BuildConfig
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.EditorialHeader
import com.unbpocket.app.ui.components.EmptyStateCard
import com.unbpocket.app.ui.components.ErrorBanner
import com.unbpocket.app.ui.components.HairlineDivider
import com.unbpocket.app.ui.components.HeaderActionIconButton
import com.unbpocket.app.ui.components.InnerTile
import com.unbpocket.app.ui.components.LocalBitmapImage
import com.unbpocket.app.ui.components.OfflineOriginStrip
import com.unbpocket.app.ui.components.PreloadStatusBanner
import com.unbpocket.app.ui.components.SkeletonBox
import com.unbpocket.app.ui.components.SkeletonInlineBar
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.components.UnbPullToRefreshBox
import com.unbpocket.app.ui.theme.UnbShapes
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.viewmodel.AuthUiState
import com.unbpocket.app.ui.viewmodel.DiscenteUiState
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.common.PreloadPhase
import com.unbpocket.core.model.discente.CarteirinhaEstudantil
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.ru.SaldoExtratoRu
import java.util.Locale

@Composable
fun DiscenteScreen(
    uiState: DiscenteUiState,
    authUiState: AuthUiState,
    bottomContentPadding: Dp,
    onToggleTheme: () -> Unit,
    onToggleQrCardFace: () -> Unit,
    onToggleBrightnessBoost: () -> Unit,
    onRefreshProfile: () -> Unit,
    onRefreshCarteirinha: () -> Unit,
    onSyncAll: () -> Unit,
    onOpenBoletim: () -> Unit,
    onOpenDocumentos: () -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismissProfileError: () -> Unit,
    onDismissCarteirinhaError: () -> Unit,
    onDismissSyncError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val view = LocalView.current

    var showLogoutDialog by remember { mutableStateOf(false) }

    // Boost screen brightness only while the turnstile QR face is actively open.
    DisposableEffect(uiState.isFlippedToQr, uiState.boostQrBrightness) {
        val window = (view.context as? Activity)?.window
        val originalBrightness = window?.attributes?.screenBrightness
        if (window != null && uiState.isFlippedToQr && uiState.boostQrBrightness) {
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = lp
        }
        onDispose {
            if (window != null && originalBrightness != null) {
                val lp = window.attributes
                lp.screenBrightness = originalBrightness
                window.attributes = lp
            }
        }
    }

    val resumoSynced = uiState.discenteResumo
    val resumo = resumoSynced?.value
    val saldoSynced = uiState.saldoExtrato
    val saldo = saldoSynced?.value
    val carteirinha = uiState.carteirinha?.value
    val isEssentialSyncing = authUiState.isSubmittingLogin ||
        authUiState.isSyncingAll ||
        uiState.authState is AuthState.Reauthenticating ||
        authUiState.preloadProgress.phase == PreloadPhase.RUNNING_ESSENTIAL
    val isLoadingEssencial = uiState.isRefreshingProfile || isEssentialSyncing || !uiState.hasResolvedInitialCache
    val isPreloadingCarteirinha = authUiState.preloadProgress.phase == PreloadPhase.RUNNING_BACKGROUND &&
        authUiState.preloadProgress.currentTarget == "Carteirinha Estudantil"
    val isLoadingQr = uiState.isRefreshingCarteirinha ||
        (carteirinha == null && (isPreloadingCarteirinha || !uiState.hasResolvedCarteirinhaCache))
    val hasDataOrAuth = resumo != null ||
        saldo != null ||
        uiState.authState is AuthState.Authenticated ||
        isEssentialSyncing ||
        !uiState.hasResolvedInitialCache

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        EditorialHeader(
            title = "Perfil Discente",
            subtitle = {
                val subtitle = when {
                    resumo != null -> listOf(
                        resumo.perfil.semestreAtual.takeIf { it.isNotBlank() },
                        resumo.perfil.curso.takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString(" · ")
                    uiState.authState is AuthState.Authenticated || isEssentialSyncing -> "Conta SIGAA conectada"
                    else -> "Conecte sua conta para acessar perfil e RU"
                }
                Text(
                    text = subtitle.ifBlank { "Universidade de Brasília" },
                    style = typography.label,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                HeaderActionIconButton(
                    icon = if (colors.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (colors.isDark) "Mudar para tema claro" else "Mudar para tema escuro",
                    onClick = onToggleTheme,
                )
                if (hasDataOrAuth) {
                    HeaderActionIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Atualizar perfil",
                        isLoading = uiState.isRefreshingProfile || isEssentialSyncing,
                        onClick = onRefreshProfile,
                    )
                }
            },
        )

        if (!hasDataOrAuth) {
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
                if (uiState.profileError != null) {
                    item(key = "discente_gate_error") {
                        ErrorBanner(
                            banner = uiState.profileError,
                            onRetry = if (uiState.profileError.canRetry) onRefreshProfile else null,
                            onDismiss = onDismissProfileError,
                        )
                    }
                }
                item(key = "discente_login_gate") {
                    EmptyStateCard(
                        icon = Icons.Rounded.Lock,
                        title = "Entre com sua conta do SIGAA",
                        body = "Veja seu IRA, Média Ponderada, integralização curricular, saldo e extrato do RU, passe QR da catraca, boletim e declarações oficiais.",
                        actionLabel = "Entrar no SIGAA",
                        onAction = onOpenLogin,
                    )
                }
            }
            return
        }

        UnbPullToRefreshBox(
            isRefreshing = uiState.isRefreshingProfile || isEssentialSyncing,
            onRefresh = onRefreshProfile,
            modifier = Modifier.fillMaxSize(),
        ) {
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
                item(key = "discente_preload_banner") {
                    PreloadStatusBanner(progress = authUiState.preloadProgress)
                }

                authUiState.syncError?.let { syncErr ->
                    item(key = "sync_err") {
                        ErrorBanner(
                            banner = syncErr,
                            onRetry = if (syncErr.canRetry) onSyncAll else null,
                            onLoginClick = if (syncErr.requiresLogin) onOpenLogin else null,
                            onDismiss = onDismissSyncError,
                        )
                    }
                }

                uiState.profileError?.let { err ->
                    item(key = "profile_err") {
                        ErrorBanner(
                            banner = err,
                            onRetry = if (err.canRetry) onRefreshProfile else null,
                            onLoginClick = if (err.requiresLogin) onOpenLogin else null,
                            onDismiss = onDismissProfileError,
                        )
                    }
                }

                if (resumo == null && isLoadingEssencial) {
                    item(key = "identity_skeleton") { StudentIdentitySkeletonCard() }
                    item(key = "indices_skeleton") { AcademicMetricsSkeletonCard() }
                } else if (resumo != null) {
                    item(key = "identity_card") {
                        StudentIdentityCard(
                            resumo = resumo,
                            fotoLocalPath = carteirinha?.foto3x4LocalPath,
                        )
                    }

                    item(key = "indices_card") {
                        AcademicMetricsCard(resumo = resumo)
                    }
                }

                item(key = "boletim_nav_card") {
                    NavEntryCard(
                        icon = Icons.Rounded.School,
                        title = "Boletim & Menções",
                        subtitle = "Disciplinas cursadas, menções e histórico por semestre",
                        onClick = onOpenBoletim,
                    )
                }

                item(key = "documentos_nav_card") {
                    NavEntryCard(
                        icon = Icons.Rounded.Description,
                        title = "Documentos Digitais",
                        subtitle = "Declaração de vínculo, atestado, histórico e passe livre",
                        onClick = onOpenDocumentos,
                    )
                }

                item(key = "ru_flip_card") {
                    RuBalanceOrQrFlipCard(
                        saldo = saldo,
                        carteirinha = carteirinha,
                        isFlippedToQr = uiState.isFlippedToQr,
                        boostBrightness = uiState.boostQrBrightness,
                        isLoadingSaldo = saldo == null && isLoadingEssencial,
                        isLoadingQr = isLoadingQr,
                        qrError = uiState.carteirinhaError,
                        onToggleFace = onToggleQrCardFace,
                        onToggleBrightness = onToggleBrightnessBoost,
                        onRefreshQr = onRefreshCarteirinha,
                        onDismissQrError = onDismissCarteirinhaError,
                        onOpenLogin = onOpenLogin,
                    )
                }

                item(key = "app_version_card") {
                    EditorialCard(contentPadding = PaddingValues(UnbSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Versão do Aplicativo",
                                    style = typography.title,
                                    color = colors.ink,
                                )
                                Text(
                                    text = "UnB Pocket · Somente leitura",
                                    style = typography.label,
                                    color = colors.muted,
                                )
                            }
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = typography.mono,
                                color = colors.muted,
                            )
                        }
                    }
                }

                if (uiState.authState is AuthState.Authenticated) {
                    item(key = "footer_actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = UnbSpacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
                        ) {
                            OutlinedButton(
                                onClick = onSyncAll,
                                enabled = !authUiState.isSyncingAll,
                                border = BorderStroke(1.dp, colors.border),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                                shape = UnbShapes.inner,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                if (authUiState.isSyncingAll) {
                                    SkeletonInlineBar(
                                        width = 20.dp,
                                        height = 6.dp,
                                        color = colors.ink,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (authUiState.isSyncingAll) "Sincronizando..." else "Sincronizar dados",
                                    style = typography.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            OutlinedButton(
                                onClick = { showLogoutDialog = true },
                                enabled = !authUiState.isLoggingOut,
                                border = BorderStroke(1.dp, colors.dangerText.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.dangerText),
                                shape = UnbShapes.inner,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Desconectar",
                                    style = typography.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    item(key = "reconnect_action") {
                        Button(
                            onClick = onOpenLogin,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.pillActiveBg,
                                contentColor = colors.pillActiveInk,
                            ),
                            shape = UnbShapes.inner,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text(text = "Conectar conta SIGAA", style = typography.title)
                        }
                    }
                }

                (resumoSynced ?: saldoSynced)?.let { synced ->
                    item(key = "discente_origin") {
                        OfflineOriginStrip(
                            origin = synced.origin,
                            syncedAtEpochMillis = synced.syncedAtEpochMillis,
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.ink,
            textContentColor = colors.muted,
            title = {
                Text(
                    text = "Desconectar conta SIGAA?",
                    style = typography.titleStrong,
                )
            },
            text = {
                Text(
                    text = "As credenciais salvas no Keystore e os dados acadêmicos em cache serão removidos deste dispositivo.",
                    style = typography.body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                ) {
                    Text(
                        text = "Desconectar",
                        style = typography.label,
                        color = colors.dangerText,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = "Cancelar",
                        style = typography.label,
                        color = colors.ink,
                    )
                }
            },
        )
    }
}

@Composable
private fun StudentIdentityCard(
    resumo: DiscenteResumo,
    fotoLocalPath: String?,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val perfil = resumo.perfil

    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                LocalBitmapImage(
                    localPath = fotoLocalPath,
                    contentDescription = "Foto do discente",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    fallback = {
                        Text(
                            text = Formatters.initialsFromName(perfil.nomeCompleto),
                            style = typography.titleStrong,
                            color = colors.ink,
                        )
                    },
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = perfil.nomeCompleto,
                    style = typography.titleStrong,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Matrícula: ${perfil.matricula}",
                    style = typography.mono,
                    color = colors.muted,
                )
                if (perfil.nivel.isNotBlank()) {
                    Text(
                        text = perfil.nivel,
                        style = typography.label,
                        color = colors.muted,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (perfil.status.isNotBlank()) {
                        StatusTag(text = perfil.status.uppercase())
                    }
                    if (perfil.semestreAtual.isNotBlank()) {
                        StatusTag(text = perfil.semestreAtual)
                    }
                }
            }
        }
    }
}

@Composable
private fun AcademicMetricsCard(resumo: DiscenteResumo) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val indices = resumo.indices

    EditorialCard {
        Text(
            text = "RENDIMENTO ACADÊMICO",
            style = typography.overline,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(UnbSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
        ) {
            InnerTile(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(UnbSpacing.md),
            ) {
                Text(
                    text = "IRA",
                    style = typography.overline,
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = indices.ira?.let { String.format(Locale.US, "%.4f", it) } ?: "-",
                    style = typography.metricSerif,
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Índice de Rendimento",
                    style = typography.label,
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            InnerTile(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(UnbSpacing.md),
            ) {
                Text(
                    text = "MÉDIA PONDERADA",
                    style = typography.overline,
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = indices.mp?.let { String.format(Locale.US, "%.4f", it) } ?: "-",
                    style = typography.metricSerif,
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ponderada Geral",
                    style = typography.label,
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(UnbSpacing.md))

        val percentValue = (indices.percentualIntegralizadoExato ?: 0.0).coerceIn(0.0, 100.0)
        val percentLabel = when {
            !indices.percentualIntegralizadoTexto.isNullOrBlank() -> indices.percentualIntegralizadoTexto.orEmpty()
            indices.percentualIntegralizadoExato != null -> String.format(Locale.US, "%.1f%%", percentValue)
            else -> "-"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Integralização Curricular",
                style = typography.title,
                color = colors.ink,
            )
            Text(
                text = percentLabel,
                style = typography.bodyTabular.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceRaised),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percentValue / 100.0).toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.pillActiveBg),
            )
        }

        val totalCh = indices.chTotalCurriculo
        val obPending = indices.chObrigatoriaPendente
        val optPending = indices.chOptativaPendente
        val compPending = indices.chComplementarPendente ?: 0

        if (totalCh != null) {
            val completedCh = (totalCh - (obPending ?: 0) - (optPending ?: 0) - compPending).coerceAtLeast(0)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Concluídas: ${completedCh}h",
                    style = typography.label,
                    color = colors.muted,
                )
                Text(
                    text = "Total: ${totalCh}h",
                    style = typography.label,
                    color = colors.muted,
                )
            }
            if (obPending != null || optPending != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pendentes: Obrigatórias ${obPending ?: 0}h · Optativas ${optPending ?: 0}h",
                    style = typography.label,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun StudentIdentitySkeletonCard() {
    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            SkeletonBox(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(18.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                SkeletonBox(
                    modifier = Modifier
                        .width(136.dp)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                )
                SkeletonBox(
                    modifier = Modifier
                        .width(94.dp)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(64.dp)
                            .height(24.dp),
                        shape = UnbShapes.tag,
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .width(58.dp)
                            .height(24.dp),
                        shape = UnbShapes.tag,
                    )
                }
            }
        }
    }
}

@Composable
private fun AcademicMetricsSkeletonCard() {
    val colors = UnbTheme.colors
    EditorialCard {
        SkeletonBox(
            modifier = Modifier
                .width(142.dp)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(modifier = Modifier.height(UnbSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
        ) {
            repeat(2) {
                InnerTile(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(UnbSpacing.md),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SkeletonBox(
                            modifier = Modifier
                                .width(64.dp)
                                .height(11.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = colors.border,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .width(84.dp)
                                .height(26.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = colors.border,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .width(92.dp)
                                .height(11.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = colors.border,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(UnbSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(
                modifier = Modifier
                    .width(164.dp)
                    .height(16.dp),
                shape = RoundedCornerShape(5.dp),
            )
            SkeletonBox(
                modifier = Modifier
                    .width(44.dp)
                    .height(16.dp),
                shape = RoundedCornerShape(5.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            shape = RoundedCornerShape(4.dp),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SkeletonBox(
                modifier = Modifier
                    .width(104.dp)
                    .height(11.dp),
                shape = RoundedCornerShape(4.dp),
            )
            SkeletonBox(
                modifier = Modifier
                    .width(74.dp)
                    .height(11.dp),
                shape = RoundedCornerShape(4.dp),
            )
        }
    }
}

@Composable
private fun NavEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.title,
                    color = colors.ink,
                )
                Text(
                    text = subtitle,
                    style = typography.label,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.muted,
            )
        }
    }
}

@Composable
private fun RuBalanceOrQrFlipCard(
    saldo: SaldoExtratoRu?,
    carteirinha: CarteirinhaEstudantil?,
    isFlippedToQr: Boolean,
    boostBrightness: Boolean,
    isLoadingSaldo: Boolean,
    isLoadingQr: Boolean,
    qrError: com.unbpocket.app.ui.common.UiErrorBanner?,
    onToggleFace: () -> Unit,
    onToggleBrightness: () -> Unit,
    onRefreshQr: () -> Unit,
    onDismissQrError: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlippedToQr) 180f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "ruQrFlipRotation",
    )
    val density = LocalDensity.current.density

    val showingBackFace = rotation > 90f

    EditorialCard(
        modifier = Modifier.graphicsLayer {
            rotationY = if (showingBackFace) rotation - 180f else rotation
            cameraDistance = 12f * density
        },
    ) {
        if (!showingBackFace) {
            RuBalanceFrontFace(
                saldo = saldo,
                isLoading = isLoadingSaldo,
                onFlipToQr = onToggleFace,
            )
        } else {
            RuQrBackFace(
                carteirinha = carteirinha,
                boostBrightness = boostBrightness,
                isLoading = isLoadingQr,
                error = qrError,
                onFlipToBalance = onToggleFace,
                onToggleBrightness = onToggleBrightness,
                onRefresh = onRefreshQr,
                onDismissError = onDismissQrError,
                onOpenLogin = onOpenLogin,
            )
        }
    }
}

@Composable
private fun RuBalanceFrontFace(
    saldo: SaldoExtratoRu?,
    isLoading: Boolean,
    onFlipToQr: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    Text(
        text = "RESTAURANTE UNIVERSITÁRIO",
        style = typography.overline,
        color = colors.muted,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = "Saldo Disponível",
        style = typography.label,
        color = colors.muted,
    )

    Spacer(modifier = Modifier.height(UnbSpacing.md))

    InnerTile(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 22.dp, horizontal = UnbSpacing.lg),
    ) {
        if (saldo == null && isLoading) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .width(136.dp)
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.border,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(
                    modifier = Modifier
                        .width(184.dp)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = colors.border,
                )
            }
        } else {
            Text(
                text = saldo?.let { Formatters.formatCentavosBrl(it.saldoAtualCentavos) } ?: "R$ -",
                style = typography.metricBalance,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (saldo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val groupLabel = "${saldo.grupoAcesso.roman} · ${saldo.grupoAcesso.descricao}"
                val restantes = saldo.refeicoesRestantes
                val remainingText = when {
                    restantes == null -> null
                    restantes.gratuito -> "Gratuidade ativa"
                    else -> "≈ ${restantes.almocosOuJantaresRestantes} almoço(s)/jantar(es)"
                }
                Text(
                    text = listOfNotNull(groupLabel, remainingText).joinToString(" · "),
                    style = typography.label,
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(UnbSpacing.md))

    Button(
        onClick = onFlipToQr,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        shape = UnbShapes.inner,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceRaised,
            contentColor = colors.ink,
        ),
    ) {
        Icon(
            imageVector = Icons.Rounded.QrCode2,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Girar para QR Code da Catraca", style = typography.title)
    }

    val transactions = saldo?.transacoesUltimos7Dias.orEmpty()
    if (saldo == null && isLoading) {
        Spacer(modifier = Modifier.height(UnbSpacing.lg))
        SkeletonBox(
            modifier = Modifier
                .width(140.dp)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(modifier = Modifier.height(UnbSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                InnerTile(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SkeletonBox(
                                modifier = Modifier
                                    .fillMaxWidth(0.62f)
                                    .height(14.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = colors.border,
                            )
                            SkeletonBox(
                                modifier = Modifier
                                    .width(104.dp)
                                    .height(11.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = colors.border,
                            )
                        }
                        Spacer(modifier = Modifier.width(UnbSpacing.sm))
                        SkeletonBox(
                            modifier = Modifier
                                .width(62.dp)
                                .height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = colors.border,
                        )
                    }
                }
            }
        }
    } else if (transactions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(UnbSpacing.lg))
        Text(
            text = "TRANSAÇÕES RECENTES",
            style = typography.overline,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(UnbSpacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            transactions.take(6).forEach { tx ->
                InnerTile(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.descricao,
                                style = typography.title,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = tx.dataHoraTexto,
                                style = typography.label,
                                color = colors.muted,
                            )
                        }
                        Spacer(modifier = Modifier.width(UnbSpacing.sm))
                        Text(
                            text = Formatters.formatCentavosBrl(tx.valorCentavos),
                            style = typography.bodyTabular.copy(fontWeight = FontWeight.SemiBold),
                            color = if (tx.valorCentavos < 0) colors.ink else colors.tagVegInk,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuQrBackFace(
    carteirinha: CarteirinhaEstudantil?,
    boostBrightness: Boolean,
    isLoading: Boolean,
    error: com.unbpocket.app.ui.common.UiErrorBanner?,
    onFlipToBalance: () -> Unit,
    onToggleBrightness: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "PASSE DA CATRACA UNB",
                style = typography.overline,
                color = colors.muted,
            )
            Text(
                text = "QR Code Identificação",
                style = typography.title,
                color = colors.ink,
            )
        }
        IconButtonTile(
            active = boostBrightness,
            onClick = onToggleBrightness,
        )
    }

    if (error != null) {
        Spacer(modifier = Modifier.height(UnbSpacing.sm))
        ErrorBanner(
            banner = error,
            onRetry = if (error.canRetry) onRefresh else null,
            onLoginClick = if (error.requiresLogin) onOpenLogin else null,
            onDismiss = onDismissError,
        )
    }

    Spacer(modifier = Modifier.height(UnbSpacing.md))

    // Pure white #FFFFFF canvas with 16dp quiet margin so optical scanners read cleanly in both themes.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = UnbShapes.card,
        color = colors.qrCanvas,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UnbSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                carteirinha == null && isLoading -> {
                    SkeletonBox(
                        modifier = Modifier.size(220.dp),
                        shape = UnbShapes.inner,
                        color = colors.qrInk.copy(alpha = 0.14f),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .width(156.dp)
                            .height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = colors.qrInk.copy(alpha = 0.14f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .width(112.dp)
                            .height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = colors.qrInk.copy(alpha = 0.14f),
                    )
                }

                carteirinha == null -> {
                    Text(
                        text = "QR Code ainda não carregado.",
                        style = typography.body,
                        color = colors.qrInk,
                    )
                    Spacer(modifier = Modifier.height(UnbSpacing.sm))
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.qrInk,
                            contentColor = colors.qrCanvas,
                        ),
                    ) {
                        Text(text = "Carregar QR Code", style = typography.label)
                    }
                }

                else -> {
                    LocalBitmapImage(
                        localPath = carteirinha.qrImageLocalPath,
                        contentDescription = "QR Code da carteirinha estudantil UnB",
                        modifier = Modifier.size(220.dp),
                        contentScale = ContentScale.Fit,
                        fallback = {
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .background(Color(0xFFF3F3F2), UnbShapes.inner),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Imagem QR indisponível offline.\nToque em atualizar.",
                                    style = typography.label,
                                    color = colors.qrInk,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(UnbSpacing.md),
                                )
                            }
                        },
                    )
                    if (carteirinha.qrPayload16Digitos.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = carteirinha.qrPayload16Digitos,
                            style = typography.mono,
                            color = colors.qrInk,
                        )
                    }
                    if (carteirinha.validadeTexto.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Validade: ${carteirinha.validadeTexto}",
                            style = typography.label,
                            color = colors.qrInk.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(UnbSpacing.md))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
    ) {
        Button(
            onClick = onFlipToBalance,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp),
            shape = UnbShapes.inner,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surfaceRaised,
                contentColor = colors.ink,
            ),
        ) {
            Text(text = "Voltar para Saldo RU", style = typography.title)
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isLoading,
            border = BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            shape = UnbShapes.inner,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Atualizar QR Code",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun IconButtonTile(
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    Surface(
        modifier = Modifier
            .clip(UnbShapes.tag)
            .clickable(role = Role.Switch, onClick = onClick),
        shape = UnbShapes.tag,
        color = if (active) colors.pillActiveBg else colors.surfaceRaised,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.BrightnessHigh,
                contentDescription = "Brilho máximo no QR",
                tint = if (active) colors.pillActiveInk else colors.muted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Brilho",
                style = UnbTheme.typography.label,
                color = if (active) colors.pillActiveInk else colors.muted,
            )
        }
    }
}
