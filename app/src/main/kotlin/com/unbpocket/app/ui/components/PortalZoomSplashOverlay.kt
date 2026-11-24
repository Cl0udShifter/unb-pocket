package com.unbpocket.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.unbpocket.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashBackgroundColor = Color(0xFF111112)
private val PortalZoomEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
fun PortalZoomSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconScale = remember { Animatable(0.85f) }
    val iconAlpha = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // 1. Crisp entrance
        val enterScale = launch {
            iconScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 240, easing = PortalZoomEasing),
            )
        }
        val enterAlpha = launch {
            iconAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
        }
        enterScale.join()
        enterAlpha.join()

        // 2. Brief grounded hold
        delay(110L)

        // 3. Subtle anticipation dip
        iconScale.animateTo(
            targetValue = 0.90f,
            animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        )

        // 4. Portal zoom expansion + fade revealing app underneath
        val zoomOut = launch {
            iconScale.animateTo(
                targetValue = 2.8f,
                animationSpec = tween(durationMillis = 260, easing = PortalZoomEasing),
            )
        }
        val fadeIcon = launch {
            iconAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        }
        val fadeBackdrop = launch {
            backdropAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = PortalZoomEasing),
            )
        }
        zoomOut.join()
        fadeIcon.join()
        fadeBackdrop.join()

        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = backdropAlpha.value }
            .background(SplashBackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(152.dp)
                .graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                    alpha = iconAlpha.value
                },
        )
    }
}
