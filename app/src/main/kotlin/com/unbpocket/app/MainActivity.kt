package com.unbpocket.app

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                )
            }
        }
    }
}
