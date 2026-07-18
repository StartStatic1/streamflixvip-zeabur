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
 * Splash Screen animada do StreamFlixVIP.
 *
 * Sequência de animações:
 * 1. Ícone de play + logo "StreamFlixVIP" fazem fade-in com scale
 * 2. Frase de efeito aparece com delay
 * 3. Partículas brilhantes flutuam ao fundo
 * 4. Fade-out antes de liberar navegação
 *
 * NOTA: Não usa MaterialTheme porque roda antes do tema ser carregado.
 */
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    // Cores
    val Gold = Color(0xFFD4AF37)
    val DarkBg = Color(0xFF0A0A10)

    // Animação 1: escala do logo (0.5 -> 1.0)
    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = EaseOutBack),
        label = "logo_scale"
    )

    // Animação 2: alpha do logo (0 -> 1)
    val alphaAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "logo_alpha"
    )

    // Animação 3: pulse contínuo no ícone
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Animação 4: glow do ícone
    val glowAlpha by pulseAnim.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Animação 5: frase de efeito aparece com delay
    var showTagline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        showTagline = true
    }
    val taglineAlpha by animateFloatAsState(
        targetValue = if (showTagline) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "tagline_alpha"
    )

    // Animação 6: fade-out final
    var startExit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3500)
        startExit = true
    }
    val exitAlpha by animateFloatAsState(
        targetValue = if (startExit) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "exit_alpha"
    )

    // Dispara callback quando animação de saída termina
    LaunchedEffect(exitAlpha) {
        if (exitAlpha == 0f) {
            delay(100)
            onSplashFinished()
        }
    }

    // Partículas flutuantes
    val particles = remember {
        (0 until 12).map { _ ->
            Particle(
                startX = (10..90).random().toFloat(),
                startY = (10..90).random().toFloat(),
                sizeDp = (2..5).random(),
                duration = (3000..6000).random(),
                delayMs = (0..2000).random(),
                maxOpacity = (0.2f..0.6f).random()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(exitAlpha),
        contentAlignment = Alignment.Center
    ) {
        // Fundo com gradiente sutil
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF12121A),
                            DarkBg
                        ),
                        center = Offset(0.5f, 0.4f),
                        radius = 0.8f
                    )
                )
        )

        // Partículas brilhantes
        particles.forEach { particle ->
            FloatingParticle(
                particle = particle,
                modifier = Modifier.alpha(exitAlpha)
            )
        }

        // Conteúdo central
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scaleAnim)
                .alpha(alphaAnim)
        ) {
            // Ícone de play com glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = Gold.copy(alpha = glowAlpha * 0.15f),
                        shape = CircleShape
                    )
                    .scale(pulseScale)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "StreamFlixVIP",
                    tint = Gold,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            // Logo "StreamFlix" em branco
            Text(
                text = "StreamFlix",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            // Logo "VIP" em dourado
            Text(
                text = "VIP",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Gold,
                textAlign = TextAlign.Center
            )

            // Frase de efeito
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Seu cinema. Seu ritmo.",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f),
                alpha = taglineAlpha,
                textAlign = TextAlign.Center
            )
        }

        // Linha decorativa inferior animada
        if (showTagline) {
            AnimatedDecorativeLine(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
                    .alpha(exitAlpha)
            )
        }
    }
}

/**
 * Partícula flutuante individual.
 */
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
    val transition = rememberInfiniteTransition(label = "particle_${particle.startX}")
    val offsetY by transition.animateFloat(
        initialValue = particle.startY,
        targetValue = particle.startY - 30f,
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

@Composable
private fun AnimatedDecorativeLine(modifier: Modifier = Modifier) {
    val Gold = Color(0xFFD4AF37)
    val anim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1500),
        label = "line_anim"
    )
    Box(
        modifier = modifier
            .fillMaxWidth(anim * 0.3f)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Gold,
                        Color.Transparent
                    )
                )
            )
    )
}

private val Gold = Color(0xFFD4AF37)
