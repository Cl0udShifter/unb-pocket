package com.unbpocket.core.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unbpocket.core.data.UnbPocketCoreFactory
import com.unbpocket.core.data.notification.SigaaAlarmScheduler
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import kotlinx.coroutines.flow.first
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
        val core = UnbPocketCoreFactory.create(applicationContext)
        val config = core.observeSigaaNotificacoesConfig().first()
        if (!config.hasAnyEnabled) {
            SigaaBackgroundSyncScheduler.syncSchedule(applicationContext, config)
            return Result.success()
        }
        if (!core.auth.hasSavedCredentials()) {
            return Result.success()
        }

        val syncResult = UnbPocketCoreFactory.runSigaaBackgroundNotificationSync(applicationContext)
        val nextTrigger = UnbPocketCoreFactory.prepareSigaaLocalReminders(
            context = applicationContext,
            postDueNow = false,
        )
        SigaaAlarmScheduler.applyTriggerMillis(applicationContext, nextTrigger)

        return when (syncResult) {
            is CoreResult.Success -> Result.success()
            is CoreResult.Failure -> {
                if (syncResult.error.isRetryableRemoteError() && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        }
    }

    private fun CoreError.isRetryableRemoteError(): Boolean = when (this) {
        is CoreError.NetworkUnavailable -> true
        is CoreError.HttpFailure -> statusCode >= 500 || statusCode == 429 || statusCode == 408
        else -> false
    }

    private companion object {
        const val MAX_RETRY_ATTEMPTS: Int = 3
    }
}

/**
 * Configura a janela periodica de sincronizacao de notificacoes SIGAA e RU Discente via WorkManager.
 */
object SigaaBackgroundSyncScheduler {
    private const val UNIQUE_WORK_NAME: String = "sigaa_background_notifications"

    fun syncSchedule(
        context: Context,
        config: SigaaNotificacaoConfig,
    ) {
        val appContext = context.applicationContext
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return

        if (!config.hasAnyRemoteSyncEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
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
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
