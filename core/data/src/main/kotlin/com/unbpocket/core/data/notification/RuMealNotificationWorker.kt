package com.unbpocket.core.data.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unbpocket.core.data.UnbPocketCoreFactory
import com.unbpocket.core.model.common.CoreResult
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.parser.RuMenuFreshness
import com.unbpocket.core.parser.RuNotificationPlanner
import kotlinx.coroutines.flow.first

/**
 * Worker de contingencia executado quando o alarme dispara sem cardapio estruturado local
 * ou como backup do alarme inexact, tentando sincronizar e garantindo envio via fallback.
 */
class RuMealNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mealPageIndex = inputData.getInt(KEY_MEAL_PAGE_INDEX, -1)
        val meal = TipoRefeicao.entries.firstOrNull { it.pageIndex == mealPageIndex }
            ?: return Result.failure()

        val core = UnbPocketCoreFactory.create(applicationContext)
        val nowMillis = System.currentTimeMillis()
        val today = RuMenuFreshness.localDateInBrasilia(nowMillis)
        val prefs = core.ru.observePreferencias().first()

        val force = inputData.getBoolean(KEY_FORCE_EVEN_IF_MARKED, false)
        val expectedDate = inputData.getString(KEY_EXPECTED_DATE)
        if (!force && expectedDate != null && expectedDate != today.toString()) {
            Log.i(TAG, "RuMealNotificationWorker skipped stale backup expectedDate=$expectedDate today=$today")
            return Result.success()
        }

        // Evita duplicidade caso o AlarmManager ja tenha postado no mesmo dia, exceto se explicitamente disparado
        val alreadySent = RuMealAlarmStateStore.isSentToday(applicationContext, today, meal, nowMillis)
        if (alreadySent && !force) {
            Log.i(TAG, "RuMealNotificationWorker skipped already-sent meal=${meal.name}")
            return Result.success()
        }

        val content = when (val result = core.ru.prepareNotificacaoRefeicao(meal, nowMillis)) {
            is CoreResult.Success -> result.value
            is CoreResult.Failure -> {
                Log.w(TAG, "prepareNotificacaoRefeicao failed: ${result.error}, using offline fallback")
                RuNotificationPlanner.buildNotificationContentOrFallback(
                    cardapio = null,
                    preferencias = prefs,
                    refeicao = meal,
                    nowEpochMillis = nowMillis,
                )
            }
        }

        if (content != null) {
            RuMealAlarmStateStore.markSent(applicationContext, today, meal, nowMillis)
            RuMealNotificationPoster.post(applicationContext, content)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "UnbPocketNotif"
        const val KEY_MEAL_PAGE_INDEX = "meal_page_index"
        const val KEY_EXPECTED_DATE = "expected_date"
        private const val KEY_FORCE_EVEN_IF_MARKED = "force_even_if_marked"

        fun enqueue(context: Context, meal: TipoRefeicao) {
            val request = OneTimeWorkRequestBuilder<RuMealNotificationWorker>()
                .setInputData(
                    Data.Builder()
                        .putInt(KEY_MEAL_PAGE_INDEX, meal.pageIndex)
                        .putBoolean(KEY_FORCE_EVEN_IF_MARKED, true)
                        .build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    "ru_meal_notif_${meal.pageIndex}",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }
}
