package com.unbpocket.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

@Immutable
data class UnbPocketColors(
    val isDark: Boolean,
    val ground: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val rule: Color,
    val ink: Color,
    val muted: Color,
    val pillActiveBg: Color,
    val pillActiveInk: Color,
    val navBg: Color,
    val navBorder: Color,
    val tagStdBg: Color,
    val tagStdInk: Color,
    val tagOvoBg: Color,
    val tagOvoInk: Color,
    val tagVegBg: Color,
    val tagVegInk: Color,
    val warningBg: Color,
    val warningInk: Color,
    val dangerBg: Color,
    val dangerInk: Color,
    val dangerText: Color,
    val infoBg: Color,
    val infoInk: Color,
    val qrCanvas: Color,
    val qrInk: Color,
)

val DarkUnbPalette = UnbPocketColors(
    isDark = true,
    ground = Color(0xFF111112),
    surface = Color(0xFF1A1A1C),
    surfaceRaised = Color(0xFF232326),
    border = Color(0xFF2B2B30),
    rule = Color(0xFF26262A),
    ink = Color(0xFFF3F3F2),
    muted = Color(0xFF9A9AA0),
    pillActiveBg = Color(0xFFF2F2F0),
    pillActiveInk = Color(0xFF111112),
    navBg = Color(0xFF1C1C1E),
    navBorder = Color(0xFF2D2D32),
    tagStdBg = Color(0xFFEAEAE7),
    tagStdInk = Color(0xFF141414),
    tagOvoBg = Color(0xFFF7ECC8),
    tagOvoInk = Color(0xFF3E2D00),
    tagVegBg = Color(0xFFDFF0E4),
    tagVegInk = Color(0xFF0D4322),
    warningBg = Color(0xFF332512),
    warningInk = Color(0xFFF5B84D),
    dangerBg = Color(0xFF3A1B1E),
    dangerInk = Color(0xFFFF8F98),
    dangerText = Color(0xFFEF5350),
    infoBg = Color(0xFF1B2B3D),
    infoInk = Color(0xFF7DB9FF),
    qrCanvas = Color(0xFFFFFFFF),
    qrInk = Color(0xFF0B0C0E),
)

val LightUnbPalette = UnbPocketColors(
    isDark = false,
    ground = Color(0xFFF5F5F3),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFECECE9),
    border = Color(0xFFE2E2DE),
    rule = Color(0xFFECECE8),
    ink = Color(0xFF141413),
    muted = Color(0xFF61615E),
    pillActiveBg = Color(0xFF141413),
    pillActiveInk = Color(0xFFFFFFFF),
    navBg = Color(0xFFFFFFFF),
    navBorder = Color(0xFFDFDFDA),
    tagStdBg = Color(0xFFEAEAE7),
    tagStdInk = Color(0xFF141414),
    tagOvoBg = Color(0xFFF7ECC8),
    tagOvoInk = Color(0xFF3E2D00),
    tagVegBg = Color(0xFFDFF0E4),
    tagVegInk = Color(0xFF0D4322),
    warningBg = Color(0xFFFFF4DC),
    warningInk = Color(0xFF8A4F00),
    dangerBg = Color(0xFFFFE4E6),
    dangerInk = Color(0xFF9F1239),
    dangerText = Color(0xFFC62828),
    infoBg = Color(0xFFE3EFFC),
    infoInk = Color(0xFF0B4C8C),
    qrCanvas = Color(0xFFFFFFFF),
    qrInk = Color(0xFF0B0C0E),
)

object UnbSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object UnbShapes {
    val tag = RoundedCornerShape(8.dp)
    val inner = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val capsule = CircleShape
}

val LocalUnbColors = staticCompositionLocalOf { DarkUnbPalette }

class ThemePreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("unb_pocket_ui_prefs", Context.MODE_PRIVATE)

    fun load(): ThemeMode {
        val raw = prefs.getString("theme_mode", ThemeMode.DARK.name)
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
    }

    fun save(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }
}
