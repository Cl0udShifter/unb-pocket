package com.unbpocket.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.unbpocket.app.ui.UnbPocketApp
import com.unbpocket.app.ui.theme.ThemeMode
import com.unbpocket.app.ui.theme.ThemePreferenceStore
import com.unbpocket.app.ui.theme.UnbPocketTheme
import com.unbpocket.core.model.notification.SigaaDeepLinkTarget
import com.unbpocket.core.model.notification.SigaaNotificacaoTarget
import com.unbpocket.core.model.notification.SigaaNotificationExtras
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.RuDeepLinkTarget
import com.unbpocket.core.model.ru.RuNotificationExtras
import com.unbpocket.core.model.ru.TipoRefeicao

class MainActivity : ComponentActivity() {
    private var pendingRuDeepLink by mutableStateOf<RuDeepLinkTarget?>(null)
    private var pendingSigaaDeepLink by mutableStateOf<SigaaDeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleDeepLinkIntent(intent)

        val core = (application as UnbPocketApplication).core
        val themeStore = ThemePreferenceStore(applicationContext)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var themeMode by remember { mutableStateOf(themeStore.load()) }

            val toggleTheme = {
                val currentlyDark = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> systemDark
                }
                val nextMode = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
                themeMode = nextMode
                themeStore.save(nextMode)
            }

            UnbPocketTheme(themeMode = themeMode) {
                UnbPocketApp(
                    core = core,
                    themeMode = themeMode,
                    onToggleTheme = toggleTheme,
                    pendingRuDeepLink = pendingRuDeepLink,
                    onConsumeRuDeepLink = { pendingRuDeepLink = null },
                    pendingSigaaDeepLink = pendingSigaaDeepLink,
                    onConsumeSigaaDeepLink = { pendingSigaaDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent == null) return

        val sigaaTabKey = intent.getStringExtra(SigaaNotificationExtras.EXTRA_TARGET_TAB)
        val sigaaTarget = sigaaTabKey?.let { key ->
            SigaaNotificacaoTarget.entries.firstOrNull { it.routeTabKey == key }
        }
        if (sigaaTarget != null) {
            pendingSigaaDeepLink = SigaaDeepLinkTarget(target = sigaaTarget)
            intent.removeExtra(SigaaNotificationExtras.EXTRA_TARGET_TAB)
            intent.removeExtra(SigaaNotificationExtras.EXTRA_NOTIFICATION_TYPE)
        }

        val mealPageIndex = intent.getIntExtra(RuNotificationExtras.EXTRA_MEAL_PAGE_INDEX, -1)
        val meal = TipoRefeicao.entries.firstOrNull { it.pageIndex == mealPageIndex } ?: return
        val campusSlug = intent.getStringExtra(RuNotificationExtras.EXTRA_CAMPUS_SLUG)
        val campus = campusSlug?.let { slug -> CampusRu.entries.firstOrNull { it.slug == slug } }
        val dataTexto = intent.getStringExtra(RuNotificationExtras.EXTRA_DATA_TEXTO)
        val dayIndex = intent.getIntExtra(RuNotificationExtras.EXTRA_DAY_INDEX, -1).takeIf { it >= 0 }

        pendingRuDeepLink = RuDeepLinkTarget(
            campus = campus,
            dataTexto = dataTexto,
            dayIndex = dayIndex,
            refeicao = meal,
        )

        intent.removeExtra(RuNotificationExtras.EXTRA_MEAL_PAGE_INDEX)
        intent.removeExtra(RuNotificationExtras.EXTRA_CAMPUS_SLUG)
        intent.removeExtra(RuNotificationExtras.EXTRA_DATA_TEXTO)
        intent.removeExtra(RuNotificationExtras.EXTRA_DAY_INDEX)
    }
}
