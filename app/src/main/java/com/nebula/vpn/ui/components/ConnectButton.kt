package com.nebula.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nebula.vpn.ui.theme.NebulaAccent
import com.nebula.vpn.ui.theme.NebulaError
import com.nebula.vpn.ui.theme.NebulaOnDark
import com.nebula.vpn.ui.theme.NebulaPrimary
import com.nebula.vpn.ui.theme.NebulaPrimaryDark

enum class ButtonMode { IDLE, BUSY, CONNECTED, ERROR }

@Composable
fun ConnectButton(
    mode: ButtonMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val idleColor = NebulaPrimary
    val connectedColor = NebulaAccent
    val errorColor = NebulaError
    val busyColor = NebulaOnDark

    val targetColor = when (mode) {
        ButtonMode.CONNECTED -> connectedColor
        ButtonMode.ERROR -> errorColor
        ButtonMode.BUSY -> busyColor
        ButtonMode.IDLE -> idleColor
    }

    val buttonColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "buttonColor",
    )

    // Pulsing glow for connected state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    // Rotation for busy state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // Glow color
    val glowColor = when (mode) {
        ButtonMode.CONNECTED -> NebulaAccent.copy(alpha = pulseAlpha)
        ButtonMode.BUSY -> NebulaOnDark.copy(alpha = 0.3f)
        ButtonMode.ERROR -> NebulaError.copy(alpha = pulseAlpha)
        ButtonMode.IDLE -> NebulaPrimary.copy(alpha = pulseAlpha * 0.5f)
    }

    Box(
        modifier = modifier.size(size + 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow ring
        Canvas(modifier = Modifier.size(size + 40.dp)) {
            val strokeWidth = 3.dp.toPx()
            drawCircle(
                color = glowColor,
                radius = this.size.minDimension / 2 - strokeWidth,
                style = Stroke(width = strokeWidth),
            )
        }

        // Animated arc ring (busy state)
        if (mode == ButtonMode.BUSY) {
            Canvas(modifier = Modifier.size(size + 10.dp)) {
                rotate(rotation) {
                    drawArc(
                        color = buttonColor.copy(alpha = 0.7f),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
        }

        // Main button circle
        Canvas(modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        ) {
            // Background gradient
            val gradient = Brush.radialGradient(
                colors = listOf(
                    buttonColor.copy(alpha = 1f),
                    buttonColor.copy(alpha = 0.7f),
                ),
                center = Offset(size.toPx() / 2, size.toPx() / 2),
                radius = size.toPx() / 2,
            )
            drawCircle(
                brush = gradient,
                radius = this.size.minDimension / 2,
            )

            // Inner ring
            drawCircle(
                color = NebulaOnDark.copy(alpha = 0.2f),
                radius = this.size.minDimension / 2 - 8.dp.toPx(),
                style = Stroke(width = 2.dp.toPx()),
            )

            // Center icon
            val iconSize = size.toPx() * 0.25f
            when (mode) {
                ButtonMode.CONNECTED -> {
                    // Power icon (on)
                    drawCircle(
                        color = Color.White,
                        radius = iconSize * 0.4f,
                        center = Offset(size.toPx() / 2, size.toPx() / 2),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = iconSize * 0.8f,
                        center = Offset(size.toPx() / 2, size.toPx() / 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.toPx() / 2, size.toPx() / 2 - iconSize * 0.4f),
                        end = Offset(size.toPx() / 2, size.toPx() / 2 - iconSize),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                ButtonMode.IDLE -> {
                    // Power icon (off)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = iconSize * 0.4f,
                        center = Offset(size.toPx() / 2, size.toPx() / 2),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = iconSize * 0.8f,
                        center = Offset(size.toPx() / 2, size.toPx() / 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(size.toPx() / 2, size.toPx() / 2 - iconSize * 0.4f),
                        end = Offset(size.toPx() / 2, size.toPx() / 2 - iconSize),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                ButtonMode.BUSY -> {
                    // Connecting dots
                    for (i in 0..2) {
                        val angle = (i * 120f + rotation) * Math.PI.toFloat() / 180f
                        val x = size.toPx() / 2 + kotlin.math.cos(angle) * iconSize * 0.6f
                        val y = size.toPx() / 2 + kotlin.math.sin(angle) * iconSize * 0.6f
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }
                ButtonMode.ERROR -> {
                    // X icon
                    drawLine(
                        color = Color.White,
                        start = Offset(size.toPx() / 2 - iconSize * 0.4f, size.toPx() / 2 - iconSize * 0.4f),
                        end = Offset(size.toPx() / 2 + iconSize * 0.4f, size.toPx() / 2 + iconSize * 0.4f),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.toPx() / 2 + iconSize * 0.4f, size.toPx() / 2 - iconSize * 0.4f),
                        end = Offset(size.toPx() / 2 - iconSize * 0.4f, size.toPx() / 2 + iconSize * 0.4f),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
