package com.unbpocket.core.data.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.unbpocket.core.data.UnbPocketCoreFactory
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gerencia o alarme local do AlarmManager para proximos lembretes de aulas, provas e entregas.
 */
object SigaaAlarmScheduler {
    const val ACTION_SIGAA_REMINDER_ALARM: String = "com.unbpocket.action.SIGAA_REMINDER_ALARM"
    private const val ALARM_REQUEST_CODE: Int = 4201

    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncScheduleAsync(
        context: Context,
        config: SigaaNotificacaoConfig? = null,
    ) {
        val appContext = context.applicationContext
        if (config != null && !config.hasAnyLocalScheduleEnabled) {
            cancel(appContext)
            return
        }
        asyncScope.launch {
            runCatching {
                val nextTrigger = UnbPocketCoreFactory.prepareSigaaLocalReminders(
                    context = appContext,
                    postDueNow = false,
                )
                applyTriggerMillis(appContext, nextTrigger)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun applyTriggerMillis(
        context: Context,
        nextTriggerEpochMillis: Long?,
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(appContext)

        alarmManager.cancel(pendingIntent)
        if (nextTriggerEpochMillis == null) return

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerEpochMillis,
                    pendingIntent,
                )
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerEpochMillis,
                    pendingIntent,
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerEpochMillis,
                pendingIntent,
            )
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildPendingIntent(appContext))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SigaaNotificationAlarmReceiver::class.java).apply {
            action = ACTION_SIGAA_REMINDER_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
