package com.unbpocket.app.ui.components

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unbpocket.app.ui.common.DietaryBadgeKind
import com.unbpocket.app.ui.common.Formatters
import com.unbpocket.app.ui.common.UiErrorBanner
import com.unbpocket.app.ui.theme.UnbShapes
import com.unbpocket.app.ui.theme.UnbSpacing
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.core.model.common.DataOrigin
import com.unbpocket.core.model.common.PreloadPhase
import com.unbpocket.core.model.common.PreloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    RU(route = "ru", label = "RU", icon = Icons.Rounded.Restaurant),
    TURMAS(route = "turmas", label = "Turmas", icon = Icons.Rounded.CalendarMonth),
    DISCENTE(route = "discente", label = "Discente", icon = Icons.Rounded.School),
}

enum class StatusTagTone {
    NEUTRAL,
    INFO,
    WARNING,
    DANGER,
    INVERTED,
}

@Composable
fun EditorialHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = UnbSpacing.lg, vertical = UnbSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(UnbSpacing.sm))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.displayEditorial,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    subtitle()
                }
            }
        }
        Spacer(modifier = Modifier.width(UnbSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = actions,
        )
    }
}

@Composable
fun BackHeaderButton(
    onClick: () -> Unit,
    contentDescription: String = "Voltar",
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(UnbTheme.colors.surface)
            .border(1.dp, UnbTheme.colors.border, CircleShape),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
            tint = UnbTheme.colors.ink,
        )
    }
}

@Composable
fun HeaderActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    isLoading: Boolean = false,
    tint: Color = UnbTheme.colors.ink,
) {
    val colors = UnbTheme.colors
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier.size(48.dp),
        ) {
            if (isLoading) {
                SkeletonInlineBar(
                    width = 20.dp,
                    height = 6.dp,
                    color = colors.ink,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (showDot && !isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 11.dp, end = 11.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.ink),
            )
        }
    }
}

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(UnbSpacing.lg),
    containerColor: Color = UnbTheme.colors.surface,
    borderColor: Color = UnbTheme.colors.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(UnbShapes.card)
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = UnbShapes.card,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun InnerTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(UnbSpacing.md),
    containerColor: Color = UnbTheme.colors.surfaceRaised,
    borderColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clip(UnbShapes.inner)
                    .clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = UnbShapes.inner,
        color = containerColor,
        border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = UnbTheme.colors.rule,
    )
}

@Composable
fun SegmentedPillBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Tab) { onSelect(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) colors.surfaceRaised else Color.Transparent,
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            style = typography.label,
                            color = if (selected) colors.ink else colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DietaryBadge(
    kind: DietaryBadgeKind,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    val (bg, fg) = when (kind) {
        DietaryBadgeKind.PADRAO -> colors.tagStdBg to colors.tagStdInk
        DietaryBadgeKind.OVOLACTO -> colors.tagOvoBg to colors.tagOvoInk
        DietaryBadgeKind.VEGANO -> colors.tagVegBg to colors.tagVegInk
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 76.dp)
            .clip(UnbShapes.tag)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kind.label,
            style = UnbTheme.typography.label,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTagTone = StatusTagTone.NEUTRAL,
) {
    val colors = UnbTheme.colors
    val (bg, fg) = when (tone) {
        StatusTagTone.NEUTRAL -> colors.surfaceRaised to colors.muted
        StatusTagTone.INFO -> colors.infoBg to colors.infoInk
        StatusTagTone.WARNING -> colors.warningBg to colors.warningInk
        StatusTagTone.DANGER -> colors.dangerBg to colors.dangerInk
        StatusTagTone.INVERTED -> colors.pillActiveBg to colors.pillActiveInk
    }

    Box(
        modifier = modifier
            .clip(UnbShapes.tag)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = UnbTheme.typography.label,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun OfflineOriginStrip(
    origin: DataOrigin,
    syncedAtEpochMillis: Long,
    modifier: Modifier = Modifier,
    extraNote: String? = null,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val text = buildString {
        append(Formatters.formatOriginSummary(origin, syncedAtEpochMillis))
        if (!extraNote.isNullOrBlank()) {
            append(" · ")
            append(extraNote)
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val dotColor = when (origin) {
            DataOrigin.REMOTE -> colors.muted
            DataOrigin.CACHE -> colors.warningInk
            DataOrigin.FALLBACK -> colors.infoInk
        }
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = text,
            style = typography.label,
            color = colors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ErrorBanner(
    banner: UiErrorBanner,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onLoginClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography
    val bg = if (banner.isWarning) colors.warningBg else colors.dangerBg
    val fg = if (banner.isWarning) colors.warningInk else colors.dangerInk

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = UnbShapes.card,
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.3f)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(UnbSpacing.md)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (banner.isWarning) {
                            Icons.Rounded.WarningAmber
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = banner.title,
                        style = typography.title,
                        color = fg,
                    )
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar aviso",
                            tint = fg,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = banner.message,
                style = typography.body,
                color = fg,
            )
            if ((banner.requiresLogin && onLoginClick != null) || (banner.canRetry && onRetry != null)) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (banner.requiresLogin && onLoginClick != null) {
                        Button(
                            onClick = onLoginClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = fg,
                                contentColor = bg,
                            ),
                            shape = UnbShapes.tag,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(text = "Entrar no SIGAA", style = typography.label)
                        }
                    }
                    if (banner.canRetry && onRetry != null) {
                        OutlinedButton(
                            onClick = onRetry,
                            border = BorderStroke(1.dp, fg),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
                            shape = UnbShapes.tag,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(text = "Tentar novamente", style = typography.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    EditorialCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = UnbSpacing.sm),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(UnbShapes.inner)
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(UnbSpacing.md))
            Text(
                text = title,
                style = typography.title,
                color = colors.ink,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = typography.body,
                color = colors.muted,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(UnbSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(UnbSpacing.sm)) {
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.pillActiveBg,
                            contentColor = colors.pillActiveInk,
                        ),
                        shape = UnbShapes.inner,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = actionLabel, style = typography.label)
                    }
                    if (secondaryLabel != null && onSecondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                            shape = UnbShapes.inner,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text(text = secondaryLabel, style = typography.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSkeletonAlpha(): Float {
    val animatorsEnabled = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
    if (!animatorsEnabled) return 0.88f
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = UnbShapes.inner,
    color: Color = UnbTheme.colors.surfaceRaised,
) {
    val alpha = rememberSkeletonAlpha()
    Box(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = color.alpha * alpha)),
    )
}

@Composable
fun SkeletonInlineBar(
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 8.dp,
    color: Color = UnbTheme.colors.surfaceRaised,
) {
    SkeletonBox(
        modifier = modifier
            .width(width)
            .height(height),
        shape = CircleShape,
        color = color,
    )
}

@Composable
fun PreloadStatusBanner(
    progress: PreloadProgress,
    modifier: Modifier = Modifier,
    containerColor: Color = UnbTheme.colors.surface,
    trackColor: Color = UnbTheme.colors.surfaceRaised,
) {
    val isActive = progress.phase == PreloadPhase.RUNNING_ESSENTIAL ||
        progress.phase == PreloadPhase.RUNNING_BACKGROUND
    val colors = UnbTheme.colors
    val typography = UnbTheme.typography

    AnimatedVisibility(
        visible = isActive,
        enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)),
        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(140)),
        modifier = modifier,
    ) {
        EditorialCard(
            contentPadding = PaddingValues(horizontal = UnbSpacing.md, vertical = 12.dp),
            containerColor = containerColor,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (progress.phase) {
                        PreloadPhase.RUNNING_ESSENTIAL -> "SINCRONIZANDO DADOS ESSENCIAIS"
                        PreloadPhase.RUNNING_BACKGROUND -> "CARREGANDO CACHE OFFLINE"
                        PreloadPhase.IDLE, PreloadPhase.COMPLETED -> ""
                    },
                    style = typography.overline,
                    color = colors.muted,
                )
                if (progress.totalSteps > 0) {
                    Text(
                        text = "${progress.completedSteps.coerceAtMost(progress.totalSteps)}/${progress.totalSteps}",
                        style = typography.mono,
                        color = colors.ink,
                    )
                } else {
                    SkeletonInlineBar(
                        width = 24.dp,
                        height = 6.dp,
                        color = colors.muted,
                    )
                }
            }

            progress.currentTarget?.takeIf { it.isNotBlank() }?.let { target ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = target,
                    style = typography.label,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (progress.totalSteps > 0) {
                val targetFraction = (progress.completedSteps.toFloat() / progress.totalSteps.toFloat())
                    .coerceIn(0.06f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "preloadProgressFraction",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(trackColor),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.pillActiveBg),
                    )
                }
            } else {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = colors.muted.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnbPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = UnbTheme.colors
    val state = rememberPullToRefreshState()
    val pullFraction = state.distanceFraction.coerceIn(0f, 1.35f)
    val showIndicator = isRefreshing || pullFraction > 0.04f

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            if (showIndicator) {
                val visualFraction = if (isRefreshing) 1f else pullFraction.coerceIn(0f, 1f)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .graphicsLayer {
                            alpha = visualFraction
                            translationY = if (isRefreshing) 0f else (visualFraction - 1f) * 18f
                        },
                    shape = CircleShape,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isRefreshing) {
                            SkeletonInlineBar(
                                width = 38.dp,
                                height = 6.dp,
                                color = colors.ink,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width((16 + 22 * visualFraction).dp)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.muted.copy(alpha = 0.55f + 0.3f * visualFraction)),
                            )
                        }
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    lineCount: Int = 4,
) {
    EditorialCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(12.dp),
                shape = RoundedCornerShape(4.dp),
            )
            repeat(lineCount) { idx ->
                val fraction = if (idx % 2 == 0) 0.92f else 0.72f
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(18.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
        }
    }
}

@Composable
fun InfoCalloutRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(UnbShapes.inner)
            .background(colors.surfaceRaised)
            .padding(UnbSpacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Text(
            text = text,
            style = UnbTheme.typography.body,
            color = colors.muted,
        )
    }
}

@Composable
fun CapsuleBottomBar(
    currentDestination: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = UnbSpacing.lg, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = colors.navBg,
            border = BorderStroke(1.dp, colors.navBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopDestination.entries.forEach { dest ->
                    val selected = dest == currentDestination
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.pillActiveBg else Color.Transparent)
                            .clickable(role = Role.Tab) { onSelect(dest) }
                            .semantics { contentDescription = dest.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = if (selected) colors.pillActiveInk else colors.muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnbNavigationRail(
    currentDestination: TopDestination,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UnbTheme.colors
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .border(width = 1.dp, color = colors.border),
        containerColor = colors.navBg,
        contentColor = colors.ink,
    ) {
        Spacer(modifier = Modifier.height(UnbSpacing.lg))
        TopDestination.entries.forEach { dest ->
            val selected = dest == currentDestination
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                    )
                },
                label = {
                    Text(
                        text = dest.label,
                        style = UnbTheme.typography.label,
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = colors.pillActiveInk,
                    selectedTextColor = colors.ink,
                    indicatorColor = colors.pillActiveBg,
                    unselectedIconColor = colors.muted,
                    unselectedTextColor = colors.muted,
                ),
            )
        }
    }
}

@Composable
fun LocalBitmapImage(
    localPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {},
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = localPath) {
        value = if (localPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(localPath)
                    if (!file.exists()) return@runCatching null
                    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
                    if (isBlankOrUniformBitmap(decoded)) {
                        decoded.recycle()
                        return@runCatching null
                    }
                    decoded.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        fallback()
    }
}

private fun isBlankOrUniformBitmap(bitmap: Bitmap): Boolean {
    if (bitmap.width <= 0 || bitmap.height <= 0) return true
    val steps = 12
    var minR = 255
    var maxR = 0
    var minG = 255
    var maxG = 0
    var minB = 255
    var maxB = 0
    var hasOpaque = false

    for (iy in 0 until steps) {
        val y = (iy * (bitmap.height - 1)) / (steps - 1).coerceAtLeast(1)
        for (ix in 0 until steps) {
            val x = (ix * (bitmap.width - 1)) / (steps - 1).coerceAtLeast(1)
            val px = bitmap.getPixel(x, y)
            val a = (px ushr 24) and 0xFF
            if (a < 16) continue
            hasOpaque = true
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            if (r < minR) minR = r
            if (r > maxR) maxR = r
            if (g < minG) minG = g
            if (g > maxG) maxG = g
            if (b < minB) minB = b
            if (b > maxB) maxB = b
        }
    }

    if (!hasOpaque) return true
    val spread = maxOf(maxR - minR, maxG - minG, maxB - minB)
    return spread <= 10
}

@Composable
fun RemoteBitmapImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    minHeight: Dp = 200.dp,
    onFailed: @Composable () -> Unit = {},
) {
    val state by produceState<Pair<Boolean, ImageBitmap?>>(initialValue = true to null, key1 = imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            value = false to null
            return@produceState
        }
        value = true to null
        val loaded: Bitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        value = false to loaded?.asImageBitmap()
    }

    val (loading, bitmap) = state
    when {
        loading -> {
            SkeletonBox(
                modifier = modifier
                    .fillMaxWidth()
                    .height(minHeight),
                shape = UnbShapes.inner,
            )
        }

        bitmap != null -> {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = modifier
                    .fillMaxWidth()
                    .clip(UnbShapes.inner),
                contentScale = ContentScale.FillWidth,
            )
        }

        else -> onFailed()
    }
}

@Composable
fun AnimatedSectionVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
    ) {
        content()
    }
}
