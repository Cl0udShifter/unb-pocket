package com.unbpocket.core.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.unbpocket.core.data.local.RuNotificationSchedulerPort
import com.unbpocket.core.data.local.RuPreferencesStore
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.parser.RuNotificationPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Agenda o proximo alarme de notificacao de refeicao via `AlarmManager` para funcionar
 * mesmo com o processo do aplicativo completamente encerrado.
 */
class RuMealAlarmScheduler(
    private val context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : RuNotificationSchedulerPort {

    override fun syncSchedule(config: RuNotificacaoConfig) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val nextSlot = RuNotificationPlanner.findNextTrigger(config, clockMillis())

        val baseIntent = Intent(appContext, RuNotificationAlarmReceiver::class.java).apply {
            action = RuNotificationAlarmReceiver.ACTION_MEAL_ALARM
        }

        if (nextSlot == null) {
            val existing = PendingIntent.getBroadcast(
                appContext,
                REQUEST_CODE_NEXT_ALARM,
                baseIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (existing != null) {
                alarmManager.cancel(existing)
                existing.cancel()
            }
            return
        }

        val alarmIntent = baseIntent.apply {
            putExtra(RuNotificationAlarmReceiver.EXTRA_MEAL_PAGE_INDEX, nextSlot.refeicao.pageIndex)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_NEXT_ALARM,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextSlot.triggerEpochMillis,
                    pendingIntent,
                )
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextSlot.triggerEpochMillis,
                    pendingIntent,
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextSlot.triggerEpochMillis,
                pendingIntent,
            )
        }
    }

    companion object {
        private const val REQUEST_CODE_NEXT_ALARM = 4101
        private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun syncFromPreferencesAsync(context: Context) {
            val appContext = context.applicationContext
            initScope.launch {
                runCatching {
                    val prefs = RuPreferencesStore(appContext).preferencias.first()
                    RuMealAlarmScheduler(appContext).syncSchedule(prefs.notificacoes)
                }
            }
        }
    }
}
