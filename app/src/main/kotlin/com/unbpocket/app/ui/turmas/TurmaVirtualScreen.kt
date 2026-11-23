package com.unbpocket.app.ui.turmas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Grading
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unbpocket.app.ui.common.FileOpener
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.components.BackHeaderButton
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.EditorialHeader
import com.unbpocket.app.ui.components.EmptyStateCard
import com.unbpocket.app.ui.components.ErrorBanner
import com.unbpocket.app.ui.components.HairlineDivider
import com.unbpocket.app.ui.components.HeaderActionIconButton
import com.unbpocket.app.ui.components.InfoCalloutRow
import com.unbpocket.app.ui.components.InnerTile
import com.unbpocket.app.ui.components.OfflineOriginStrip
import com.unbpocket.app.ui.components.SegmentedPillBar
import com.unbpocket.app.ui.components.SkeletonCard
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.viewmodel.TurmaDetailTab
import com.unbpocket.app.ui.viewmodel.TurmaVirtualUiState
import com.unbpocket.core.model.turmas.AnexoArquivoSigaa
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.MapaFrequenciaTurma
import com.unbpocket.core.model.turmas.MaterialAulaAva
import com.unbpocket.core.model.turmas.NotasParciaisTurma
import com.unbpocket.core.model.turmas.NoticiaTurmaAva
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.model.turmas.TopicoAulaAva
import com.unbpocket.core.model.turmas.TurmaDetalheAva

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurmaVirtualScreen(
    uiState: TurmaVirtualUiState,
    onBack: () -> Unit,
    onSelectTab: (TurmaDetailTab) -> Unit,
    onRefreshTab: () -> Unit,
    onToggleNoticia: (String) -> Unit,
    onDownloadMaterial: (String) -> Unit,
    onDownloadAnexo: (String) -> Unit,
    onConsumeDownloadedFile: () -> Unit,
    onOpenLogin: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val context = LocalContext.current

    LaunchedEffect(uiState.downloadedFileEvent) {
        val downloaded = uiState.downloadedFileEvent ?: return@LaunchedEffect
        FileOpener.openLocalFile(
            context = context,
            localPath = downloaded.localFilePath,
            mimeType = downloaded.mimeType,
        )
        onConsumeDownloadedFile()
    }

    val detalhe = uiState.detalhe?.value
    val title = detalhe?.nomeDisciplina
        ?: uiState.summary?.nomeDisciplina
        ?: "Turma Virtual"

    val code = detalhe?.codigoDisciplina ?: uiState.summary?.codigoDisciplina.orEmpty()
    val periodo = detalhe?.periodoTurmaTexto
        ?: uiState.summary?.semestre
        ?: ""
    val subtitle = listOfNotNull(
        code.takeIf { it.isNotBlank() },
        uiState.summary?.numeroTurma?.takeIf { it.isNotBlank() }?.let { "Turma $it" },
        periodo.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        EditorialHeader(
            title = title,
            navigationIcon = {
                BackHeaderButton(onClick = onBack, contentDescription = "Voltar para turmas")
            },
            subtitle = if (subtitle.isNotBlank()) {
                {
                    Text(
                        text = subtitle,
                        style = typography.label,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                null
            },
            actions = {
                HeaderActionIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Atualizar aba",
                    isLoading = uiState.isRefreshingDetail,
                    onClick = onRefreshTab,
                )
            },
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshingDetail,
            onRefresh = onRefreshTab,
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
                if (uiState.detailError != null) {
                    item(key = "detail_error") {
                        ErrorBanner(
                            banner = uiState.detailError,
                            onRetry = if (uiState.detailError.canRetry) onRefreshTab else null,
                            onLoginClick = if (uiState.detailError.requiresLogin) onOpenLogin else null,
                            onDismiss = onDismissError,
                        )
                    }
                }

                item(key = "detail_tabs") {
                    SegmentedPillBar(
                        options = listOf("Tópicos", "Notícias", "Faltas", "Notas"),
                        selectedIndex = uiState.selectedDetailTab.ordinal,
                        onSelect = { idx -> onSelectTab(TurmaDetailTab.entries[idx]) },
                    )
                }

                when (uiState.selectedDetailTab) {
                    TurmaDetailTab.TOPICOS -> {
                        if (detalhe == null && uiState.isRefreshingDetail) {
                            item(key = "topicos_skeleton") { SkeletonCard(lineCount = 5) }
                        } else if (detalhe == null) {
                            item(key = "topicos_empty_data") {
                                EmptyStateCard(
                                    icon = Icons.Rounded.Book,
                                    title = "Conteúdo da turma não carregado",
                                    body = "Toque abaixo para buscar tópicos de aula e arquivos do SIGAA.",
                                    actionLabel = "Carregar tópicos",
                                    onAction = onRefreshTab,
                                )
                            }
                        } else {
                            item(key = "course_progress") {
                                CourseProgressCard(detalhe = detalhe)
                            }
                            if (detalhe.topicosAula.isEmpty()) {
                                item(key = "topicos_list_empty") {
                                    EmptyStateCard(
                                        icon = Icons.Rounded.Book,
                                        title = "Nenhum tópico cadastrado",
                                        body = "O docente ainda não publicou tópicos de aula ou materiais nesta turma.",
                                    )
                                }
                            } else {
                                items(
                                    items = detalhe.topicosAula,
                                    key = { "topic_${it.index}_${it.titulo}" },
                                ) { topico ->
                                    TopicoCard(
                                        topico = topico,
                                        downloadingItemId = uiState.downloadingItemId,
                                        onDownloadMaterial = onDownloadMaterial,
                                    )
                                }
                            }
                        }
                    }

                    TurmaDetailTab.NOTICIAS -> {
                        if (detalhe == null && uiState.isRefreshingDetail) {
                            item(key = "noticias_skeleton") { SkeletonCard(lineCount = 5) }
                        } else if (detalhe == null || (detalhe.ultimaNoticia == null && detalhe.historicoNoticiasResumo.isEmpty())) {
                            item(key = "noticias_empty") {
                                EmptyStateCard(
                                    icon = Icons.Rounded.NotificationsNone,
                                    title = "Sem notícias na turma",
                                    body = "Nenhum comunicado foi publicado pelos docentes nesta turma até o momento.",
                                    actionLabel = "Atualizar",
                                    onAction = onRefreshTab,
                                )
                            }
                        } else {
                            detalhe.ultimaNoticia?.let { latest ->
                                item(key = "latest_noticia_${latest.idNoticia}") {
                                    NoticiaFullCard(
                                        badgeLabel = "ÚLTIMA NOTÍCIA",
                                        noticia = latest,
                                        downloadingItemId = uiState.downloadingItemId,
                                        onDownloadAnexo = onDownloadAnexo,
                                    )
                                }
                            }

                            if (detalhe.historicoNoticiasResumo.isNotEmpty()) {
                                item(key = "historico_overline") {
                                    Text(
                                        text = "HISTÓRICO DE COMUNICADOS",
                                        style = typography.overline,
                                        color = colors.muted,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }

                                items(
                                    items = detalhe.historicoNoticiasResumo,
                                    key = { it.idNoticia },
                                ) { resumo ->
                                    val expanded = uiState.expandedNoticiaId == resumo.idNoticia
                                    val fullNoticia = uiState.fullNoticiaById[resumo.idNoticia]
                                    val isLoadingThis = uiState.loadingNoticiaId == resumo.idNoticia

                                    EditorialCard(
                                        onClick = { onToggleNoticia(resumo.idNoticia) },
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = resumo.dataHoraTexto,
                                                style = typography.label,
                                                color = colors.muted,
                                            )
                                            if (isLoadingThis) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = colors.ink,
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = resumo.titulo,
                                            style = typography.title,
                                            color = colors.ink,
                                        )

                                        AnimatedVisibility(
                                            visible = expanded && fullNoticia != null,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            fullNoticia?.let { full ->
                                                Column(modifier = Modifier.padding(top = UnbSpacing.md)) {
                                                    HairlineDivider()
                                                    Spacer(modifier = Modifier.height(UnbSpacing.md))
                                                    NoticiaBodyContent(
                                                        noticia = full,
                                                        downloadingItemId = uiState.downloadingItemId,
                                                        onDownloadAnexo = onDownloadAnexo,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TurmaDetailTab.FREQUENCIA -> {
                        val freq = uiState.frequencia?.value
                        if (freq == null && uiState.isRefreshingDetail) {
                            item(key = "freq_skeleton") { SkeletonCard(lineCount = 5) }
                        } else if (freq == null) {
                            item(key = "freq_empty") {
                                EmptyStateCard(
                                    icon = Icons.Rounded.EventAvailable,
                                    title = "Frequência ainda não carregada",
                                    body = "Toque abaixo para consultar o mapa de presença no SIGAA.",
                                    actionLabel = "Carregar frequência",
                                    onAction = onRefreshTab,
                                )
                            }
                        } else {
                            item(key = "freq_summary") {
                                FrequenciaSummaryCard(freq = freq)
                            }

                            if (freq.registrosDiarios.isEmpty()) {
                                item(key = "freq_days_empty") {
                                    InfoCalloutRow(
                                        text = "Nenhum lançamento diário de chamada disponível ainda.",
                                    )
                                }
                            } else {
                                item(key = "freq_days_overline") {
                                    Text(
                                        text = "CHAMADAS REGISTRADAS",
                                        style = typography.overline,
                                        color = colors.muted,
                                    )
                                }
                                items(
                                    items = freq.registrosDiarios,
                                    key = { "${it.dataTexto}_${it.situacaoTexto}" },
                                ) { reg ->
                                    EditorialCard {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = reg.dataTexto,
                                                    style = typography.title,
                                                    color = colors.ink,
                                                )
                                                Text(
                                                    text = reg.situacaoTexto,
                                                    style = typography.label,
                                                    color = colors.muted,
                                                )
                                            }
                                            StatusTag(
                                                text = if (reg.presente) {
                                                    "Presente"
                                                } else {
                                                    "${reg.faltasNoDia} falta(s)"
                                                },
                                                tone = if (reg.presente) StatusTagTone.INFO else StatusTagTone.DANGER,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TurmaDetailTab.NOTAS_AVALIACOES -> {
                        val notas = uiState.notasParciais?.value
                        val avaliacoes = uiState.avaliacoes?.value.orEmpty()
                        val tarefas = uiState.tarefas?.value.orEmpty()

                        if (notas == null && uiState.avaliacoes == null && uiState.tarefas == null && uiState.isRefreshingDetail) {
                            item(key = "notas_skeleton") { SkeletonCard(lineCount = 5) }
                        } else {
                            item(key = "notas_parciais_card") {
                                NotasParciaisSectionCard(notas = notas)
                            }

                            item(key = "avaliacoes_overline") {
                                Text(
                                    text = "AVALIAÇÕES (${avaliacoes.size})",
                                    style = typography.overline,
                                    color = colors.muted,
                                )
                            }

                            if (avaliacoes.isEmpty()) {
                                item(key = "avaliacoes_empty") {
                                    InfoCalloutRow(text = "Nenhuma avaliação marcada no SIGAA.")
                                }
                            } else {
                                items(
                                    items = avaliacoes,
                                    key = { "aval_${it.dataTexto}_${it.descricao}" },
                                ) { aval ->
                                    AvaliacaoCard(aval = aval)
                                }
                            }

                            item(key = "tarefas_overline") {
                                Text(
                                    text = "TAREFAS (${tarefas.size})",
                                    style = typography.overline,
                                    color = colors.muted,
                                )
                            }

                            if (tarefas.isEmpty()) {
                                item(key = "tarefas_empty") {
                                    InfoCalloutRow(text = "Nenhuma tarefa cadastrada no AVA.")
                                }
                            } else {
                                items(
                                    items = tarefas,
                                    key = { "tarefa_${it.titulo}_${it.periodoEntrega}" },
                                ) { tarefa ->
                                    TarefaCard(tarefa = tarefa)
                                }
                            }
                        }
                    }
                }

                val activeOrigin = when (uiState.selectedDetailTab) {
                    TurmaDetailTab.TOPICOS, TurmaDetailTab.NOTICIAS -> uiState.detalhe
                    TurmaDetailTab.FREQUENCIA -> uiState.frequencia
                    TurmaDetailTab.NOTAS_AVALIACOES -> uiState.notasParciais
                }
                activeOrigin?.let { synced ->
                    item(key = "detail_origin") {
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
private fun CourseProgressCard(detalhe: TurmaDetalheAva) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val fraction = (detalhe.percentualAndamento.coerceIn(0, 100) / 100f)

    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ANDAMENTO DO CURSO",
                style = typography.overline,
                color = colors.muted,
            )
            Text(
                text = "${detalhe.aulasMinistradas}/${detalhe.aulasTotal} aulas · ${detalhe.percentualAndamento}%",
                style = typography.label,
                color = colors.ink,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceRaised),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.pillActiveBg),
            )
        }
    }
}

@Composable
private fun TopicoCard(
    topico: TopicoAulaAva,
    downloadingItemId: String?,
    onDownloadMaterial: (String) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        topico.periodoTexto?.takeIf { it.isNotBlank() }?.let { periodo ->
            StatusTag(text = periodo)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = topico.titulo,
            style = typography.titleStrong,
            color = colors.ink,
        )
        if (topico.conteudoTexto.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = topico.conteudoTexto,
                style = typography.body,
                color = colors.muted,
            )
        }

        if (topico.materiais.isNotEmpty()) {
            Spacer(modifier = Modifier.height(UnbSpacing.md))
            HairlineDivider()
            Spacer(modifier = Modifier.height(UnbSpacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topico.materiais.forEach { material ->
                    MaterialRow(
                        material = material,
                        isDownloading = downloadingItemId == material.materialId,
                        onClick = { onDownloadMaterial(material.materialId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(
    material: MaterialAulaAva,
    isDownloading: Boolean,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    InnerTile(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!isDownloading) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Text(
                        text = material.nome,
                        style = typography.title,
                        color = colors.ink,
                    )
                    if (material.tipoExtensao.isNotBlank()) {
                        Text(
                            text = material.tipoExtensao.uppercase(),
                            style = typography.mono,
                            color = colors.muted,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.ink,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Baixar ${material.nome}",
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun NoticiaFullCard(
    badgeLabel: String,
    noticia: NoticiaTurmaAva,
    downloadingItemId: String?,
    onDownloadAnexo: (String) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusTag(text = badgeLabel, tone = StatusTagTone.INVERTED)
            if (noticia.dataHoraTexto.isNotBlank()) {
                Text(
                    text = noticia.dataHoraTexto,
                    style = typography.label,
                    color = colors.muted,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = noticia.titulo,
            style = typography.titleStrong,
            color = colors.ink,
        )
        Spacer(modifier = Modifier.height(8.dp))
        NoticiaBodyContent(
            noticia = noticia,
            downloadingItemId = downloadingItemId,
            onDownloadAnexo = onDownloadAnexo,
        )
    }
}

@Composable
private fun NoticiaBodyContent(
    noticia: NoticiaTurmaAva,
    downloadingItemId: String?,
    onDownloadAnexo: (String) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val body = remember(noticia.conteudoTexto, noticia.conteudoHtml) {
        noticia.conteudoTexto.ifBlank { Formatters.htmlToPlainText(noticia.conteudoHtml) }
    }

    noticia.autor?.takeIf { it.isNotBlank() }?.let { autor ->
        Text(
            text = "Por $autor",
            style = typography.label,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(6.dp))
    }

    if (body.isNotBlank()) {
        Text(
            text = body,
            style = typography.body,
            color = colors.ink,
        )
    }

    if (noticia.anexos.isNotEmpty()) {
        Spacer(modifier = Modifier.height(UnbSpacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            noticia.anexos.forEach { anexo ->
                AnexoRow(
                    anexo = anexo,
                    isDownloading = downloadingItemId == anexo.id,
                    onClick = { onDownloadAnexo(anexo.id) },
                )
            }
        }
    }
}

@Composable
private fun AnexoRow(
    anexo: AnexoArquivoSigaa,
    isDownloading: Boolean,
    onClick: () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    InnerTile(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!isDownloading) onClick else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = anexo.nomeArquivo,
                style = typography.label,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.ink,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Baixar ${anexo.nomeArquivo}",
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FrequenciaSummaryCard(freq: MapaFrequenciaTurma) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        if (freq.emRiscoReprovacaoPorFalta) {
            StatusTag(
                text = "Atenção: limite de faltas próximo ou atingido",
                tone = StatusTagTone.DANGER,
            )
            Spacer(modifier = Modifier.height(UnbSpacing.md))
        }
        Text(
            text = "RESUMO DE FREQUÊNCIA",
            style = typography.overline,
            color = colors.muted,
        )
        Spacer(modifier = Modifier.height(UnbSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InnerTile(modifier = Modifier.weight(1f)) {
                Text(text = "PRESENÇA", style = typography.overline, color = colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${freq.percentualSobreRegistradas}%",
                    style = typography.metricSerif,
                    color = colors.ink,
                )
                Text(
                    text = "${freq.presencasRegistradas}/${freq.aulasComRegistro} aulas",
                    style = typography.label,
                    color = colors.muted,
                )
            }
            InnerTile(modifier = Modifier.weight(1f)) {
                Text(text = "FALTAS", style = typography.overline, color = colors.muted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${freq.faltasComputadas}/${freq.limiteMaximoFaltas}",
                    style = typography.metricSerif,
                    color = if (freq.emRiscoReprovacaoPorFalta) colors.dangerText else colors.ink,
                )
                Text(
                    text = "Restam ${freq.faltasRestantes}",
                    style = typography.label,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun NotasParciaisSectionCard(notas: NotasParciaisTurma?) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Grading,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "NOTAS PARCIAIS",
                style = typography.overline,
                color = colors.muted,
            )
        }
        Spacer(modifier = Modifier.height(UnbSpacing.md))

        if (notas == null) {
            Text(
                text = "Toque em atualizar para buscar notas parciais desta turma.",
                style = typography.body,
                color = colors.muted,
            )
        } else if (!notas.notasDisponiveis) {
            InfoCalloutRow(
                text = notas.mensagemAviso?.takeIf { it.isNotBlank() }
                    ?: "Notas parciais ainda não lançadas pelo docente no SIGAA.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notas.unidades.forEach { unidade ->
                    InnerTile(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = unidade.nome,
                                style = typography.title,
                                color = colors.ink,
                            )
                            Text(
                                text = unidade.valor?.takeIf { it.isNotBlank() } ?: "-",
                                style = typography.bodyTabular.copy(fontWeight = FontWeight.Bold),
                                color = colors.ink,
                            )
                        }
                    }
                }

                if (!notas.mediaFinal.isNullOrBlank() || !notas.situacao.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Média / Situação",
                            style = typography.label,
                            color = colors.muted,
                        )
                        Text(
                            text = listOfNotNull(
                                notas.mediaFinal?.takeIf { it.isNotBlank() },
                                notas.situacao?.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = typography.titleStrong,
                            color = colors.ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvaliacaoCard(aval: AvaliacaoTurma) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusTag(
                text = listOfNotNull(aval.dataTexto, aval.horaTexto)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                tone = StatusTagTone.WARNING,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = aval.descricao,
            style = typography.title,
            color = colors.ink,
        )
    }
}

@Composable
private fun TarefaCard(tarefa: TarefaTurma) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard {
        Text(
            text = tarefa.titulo,
            style = typography.title,
            color = colors.ink,
        )
        if (tarefa.periodoEntrega.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Entrega: ${tarefa.periodoEntrega}",
                style = typography.label,
                color = colors.muted,
            )
        }
        val tags = buildList {
            tarefa.notaMaxima?.takeIf { it.isNotBlank() }?.let { add("Máx. $it") }
            if (tarefa.emGrupo) add("Em grupo")
            if (tarefa.possuiArquivo) add("Com arquivo")
        }
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag -> StatusTag(text = tag) }
            }
        }
    }
}
