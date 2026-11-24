package com.unbpocket.core.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.unbpocket.core.data.local.CacheMappers
import com.unbpocket.core.data.local.CardapioSemanalRuDto
import com.unbpocket.core.data.local.RuPreferencesStore
import com.unbpocket.core.data.local.SnapshotCacheStore
import com.unbpocket.core.data.local.UnbPocketDatabase
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.parser.RuMenuFreshness
import com.unbpocket.core.parser.RuNotificationPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receiver de alarmes do Cardapio RU e eventos de sistema (boot, atualizacao do app e troca de horario).
 */
class RuNotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isSupportedAction = action == ACTION_MEAL_ALARM ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == ACTION_EXACT_ALARM_PERMISSION_CHANGED
        if (!isSupportedAction) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val mealPageIndex = intent.getIntExtra(EXTRA_MEAL_PAGE_INDEX, -1)

        receiverScope.launch {
            try {
                Log.i(TAG, "RU receiver triggered action=$action mealPageIndex=$mealPageIndex")
                val prefs = RuPreferencesStore(appContext).preferencias.first()

                if (action == ACTION_MEAL_ALARM) {
                    val meal = TipoRefeicao.entries.firstOrNull { it.pageIndex == mealPageIndex }
                    if (meal != null) {
                        val nowMillis = System.currentTimeMillis()
                        val today = RuMenuFreshness.localDateInBrasilia(nowMillis)
                        val dia = DiaSemanaRu.fromDayOfWeekIso(today.dayOfWeek.value)
                        if (dia != null && prefs.notificacoes.isEnabledFor(dia, meal)) {
                            val cacheStore = SnapshotCacheStore(UnbPocketDatabase.build(appContext).snapshotDao())
                            val cachedMenu = cacheStore.get(
                                cacheKey = SnapshotCacheStore.Keys.cardapio(prefs.campusSelecionado),
                                serializer = CardapioSemanalRuDto.serializer(),
                                fromDto = CacheMappers::fromDto,
                            )?.value

                            val structured = RuNotificationPlanner.buildNotificationContent(
                                cardapio = cachedMenu,
                                preferencias = prefs,
                                refeicao = meal,
                                nowEpochMillis = nowMillis,
                            )
                            if (structured != null) {
                                RuMealAlarmStateStore.markSent(appContext, today, meal, nowMillis)
                                RuMealNotificationPoster.post(appContext, structured)
                            } else {
                                // Busca atualizacao remota no Worker e posta cardapio ou fallback garantido
                                RuMealAlarmStateStore.markSent(appContext, today, meal, nowMillis)
                                RuMealNotificationWorker.enqueue(appContext, meal)
                            }
                        }
                    }
                }

                RuMealAlarmScheduler(appContext).syncSchedule(prefs.notificacoes)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UnbPocketNotif"
        const val ACTION_MEAL_ALARM = "com.unbpocket.core.data.action.RU_MEAL_ALARM"
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        const val EXTRA_MEAL_PAGE_INDEX = "meal_page_index"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
