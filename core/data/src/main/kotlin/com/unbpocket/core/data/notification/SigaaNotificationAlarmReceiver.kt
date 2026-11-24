package com.unbpocket.core.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.unbpocket.core.data.UnbPocketCoreFactory
import com.unbpocket.core.data.worker.SigaaBackgroundSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receiver para disparos locais de aulas/provas/tarefas e reagendamento apos reboot ou mudanca de hora.
 */
class SigaaNotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        val isReminderAlarm = action == SigaaAlarmScheduler.ACTION_SIGAA_REMINDER_ALARM
        val isSystemReschedule = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == ACTION_EXACT_ALARM_PERMISSION_CHANGED

        if (!isReminderAlarm && !isSystemReschedule) return

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                withTimeoutOrNull(ASYNC_TIMEOUT_MILLIS) {
                    Log.i(TAG, "SIGAA receiver triggered action=$action")
                    val core = UnbPocketCoreFactory.create(appContext)
                    val config = core.observeSigaaNotificacoesConfig().first()
                    if (isSystemReschedule) {
                        SigaaBackgroundSyncScheduler.syncSchedule(appContext, config)
                    }
                    val nextTrigger = UnbPocketCoreFactory.prepareSigaaLocalReminders(
                        context = appContext,
                        postDueNow = true,
                    )
                    SigaaAlarmScheduler.applyTriggerMillis(appContext, nextTrigger)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG: String = "UnbPocketNotif"
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED: String =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        const val ASYNC_TIMEOUT_MILLIS: Long = 25_000L
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
