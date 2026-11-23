package com.unbpocket.app.ui.discente

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unbpocket.app.ui.common.FileOpener
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.components.BackHeaderButton
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.EditorialHeader
import com.unbpocket.app.ui.components.ErrorBanner
import com.unbpocket.app.ui.components.InfoCalloutRow
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.theme.UnbShapes
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.viewmodel.DiscenteUiState
import com.unbpocket.core.model.discente.DocumentoOficialEmitido
import com.unbpocket.core.model.discente.TipoDocumentoOficial

@Composable
fun DocumentosScreen(
    uiState: DiscenteUiState,
    onBack: () -> Unit,
    onEmitirDocumento: (TipoDocumentoOficial) -> Unit,
    onConsumeEmitted: () -> Unit,
    onOpenLogin: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val context = LocalContext.current

    LaunchedEffect(uiState.lastEmittedDocumentoEvent) {
        val doc = uiState.lastEmittedDocumentoEvent ?: return@LaunchedEffect
        FileOpener.openLocalFile(
            context = context,
            localPath = doc.localFilePath,
            mimeType = doc.mimeType,
        )
        onConsumeEmitted()
    }

    // Carteirinha Estudantil PDF and Cardapio PDF are intentionally excluded from document actions.
    val availableTypes = remember {
        listOf(
            TipoDocumentoOficial.ATESTADO_MATRICULA,
            TipoDocumentoOficial.DECLARACAO_VINCULO,
            TipoDocumentoOficial.HISTORICO_ESCOLAR,
            TipoDocumentoOficial.PASSE_LIVRE_ESTUDANTIL,
        )
    }
    val visibleHistory = remember(uiState.documentosEmitidos) {
        uiState.documentosEmitidos.filter {
            it.tipo != TipoDocumentoOficial.CARTEIRINHA_ESTUDANTIL
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        EditorialHeader(
            title = "Documentos Digitais",
            navigationIcon = {
                BackHeaderButton(onClick = onBack, contentDescription = "Voltar ao perfil")
            },
            subtitle = {
                Text(
                    text = "Emissão oficial via SIGAA (PDF)",
                    style = typography.label,
                    color = colors.muted,
                )
            },
        )

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
            uiState.documentosError?.let { err ->
                item(key = "docs_error") {
                    ErrorBanner(
                        banner = err,
                        onLoginClick = if (err.requiresLogin) onOpenLogin else null,
                        onDismiss = onDismissError,
                    )
                }
            }

            item(key = "docs_info") {
                InfoCalloutRow(
                    text = "Os documentos são emitidos em modo somente leitura direto do SIGAA e abertos com o leitor de PDF do sistema.",
                )
            }

            item(key = "emitir_overline") {
                Text(
                    text = "EMITIR NOVO DOCUMENTO",
                    style = typography.overline,
                    color = colors.muted,
                )
            }

            items(
                items = availableTypes,
                key = { it.name },
            ) { tipo ->
                val isEmittingThis = uiState.emittingDocumentoTipo == tipo
                val anyEmitting = uiState.emittingDocumentoTipo != null

                EditorialCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UnbSpacing.md),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tipo.displayName,
                                style = typography.title,
                                color = colors.ink,
                            )
                            Text(
                                text = documentoSubtitle(tipo),
                                style = typography.label,
                                color = colors.muted,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(UnbSpacing.md))

                    Button(
                        onClick = { onEmitirDocumento(tipo) },
                        enabled = !anyEmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = UnbShapes.inner,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.pillActiveBg,
                            contentColor = colors.pillActiveInk,
                            disabledContainerColor = colors.surfaceRaised,
                            disabledContentColor = colors.muted,
                        ),
                    ) {
                        if (isEmittingThis) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.ink,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Emitindo PDF...", style = typography.label)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Emitir e abrir", style = typography.label)
                        }
                    }
                }
            }

            if (visibleHistory.isNotEmpty()) {
                item(key = "history_overline") {
                    Text(
                        text = "DOCUMENTOS SALVOS (${visibleHistory.size})",
                        style = typography.overline,
                        color = colors.muted,
                    )
                }

                items(
                    items = visibleHistory,
                    key = { "${it.tipo.name}_${it.emitidoEmEpochMillis}_${it.localFilePath}" },
                ) { doc ->
                    SavedDocumentCard(
                        doc = doc,
                        onOpen = {
                            FileOpener.openLocalFile(
                                context = context,
                                localPath = doc.localFilePath,
                                mimeType = doc.mimeType,
                            )
                        },
                        onShare = {
                            FileOpener.shareLocalFile(
                                context = context,
                                localPath = doc.localFilePath,
                                mimeType = doc.mimeType,
                                title = doc.titulo,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedDocumentCard(
    doc: DocumentoOficialEmitido,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusTag(text = doc.tipo.displayName, tone = StatusTagTone.INFO)
            Text(
                text = Formatters.formatSyncTimestamp(doc.emitidoEmEpochMillis),
                style = typography.label,
                color = colors.muted,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = doc.titulo,
            style = typography.title,
            color = colors.ink,
        )

        doc.codigoVerificacao?.takeIf { it.isNotBlank() }?.let { code ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Código: $code",
                style = typography.mono,
                color = colors.muted,
            )
        }

        Spacer(modifier = Modifier.height(UnbSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
        ) {
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
                shape = UnbShapes.inner,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceRaised,
                    contentColor = colors.ink,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Abrir", style = typography.label)
            }

            OutlinedButton(
                onClick = onShare,
                border = BorderStroke(1.dp, colors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                shape = UnbShapes.inner,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Compartilhar", style = typography.label)
            }
        }
    }
}

private fun documentoSubtitle(tipo: TipoDocumentoOficial): String = when (tipo) {
    TipoDocumentoOficial.ATESTADO_MATRICULA -> "Comprovante das disciplinas matriculadas no semestre"
    TipoDocumentoOficial.DECLARACAO_VINCULO -> "Declaração regular de vínculo estudantil com a UnB"
    TipoDocumentoOficial.HISTORICO_ESCOLAR -> "Histórico acadêmico oficial completo"
    TipoDocumentoOficial.PASSE_LIVRE_ESTUDANTIL -> "Comprovante para cadastro ou renovação de passe livre"
    TipoDocumentoOficial.CARTEIRINHA_ESTUDANTIL -> ""
}
