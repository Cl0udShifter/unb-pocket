package com.unbpocket.core.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.unbpocket.core.data.R
import com.unbpocket.core.model.ru.RuNotificacaoConteudo
import com.unbpocket.core.model.ru.RuNotificationExtras

/**
 * Emissor nativo de notificacoes locais do Cardapio RU com deep link direto para dia e refeicao.
 */
internal object RuMealNotificationPoster {
    private const val TAG = "UnbPocketNotif"
    private const val CHANNEL_ID = "ru_meal_reminders"
    private const val CHANNEL_NAME = "Lembretes do Cardápio RU"
    private const val CHANNEL_DESC = "Avisa 1 hora antes das refeições escolhidas com os pratos do dia."

    fun post(
        context: Context,
        content: RuNotificacaoConteudo,
    ): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "RU notification blocked: POST_NOTIFICATIONS not granted")
                return false
            }
        }

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "RU notification blocked: system notifications disabled")
            return false
        }
        ensureChannel(manager)

        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(RuNotificationExtras.EXTRA_CAMPUS_SLUG, content.campus.slug)
                putExtra(RuNotificationExtras.EXTRA_DATA_TEXTO, content.dataTexto)
                putExtra(RuNotificationExtras.EXTRA_DAY_INDEX, content.dayIndex)
                putExtra(RuNotificationExtras.EXTRA_MEAL_PAGE_INDEX, content.refeicao.pageIndex)
            } ?: return false

        val requestCode = 7000 + content.dayIndex * 10 + content.refeicao.pageIndex
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ru)
            .setContentTitle(content.titulo)
            .setContentText(content.mensagem)
            .setStyle(Notification.BigTextStyle().bigText(content.mensagem))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()

        val notificationId = 9000 + content.refeicao.pageIndex
        manager.notify(notificationId, notification)
        Log.i(TAG, "Posted RU notification id=$notificationId meal=${content.refeicao.name} title='${content.titulo}'")
        return true
    }

    fun ensureChannel(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)?.let(::ensureChannel)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESC
        }
        manager.createNotificationChannel(channel)
    }
}
