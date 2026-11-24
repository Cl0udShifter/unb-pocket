package com.unbpocket.core.data.notification

import com.unbpocket.core.data.local.CacheMappers
import com.unbpocket.core.data.local.SigaaNotifBaselineMappers
import com.unbpocket.core.data.local.SigaaNotifPrefsSource
import com.unbpocket.core.data.local.SigaaNotificationPosterPort
import com.unbpocket.core.data.local.SigaaNotificationSchedulerPort
import com.unbpocket.core.data.local.SigaaSentRemindersDto
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.model.notification.SigaaNotificacaoConteudo
import com.unbpocket.core.model.turmas.AvaliacaoTurma
import com.unbpocket.core.model.turmas.TarefaTurma
import com.unbpocket.core.parser.SigaaNotificationBaseline
import com.unbpocket.core.parser.SigaaNotificationPlanner
import com.unbpocket.core.parser.SigaaSnapshotInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orquestra configuracoes, baseline de alteracoes, lembretes locais agendados e disparo de
 * notificacoes para Aulas & Turmas e Painel Discente, sem dependencias diretas da camada UI.
 */
class SigaaNotificationCoordinator(
    private val cacheStore: SnapshotCacheStore,
    private val prefsSource: SigaaNotifPrefsSource,
    private val scheduler: SigaaNotificationSchedulerPort,
    private val poster: SigaaNotificationPosterPort,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val diagLog: (String) -> Unit = {},
) {
    private val stateMutex = Mutex()

    fun observeConfig(): Flow<SigaaNotificacaoConfig> = prefsSource.config

    suspend fun getConfig(): SigaaNotificacaoConfig = prefsSource.config.first()

    suspend fun setConfig(config: SigaaNotificacaoConfig): CoreResult<Unit> = runCatching {
        val previousConfig = prefsSource.config.first()
        prefsSource.setConfig(config)
        if (config.hasAnyEnabled) {
            if (!previousConfig.hasAnyEnabled) {
                seedMissingBaselineFromCache()
            }
            evaluateUpdatesAndNotify(postNotifications = true)
        }
        scheduler.syncSchedule(config)
    }.fold(
        onSuccess = { CoreResult.Success(Unit) },
        onFailure = { err ->
            CoreResult.Failure(
                CoreError.StorageError("setSigaaNotificacoesConfig", err.message.orEmpty()),
            )
        },
    )

    suspend fun autoEnableIfEligible(): CoreResult<Boolean> = runCatching {
        val current = prefsSource.config.first()
        if (current.userConfigured || current.habilitado) {
            return@runCatching false
        }
        val enabledConfig = current.copy(habilitado = true)
        prefsSource.setConfig(enabledConfig)
        seedMissingBaselineFromCache()
        scheduler.syncSchedule(enabledConfig)
        true
    }.fold(
        onSuccess = { CoreResult.Success(it) },
        onFailure = { err ->
            CoreResult.Failure(
                CoreError.StorageError("autoEnableSigaaNotificacoesIfEligible", err.message.orEmpty()),
            )
        },
    )

    suspend fun seedMissingBaselineFromCache() {
        val config = prefsSource.config.first()
        if (!config.hasAnyEnabled) return

        stateMutex.withLock {
            val now = clockMillis()
            val previous = loadBaseline()
            val snapshot = loadSnapshotInput()
            val result = SigaaNotificationPlanner.detectSnapshotChanges(
                previous = previous,
                snapshot = snapshot,
                config = config,
                nowEpochMillis = now,
            )
            // Ao popular baseline faltante (ex: pos-login), preserva rastreamento silencioso
            if (previous == null || result.updatedBaseline != previous) {
                saveBaseline(result.updatedBaseline, now)
            }
        }
    }

    suspend fun evaluateUpdatesAndNotify(
        postNotifications: Boolean = true,
    ): List<SigaaNotificacaoConteudo> {
        val config = prefsSource.config.first()
        if (!config.hasAnyEnabled) return emptyList()

        val posted = stateMutex.withLock {
            val now = clockMillis()
            val previous = loadBaseline()
            val snapshot = loadSnapshotInput()
            val diff = SigaaNotificationPlanner.detectSnapshotChanges(
                previous = previous,
                snapshot = snapshot,
                config = config,
                nowEpochMillis = now,
            )
            if (previous == null || diff.updatedBaseline != previous) {
                saveBaseline(diff.updatedBaseline, now)
            }

            if (!postNotifications || diff.notifications.isEmpty()) {
                return@withLock emptyList()
            }

            val sentMap = loadPrunedSentKeys(now).toMutableMap()
            val freshNotifications = diff.notifications.filter { it.dedupKey !in sentMap }
            if (freshNotifications.isNotEmpty()) {
                freshNotifications.forEach { sentMap[it.dedupKey] = now }
                saveSentKeys(sentMap, now)
            }
            freshNotifications
        }

        if (posted.isNotEmpty()) {
            diagLog("evaluateUpdatesAndNotify posting=${posted.map { it.tipo.name }}")
            poster.post(posted)
        } else {
            diagLog("evaluateUpdatesAndNotify diff=0 postNotifications=$postNotifications")
        }
        return posted
    }

    suspend fun prepareAndScheduleLocalReminders(
        postDueNow: Boolean = false,
    ): Long? {
        val config = prefsSource.config.first()
        if (!config.hasAnyLocalScheduleEnabled) return null

        val dueAndNext: Pair<List<SigaaNotificacaoConteudo>, Long?> = stateMutex.withLock {
            val now = clockMillis()
            val turmas = cacheStore.get(
                cacheKey = SnapshotCacheStore.Keys.TURMAS_LIST,
                serializer = CacheMappers.turmaResumoListSerializer,
                fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
            )?.value.orEmpty()

            val avaliacoesByTurma = mutableMapOf<String, List<AvaliacaoTurma>>()
            val tarefasByTurma = mutableMapOf<String, List<TarefaTurma>>()
            for (turma in turmas) {
                val avaliacoes = cacheStore.get(
                    cacheKey = SnapshotCacheStore.Keys.turmaAvaliacoes(turma.frontEndIdTurma),
                    serializer = CacheMappers.avaliacaoListSerializer,
                    fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
                )?.value.orEmpty()
                if (avaliacoes.isNotEmpty()) {
                    avaliacoesByTurma[turma.frontEndIdTurma] = avaliacoes
                }

                val tarefas = cacheStore.get(
                    cacheKey = SnapshotCacheStore.Keys.turmaTarefas(turma.frontEndIdTurma),
                    serializer = CacheMappers.tarefaListSerializer,
                    fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
                )?.value.orEmpty()
                if (tarefas.isNotEmpty()) {
                    tarefasByTurma[turma.frontEndIdTurma] = tarefas
                }
            }

            val reminders = SigaaNotificationPlanner.buildUpcomingReminders(
                turmas = turmas,
                avaliacoesByTurma = avaliacoesByTurma,
                tarefasByTurma = tarefasByTurma,
                config = config,
                nowEpochMillis = now,
            )

            val sentMap = loadPrunedSentKeys(now).toMutableMap()
            val due = if (postDueNow) {
                SigaaNotificationPlanner.extractDueReminders(
                    reminders = reminders,
                    alreadySentKeys = sentMap.keys,
                    nowEpochMillis = now,
                )
            } else {
                emptyList()
            }

            if (due.isNotEmpty()) {
                due.forEach { reminder ->
                    sentMap[reminder.notificacao.dedupKey] = now
                }
                saveSentKeys(sentMap, now)
            }

            val nextTrigger = SigaaNotificationPlanner.findNextTriggerEpochMillis(
                reminders = reminders,
                alreadySentKeys = sentMap.keys,
                nowEpochMillis = now,
            )
            due.map { it.notificacao } to nextTrigger
        }

        if (dueAndNext.first.isNotEmpty()) {
            poster.post(dueAndNext.first)
        }
        return dueAndNext.second
    }

    suspend fun onLogout(clearCachedData: Boolean) {
        if (clearCachedData) {
            prefsSource.resetForLogout()
            cacheStore.clearKey(SnapshotCacheStore.Keys.SIGAA_NOTIF_BASELINE)
            cacheStore.clearKey(SnapshotCacheStore.Keys.SIGAA_NOTIF_SENT_KEYS)
        }
        scheduler.syncSchedule(SigaaNotificacaoConfig())
    }

    private suspend fun loadSnapshotInput(): SigaaSnapshotInput {
        val turmas = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_LIST,
            serializer = CacheMappers.turmaResumoListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )?.value.orEmpty()

        val feedSynced = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.TURMAS_FEED,
            serializer = CacheMappers.feedListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )

        val detalhes = mutableMapOf<String, com.unbpocket.core.model.turmas.TurmaDetalheAva>()
        val notas = mutableMapOf<String, com.unbpocket.core.model.turmas.NotasParciaisTurma>()
        val freq = mutableMapOf<String, com.unbpocket.core.model.turmas.MapaFrequenciaTurma>()

        for (turma in turmas) {
            cacheStore.get(
                cacheKey = SnapshotCacheStore.Keys.turmaDetalhe(turma.frontEndIdTurma),
                serializer = com.unbpocket.core.data.local.TurmaDetalheAvaDto.serializer(),
                fromDto = CacheMappers::fromDto,
            )?.value?.let { detalhes[turma.frontEndIdTurma] = it }

            cacheStore.get(
                cacheKey = SnapshotCacheStore.Keys.turmaNotas(turma.frontEndIdTurma),
                serializer = com.unbpocket.core.data.local.NotasParciaisTurmaDto.serializer(),
                fromDto = CacheMappers::fromDto,
            )?.value?.let { notas[turma.frontEndIdTurma] = it }

            cacheStore.get(
                cacheKey = SnapshotCacheStore.Keys.turmaFrequencia(turma.frontEndIdTurma),
                serializer = com.unbpocket.core.data.local.MapaFrequenciaTurmaDto.serializer(),
                fromDto = CacheMappers::fromDto,
            )?.value?.let { freq[turma.frontEndIdTurma] = it }
        }

        val saldoRu = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.RU_SALDO,
            serializer = com.unbpocket.core.data.local.SaldoExtratoRuDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )?.value

        val boletim = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_BOLETIM,
            serializer = CacheMappers.boletimListSerializer,
            fromDto = { dtos -> dtos.map(CacheMappers::fromDto) },
        )?.value

        val indices = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.DISCENTE_RESUMO,
            serializer = com.unbpocket.core.data.local.DiscenteResumoDto.serializer(),
            fromDto = CacheMappers::fromDto,
        )?.value?.indices

        return SigaaSnapshotInput(
            turmas = turmas,
            feed = feedSynced?.value.orEmpty(),
            feedAvailable = feedSynced != null,
            detalhesPorTurma = detalhes,
            notasPorTurma = notas,
            frequenciaPorTurma = freq,
            saldoExtratoRu = saldoRu,
            boletim = boletim,
            indices = indices,
        )
    }

    private suspend fun loadBaseline(): SigaaNotificationBaseline? = cacheStore.get(
        cacheKey = SnapshotCacheStore.Keys.SIGAA_NOTIF_BASELINE,
        serializer = com.unbpocket.core.data.local.SigaaNotificationBaselineDto.serializer(),
        fromDto = SigaaNotifBaselineMappers::fromDto,
    )?.value

    private suspend fun saveBaseline(
        baseline: SigaaNotificationBaseline,
        nowEpochMillis: Long,
    ) {
        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.SIGAA_NOTIF_BASELINE,
            domain = SnapshotCacheStore.Domains.TURMAS,
            origin = DataOrigin.CACHE,
            syncedAtEpochMillis = nowEpochMillis,
            value = baseline,
            serializer = com.unbpocket.core.data.local.SigaaNotificationBaselineDto.serializer(),
            toDto = SigaaNotifBaselineMappers::toDto,
        )
    }

    private suspend fun loadPrunedSentKeys(nowEpochMillis: Long): Map<String, Long> {
        val raw = cacheStore.get(
            cacheKey = SnapshotCacheStore.Keys.SIGAA_NOTIF_SENT_KEYS,
            serializer = SigaaSentRemindersDto.serializer(),
            fromDto = { it.sentEpochByKey },
        )?.value.orEmpty()
        val cutoff = nowEpochMillis - SENT_KEY_RETENTION_MILLIS
        return raw.filterValues { it >= cutoff }
    }

    private suspend fun saveSentKeys(
        sentKeys: Map<String, Long>,
        nowEpochMillis: Long,
    ) {
        cacheStore.put(
            cacheKey = SnapshotCacheStore.Keys.SIGAA_NOTIF_SENT_KEYS,
            domain = SnapshotCacheStore.Domains.TURMAS,
            origin = DataOrigin.CACHE,
            syncedAtEpochMillis = nowEpochMillis,
            value = sentKeys,
            serializer = SigaaSentRemindersDto.serializer(),
            toDto = { SigaaSentRemindersDto(it) },
        )
    }

    private companion object {
        const val SENT_KEY_RETENTION_MILLIS: Long = 14L * 24L * 60L * 60L * 1000L
    }
}
