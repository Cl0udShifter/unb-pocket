package com.unbpocket.core.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.unbpocket.core.data.local.RuNotificationSchedulerPort
import com.unbpocket.core.data.local.RuPreferencesStore
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.TipoRefeicao
import com.unbpocket.core.parser.RuMenuFreshness
import com.unbpocket.core.parser.RuNotificationPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Agenda o proximo alarme de notificacao de refeicao via `AlarmManager` (com fallback
 * WorkManager e catch-up para atrasos de Doze/Standby) para funcionar mesmo com o app fechado.
 */
class RuMealAlarmScheduler(
    private val context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : RuNotificationSchedulerPort {

    override fun syncSchedule(config: RuNotificacaoConfig) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val nowMillis = clockMillis()

        if (config.hasAnySlotSelected()) {
            RuMealNotificationPoster.ensureChannel(appContext)
        }
        catchUpMissedTodayMealIfNeeded(appContext, config, nowMillis)

        val nextSlot = RuNotificationPlanner.findNextTrigger(config, nowMillis)
        val baseIntent = Intent(appContext, RuNotificationAlarmReceiver::class.java).apply {
            action = RuNotificationAlarmReceiver.ACTION_MEAL_ALARM
        }

        if (nextSlot == null) {
            RuMealAlarmStateStore.clearArmedSlot(appContext)
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
            cancelBackupWork(appContext)
            Log.i(TAG, "RU meal alarm cleared (disabled or no slots selected)")
            return
        }

        val triggerDate = RuMenuFreshness.localDateInBrasilia(nextSlot.triggerEpochMillis)
        val slotKey = RuNotificationPlanner.sentSlotKey(triggerDate, nextSlot.refeicao)
        RuMealAlarmStateStore.saveArmedSlot(appContext, slotKey, nextSlot.triggerEpochMillis)

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
            }.onSuccess {
                cancelBackupWork(appContext)
                Log.i(TAG, "Scheduled EXACT RU alarm key=$slotKey at=${nextSlot.triggerEpochMillis}")
            }.onFailure { err ->
                Log.w(TAG, "Exact RU alarm failed, falling back to inexact+WorkManager: ${err.message}")
                scheduleInexactWithBackup(appContext, alarmManager, nextSlot.triggerEpochMillis, nextSlot.refeicao, pendingIntent, nowMillis)
            }
        } else {
            Log.i(TAG, "SCHEDULE_EXACT_ALARM denied; scheduling inexact+WorkManager backup for RU key=$slotKey")
            scheduleInexactWithBackup(appContext, alarmManager, nextSlot.triggerEpochMillis, nextSlot.refeicao, pendingIntent, nowMillis)
        }
    }

    private fun scheduleInexactWithBackup(
        appContext: Context,
        alarmManager: AlarmManager,
        triggerEpochMillis: Long,
        refeicao: TipoRefeicao,
        pendingIntent: PendingIntent,
        nowMillis: Long,
    ) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerEpochMillis,
            pendingIntent,
        )
        val delayMillis = (triggerEpochMillis - nowMillis).coerceAtLeast(0L)
        val triggerDate = RuMenuFreshness.localDateInBrasilia(triggerEpochMillis).toString()
        val request = OneTimeWorkRequestBuilder<RuMealNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putInt(RuMealNotificationWorker.KEY_MEAL_PAGE_INDEX, refeicao.pageIndex)
                    .putString(RuMealNotificationWorker.KEY_EXPECTED_DATE, triggerDate)
                    .build(),
            )
            .build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                BACKUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    private fun cancelBackupWork(appContext: Context) {
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(BACKUP_WORK_NAME)
        }
    }

    companion object {
        private const val TAG = "UnbPocketNotif"
        private const val REQUEST_CODE_NEXT_ALARM = 4101
        private const val BACKUP_WORK_NAME = "ru_meal_exact_fallback"
        private const val RECENT_TRIGGER_CATCHUP_MILLIS = 60 * 60 * 1000L
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

        fun catchUpMissedTodayMealIfNeeded(
            context: Context,
            config: RuNotificacaoConfig,
            nowMillis: Long = System.currentTimeMillis(),
        ) {
            if (!config.hasAnySlotSelected()) return
            val appContext = context.applicationContext
            val sentKeys = RuMealAlarmStateStore.getSentKeys(appContext, nowMillis)
            val armedKey = RuMealAlarmStateStore.getArmedSlotKey(appContext)

            val candidate = RuNotificationPlanner.findCatchUpMeal(
                config = config,
                alreadySentKeys = sentKeys,
                nowEpochMillis = nowMillis,
            ) ?: return

            val today = RuMenuFreshness.localDateInBrasilia(nowMillis)
            val candidateKey = RuNotificationPlanner.sentSlotKey(today, candidate.refeicao)
            val wasPreviouslyArmed = armedKey == candidateKey
            val triggeredRecently = nowMillis - candidate.triggerEpochMillis <= RECENT_TRIGGER_CATCHUP_MILLIS
            if (!wasPreviouslyArmed && !triggeredRecently) return

            Log.i(TAG, "Triggering catch-up RU worker for key=$candidateKey (armed=$wasPreviouslyArmed)")
            RuMealAlarmStateStore.markSent(appContext, today, candidate.refeicao, nowMillis)
            RuMealNotificationWorker.enqueue(appContext, candidate.refeicao)
        }
    }
}

internal object RuMealAlarmStateStore {
    private const val PREFS_NAME = "ru_meal_alarm_state"
    private const val KEY_SENT_SLOTS = "sent_slots"
    private const val KEY_ARMED_KEY = "armed_slot_key"
    private const val KEY_ARMED_TRIGGER_MS = "armed_trigger_ms"
    private const val RETENTION_DAYS = 7L

    fun getSentKeys(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_SENT_SLOTS, emptySet()).orEmpty()
        val cutoffDate = RuMenuFreshness.localDateInBrasilia(nowMillis).minusDays(RETENTION_DAYS)
        return raw.filter { key ->
            val datePart = key.substringBefore(':')
            val parsed = runCatching { LocalDate.parse(datePart) }.getOrNull()
            parsed != null && !parsed.isBefore(cutoffDate)
        }.toSet()
    }

    fun isSentToday(
        context: Context,
        today: LocalDate,
        refeicao: TipoRefeicao,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = RuNotificationPlanner.sentSlotKey(today, refeicao) in getSentKeys(context, nowMillis)

    fun markSent(
        context: Context,
        date: LocalDate,
        refeicao: TipoRefeicao,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getSentKeys(context, nowMillis).toMutableSet()
        val key = RuNotificationPlanner.sentSlotKey(date, refeicao)
        updated.add(key)
        prefs.edit().apply {
            putStringSet(KEY_SENT_SLOTS, updated)
            if (prefs.getString(KEY_ARMED_KEY, null) == key) {
                remove(KEY_ARMED_KEY)
                remove(KEY_ARMED_TRIGGER_MS)
            }
            apply()
        }
    }

    fun saveArmedSlot(
        context: Context,
        slotKey: String,
        triggerEpochMillis: Long,
    ) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ARMED_KEY, slotKey)
            .putLong(KEY_ARMED_TRIGGER_MS, triggerEpochMillis)
            .apply()
    }

    fun getArmedSlotKey(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ARMED_KEY, null)

    fun clearArmedSlot(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ARMED_KEY)
            .remove(KEY_ARMED_TRIGGER_MS)
            .apply()
    }
}
