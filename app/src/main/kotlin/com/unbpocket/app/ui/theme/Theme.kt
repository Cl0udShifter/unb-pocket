package com.unbpocket.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object UnbTheme {
    val colors: UnbPocketColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUnbColors.current

    val typography: UnbTypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalUnbTypography.current
}

@Composable
fun UnbPocketTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val palette = if (useDark) DarkUnbPalette else LightUnbPalette
    val typographyTokens = UnbTypographyTokens()

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = palette.pillActiveBg,
            onPrimary = palette.pillActiveInk,
            primaryContainer = palette.surfaceRaised,
            onPrimaryContainer = palette.ink,
            secondary = palette.surfaceRaised,
            onSecondary = palette.ink,
            secondaryContainer = palette.surfaceRaised,
            onSecondaryContainer = palette.ink,
            background = palette.ground,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.rule,
            error = palette.dangerText,
            onError = palette.qrCanvas,
            errorContainer = palette.dangerBg,
            onErrorContainer = palette.dangerInk,
        )
    } else {
        lightColorScheme(
            primary = palette.pillActiveBg,
            onPrimary = palette.pillActiveInk,
            primaryContainer = palette.surfaceRaised,
            onPrimaryContainer = palette.ink,
            secondary = palette.surfaceRaised,
            onSecondary = palette.ink,
            secondaryContainer = palette.surfaceRaised,
            onSecondaryContainer = palette.ink,
            background = palette.ground,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.rule,
            error = palette.dangerText,
            onError = palette.qrCanvas,
            errorContainer = palette.dangerBg,
            onErrorContainer = palette.dangerInk,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDark
            insetsController.isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(
        LocalUnbColors provides palette,
        LocalUnbTypography provides typographyTokens,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = unbMaterialTypography(typographyTokens),
            content = content,
        )
    }
}
