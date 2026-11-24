package com.unbpocket.core.data

import android.content.Context
import android.util.Log
import com.unbpocket.core.data.local.CredentialVault
import com.unbpocket.core.data.local.DownloadedFileStore
import com.unbpocket.core.data.local.FileStore
import com.unbpocket.core.data.local.PdfExtractor
import com.unbpocket.core.data.local.RuNotificationSchedulerPort
import com.unbpocket.core.data.local.RuPreferencesStore
import com.unbpocket.core.data.local.RuPrefsSource
import com.unbpocket.core.data.local.SigaaNotifPrefsSource
import com.unbpocket.core.data.local.SigaaNotificationPosterPort
import com.unbpocket.core.data.local.SigaaNotificationPreferencesStore
import com.unbpocket.core.data.local.SigaaNotificationSchedulerPort
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.data.local.SnapshotDao
import com.unbpocket.core.data.local.UnbPocketDatabase
import com.unbpocket.core.data.notification.RuMealAlarmScheduler
import com.unbpocket.core.data.notification.SigaaAlarmScheduler
import com.unbpocket.core.data.notification.SigaaNotificationCoordinator
import com.unbpocket.core.data.notification.SigaaNotificationPoster
import com.unbpocket.core.data.pdf.AndroidPdfBoxExtractor
import com.unbpocket.core.data.repository.DiscenteRepositoryImpl
import com.unbpocket.core.data.repository.RuRepositoryImpl
import com.unbpocket.core.data.repository.TurmasRepositoryImpl
import com.unbpocket.core.data.security.KeystoreCredentialVault
import com.unbpocket.core.data.session.SigaaSessionCoordinator
import com.unbpocket.core.data.worker.RuMenuAutoSyncScheduler
import com.unbpocket.core.data.worker.SigaaBackgroundSyncScheduler
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.PortalHomeSyncSummary
import com.unbpocket.core.model.common.PreloadPhase
import com.unbpocket.core.model.common.PreloadProgress
import com.unbpocket.core.model.contract.AuthRepository
import com.unbpocket.core.model.contract.DiscenteRepository
import com.unbpocket.core.model.contract.RuRepository
import com.unbpocket.core.model.contract.TurmasRepository
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.discente.DiscenteResumo
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.network.JsfPriority
import com.unbpocket.core.network.UnbHttpTransport
import com.unbpocket.core.parser.DiscentePortalParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fabrica do ponto de entrada unico do Core/Backend para a camada Android (`:app`).
 */
object UnbPocketCoreFactory {
    @Volatile
    private var sharedInstance: UnbPocketCore? = null
    private val instanceLock = Any()

    fun create(context: Context): UnbPocketCore {
        val appContext = context.applicationContext
        return sharedInstance ?: synchronized(instanceLock) {
            sharedInstance ?: buildCore(appContext).also { core ->
                sharedInstance = core
                RuMenuAutoSyncScheduler.ensureScheduled(appContext)
                RuMealAlarmScheduler.syncFromPreferencesAsync(appContext)
                SigaaAlarmScheduler.syncScheduleAsync(appContext)
                SigaaBackgroundSyncScheduler.ensureSyncedAsync(appContext)
            }
        }
    }

    internal suspend fun prepareSigaaLocalReminders(
        context: Context,
        postDueNow: Boolean,
    ): Long? {
        val core = create(context.applicationContext)
        return (core as? DefaultUnbPocketCore)?.prepareLocalReminders(postDueNow = postDueNow)
    }

    internal suspend fun runSigaaBackgroundNotificationSync(
        context: Context,
    ): CoreResult<PortalHomeSyncSummary> {
        val core = create(context.applicationContext)
        return (core as? DefaultUnbPocketCore)?.runBackgroundNotificationSync()
            ?: CoreResult.Failure(CoreError.Unexpected("Instancia do Core nao suporta sync de notificacoes."))
    }

    internal suspend fun prepareLocalRemindersForCore(
        core: UnbPocketCore,
        postDueNow: Boolean,
    ): Long? = (core as? DefaultUnbPocketCore)?.prepareLocalReminders(postDueNow = postDueNow)

    internal suspend fun runBackgroundNotificationSyncForCore(
        core: UnbPocketCore,
    ): CoreResult<PortalHomeSyncSummary> =
        (core as? DefaultUnbPocketCore)?.runBackgroundNotificationSync()
            ?: CoreResult.Failure(CoreError.Unexpected("Instancia do Core nao suporta sync de notificacoes."))

    private fun buildCore(appContext: Context): UnbPocketCore {
        val database = UnbPocketDatabase.build(appContext)
        return createWithPorts(
            transport = UnbHttpTransport(),
            snapshotDao = database.snapshotDao(),
            vault = KeystoreCredentialVault(appContext),
            ruPrefs = RuPreferencesStore(appContext),
            fileStore = DownloadedFileStore(appContext),
            pdfExtractor = AndroidPdfBoxExtractor(appContext),
            notificationScheduler = RuMealAlarmScheduler(appContext),
            sigaaNotifPrefs = SigaaNotificationPreferencesStore(appContext),
            sigaaNotificationScheduler = SigaaNotificationSchedulerPort { config ->
                SigaaAlarmScheduler.syncScheduleAsync(appContext, config)
                SigaaBackgroundSyncScheduler.syncSchedule(appContext, config)
            },
            sigaaNotificationPoster = SigaaNotificationPoster(appContext),
            diagLog = { msg -> Log.i("UnbPocketNotif", msg) },
        )
    }

    internal fun createWithPorts(
        transport: UnbHttpTransport,
        snapshotDao: SnapshotDao,
        vault: CredentialVault,
        ruPrefs: RuPrefsSource,
        fileStore: FileStore,
        pdfExtractor: PdfExtractor,
        clockMillis: () -> Long = { System.currentTimeMillis() },
        preloadDispatcher: CoroutineDispatcher = Dispatchers.IO,
        notificationScheduler: RuNotificationSchedulerPort = RuNotificationSchedulerPort { },
        sigaaNotifPrefs: SigaaNotifPrefsSource = InMemorySigaaNotifPrefsSource(),
        sigaaNotificationScheduler: SigaaNotificationSchedulerPort = SigaaNotificationSchedulerPort { },
        sigaaNotificationPoster: SigaaNotificationPosterPort = SigaaNotificationPosterPort { },
        diagLog: (String) -> Unit = {},
    ): UnbPocketCore {
        val cacheStore = SnapshotCacheStore(snapshotDao)
        var onLogoutCallback: suspend (Boolean) -> Unit = {}
        val coordinator = SigaaSessionCoordinator(
            transport = transport,
            vault = vault,
            cacheStore = cacheStore,
            fileStore = fileStore,
            clockMillis = clockMillis,
            onLogoutHook = { clearCachedData -> onLogoutCallback(clearCachedData) },
            diagLog = diagLog,
        )
        val ruRepo = RuRepositoryImpl(
            transport = transport,
            coordinator = coordinator,
            cacheStore = cacheStore,
            prefsSource = ruPrefs,
            fileStore = fileStore,
            pdfExtractor = pdfExtractor,
            clockMillis = clockMillis,
            notificationScheduler = notificationScheduler,
        )
        val turmasRepo = TurmasRepositoryImpl(
            coordinator = coordinator,
            cacheStore = cacheStore,
            fileStore = fileStore,
        )
        val discenteRepo = DiscenteRepositoryImpl(
            coordinator = coordinator,
            cacheStore = cacheStore,
            fileStore = fileStore,
            pdfExtractor = pdfExtractor,
        )
        val sigaaNotifCoordinator = SigaaNotificationCoordinator(
            cacheStore = cacheStore,
            prefsSource = sigaaNotifPrefs,
            scheduler = sigaaNotificationScheduler,
            poster = sigaaNotificationPoster,
            clockMillis = clockMillis,
            diagLog = diagLog,
        )
        val core = DefaultUnbPocketCore(
            coordinator = coordinator,
            ruRepo = ruRepo,
            turmasRepo = turmasRepo,
            discenteRepo = discenteRepo,
            sigaaNotifCoordinator = sigaaNotifCoordinator,
            sigaaNotificationScheduler = sigaaNotificationScheduler,
            clockMillis = clockMillis,
            preloadDispatcher = preloadDispatcher,
            diagLog = diagLog,
        )
        onLogoutCallback = { clearCachedData ->
            core.cancelAndResetPreload()
            turmasRepo.clearRuntimeState()
            sigaaNotifCoordinator.onLogout(clearCachedData)
        }
        return core
    }
}

private class InMemorySigaaNotifPrefsSource : SigaaNotifPrefsSource {
    private val state = MutableStateFlow(SigaaNotificacaoConfig())
    override val config: Flow<SigaaNotificacaoConfig> = state
    override suspend fun setConfig(config: SigaaNotificacaoConfig) {
        state.value = config
    }

    override suspend fun resetForLogout() {
        state.value = SigaaNotificacaoConfig()
    }
}

private class DefaultUnbPocketCore(
    private val coordinator: SigaaSessionCoordinator,
    private val ruRepo: RuRepositoryImpl,
    private val turmasRepo: TurmasRepositoryImpl,
    private val discenteRepo: DiscenteRepositoryImpl,
    private val sigaaNotifCoordinator: SigaaNotificationCoordinator,
    private val sigaaNotificationScheduler: SigaaNotificationSchedulerPort,
    private val clockMillis: () -> Long,
    preloadDispatcher: CoroutineDispatcher,
    private val diagLog: (String) -> Unit = {},
) : UnbPocketCore {
    override val auth: AuthRepository = coordinator
    override val ru: RuRepository = ruRepo
    override val turmas: TurmasRepository = turmasRepo
    override val discente: DiscenteRepository = discenteRepo

    private val _preloadProgress = MutableStateFlow(PreloadProgress())
    override val preloadProgress: StateFlow<PreloadProgress> = _preloadProgress.asStateFlow()

    private val preloadScope = CoroutineScope(SupervisorJob() + preloadDispatcher)
    private val preloadJobLock = Any()
    private var activePreloadJob: Job? = null

    fun cancelAndResetPreload() {
        synchronized(preloadJobLock) {
            activePreloadJob?.cancel()
            activePreloadJob = null
        }
        _preloadProgress.value = PreloadProgress(phase = PreloadPhase.IDLE)
    }

    override suspend fun syncPortalHome(includeSaldoRu: Boolean): CoreResult<PortalHomeSyncSummary> {
        _preloadProgress.value = _preloadProgress.value.copy(
            phase = PreloadPhase.RUNNING_ESSENTIAL,
            currentTarget = "Portal Discente",
            lastError = null,
        )

        val result = coordinator.withAuthenticatedJsf(priority = JsfPriority.INTERACTIVE) { nav ->
            val cachedLoginHtml = coordinator.consumeFreshLoginPortalHtml()
            var homeHtml = if (cachedLoginHtml != null) {
                cachedLoginHtml
            } else {
                when (val home = nav.getPortalHomeHtml()) {
                    is CoreResult.Failure -> return@withAuthenticatedJsf home
                    is CoreResult.Success -> home.value
                }
            }

            var anyChanged = false
            var resumoRes = discenteRepo.persistResumoFromHomeHtmlWithDiff(nav, homeHtml)
            if (resumoRes is CoreResult.Failure && cachedLoginHtml != null) {
                diagLog("syncPortalHome cachedLoginHtml failed parse, retrying getPortalHomeHtml")
                when (val directHome = nav.getPortalHomeHtml()) {
                    is CoreResult.Failure -> return@withAuthenticatedJsf directHome
                    is CoreResult.Success -> {
                        homeHtml = directHome.value
                        resumoRes = discenteRepo.persistResumoFromHomeHtmlWithDiff(nav, homeHtml)
                    }
                }
            }
            when (resumoRes) {
                is CoreResult.Failure -> return@withAuthenticatedJsf resumoRes
                is CoreResult.Success -> if (resumoRes.value.second) anyChanged = true
            }

            val saldoAtualizado = if (includeSaldoRu) {
                when (val saldoRes = ruRepo.refreshSaldoExtratoWithDiff(nav, homeHtml)) {
                    is CoreResult.Failure -> {
                        if (saldoRes.error is CoreError.SessionExpired) return@withAuthenticatedJsf saldoRes
                        false
                    }
                    is CoreResult.Success -> {
                        if (saldoRes.value.second) anyChanged = true
                        true
                    }
                }
            } else {
                false
            }

            val turmas = when (
                val turmasRes = turmasRepo.persistHomeTurmasAndFeedWithDiff(
                    nav = nav,
                    homeHtml = homeHtml,
                    enriquecerViaAtestado = true,
                )
            ) {
                is CoreResult.Failure -> return@withAuthenticatedJsf turmasRes
                is CoreResult.Success -> {
                    if (turmasRes.value.second) anyChanged = true
                    turmasRes.value.first
                }
            }

            val feedCount = DiscentePortalParser.parseAtualizacoesFeed(homeHtml).size
            CoreResult.Success(
                PortalHomeSyncSummary(
                    syncedAtEpochMillis = clockMillis(),
                    turmasCount = turmas.size,
                    atualizacoesCount = feedCount,
                    saldoRuAtualizado = saldoAtualizado,
                    anyDataChanged = anyChanged,
                ),
            )
        }

        when (result) {
            is CoreResult.Success -> {
                val prev = _preloadProgress.value
                if (prev.phase == PreloadPhase.RUNNING_ESSENTIAL) {
                    _preloadProgress.value = prev.copy(
                        phase = PreloadPhase.COMPLETED,
                        currentTarget = null,
                        updatedAnyData = prev.updatedAnyData || result.value.anyDataChanged,
                    )
                }
            }
            is CoreResult.Failure -> {
                _preloadProgress.value = _preloadProgress.value.copy(
                    phase = PreloadPhase.IDLE,
                    currentTarget = null,
                    lastError = result.error,
                )
            }
        }
        return result
    }

    override suspend fun refreshDiscentePerfilESaldo(
        forceDownloadFoto: Boolean,
    ): CoreResult<DiscenteResumo> = coordinator.withAuthenticatedJsf(priority = JsfPriority.INTERACTIVE) { nav ->
        val homeHtml = when (val home = nav.getPortalHomeHtml()) {
            is CoreResult.Failure -> return@withAuthenticatedJsf home
            is CoreResult.Success -> home.value
        }

        val resumo = when (
            val resumoRes = discenteRepo.persistResumoFromHomeHtmlWithDiff(
                nav = nav,
                homeHtml = homeHtml,
                forceDownloadFoto = forceDownloadFoto,
            )
        ) {
            is CoreResult.Failure -> return@withAuthenticatedJsf resumoRes
            is CoreResult.Success -> resumoRes.value.first
        }

        when (val saldoRes = ruRepo.refreshSaldoExtratoWithDiff(nav, homeHtml)) {
            is CoreResult.Failure -> {
                if (saldoRes.error is CoreError.SessionExpired) return@withAuthenticatedJsf saldoRes
            }
            is CoreResult.Success -> Unit
        }

        CoreResult.Success(resumo)
    }

    override fun startBackgroundPreload(includeTurmaDetails: Boolean) {
        synchronized(preloadJobLock) {
            activePreloadJob?.cancel()
            activePreloadJob = preloadScope.launch {
                runPhase2Preload(includeTurmaDetails)
            }
        }
    }

    override suspend fun silentRefreshAll(includeTurmaDetails: Boolean): CoreResult<PortalHomeSyncSummary> {
        when (val session = coordinator.ensureValidSession()) {
            is CoreResult.Failure -> {
                diagLog("silentRefreshAll session failed error=${session.error}")
                sigaaNotifCoordinator.evaluateUpdatesAndNotify(postNotifications = true)
                return session
            }
            is CoreResult.Success -> Unit
        }

        val summary = syncPortalHome(includeSaldoRu = true)
        diagLog("silentRefreshAll syncPortalHome=$summary")
        if (summary is CoreResult.Success) {
            startBackgroundPreload(includeTurmaDetails = includeTurmaDetails)
            sigaaNotifCoordinator.evaluateUpdatesAndNotify(postNotifications = true)
        } else {
            sigaaNotifCoordinator.evaluateUpdatesAndNotify(postNotifications = true)
        }
        return summary
    }

    override fun observeSigaaNotificacoesConfig(): Flow<SigaaNotificacaoConfig> =
        sigaaNotifCoordinator.observeConfig()

    override suspend fun setSigaaNotificacoesConfig(config: SigaaNotificacaoConfig): CoreResult<Unit> =
        sigaaNotifCoordinator.setConfig(config)

    override suspend fun autoEnableSigaaNotificacoesIfEligible(): CoreResult<Boolean> =
        sigaaNotifCoordinator.autoEnableIfEligible()

    suspend fun prepareLocalReminders(postDueNow: Boolean): Long? =
        sigaaNotifCoordinator.prepareAndScheduleLocalReminders(postDueNow = postDueNow)

    suspend fun runBackgroundNotificationSync(): CoreResult<PortalHomeSyncSummary> {
        val config = sigaaNotifCoordinator.getConfig()
        if (!config.hasAnyEnabled) {
            return CoreResult.Success(
                PortalHomeSyncSummary(
                    syncedAtEpochMillis = clockMillis(),
                    turmasCount = 0,
                    atualizacoesCount = 0,
                    saldoRuAtualizado = false,
                    anyDataChanged = false,
                ),
            )
        }

        when (val session = coordinator.ensureValidSession()) {
            is CoreResult.Failure -> return session
            is CoreResult.Success -> Unit
        }

        val summary = syncPortalHome(
            includeSaldoRu = config.needsDiscenteSaldoSync || config.hasAnyRemoteSyncEnabled,
        )
        if (summary is CoreResult.Failure) return summary

        if (config.needsBoletimSync) {
            discenteRepo.refreshBoletimGeralWithDiff(priority = JsfPriority.BACKGROUND_PRELOAD)
        }

        if (config.needsTurmaDetailsSync) {
            val turmas = turmasRepo.getCachedTurmasSnapshot()
            for (turma in turmas) {
                if (turma.frontEndIdTurma in config.turmasSilenciadas ||
                    turma.idTurma in config.turmasSilenciadas
                ) {
                    continue
                }
                if (config.avisosEMateriaisNovos) {
                    turmasRepo.refreshTurmaDetalheWithDiff(
                        turma.frontEndIdTurma,
                        JsfPriority.BACKGROUND_PRELOAD,
                    )
                }
                if (config.riscoFaltaAlerta) {
                    turmasRepo.refreshFrequenciaWithDiff(
                        turma.frontEndIdTurma,
                        JsfPriority.BACKGROUND_PRELOAD,
                    )
                }
                if (config.notasParciaisAlteradas) {
                    turmasRepo.refreshNotasParciaisWithDiff(
                        turma.frontEndIdTurma,
                        JsfPriority.BACKGROUND_PRELOAD,
                    )
                }
                if (config.provasETarefasLembrete) {
                    turmasRepo.refreshAvaliacoesWithDiff(
                        turma.frontEndIdTurma,
                        JsfPriority.BACKGROUND_PRELOAD,
                    )
                    turmasRepo.refreshTarefasWithDiff(
                        turma.frontEndIdTurma,
                        JsfPriority.BACKGROUND_PRELOAD,
                    )
                }
            }
        }

        sigaaNotifCoordinator.evaluateUpdatesAndNotify(postNotifications = true)
        return summary
    }

    private suspend fun runPhase2Preload(includeTurmaDetails: Boolean) {
        val turmas = turmasRepo.getCachedTurmasSnapshot()
        val totalSteps = 2 + if (includeTurmaDetails) turmas.size * 5 else 0
        var completed = 0
        var anyUpdated = _preloadProgress.value.updatedAnyData
        var lastError: CoreError? = null

        fun publish(target: String?, phase: PreloadPhase = PreloadPhase.RUNNING_BACKGROUND) {
            _preloadProgress.value = PreloadProgress(
                phase = phase,
                completedSteps = completed,
                totalSteps = totalSteps,
                currentTarget = target,
                updatedAnyData = anyUpdated,
                lastError = lastError,
            )
        }

        var abortedByAuth = false
        suspend fun runStep(label: String, block: suspend () -> CoreResult<Boolean>) {
            if (abortedByAuth) return
            kotlin.coroutines.coroutineContext.ensureActive()
            publish(label)
            try {
                when (val res = block()) {
                    is CoreResult.Success -> if (res.value) anyUpdated = true
                    is CoreResult.Failure -> {
                        lastError = res.error
                        if (res.error is CoreError.SessionExpired ||
                            res.error is CoreError.NoStoredCredentials ||
                            res.error is CoreError.InvalidCredentials
                        ) {
                            abortedByAuth = true
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } finally {
                completed++
            }
        }

        runStep("Carteirinha Estudantil") {
            when (val res = discenteRepo.refreshCarteirinhaWithDiff(force = false, priority = JsfPriority.BACKGROUND_PRELOAD)) {
                is CoreResult.Failure -> res
                is CoreResult.Success -> CoreResult.Success(res.value.second)
            }
        }

        runStep("Boletim Geral") {
            when (val res = discenteRepo.refreshBoletimGeralWithDiff(priority = JsfPriority.BACKGROUND_PRELOAD)) {
                is CoreResult.Failure -> res
                is CoreResult.Success -> CoreResult.Success(res.value.second)
            }
        }

        if (includeTurmaDetails) {
            for (turma in turmas) {
                val labelBase = turma.codigoDisciplina ?: turma.nomeDisciplina
                runStep("$labelBase · Tópicos") {
                    when (val res = turmasRepo.refreshTurmaDetalheWithDiff(turma.frontEndIdTurma, JsfPriority.BACKGROUND_PRELOAD)) {
                        is CoreResult.Failure -> res
                        is CoreResult.Success -> CoreResult.Success(res.value.second)
                    }
                }
                runStep("$labelBase · Frequência") {
                    when (val res = turmasRepo.refreshFrequenciaWithDiff(turma.frontEndIdTurma, JsfPriority.BACKGROUND_PRELOAD)) {
                        is CoreResult.Failure -> res
                        is CoreResult.Success -> CoreResult.Success(res.value.second)
                    }
                }
                runStep("$labelBase · Notas") {
                    when (val res = turmasRepo.refreshNotasParciaisWithDiff(turma.frontEndIdTurma, JsfPriority.BACKGROUND_PRELOAD)) {
                        is CoreResult.Failure -> res
                        is CoreResult.Success -> CoreResult.Success(res.value.second)
                    }
                }
                runStep("$labelBase · Avaliações") {
                    when (val res = turmasRepo.refreshAvaliacoesWithDiff(turma.frontEndIdTurma, JsfPriority.BACKGROUND_PRELOAD)) {
                        is CoreResult.Failure -> res
                        is CoreResult.Success -> CoreResult.Success(res.value.second)
                    }
                }
                runStep("$labelBase · Tarefas") {
                    when (val res = turmasRepo.refreshTarefasWithDiff(turma.frontEndIdTurma, JsfPriority.BACKGROUND_PRELOAD)) {
                        is CoreResult.Failure -> res
                        is CoreResult.Success -> CoreResult.Success(res.value.second)
                    }
                }
            }
        }

        publish(target = null, phase = PreloadPhase.COMPLETED)
        val config = sigaaNotifCoordinator.getConfig()
        if (config.hasAnyEnabled) {
            sigaaNotifCoordinator.evaluateUpdatesAndNotify(postNotifications = true)
            if (config.hasAnyLocalScheduleEnabled) {
                sigaaNotificationScheduler.syncSchedule(config)
            }
        }
    }
}
