package com.streamflixvip.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Splash Cinema Flutuante — preto profundo, play dourado, partículas lentas.
 */
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val Gold = Color(0xFFD4AF37)
    val DarkBg = Color(0xFF05050A)

    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = EaseOutBack),
        label = "logo_scale"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "logo_alpha"
    )

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by pulseAnim.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    var showTagline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1100)
        showTagline = true
    }
    val taglineAlpha by animateFloatAsState(
        targetValue = if (showTagline) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "tagline_alpha"
    )

    var startExit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3400)
        startExit = true
    }
    val exitAlpha by animateFloatAsState(
        targetValue = if (startExit) 0f else 1f,
        animationSpec = tween(durationMillis = 550),
        label = "exit_alpha"
    )

    LaunchedEffect(exitAlpha) {
        if (exitAlpha == 0f) {
            delay(80)
            onSplashFinished()
        }
    }

    val particles = remember {
        (0 until 14).map { _ ->
            Particle(
                startX = (8..92).random().toFloat(),
                startY = (8..92).random().toFloat(),
                sizeDp = (2..4).random(),
                duration = (3500..7000).random(),
                delayMs = (0..2500).random(),
                maxOpacity = 0.15f + (Math.random() * 0.35).toFloat()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(exitAlpha),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0C0C14),
                            DarkBg
                        ),
                        center = Offset(0.5f, 0.42f),
                        radius = 0.85f
                    )
                )
        )

        particles.forEach { particle ->
            FloatingParticle(
                particle = particle,
                modifier = Modifier.alpha(exitAlpha)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scaleAnim)
                .alpha(alphaAnim)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(108.dp)
                    .background(
                        color = Gold.copy(alpha = glowAlpha * 0.18f),
                        shape = CircleShape
                    )
                    .scale(pulseScale)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "StreamFlixVIP",
                    tint = Gold,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(Modifier.height(30.dp))

            Text(
                text = "StreamFlix",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "VIP",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Gold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))
            Text(
                text = "Seu cinema. Seu ritmo.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.65f * taglineAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}

data class Particle(
    val startX: Float,
    val startY: Float,
    val sizeDp: Int,
    val duration: Int,
    val delayMs: Int,
    val maxOpacity: Float
)

@Composable
private fun FloatingParticle(particle: Particle, modifier: Modifier = Modifier) {
    val Gold = Color(0xFFD4AF37)
    val transition = rememberInfiniteTransition(label = "particle_${particle.startX}")
    val offsetY by transition.animateFloat(
        initialValue = particle.startY,
        targetValue = particle.startY - 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(particle.duration, delayMillis = particle.delayMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particle_offset"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset(
                x = (particle.startX / 100f * 100).dp,
                y = (offsetY / 100f * 100).dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(particle.sizeDp.dp)
                .background(
                    color = Gold.copy(alpha = particle.maxOpacity),
                    shape = CircleShape
                )
        )
    }
}
