package com.nebula.vpn.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AmbientBackground(
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")

    // Slow rotation for nebula effect
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // Pulsing glow intensity
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = if (active) 0.35f else 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowIntensity",
    )

    // Generate nebula orbs (fixed positions)
    val orbs = remember {
        List(6) { i ->
            val angle = (i * 60f) * Math.PI.toFloat() / 180f
            val radius = 0.3f + Random.nextFloat() * 0.3f
            Triple(
                cos(angle) * radius,
                sin(angle) * radius,
                100f + Random.nextFloat() * 150f,
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.width.coerceAtLeast(size.height) * 0.6f

        // Background gradient
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF12121F),
                    Color(0xFF0A0A12),
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius,
            ),
        )

        // Nebula orbs
        orbs.forEach { (ox, oy, orbRadius) ->
            val x = centerX + ox * size.width
            val y = centerY + oy * size.height
            val angle = (rotation + ox * 100) * Math.PI.toFloat() / 180f
            val animatedX = x + cos(angle) * 30f
            val animatedY = y + sin(angle) * 30f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = glowIntensity),
                        accent.copy(alpha = glowIntensity * 0.3f),
                        Color.Transparent,
                    ),
                    center = Offset(animatedX, animatedY),
                    radius = orbRadius,
                ),
                radius = orbRadius,
                center = Offset(animatedX, animatedY),
            )
        }

        // Central glow when active
        if (active) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.2f),
                        accent.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 0.5f,
                ),
                radius = maxRadius * 0.5f,
                center = Offset(centerX, centerY),
            )
        }

        // Stars
        val starCount = 30
        repeat(starCount) { i ->
            val starX = (Random(i).nextFloat() * size.width)
            val starY = (Random(i + 1000).nextFloat() * size.height)
            val starSize = 1f + Random(i + 2000).nextFloat() * 2f
            val starAlpha = 0.3f + Random(i + 3000).nextFloat() * 0.5f

            drawCircle(
                color = Color.White.copy(alpha = starAlpha),
                radius = starSize,
                center = Offset(starX, starY),
            )
        }
    }
}
