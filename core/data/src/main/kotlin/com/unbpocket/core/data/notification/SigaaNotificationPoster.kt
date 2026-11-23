package com.unbpocket.core.data.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.unbpocket.core.data.R
import com.unbpocket.core.data.local.SigaaNotificationPosterPort
import com.unbpocket.core.model.notification.SigaaNotificacaoConteudo
import com.unbpocket.core.model.notification.SigaaNotificacaoTarget
import com.unbpocket.core.model.notification.SigaaNotificacaoTipo
import com.unbpocket.core.model.notification.SigaaNotificationExtras

/**
 * Constroi canais e posta notificacoes locais de Aulas & Turmas e Painel Discente.
 */
class SigaaNotificationPoster(
    private val context: Context,
) : SigaaNotificationPosterPort {

    override fun post(notifications: List<SigaaNotificacaoConteudo>) {
        if (notifications.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!hasPermission(context, manager)) return

        ensureChannels(manager)
        notifications.forEach { content ->
            postSingle(context, manager, content)
        }
    }

    @SuppressLint("MissingPermission")
    private fun postSingle(
        context: Context,
        manager: NotificationManager,
        content: SigaaNotificacaoConteudo,
    ) {
        val channelId = channelIdFor(content.target)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(SigaaNotificationExtras.EXTRA_TARGET_TAB, content.target.routeTabKey)
            putExtra(SigaaNotificationExtras.EXTRA_NOTIFICATION_TYPE, content.tipo.name)
        }

        val contentIntent = launchIntent?.let { intent ->
            PendingIntent.getActivity(
                context,
                REQUEST_CODE_BASE + (content.notificationId and 0x0FFF_FFFF),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val expandedText = content.detalhesExpandido?.takeIf { it.isNotBlank() } ?: content.resumo
        val category = when (content.tipo) {
            SigaaNotificacaoTipo.AULA_PROXIMA,
            SigaaNotificacaoTipo.AVALIACAO_OU_TAREFA,
            -> Notification.CATEGORY_REMINDER
            else -> Notification.CATEGORY_STATUS
        }

        val builder = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_ru)
            .setContentTitle(content.titulo)
            .setContentText(content.resumo)
            .setStyle(Notification.BigTextStyle().bigText(expandedText))
            .setCategory(category)
            .setAutoCancel(true)
            .apply {
                if (contentIntent != null) {
                    setContentIntent(contentIntent)
                }
            }

        manager.notify(content.notificationId, builder.build())
    }

    companion object {
        const val CHANNEL_TURMAS_ID: String = "unb_pocket_sigaa_turmas"
        const val CHANNEL_DISCENTE_ID: String = "unb_pocket_sigaa_discente"
        private const val REQUEST_CODE_BASE: Int = 5000

        fun hasPermission(
            context: Context,
            manager: NotificationManager? = context.getSystemService(NotificationManager::class.java),
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }
            return manager?.areNotificationsEnabled() ?: true
        }

        fun ensureChannels(manager: NotificationManager) {
            if (manager.getNotificationChannel(CHANNEL_TURMAS_ID) == null) {
                val turmasChannel = NotificationChannel(
                    CHANNEL_TURMAS_ID,
                    "Aulas e Turmas",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Lembretes de aulas, provas, tarefas, notas parciais, avisos e risco de faltas."
                }
                manager.createNotificationChannel(turmasChannel)
            }
            if (manager.getNotificationChannel(CHANNEL_DISCENTE_ID) == null) {
                val discenteChannel = NotificationChannel(
                    CHANNEL_DISCENTE_ID,
                    "Painel Discente",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alertas de saldo do RU, extrato, boletim final e índices acadêmicos."
                }
                manager.createNotificationChannel(discenteChannel)
            }
        }

        private fun channelIdFor(target: SigaaNotificacaoTarget): String = when (target) {
            SigaaNotificacaoTarget.TURMAS -> CHANNEL_TURMAS_ID
            SigaaNotificacaoTarget.DISCENTE -> CHANNEL_DISCENTE_ID
        }
    }
}
