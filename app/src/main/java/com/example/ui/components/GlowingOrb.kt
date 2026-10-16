package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.SessionState
import com.example.ui.theme.GirlfriendPink
import com.example.ui.theme.GirlfriendRose
import com.example.ui.theme.JarvisNeonBlue
import com.example.ui.theme.JarvisNeonCyan
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlowingOrb(
    sessionState: SessionState,
    audioLevel: Float, // 0.0 to 1.0
    isGirlfriendMode: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    val primaryColor = if (isGirlfriendMode) GirlfriendRose else JarvisNeonCyan
    val secondaryColor = if (isGirlfriendMode) GirlfriendPink else JarvisNeonBlue

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    // Breathing pulse for idle
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    // Rotation for thinking swirling gradient
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Wave ring expansion
    val ringExpansion by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_expansion"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_alpha"
    )

    val scaleMultiplier = when (sessionState) {
        SessionState.SPEAKING -> 1.0f + (audioLevel * 0.45f)
        SessionState.LISTENING -> 1.0f + (audioLevel * 0.35f)
        SessionState.THINKING -> 1.08f
        SessionState.CONNECTING -> 1.04f
        else -> idlePulse
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.55f * scaleMultiplier

            // 1. Outer animated expanding ring for Listening/Speaking
            if (sessionState == SessionState.LISTENING || sessionState == SessionState.SPEAKING) {
                drawCircle(
                    color = primaryColor.copy(alpha = ringAlpha),
                    radius = baseRadius * ringExpansion,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // 2. Outermost soft aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.55f
                ),
                radius = baseRadius * 1.55f,
                center = center
            )

            // 3. Middle glowing energy field
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.65f),
                        secondaryColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.25f
                ),
                radius = baseRadius * 1.25f,
                center = center
            )

            // 4. Rotating energy arcs if thinking
            if (sessionState == SessionState.THINKING) {
                val rad = Math.toRadians(rotationAngle.toDouble())
                val arcOffset = Offset(
                    center.x + (cos(rad) * baseRadius * 0.3f).toFloat(),
                    center.y + (sin(rad) * baseRadius * 0.3f).toFloat()
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, primaryColor, secondaryColor),
                        center = arcOffset,
                        radius = baseRadius * 0.8f
                    ),
                    radius = baseRadius * 0.95f,
                    center = center
                )
            } else {
                // 5. Solid core
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            primaryColor,
                            secondaryColor
                        ),
                        center = Offset(center.x - baseRadius * 0.15f, center.y - baseRadius * 0.15f),
                        radius = baseRadius
                    ),
                    radius = baseRadius,
                    center = center
                )
            }

            // 6. Central crystal spark
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = baseRadius * 0.22f,
                center = center
            )
        }
    }
}
