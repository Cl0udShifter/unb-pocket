package com.unbpocket.core.data.notification

import android.content.Context
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

/**
 * Worker de contingencia executado quando o alarme dispara sem cache vigente para hoje,
 * tentando sincronizar o cardapio antes de postar a notificacao.
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
        return when (val result = core.ru.prepareNotificacaoRefeicao(meal)) {
            is CoreResult.Success -> {
                result.value?.let { content ->
                    RuMealNotificationPoster.post(applicationContext, content)
                }
                Result.success()
            }
            is CoreResult.Failure -> Result.success()
        }
    }

    companion object {
        private const val KEY_MEAL_PAGE_INDEX = "meal_page_index"

        fun enqueue(context: Context, meal: TipoRefeicao) {
            val request = OneTimeWorkRequestBuilder<RuMealNotificationWorker>()
                .setInputData(
                    Data.Builder()
                        .putInt(KEY_MEAL_PAGE_INDEX, meal.pageIndex)
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
