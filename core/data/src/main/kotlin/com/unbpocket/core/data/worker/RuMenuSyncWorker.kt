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
import com.unbpocket.core.model.common.CoreError
import com.unbpocket.core.model.common.CoreResult
import java.util.concurrent.TimeUnit

/**
 * Worker periodico que verifica e atualiza os cardapios de todos os campi do RU em segundo plano.
 */
class RuMenuSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val core = UnbPocketCoreFactory.create(applicationContext)
        return when (val result = core.ru.syncCardapiosIfNeeded(forceCheck = true, includeExtras = true)) {
            is CoreResult.Success -> {
                val hasRetryableFailure = result.value.failedCampi.values.any { error ->
                    error.isRetryableRemoteError()
                }
                if (hasRetryableFailure && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
            is CoreResult.Failure -> {
                if (result.error.isRetryableRemoteError() && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
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
        const val MAX_RETRY_ATTEMPTS = 3
    }
}

/**
 * Agenda a sincronizacao periodica dos cardapios do RU com WorkManager enquanto houver internet.
 */
object RuMenuAutoSyncScheduler {
    private const val UNIQUE_WORK_NAME = "ru_weekly_menu_auto_sync"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<RuMenuSyncWorker>(
            repeatInterval = 6L,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 1L,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
