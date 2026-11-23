package com.unbpocket.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.unbpocket.app.R

val SourceSerif4Family = FontFamily(
    Font(R.font.source_serif_4_regular, FontWeight.Normal),
    Font(R.font.source_serif_4_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif_4_bold, FontWeight.Bold),
)

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

@Immutable
data class UnbTypographyTokens(
    val displayEditorial: TextStyle = TextStyle(
        fontFamily = SourceSerif4Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.42).sp,
    ),
    val metricSerif: TextStyle = TextStyle(
        fontFamily = SourceSerif4Family,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = "tnum",
    ),
    val metricBalance: TextStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontFeatureSettings = "tnum",
    ),
    val title: TextStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    val titleStrong: TextStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    val bodyTabular: TextStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    ),
    val label: TextStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
    ),
    val overline: TextStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.66.sp,
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

val LocalUnbTypography = staticCompositionLocalOf { UnbTypographyTokens() }

fun unbMaterialTypography(tokens: UnbTypographyTokens = UnbTypographyTokens()): Typography = Typography(
    displaySmall = tokens.displayEditorial,
    headlineMedium = tokens.displayEditorial,
    headlineSmall = tokens.metricSerif,
    titleLarge = tokens.title,
    titleMedium = tokens.titleStrong,
    titleSmall = tokens.label,
    bodyLarge = tokens.body,
    bodyMedium = tokens.body,
    bodySmall = tokens.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = tokens.label,
    labelMedium = tokens.overline,
    labelSmall = tokens.mono,
)
