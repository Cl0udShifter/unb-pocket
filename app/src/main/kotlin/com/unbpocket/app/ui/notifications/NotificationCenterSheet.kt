package com.unbpocket.app.ui.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unbpocket.app.ui.ru.RuNotificationScheduleGrid
import com.unbpocket.app.ui.components.EditorialCard
import com.unbpocket.app.ui.components.HairlineDivider
import com.unbpocket.app.ui.components.InnerTile
import com.unbpocket.app.ui.components.StatusTag
import com.unbpocket.app.ui.components.StatusTagTone
import com.unbpocket.app.ui.theme.UnbShapes
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.core.model.notification.AulaLeadMinutes
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.model.notification.SigaaSyncIntervalHours
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.model.turmas.TurmaResumo

enum class NotificationCenterSection {
    RU,
    TURMAS,
    DISCENTE,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationCenterBottomSheet(
    initialSection: NotificationCenterSection?,
    ruNotificacoes: RuNotificacaoConfig,
    sigaaNotificacoes: SigaaNotificacaoConfig,
    turmas: List<TurmaResumo>,
    onSetRuHabilitadas: (Boolean) -> Unit,
    onToggleRuSlot: (DiaSemanaRu, TipoRefeicao) -> Unit,
    onToggleRuDay: (DiaSemanaRu) -> Unit,
    onToggleSigaaTurmasSecao: (Boolean) -> Unit,
    onSetAulasLembrete: (Boolean) -> Unit,
    onSetAntecedenciaAulaMinutos: (Int) -> Unit,
    onSetAvisosEMateriaisNovos: (Boolean) -> Unit,
    onSetProvasETarefasLembrete: (Boolean) -> Unit,
    onSetNotasParciaisAlteradas: (Boolean) -> Unit,
    onSetRiscoFaltaAlerta: (Boolean) -> Unit,
    onToggleTurmaSilenciada: (TurmaResumo) -> Unit,
    onToggleSigaaDiscenteSecao: (Boolean) -> Unit,
    onSetSaldoRuBaixoAlerta: (Boolean) -> Unit,
    onSetLimiteRefeicoesRuBaixo: (Int) -> Unit,
    onSetExtratoRuMovimentacaoAlerta: (Boolean) -> Unit,
    onSetBoletimAlteradoAlerta: (Boolean) -> Unit,
    onSetIndicesAlteradosAlerta: (Boolean) -> Unit,
    onSetSyncInterval: (SigaaSyncIntervalHours) -> Unit,
    onSetSomenteWifi: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onResyncSchedules: () -> Unit = {},
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val context = LocalContext.current

    var expandedRu by remember(initialSection) {
        mutableStateOf(
            initialSection == NotificationCenterSection.RU ||
                (initialSection == null && ruNotificacoes.habilitado),
        )
    }
    var expandedTurmas by remember(initialSection) {
        mutableStateOf(
            initialSection == NotificationCenterSection.TURMAS ||
                (initialSection == null && (sigaaNotificacoes.isTurmasAtivo || !sigaaNotificacoes.habilitado)),
        )
    }
    var expandedDiscente by remember(initialSection) {
        mutableStateOf(
            initialSection == NotificationCenterSection.DISCENTE ||
                (initialSection == null && sigaaNotificacoes.isDiscenteAtivo),
        )
    }

    var permissionDeniedWarning by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationManager = remember(context) {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }
    val alarmManager = remember(context) {
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    fun checkRuntimePermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    fun checkExactAlarms(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() ?: true
    } else {
        true
    }

    fun checkBatteryUnrestricted(): Boolean =
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    var hasRuntimePermission by remember { mutableStateOf(checkRuntimePermission()) }
    var systemNotificationsEnabled by remember {
        mutableStateOf(notificationManager?.areNotificationsEnabled() ?: true)
    }
    var canScheduleExactAlarms by remember { mutableStateOf(checkExactAlarms()) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(checkBatteryUnrestricted()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val nextRuntime = checkRuntimePermission()
        val nextSystem = notificationManager?.areNotificationsEnabled() ?: true
        val nextExact = checkExactAlarms()
        val nextBattery = checkBatteryUnrestricted()
        val regainedCapability = (!canScheduleExactAlarms && nextExact) ||
            (!hasRuntimePermission && nextRuntime) ||
            (!systemNotificationsEnabled && nextSystem) ||
            (!isIgnoringBatteryOptimizations && nextBattery)
        hasRuntimePermission = nextRuntime
        systemNotificationsEnabled = nextSystem
        canScheduleExactAlarms = nextExact
        isIgnoringBatteryOptimizations = nextBattery
        if (regainedCapability) {
            onResyncSchedules()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        hasRuntimePermission = checkRuntimePermission()
        systemNotificationsEnabled = notificationManager?.areNotificationsEnabled() ?: true
        if (granted) {
            permissionDeniedWarning = false
            action?.invoke()
            onResyncSchedules()
        } else {
            permissionDeniedWarning = true
        }
    }

    fun runWithPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                hasRuntimePermission = true
                permissionDeniedWarning = false
                action()
            } else {
                pendingPermissionAction = action
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            action()
        }
    }

    val openSystemNotificationSettings = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(fallback) } }
        Unit
    }

    val openExactAlarmSettings = {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            fallback
        }
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(fallback) } }
        Unit
    }

    val openBatteryOptimizationSettings = {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(fallback) } }
        Unit
    }

    val sheetTitle = remember(initialSection) {
        when (initialSection) {
            NotificationCenterSection.RU -> "Notificações do RU"
            NotificationCenterSection.TURMAS -> "Notificações · Turmas"
            NotificationCenterSection.DISCENTE -> "Notificações · Discente"
            null -> "Central de Notificações"
        }
    }
    val sheetSubtitle = remember(initialSection) {
        when (initialSection) {
            NotificationCenterSection.RU -> "Alertas locais 1h antes das refeições escolhidas, mesmo com o app fechado."
            NotificationCenterSection.TURMAS -> "Lembretes de aulas, avisos, materiais, provas, notas e faltas."
            NotificationCenterSection.DISCENTE -> "Alertas de saldo RU, extrato, boletim e índices acadêmicos."
            null -> "Lembretes locais e monitoramento em segundo plano com o app fechado."
        }
    }
    val isScopedToSingleScreen = initialSection != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UnbSpacing.lg, vertical = UnbSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(UnbSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = sheetTitle,
                        style = typography.displayEditorial,
                        color = colors.ink,
                    )
                    Text(
                        text = sheetSubtitle,
                        style = typography.label,
                        color = colors.muted,
                    )
                }
                Spacer(modifier = Modifier.width(UnbSpacing.sm))
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(22.dp),
                )
            }

            if (!hasRuntimePermission || !systemNotificationsEnabled || permissionDeniedWarning) {
                InnerTile(
                    containerColor = colors.warningBg,
                    borderColor = colors.warningInk.copy(alpha = 0.3f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Permissão de notificações necessária",
                            style = typography.titleStrong,
                            color = colors.warningInk,
                        )
                        Text(
                            text = "Autorize as notificações do UnB Pocket no Android para receber avisos de aulas, provas, notas e cardápio.",
                            style = typography.label,
                            color = colors.warningInk,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasRuntimePermission) {
                                TextButton(
                                    onClick = { runWithPermission {} },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "Permitir agora",
                                        style = typography.label.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.ink,
                                    )
                                }
                            }
                            TextButton(
                                onClick = openSystemNotificationSettings,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "Abrir ajustes",
                                    style = typography.label.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                )
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !canScheduleExactAlarms &&
                (ruNotificacoes.habilitado || sigaaNotificacoes.hasAnyLocalScheduleEnabled)
            ) {
                InnerTile(
                    containerColor = colors.warningBg,
                    borderColor = colors.warningInk.copy(alpha = 0.3f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Alarmes exatos desativados no Android",
                            style = typography.titleStrong,
                            color = colors.warningInk,
                        )
                        Text(
                            text = "Sem esta permissão, o sistema pode atrasar avisos do RU, aulas e provas em até 1 hora.",
                            style = typography.label,
                            color = colors.warningInk,
                        )
                        TextButton(
                            onClick = openExactAlarmSettings,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Permitir horários exatos",
                                style = typography.label.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                            )
                        }
                    }
                }
            }

            if (!isIgnoringBatteryOptimizations &&
                (ruNotificacoes.habilitado || sigaaNotificacoes.hasAnyEnabled)
            ) {
                InnerTile(
                    containerColor = colors.surfaceRaised,
                    borderColor = colors.border,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Restrição em segundo plano ativa",
                            style = typography.titleStrong,
                            color = colors.ink,
                        )
                        Text(
                            text = "A economia de bateria do sistema pode atrasar sincronizações com o app fechado. Recomendado selecionar \"Sem restrições\".",
                            style = typography.label,
                            color = colors.muted,
                        )
                        TextButton(
                            onClick = openBatteryOptimizationSettings,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Ajustar economia de bateria",
                                style = typography.label.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                            )
                        }
                    }
                }
            }

            val visibleSections = remember(initialSection) {
                when (initialSection) {
                    NotificationCenterSection.RU -> listOf(NotificationCenterSection.RU)
                    NotificationCenterSection.TURMAS -> listOf(NotificationCenterSection.TURMAS)
                    NotificationCenterSection.DISCENTE -> listOf(NotificationCenterSection.DISCENTE)
                    null -> listOf(
                        NotificationCenterSection.RU,
                        NotificationCenterSection.TURMAS,
                        NotificationCenterSection.DISCENTE,
                    )
                }
            }

            visibleSections.forEach { section ->
                when (section) {
                    NotificationCenterSection.RU -> {
                        val ruSlotsCount = ruNotificacoes.refeicoesPorDia.values.sumOf { it.size }
                        NotificationSectionCard(
                            icon = Icons.Rounded.Restaurant,
                            title = "Cardápio do RU",
                            subtitle = if (ruNotificacoes.habilitado) {
                                if (ruSlotsCount > 0) "$ruSlotsCount refeições marcadas na semana" else "Ativo · selecione horários na grade"
                            } else {
                                "Café (06h), Almoço (10h) e Jantar (16h)"
                            },
                            checked = ruNotificacoes.habilitado,
                            expanded = if (isScopedToSingleScreen) true else expandedRu,
                            collapsible = !isScopedToSingleScreen,
                            onToggleExpand = { expandedRu = !expandedRu },
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    runWithPermission {
                                        onSetRuHabilitadas(true)
                                        expandedRu = true
                                    }
                                } else {
                                    onSetRuHabilitadas(false)
                                }
                            },
                        ) {
                            RuNotificationScheduleGrid(
                                notificacoes = ruNotificacoes,
                                onToggleSlot = { dia, refeicao ->
                                    val alreadySelected = refeicao in ruNotificacoes.refeicoesPorDia[dia].orEmpty()
                                    if (!alreadySelected || !ruNotificacoes.habilitado) {
                                        runWithPermission { onToggleRuSlot(dia, refeicao) }
                                    } else {
                                        onToggleRuSlot(dia, refeicao)
                                    }
                                },
                                onToggleDay = { dia ->
                                    val allSelected = ruNotificacoes.refeicoesPorDia[dia].orEmpty().size ==
                                        TipoRefeicao.entries.size
                                    if (!allSelected || !ruNotificacoes.habilitado) {
                                        runWithPermission { onToggleRuDay(dia) }
                                    } else {
                                        onToggleRuDay(dia)
                                    }
                                },
                            )
                        }
                    }

                    NotificationCenterSection.TURMAS -> {
                        val turmasEnabledCount = listOf(
                            sigaaNotificacoes.aulasLembrete,
                            sigaaNotificacoes.avisosEMateriaisNovos,
                            sigaaNotificacoes.provasETarefasLembrete,
                            sigaaNotificacoes.notasParciaisAlteradas,
                            sigaaNotificacoes.riscoFaltaAlerta,
                        ).count { it }
                        val turmasChecked = sigaaNotificacoes.habilitado && sigaaNotificacoes.turmasHabilitado

                        NotificationSectionCard(
                            icon = Icons.Rounded.School,
                            title = "Aulas & Turmas",
                            subtitle = if (turmasChecked) {
                                "$turmasEnabledCount categorias ativas"
                            } else {
                                "Horários, avisos, materiais, provas, notas e faltas"
                            },
                            checked = turmasChecked,
                            expanded = if (isScopedToSingleScreen) true else expandedTurmas,
                            collapsible = !isScopedToSingleScreen,
                            onToggleExpand = { expandedTurmas = !expandedTurmas },
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    runWithPermission {
                                        onToggleSigaaTurmasSecao(true)
                                        expandedTurmas = true
                                    }
                                } else {
                                    onToggleSigaaTurmasSecao(false)
                                }
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                NotificationSubToggleRow(
                                    title = "Próximas aulas e sala",
                                    subtitle = "Usa a grade local em cache sem precisar de internet",
                                    checked = sigaaNotificacoes.aulasLembrete,
                                    enabled = turmasChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetAulasLembrete(true) }
                                        else onSetAulasLembrete(false)
                                    },
                                )

                                AnimatedVisibility(visible = turmasChecked && sigaaNotificacoes.aulasLembrete) {
                                    Column(
                                        modifier = Modifier.padding(start = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "ANTECEDÊNCIA DO LEMBRETE",
                                            style = typography.overline,
                                            color = colors.muted,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            AulaLeadMinutes.entries.forEach { option ->
                                                EditorialOptionChip(
                                                    label = option.label,
                                                    selected = sigaaNotificacoes.antecedenciaAulaMinutos == option.minutes,
                                                    onClick = { onSetAntecedenciaAulaMinutos(option.minutes) },
                                                )
                                            }
                                        }
                                    }
                                }

                                HairlineDivider()

                                NotificationSubToggleRow(
                                    title = "Novos avisos e materiais",
                                    subtitle = "Comunicados do professor e arquivos nos tópicos",
                                    checked = sigaaNotificacoes.avisosEMateriaisNovos,
                                    enabled = turmasChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetAvisosEMateriaisNovos(true) }
                                        else onSetAvisosEMateriaisNovos(false)
                                    },
                                )

                                HairlineDivider()

                                NotificationSubToggleRow(
                                    title = "Provas e entregas de tarefas",
                                    subtitle = "Lembra às 20h na véspera e às 07h ou 1h antes no dia",
                                    checked = sigaaNotificacoes.provasETarefasLembrete,
                                    enabled = turmasChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetProvasETarefasLembrete(true) }
                                        else onSetProvasETarefasLembrete(false)
                                    },
                                )

                                HairlineDivider()

                                NotificationSubToggleRow(
                                    title = "Notas parciais postadas",
                                    subtitle = "Lançamentos e atualizações por unidade no AVA",
                                    checked = sigaaNotificacoes.notasParciaisAlteradas,
                                    enabled = turmasChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetNotasParciaisAlteradas(true) }
                                        else onSetNotasParciaisAlteradas(false)
                                    },
                                )

                                HairlineDivider()

                                NotificationSubToggleRow(
                                    title = "Risco de reprovação por faltas",
                                    subtitle = "Alerta quando restarem poucas faltas ou ao atingir risco",
                                    checked = sigaaNotificacoes.riscoFaltaAlerta,
                                    enabled = turmasChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetRiscoFaltaAlerta(true) }
                                        else onSetRiscoFaltaAlerta(false)
                                    },
                                )

                                if (turmas.isNotEmpty()) {
                                    HairlineDivider()
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "TURMAS MONITORADAS",
                                            style = typography.overline,
                                            color = colors.muted,
                                        )
                                        Text(
                                            text = "Desmarque alguma disciplina se não quiser receber alertas dela.",
                                            style = typography.label,
                                            color = colors.muted,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            turmas.forEach { turma ->
                                                val isMuted = turma.frontEndIdTurma in sigaaNotificacoes.turmasSilenciadas ||
                                                    turma.idTurma in sigaaNotificacoes.turmasSilenciadas
                                                val shortLabel = turma.codigoDisciplina?.takeIf { it.isNotBlank() }
                                                    ?: turma.nomeDisciplina.take(18)
                                                EditorialOptionChip(
                                                    label = shortLabel,
                                                    selected = !isMuted,
                                                    showCheck = !isMuted,
                                                    onClick = { onToggleTurmaSilenciada(turma) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NotificationCenterSection.DISCENTE -> {
                        val discenteEnabledCount = listOf(
                            sigaaNotificacoes.saldoRuBaixoAlerta,
                            sigaaNotificacoes.extratoRuMovimentacaoAlerta,
                            sigaaNotificacoes.boletimAlteradoAlerta,
                            sigaaNotificacoes.indicesAlteradosAlerta,
                        ).count { it }
                        val discenteChecked = sigaaNotificacoes.habilitado && sigaaNotificacoes.discenteHabilitado

                        NotificationSectionCard(
                            icon = Icons.Rounded.Person,
                            title = "Painel Discente",
                            subtitle = if (discenteChecked) {
                                "$discenteEnabledCount alertas ativos"
                            } else {
                                "Saldo baixo RU, extrato, boletim e índices (IRA/MP)"
                            },
                            checked = discenteChecked,
                            expanded = if (isScopedToSingleScreen) true else expandedDiscente,
                            collapsible = !isScopedToSingleScreen,
                            onToggleExpand = { expandedDiscente = !expandedDiscente },
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    runWithPermission {
                                        onToggleSigaaDiscenteSecao(true)
                                        expandedDiscente = true
                                    }
                                } else {
                                    onToggleSigaaDiscenteSecao(false)
                                }
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                NotificationSubToggleRow(
                                    title = "Saldo baixo no RU",
                                    subtitle = "Avisa uma vez por queda quando atingir o limite de refeições",
                                    checked = sigaaNotificacoes.saldoRuBaixoAlerta,
                                    enabled = discenteChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetSaldoRuBaixoAlerta(true) }
                                        else onSetSaldoRuBaixoAlerta(false)
                                    },
                                )

                                AnimatedVisibility(visible = discenteChecked && sigaaNotificacoes.saldoRuBaixoAlerta) {
                                    Column(
                                        modifier = Modifier.padding(start = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "ALERTAR QUANDO RESTAREM ATÉ",
                                            style = typography.overline,
                                            color = colors.muted,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            SigaaNotificacaoConfig.LIMITE_REFEICOES_OPCOES.forEach { count ->
                                                val label = if (count == 1) "1 refeição" else "$count refeições"
                                                EditorialOptionChip(
                                                    label = label,
                                                    selected = sigaaNotificacoes.limiteRefeicoesRuBaixo == count,
                                                    onClick = { onSetLimiteRefeicoesRuBaixo(count) },
                                                )
                                            }
                                        }
                                    }
                                }

                                HairlineDivider()

                                NotificationSubToggleRow(
                                    title = "Movimentações no extrato RU",
                                    subtitle = "Novas recargas ou débitos registrados nos últimos 7 dias",
                                    checked = sigaaNotificacoes.extratoRuMovimentacaoAlerta,
                                    enabled = discenteChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetExtratoRuMovimentacaoAlerta(true) }
                                        else onSetExtratoRuMovimentacaoAlerta(false)
                                    },
                                )

                                HairlineDivider()

                                NotificationSubToggleRow(
                                    title = "Boletim atualizado",
                                    subtitle = "Notas finais, menções (SS, MS, MM...) ou situação final",
                                    checked = sigaaNotificacoes.boletimAlteradoAlerta,
                                    enabled = discenteChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetBoletimAlteradoAlerta(true) }
                                        else onSetBoletimAlteradoAlerta(false)
                                    },
                                )

                                HairlineDivider()

                                NotificationSubToggleRow(
                                    title = "Índices acadêmicos (IRA / MP)",
                                    subtitle = "Mudanças em IRA, MP ou percentual integralizado",
                                    checked = sigaaNotificacoes.indicesAlteradosAlerta,
                                    enabled = discenteChecked,
                                    onCheckedChange = { enabled ->
                                        if (enabled) runWithPermission { onSetIndicesAlteradosAlerta(true) }
                                        else onSetIndicesAlteradosAlerta(false)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (initialSection != NotificationCenterSection.RU) {
                EditorialCard(
                    contentPadding = PaddingValues(UnbSpacing.md),
                    containerColor = colors.surfaceRaised,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "SINCRONIZAÇÃO EM SEGUNDO PLANO (SIGAA)",
                            style = typography.overline,
                            color = colors.ink,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Define com que frequência o app consulta o SIGAA fechado para avisar de novidades remotas.",
                        style = typography.label,
                        color = colors.muted,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SigaaSyncIntervalHours.entries.forEach { interval ->
                            EditorialOptionChip(
                                label = interval.label,
                                selected = sigaaNotificacoes.syncIntervalHours == interval,
                                onClick = { onSetSyncInterval(interval) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    NotificationSubToggleRow(
                        title = "Sincronizar somente no Wi-Fi",
                        subtitle = "Evita consumo de dados móveis em segundo plano",
                        checked = sigaaNotificacoes.somenteWifi,
                        enabled = true,
                        onCheckedChange = onSetSomenteWifi,
                    )
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = UnbShapes.inner,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = "Concluir",
                    style = typography.titleStrong,
                    color = colors.ink,
                )
            }

            Spacer(modifier = Modifier.height(UnbSpacing.sm))
        }
    }
}

@Composable
private fun NotificationSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    expanded: Boolean,
    collapsible: Boolean = true,
    onToggleExpand: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard(
        contentPadding = PaddingValues(UnbSpacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(UnbShapes.inner)
                .then(
                    if (collapsible) {
                        Modifier.clickable(role = Role.Button, onClick = onToggleExpand)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = typography.title,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (checked) {
                        StatusTag(text = "Ativo", tone = StatusTagTone.INVERTED)
                    }
                }
                Text(
                    text = subtitle,
                    style = typography.label,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.pillActiveInk,
                    checkedTrackColor = colors.pillActiveBg,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.surface,
                    uncheckedBorderColor = colors.border,
                ),
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Recolher seção" else "Expandir seção",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(top = UnbSpacing.md)) {
                HairlineDivider()
                Spacer(modifier = Modifier.height(UnbSpacing.md))
                content()
            }
        }
    }
}

@Composable
private fun NotificationSubToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = typography.body.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) colors.ink else colors.muted,
            )
            Text(
                text = subtitle,
                style = typography.label,
                color = colors.muted,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.pillActiveInk,
                checkedTrackColor = colors.pillActiveBg,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.surface,
                uncheckedBorderColor = colors.border,
                disabledCheckedThumbColor = colors.surface,
                disabledCheckedTrackColor = colors.border,
            ),
        )
    }
}

@Composable
private fun EditorialOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showCheck: Boolean = false,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = if (showCheck && selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.pillActiveInk,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        label = {
            Text(
                text = label,
                style = typography.label.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = UnbShapes.tag,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.pillActiveBg,
            selectedLabelColor = colors.pillActiveInk,
            containerColor = colors.surface,
            labelColor = colors.muted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.border,
            selectedBorderColor = colors.pillActiveBg,
        ),
    )
}
