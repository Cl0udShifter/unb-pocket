package com.unbpocket.core.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            action == Intent.ACTION_TIMEZONE_CHANGED
        if (!isSupportedAction) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val mealPageIndex = intent.getIntExtra(EXTRA_MEAL_PAGE_INDEX, -1)

        receiverScope.launch {
            try {
                val prefs = RuPreferencesStore(appContext).preferencias.first()
                RuMealAlarmScheduler(appContext).syncSchedule(prefs.notificacoes)

                if (action == ACTION_MEAL_ALARM) {
                    val meal = TipoRefeicao.entries.firstOrNull { it.pageIndex == mealPageIndex }
                        ?: return@launch
                    val nowMillis = System.currentTimeMillis()
                    val today = RuMenuFreshness.localDateInBrasilia(nowMillis)
                    val dia = DiaSemanaRu.fromDayOfWeekIso(today.dayOfWeek.value) ?: return@launch
                    if (!prefs.notificacoes.isEnabledFor(dia, meal)) return@launch

                    val cacheStore = SnapshotCacheStore(UnbPocketDatabase.build(appContext).snapshotDao())
                    val cachedMenu = cacheStore.observe(
                        cacheKey = SnapshotCacheStore.Keys.cardapio(prefs.campusSelecionado),
                        serializer = CardapioSemanalRuDto.serializer(),
                        fromDto = CacheMappers::fromDto,
                    ).first()?.value

                    if (cachedMenu != null && RuNotificationPlanner.findDayIndexForDate(cachedMenu, today) != null) {
                        val content = RuNotificationPlanner.buildNotificationContent(
                            cardapio = cachedMenu,
                            preferencias = prefs,
                            refeicao = meal,
                            nowEpochMillis = nowMillis,
                        )
                        if (content != null) {
                            RuMealNotificationPoster.post(appContext, content)
                        }
                    } else {
                        RuMealNotificationWorker.enqueue(appContext, meal)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MEAL_ALARM = "com.unbpocket.core.data.action.RU_MEAL_ALARM"
        const val EXTRA_MEAL_PAGE_INDEX = "meal_page_index"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
