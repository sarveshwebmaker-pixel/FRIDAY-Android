package com.example.ui

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
import androidx.compose.material3.ripple
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
import com.example.core.OrbState
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Cyan500
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FridayOrb(
    state: OrbState,
    audioAmplitude: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransitions")

    // Breathing pulse for idle & speaking
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbBreathing"
    )

    // Rotation angle for thinking / working orbital rings
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    // Wave ring expansion for listening
    val listeningWave by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ListeningWave"
    )

    val listeningAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ListeningAlpha"
    )

    // Determine color palette based on state
    val (primaryColor, secondaryColor, coreColor) = when (state) {
        OrbState.IDLE -> Triple(Cyan400, Indigo400, Slate900)
        OrbState.LISTENING -> Triple(CyanGlow, Cyan400, Slate800)
        OrbState.THINKING -> Triple(Indigo400, Cyan400, Slate900)
        OrbState.SPEAKING -> Triple(Cyan400, Cyan500, Slate800)
        OrbState.WORKING -> Triple(Amber400, Cyan400, Slate900)
        OrbState.ERROR -> Triple(Rose500, Amber400, Slate900)
        OrbState.OFFLINE -> Triple(Slate600, Slate800, Slate900)
    }

    val dynamicAudioScale = if (state == OrbState.LISTENING || state == OrbState.SPEAKING) {
        1.0f + (audioAmplitude * 0.35f)
    } else {
        breathingScale
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = size / 2),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val baseRadius = (this.size.minDimension / 2) * 0.72f
            val currentRadius = baseRadius * dynamicAudioScale

            // 1. Ambient outer aura/glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (state == OrbState.LISTENING) 0.35f else 0.18f),
                        secondaryColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.5f
                ),
                radius = currentRadius * 1.5f,
                center = center
            )

            // 2. State-specific background waves or rings
            when (state) {
                OrbState.LISTENING -> {
                    // Expanding sonar wave rings
                    drawCircle(
                        color = primaryColor.copy(alpha = listeningAlpha),
                        radius = baseRadius * listeningWave,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = secondaryColor.copy(alpha = (listeningAlpha * 0.6f)),
                        radius = baseRadius * (listeningWave * 0.85f),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                OrbState.THINKING -> {
                    // Dual orbital trajectory rings
                    rotate(rotationAngle, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(primaryColor, Color.Transparent, secondaryColor, Color.Transparent, primaryColor),
                                center = center
                            ),
                            radius = currentRadius * 1.15f,
                            center = center,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    rotate(-rotationAngle * 1.4f, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(secondaryColor, Color.Transparent, primaryColor, Color.Transparent, secondaryColor),
                                center = center
                            ),
                            radius = currentRadius * 1.25f,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                OrbState.WORKING -> {
                    // Rotating segmented ring
                    rotate(rotationAngle * 1.5f, pivot = center) {
                        val numSegments = 6
                        for (i in 0 until numSegments) {
                            val startAngle = i * (360f / numSegments)
                            drawArc(
                                color = primaryColor.copy(alpha = 0.8f),
                                startAngle = startAngle,
                                sweepAngle = 30f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = Offset(center.x - currentRadius * 1.18f, center.y - currentRadius * 1.18f),
                                size = androidx.compose.ui.geometry.Size(currentRadius * 2.36f, currentRadius * 2.36f)
                            )
                        }
                    }
                }

                OrbState.SPEAKING -> {
                    // Speech vibration harmonic concentric strokes
                    val harmonicRadius1 = currentRadius + (sin(rotationAngle * 0.1f) * 6.dp.toPx())
                    val harmonicRadius2 = currentRadius + (cos(rotationAngle * 0.15f) * 10.dp.toPx())
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.25f),
                        radius = harmonicRadius1,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = secondaryColor.copy(alpha = 0.15f),
                        radius = harmonicRadius2,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                else -> {
                    // Subtle orbital ring
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.15f),
                        radius = currentRadius * 1.12f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // 3. Deep Core Sphere with rich radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.7f),
                        secondaryColor.copy(alpha = 0.45f),
                        coreColor.copy(alpha = 0.95f),
                        Slate900
                    ),
                    center = Offset(center.x - currentRadius * 0.2f, center.y - currentRadius * 0.25f),
                    radius = currentRadius
                ),
                radius = currentRadius,
                center = center
            )

            // 4. Highlight reflection glint
            val highlightRadius = currentRadius * 0.45f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - currentRadius * 0.3f, center.y - currentRadius * 0.35f),
                    radius = highlightRadius
                ),
                radius = highlightRadius,
                center = Offset(center.x - currentRadius * 0.3f, center.y - currentRadius * 0.35f)
            )

            // 5. Outer crisp boundary rim
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.8f),
                        secondaryColor.copy(alpha = 0.3f),
                        primaryColor.copy(alpha = 0.9f),
                        secondaryColor.copy(alpha = 0.4f),
                        primaryColor.copy(alpha = 0.8f)
                    ),
                    center = center
                ),
                radius = currentRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
