package com.unbpocket.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.unbpocket.app.ui.components.AnimatedSectionVisibility
import com.unbpocket.app.ui.components.BackHeaderButton
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.EditorialHeader
import com.unbpocket.app.ui.components.ErrorBanner
import com.unbpocket.app.ui.components.HairlineDivider
import com.unbpocket.app.ui.components.InfoCalloutRow
import com.unbpocket.app.ui.components.InnerTile
import com.unbpocket.app.ui.components.PreloadStatusBanner
import com.unbpocket.app.ui.components.SkeletonInlineBar
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.theme.ThemeMode
import com.unbpocket.app.ui.theme.UnbShapes
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.viewmodel.AuthUiState
import com.unbpocket.core.model.auth.AuthState

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onMatriculaChange: (String) -> Unit,
    onSenhaChange: (String) -> Unit,
    onSubmitLogin: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val passwordFocus = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .imePadding(),
    ) {
        EditorialHeader(
            title = "Entrar no SIGAA",
            navigationIcon = {
                BackHeaderButton(onClick = onBack, contentDescription = "Fechar login")
            },
            subtitle = {
                Text(
                    text = "Autenticação CAS · Universidade de Brasília",
                    style = typography.label,
                    color = colors.muted,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = UnbSpacing.lg, vertical = UnbSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(UnbSpacing.lg),
        ) {
            InfoCalloutRow(
                text = "Acesso estritamente somente leitura. Suas credenciais são enviadas direto ao CAS oficial da UnB e protegidas localmente pelo Android Keystore.",
            )

            AnimatedSectionVisibility(visible = uiState.loginError != null) {
                uiState.loginError?.let { error ->
                    ErrorBanner(
                        banner = error,
                        onRetry = if (error.canRetry) onSubmitLogin else null,
                        onDismiss = onDismissError,
                    )
                }
            }

            EditorialCard {
                Text(
                    text = "CREDENCIAIS INSTITUCIONAIS",
                    style = typography.overline,
                    color = colors.muted,
                )
                Spacer(modifier = Modifier.height(UnbSpacing.md))

                Text(
                    text = "Matrícula",
                    style = typography.label,
                    color = colors.ink,
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.matriculaInput,
                    onValueChange = onMatriculaChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmittingLogin,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Ex.: 231012345",
                            style = typography.body,
                            color = colors.muted,
                        )
                    },
                    textStyle = typography.bodyTabular.copy(color = colors.ink),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocus.requestFocus() },
                    ),
                    shape = UnbShapes.inner,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.ink,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surfaceRaised,
                        unfocusedContainerColor = colors.surfaceRaised,
                        cursorColor = colors.ink,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                    ),
                )

                Spacer(modifier = Modifier.height(UnbSpacing.md))

                Text(
                    text = "Senha do SIGAA / CAS",
                    style = typography.label,
                    color = colors.ink,
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.senhaInput,
                    onValueChange = onSenhaChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus),
                    enabled = !uiState.isSubmittingLogin,
                    singleLine = true,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha",
                                tint = colors.muted,
                            )
                        }
                    },
                    textStyle = typography.body.copy(color = colors.ink),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onSubmitLogin() },
                    ),
                    shape = UnbShapes.inner,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.ink,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surfaceRaised,
                        unfocusedContainerColor = colors.surfaceRaised,
                        cursorColor = colors.ink,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                    ),
                )

                Spacer(modifier = Modifier.height(UnbSpacing.xl))

                Button(
                    onClick = onSubmitLogin,
                    enabled = !uiState.isSubmittingLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    shape = UnbShapes.inner,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.pillActiveBg,
                        contentColor = colors.pillActiveInk,
                        disabledContainerColor = colors.surfaceRaised,
                        disabledContentColor = colors.muted,
                    ),
                ) {
                    if (uiState.isSubmittingLogin) {
                        SkeletonInlineBar(
                            width = 24.dp,
                            height = 7.dp,
                            color = colors.muted,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.syncStatusMessage ?: "Autenticando no CAS...",
                            style = typography.title,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Conectar conta SIGAA", style = typography.title)
                    }
                }
            }

            if (uiState.isSubmittingLogin) {
                PreloadStatusBanner(progress = uiState.preloadProgress)
            }

            Text(
                text = "O Cardápio semanal e a tabela de preços do RU continuam acessíveis sem login.",
                style = typography.label,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountQuickSheet(
    authUiState: AuthUiState,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenLogin: () -> Unit,
    onSyncAll: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLogoutDialog by remember { mutableStateOf(false) }

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
            Text(
                text = "Conta & Aparência",
                style = typography.displayEditorial,
                color = colors.ink,
            )

            when (val auth = authUiState.authState) {
                is AuthState.Authenticated -> {
                    InnerTile(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = colors.border,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(UnbSpacing.md),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(colors.pillActiveBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = colors.pillActiveInk,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Matrícula ${auth.matricula}",
                                        style = typography.titleStrong,
                                        color = colors.ink,
                                    )
                                    Text(
                                        text = "Sessão SIGAA ativa",
                                        style = typography.label,
                                        color = colors.muted,
                                    )
                                }
                            }
                            StatusTag(text = "ATIVO", tone = StatusTagTone.INFO)
                        }
                    }

                    PreloadStatusBanner(
                        progress = authUiState.preloadProgress,
                        containerColor = colors.surfaceRaised,
                        trackColor = colors.border,
                    )

                    SheetActionRow(
                        icon = Icons.Rounded.Sync,
                        title = if (authUiState.isSyncingAll) "Sincronizando..." else "Sincronizar dados acadêmicos",
                        subtitle = "Turmas, avisos, perfil e saldo RU",
                        enabled = !authUiState.isSyncingAll,
                        onClick = {
                            onSyncAll()
                            onDismiss()
                        },
                    )
                }

                is AuthState.Reauthenticating -> {
                    InnerTile(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = colors.border,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.md),
                        ) {
                            SkeletonInlineBar(
                                width = 22.dp,
                                height = 8.dp,
                                color = colors.muted,
                            )
                            Column {
                                Text(
                                    text = "Renovando sessão CAS",
                                    style = typography.title,
                                    color = colors.ink,
                                )
                                Text(
                                    text = "Matrícula ${auth.matricula}",
                                    style = typography.label,
                                    color = colors.muted,
                                )
                            }
                        }
                    }
                }

                AuthState.Unauthenticated -> {
                    InnerTile(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = colors.border,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(UnbSpacing.md)) {
                            Text(
                                text = "Conta SIGAA não conectada",
                                style = typography.title,
                                color = colors.ink,
                            )
                            Text(
                                text = "Conecte sua matrícula UnB para sincronizar horários de turmas, saldo RU, carteirinha e boletim.",
                                style = typography.body,
                                color = colors.muted,
                            )
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenLogin()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                shape = UnbShapes.inner,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.pillActiveBg,
                                    contentColor = colors.pillActiveInk,
                                ),
                            ) {
                                Text(text = "Entrar com CAS / SIGAA", style = typography.title)
                            }
                        }
                    }
                }
            }

            HairlineDivider()

            ThemeModeSelectorTile(
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode,
            )

            if (authUiState.authState is AuthState.Authenticated) {
                SheetActionRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "Desconectar conta",
                    subtitle = "Remove credenciais e cache SIGAA",
                    isDestructive = true,
                    onClick = { showLogoutDialog = true },
                )
            }

            Spacer(modifier = Modifier.height(UnbSpacing.sm))
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
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = "Suas credenciais salvas e o cache local do SIGAA serão removidos deste dispositivo.",
                    style = typography.body,
                    color = colors.muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onDismiss()
                        onLogout()
                    },
                ) {
                    Text(
                        text = "Desconectar",
                        style = typography.title,
                        color = colors.dangerText,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = "Cancelar",
                        style = typography.title,
                        color = colors.ink,
                    )
                }
            },
        )
    }
}

@Composable
private fun ThemeModeSelectorTile(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val options = remember {
        listOf(
            Triple(ThemeMode.SYSTEM, "Sistema", Icons.Rounded.BrightnessAuto),
            Triple(ThemeMode.LIGHT, "Claro", Icons.Rounded.LightMode),
            Triple(ThemeMode.DARK, "Escuro", Icons.Rounded.DarkMode),
        )
    }

    InnerTile(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Aparência",
                    style = typography.title,
                    color = colors.ink,
                )
                Text(
                    text = "Escolha o tema visual do aplicativo",
                    style = typography.label,
                    color = colors.muted,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(UnbShapes.capsule)
                    .background(colors.ground)
                    .border(width = 1.dp, color = colors.border, shape = UnbShapes.capsule)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { (mode, label, icon) ->
                    val isSelected = themeMode == mode
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(UnbShapes.capsule)
                            .background(if (isSelected) colors.pillActiveBg else colors.ground)
                            .semantics { selected = isSelected }
                            .clickable(role = Role.Tab) { onSelectThemeMode(mode) }
                            .padding(horizontal = UnbSpacing.sm, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) colors.pillActiveInk else colors.muted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = typography.label,
                            color = if (isSelected) colors.pillActiveInk else colors.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val tint = if (isDestructive) colors.dangerText else colors.ink

    InnerTile(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (enabled) onClick else null,
        contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.title,
                    color = tint,
                )
                Text(
                    text = subtitle,
                    style = typography.label,
                    color = colors.muted,
                )
            }
        }
    }
}
