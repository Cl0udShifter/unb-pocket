package com.unbpocket.core.data.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.unbpocket.core.data.UnbPocketCoreFactory
import com.unbpocket.core.data.worker.SigaaLocalReminderBackupWorker
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gerencia o alarme local do AlarmManager (com backup WorkManager quando alarmes exatos estiverem
 * bloqueados) para proximos lembretes de aulas, provas e entregas.
 */
object SigaaAlarmScheduler {
    private const val TAG: String = "UnbPocketNotif"
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
                    postDueNow = true,
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
        if (nextTriggerEpochMillis == null) {
            SigaaLocalReminderBackupWorker.cancel(appContext)
            Log.i(TAG, "SIGAA local alarm cleared (no upcoming reminders)")
            return
        }
        appContext.getSystemService(NotificationManager::class.java)?.let(SigaaNotificationPoster::ensureChannels)

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
            }.onSuccess {
                SigaaLocalReminderBackupWorker.cancel(appContext)
                Log.i(TAG, "Scheduled EXACT SIGAA reminder alarm at=$nextTriggerEpochMillis")
            }.onFailure { err ->
                Log.w(TAG, "Exact SIGAA alarm failed, falling back to inexact+WorkManager: ${err.message}")
                scheduleInexactWithBackup(appContext, alarmManager, nextTriggerEpochMillis, pendingIntent)
            }
        } else {
            Log.i(TAG, "SCHEDULE_EXACT_ALARM denied; scheduling inexact+WorkManager backup for SIGAA at=$nextTriggerEpochMillis")
            scheduleInexactWithBackup(appContext, alarmManager, nextTriggerEpochMillis, pendingIntent)
        }
    }

    private fun scheduleInexactWithBackup(
        appContext: Context,
        alarmManager: AlarmManager,
        nextTriggerEpochMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTriggerEpochMillis,
            pendingIntent,
        )
        SigaaLocalReminderBackupWorker.schedule(
            context = appContext,
            triggerEpochMillis = nextTriggerEpochMillis,
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(buildPendingIntent(appContext))
        SigaaLocalReminderBackupWorker.cancel(appContext)
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
