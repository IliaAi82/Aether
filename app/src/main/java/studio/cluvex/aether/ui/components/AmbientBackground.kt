package studio.cluvex.aether.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slowly drifting aurora blobs behind the whole screen. Brightens when a
 * connection is active for a subtle, premium feel.
 */
@Composable
fun AmbientBackground(
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "drift",
    )
    val intensity by animateFloatAsState(
        targetValue = if (active) 0.75f else 0.35f,
        animationSpec = tween(1200),
        label = "intensity",
    )

    Canvas(modifier = modifier.fillMaxSize().background(Color(0xFF0A0E1A))) {
        val w = size.width
        val h = size.height

        val c1 = Offset(w * (0.30f + 0.18f * cos(t)), h * (0.24f + 0.10f * sin(t)))
        val c2 = Offset(w * (0.72f + 0.15f * sin(t * 0.8f)), h * (0.70f + 0.12f * cos(t * 0.6f)))
        val c3 = Offset(w * (0.50f + 0.20f * cos(t * 0.5f)), h * (0.90f + 0.06f * sin(t)))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.35f * intensity), Color.Transparent),
                center = c1,
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = c1,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF32E0C4).copy(alpha = 0.22f * intensity), Color.Transparent),
                center = c2,
                radius = w * 0.70f,
            ),
            radius = w * 0.70f,
            center = c2,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7C4DFF).copy(alpha = 0.18f * intensity), Color.Transparent),
                center = c3,
                radius = w * 0.60f,
            ),
            radius = w * 0.60f,
            center = c3,
        )
    }
}
