package com.unbpocket.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unbpocket.core.data.UnbPocketCoreFactory
import com.unbpocket.core.data.local.RuPreferencesStore
import com.unbpocket.core.data.local.SigaaNotificationPreferencesStore
import com.unbpocket.core.data.notification.RuMealAlarmScheduler
import com.unbpocket.core.data.notification.SigaaAlarmScheduler
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Worker periodico em segundo plano que autentica com credenciais salvas no Keystore,
 * atualiza turmas/discente/boletim e dispara notificacoes reais mesmo com o app fechado.
 */
class SigaaBackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "SigaaBackgroundSyncWorker started attempt=$runAttemptCount")
        val core = UnbPocketCoreFactory.create(applicationContext)
        val config = core.observeSigaaNotificacoesConfig().first()
        if (!config.hasAnyEnabled) {
            SigaaBackgroundSyncScheduler.syncSchedule(applicationContext, config)
            return Result.success()
        }

        // Aproveita o ciclo em segundo plano para garantir lembretes locais e RU mesmo sob restricoes de alarme
        runCatching {
            val nextTrigger = UnbPocketCoreFactory.prepareSigaaLocalReminders(
                context = applicationContext,
                postDueNow = true,
            )
            SigaaAlarmScheduler.applyTriggerMillis(applicationContext, nextTrigger)
            val ruPrefs = RuPreferencesStore(applicationContext).preferencias.first()
            RuMealAlarmScheduler.catchUpMissedTodayMealIfNeeded(applicationContext, ruPrefs.notificacoes)
        }

        if (!config.hasAnyRemoteSyncEnabled || !core.auth.hasSavedCredentials()) {
            Log.i(TAG, "SigaaBackgroundSyncWorker skipping remote sync (enabled=${config.hasAnyRemoteSyncEnabled}, creds=${core.auth.hasSavedCredentials()})")
            return Result.success()
        }

        val syncResult = UnbPocketCoreFactory.runSigaaBackgroundNotificationSync(applicationContext)
        val nextTrigger = UnbPocketCoreFactory.prepareSigaaLocalReminders(
            context = applicationContext,
            postDueNow = true,
        )
        SigaaAlarmScheduler.applyTriggerMillis(applicationContext, nextTrigger)

        return when (syncResult) {
            is CoreResult.Success -> {
                Log.i(
                    TAG,
                    "SigaaBackgroundSyncWorker synced turmas=${syncResult.value.turmasCount} updates=${syncResult.value.atualizacoesCount} changed=${syncResult.value.anyDataChanged}",
                )
                Result.success()
            }
            is CoreResult.Failure -> {
                val retry = syncResult.error.isRetryableError() && runAttemptCount < MAX_RETRY_ATTEMPTS
                Log.w(TAG, "SigaaBackgroundSyncWorker failed error=${syncResult.error} retry=$retry")
                if (retry) Result.retry() else Result.success()
            }
        }
    }

    private fun CoreError.isRetryableError(): Boolean = when (this) {
        is CoreError.NetworkUnavailable -> true
        is CoreError.SessionExpired -> true
        is CoreError.HttpFailure -> statusCode >= 500 || statusCode == 429 || statusCode == 408
        else -> false
    }

    private companion object {
        const val TAG: String = "UnbPocketNotif"
        const val MAX_RETRY_ATTEMPTS: Int = 3
    }
}

/**
 * Worker de contingencia para lembretes locais SIGAA (aulas, provas e entregas) caso alarmes
 * inexatos sejam atrasados ou diferidos pelo Android 14+/OEM.
 */
class SigaaLocalReminderBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "SigaaLocalReminderBackupWorker executing local reminder catch-up")
        val nextTrigger = UnbPocketCoreFactory.prepareSigaaLocalReminders(
            context = applicationContext,
            postDueNow = true,
        )
        SigaaAlarmScheduler.applyTriggerMillis(applicationContext, nextTrigger)
        return Result.success()
    }

    companion object {
        private const val TAG: String = "UnbPocketNotif"
        private const val UNIQUE_BACKUP_WORK_NAME: String = "sigaa_local_reminder_backup"

        fun schedule(
            context: Context,
            triggerEpochMillis: Long,
            nowEpochMillis: Long = System.currentTimeMillis(),
        ) {
            val appContext = context.applicationContext
            val delayMillis = (triggerEpochMillis - nowEpochMillis).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<SigaaLocalReminderBackupWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    UNIQUE_BACKUP_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_BACKUP_WORK_NAME)
            }
        }
    }
}

/**
 * Configura a janela periodica de sincronizacao de notificacoes SIGAA e RU Discente via WorkManager.
 */
object SigaaBackgroundSyncScheduler {
    private const val TAG: String = "UnbPocketNotif"
    private const val UNIQUE_WORK_NAME: String = "sigaa_background_notifications"
    private const val PREFS_NAME: String = "sigaa_sync_work_meta"
    private const val KEY_PERIODIC_SPEC: String = "periodic_spec"
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureSyncedAsync(context: Context) {
        val appContext = context.applicationContext
        initScope.launch {
            runCatching {
                val config = SigaaNotificationPreferencesStore(appContext).config.first()
                syncSchedule(appContext, config)
            }
        }
    }

    fun syncSchedule(
        context: Context,
        config: SigaaNotificacaoConfig,
    ) {
        val appContext = context.applicationContext
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        val metaPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!config.hasAnyRemoteSyncEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            metaPrefs.edit().remove(KEY_PERIODIC_SPEC).apply()
            Log.i(TAG, "SigaaBackgroundSyncScheduler cleared periodic work")
            return
        }

        val currentSpec = "${config.syncIntervalHours.hours}|${config.somenteWifi}"
        val previousSpec = metaPrefs.getString(KEY_PERIODIC_SPEC, null)
        // Mantem o timer existente com KEEP sempre que intervalo e Wi-Fi nao mudaram
        val policy = if (previousSpec == currentSpec) {
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            ExistingPeriodicWorkPolicy.UPDATE
        }

        val networkType = if (config.somenteWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = PeriodicWorkRequestBuilder<SigaaBackgroundSyncWorker>(
            repeatInterval = config.syncIntervalHours.hours,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 30L,
            flexTimeIntervalUnit = TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
        if (previousSpec != currentSpec) {
            metaPrefs.edit().putString(KEY_PERIODIC_SPEC, currentSpec).apply()
        }
        Log.i(TAG, "SigaaBackgroundSyncScheduler enqueued policy=${policy.name} spec=$currentSpec")
    }
}
