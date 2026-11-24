package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.OrbState
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.VioletGlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MarcoOrb(
    orbState: OrbState,
    micLevel: Float,
    speakerLevel: Float,
    waveformBands: List<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orbSize: Dp = 280.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "marco_orb_transition")

    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_phase"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (orbState == OrbState.THINKING) 1800 else 7500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    val activeEnergy = when (orbState) {
        OrbState.SPEAKING -> speakerLevel.coerceIn(0.15f, 1f)
        OrbState.LISTENING -> micLevel.coerceIn(0.08f, 1f)
        OrbState.THINKING -> 0.55f
        OrbState.STANDBY -> 0.22f + micLevel * 0.35f
        OrbState.IDLE -> 0.08f
    }

    val animatedEnergy by animateFloatAsState(
        targetValue = activeEnergy,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "orb_energy"
    )

    val interactionSource = remember { MutableInteractionSource() }

    val stateDescription = when (orbState) {
        OrbState.IDLE -> "Marco orb idle. Tap to start live voice session."
        OrbState.STANDBY -> "Marco orb on standby for Hey Marco. Tap to talk immediately."
        OrbState.LISTENING -> "Marco orb listening to your voice. Tap to pause."
        OrbState.THINKING -> "Marco orb processing request."
        OrbState.SPEAKING -> "Marco orb speaking. Tap to interrupt."
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(orbSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = NeonCyan),
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = stateDescription }
            .testTag("marco_central_orb")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val minDim = size.minDimension
            val baseRadius = minDim * 0.26f

            val primaryColor: Color
            val secondaryColor: Color
            val haloColor: Color

            when (orbState) {
                OrbState.IDLE -> {
                    primaryColor = ElectricBlue.copy(alpha = 0.72f)
                    secondaryColor = NeonPurple.copy(alpha = 0.6f)
                    haloColor = ElectricBlue.copy(alpha = 0.14f + breathingPhase * 0.08f)
                }
                OrbState.STANDBY -> {
                    primaryColor = NeonCyan.copy(alpha = 0.8f)
                    secondaryColor = StandbyAmber.copy(alpha = 0.55f)
                    haloColor = NeonCyan.copy(alpha = 0.16f + breathingPhase * 0.1f)
                }
                OrbState.LISTENING -> {
                    primaryColor = NeonCyan
                    secondaryColor = ElectricBlue
                    haloColor = NeonCyan.copy(alpha = 0.22f + animatedEnergy * 0.26f)
                }
                OrbState.THINKING -> {
                    primaryColor = VioletGlow
                    secondaryColor = NeonCyan
                    haloColor = NeonPurple.copy(alpha = 0.24f + breathingPhase * 0.18f)
                }
                OrbState.SPEAKING -> {
                    primaryColor = NeonPurple
                    secondaryColor = NeonCyan
                    haloColor = VioletGlow.copy(alpha = 0.26f + animatedEnergy * 0.30f)
                }
            }

            // Outer ambient radial halo
            val outerHaloRadius = minDim * (0.42f + breathingPhase * 0.03f + animatedEnergy * 0.05f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(haloColor, Color.Transparent),
                    center = center,
                    radius = outerHaloRadius
                ),
                radius = outerHaloRadius,
                center = center
            )

            // Waveform radial spikes when Listening, Speaking, or Standby
            if (orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING || orbState == OrbState.STANDBY) {
                val barCount = waveformBands.size.coerceAtLeast(16)
                val ringRadius = baseRadius * (1.18f + animatedEnergy * 0.14f)
                for (i in 0 until barCount) {
                    val angleDeg = (i * (360f / barCount)) + (rotationAngle * 0.35f)
                    val angleRad = angleDeg * (PI / 180.0)
                    val bandAmp = waveformBands.getOrElse(i) { 0.1f }
                    val spikeLength = when (orbState) {
                        OrbState.SPEAKING -> minDim * (0.025f + bandAmp * 0.11f)
                        OrbState.LISTENING -> minDim * (0.018f + bandAmp * 0.085f)
                        else -> minDim * (0.01f + bandAmp * 0.03f)
                    }
                    val start = Offset(
                        x = center.x + (cos(angleRad) * ringRadius).toFloat(),
                        y = center.y + (sin(angleRad) * ringRadius).toFloat()
                    )
                    val end = Offset(
                        x = center.x + (cos(angleRad) * (ringRadius + spikeLength)).toFloat(),
                        y = center.y + (sin(angleRad) * (ringRadius + spikeLength)).toFloat()
                    )
                    val barColor = if (i % 2 == 0) primaryColor else secondaryColor
                    drawLine(
                        color = barColor.copy(alpha = 0.55f + bandAmp * 0.45f),
                        start = start,
                        end = end,
                        strokeWidth = (minDim * 0.011f).coerceAtLeast(3f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Thinking pulsating neon arcs
            if (orbState == OrbState.THINKING) {
                val arcRadius = baseRadius * (1.28f + breathingPhase * 0.12f)
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, NeonCyan, VioletGlow, Color.Transparent),
                        center = center
                    ),
                    startAngle = rotationAngle,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = minDim * 0.02f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ElectricBlue.copy(alpha = 0.65f),
                    startAngle = -rotationAngle * 1.4f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius * 1.14f, center.y - arcRadius * 1.14f),
                    size = Size(arcRadius * 2.28f, arcRadius * 2.28f),
                    style = Stroke(width = minDim * 0.01f, cap = StrokeCap.Round)
                )
            }

            // Subtle orbital guide ring
            val guideRadius = baseRadius * (1.12f + breathingPhase * 0.04f)
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = guideRadius,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // Main luminous core sphere
            val coreRadius = baseRadius * (0.94f + breathingPhase * 0.05f + animatedEnergy * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryColor,
                        secondaryColor,
                        Color(0xFF0A0E24)
                    ),
                    center = Offset(center.x - coreRadius * 0.22f, center.y - coreRadius * 0.22f),
                    radius = coreRadius * 1.18f
                ),
                radius = coreRadius,
                center = center
            )

            // Inner specular crescent highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.48f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - coreRadius * 0.32f, center.y - coreRadius * 0.34f),
                    radius = coreRadius * 0.55f
                ),
                radius = coreRadius * 0.55f,
                center = Offset(center.x - coreRadius * 0.32f, center.y - coreRadius * 0.34f)
            )
        }
    }
}
