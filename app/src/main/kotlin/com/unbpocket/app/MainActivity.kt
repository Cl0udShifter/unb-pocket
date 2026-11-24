package com.unbpocket.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.unbpocket.app.ui.UnbPocketApp
import com.unbpocket.app.ui.components.PortalZoomSplashOverlay
import com.unbpocket.app.ui.theme.ThemeMode
import com.unbpocket.app.ui.theme.ThemePreferenceStore
import com.unbpocket.app.ui.theme.UnbPocketTheme
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.notification.SigaaDeepLinkTarget
import com.unbpocket.core.model.notification.SigaaNotificacaoTarget
import com.unbpocket.core.model.notification.SigaaNotificationExtras
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.RuDeepLinkTarget
import com.unbpocket.core.model.ru.RuNotificationExtras
import com.unbpocket.core.model.ru.TipoRefeicao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingRuDeepLink by mutableStateOf<RuDeepLinkTarget?>(null)
    private var pendingSigaaDeepLink by mutableStateOf<SigaaDeepLinkTarget?>(null)

    @Volatile
    private var isAppReadyForSplashExit: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleDeepLinkIntent(intent)

        val app = application as UnbPocketApplication
        val initialCore = app.coreOrNull
        isAppReadyForSplashExit = initialCore != null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installNativePortalZoomSplash()
        }

        val themeStore = ThemePreferenceStore(applicationContext)

        setContent {
            var themeMode by remember { mutableStateOf(themeStore.load()) }
            var core by remember { mutableStateOf<UnbPocketCore?>(initialCore) }
            var showLegacySplash by rememberSaveable {
                mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            }

            LaunchedEffect(Unit) {
                if (core == null) {
                    core = withContext(Dispatchers.IO) { app.core }
                }
            }

            val selectThemeMode: (ThemeMode) -> Unit = { nextMode ->
                themeMode = nextMode
                themeStore.save(nextMode)
            }

            UnbPocketTheme(themeMode = themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF111112)),
                ) {
                    val activeCore = core
                    if (activeCore != null) {
                        UnbPocketApp(
                            core = activeCore,
                            themeMode = themeMode,
                            onSelectThemeMode = selectThemeMode,
                            pendingRuDeepLink = pendingRuDeepLink,
                            onConsumeRuDeepLink = { pendingRuDeepLink = null },
                            pendingSigaaDeepLink = pendingSigaaDeepLink,
                            onConsumeSigaaDeepLink = { pendingSigaaDeepLink = null },
                        )
                        LaunchedEffect(activeCore) {
                            withFrameNanos { }
                            isAppReadyForSplashExit = true
                            findViewById<View>(android.R.id.content)?.postInvalidateOnAnimation()
                        }
                    }
                    if (showLegacySplash) {
                        PortalZoomSplashOverlay(
                            onFinished = { showLegacySplash = false },
                        )
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun installNativePortalZoomSplash() {
        val contentView = findViewById<View>(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (isAppReadyForSplashExit) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            },
        )

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = splashScreenView.iconView
            val dipInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            val zoomInterpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)

            val exitAnimator = AnimatorSet()
            if (iconView != null) {
                val dipSet = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 0.88f),
                        ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 0.88f),
                    )
                    duration = 110L
                    interpolator = dipInterpolator
                }
                val zoomSet = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(iconView, View.SCALE_X, 0.88f, 2.8f),
                        ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 0.88f, 2.8f),
                        ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f),
                        ObjectAnimator.ofFloat(splashScreenView, View.ALPHA, 1f, 0f),
                    )
                    duration = 260L
                    interpolator = zoomInterpolator
                }
                exitAnimator.playSequentially(dipSet, zoomSet)
            } else {
                val fadeOut = ObjectAnimator.ofFloat(splashScreenView, View.ALPHA, 1f, 0f).apply {
                    duration = 220L
                    interpolator = zoomInterpolator
                }
                exitAnimator.play(fadeOut)
            }

            exitAnimator.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashScreenView.remove()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        splashScreenView.remove()
                    }
                },
            )
            exitAnimator.start()
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
