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

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val Accent = Color(0xFFFFB547)
    val DarkBg = Color(0xFF08090D)

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
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by pulseAnim.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    var showTagline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(900)
        showTagline = true
    }
    val taglineAlpha by animateFloatAsState(
        targetValue = if (showTagline) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "tagline_alpha"
    )

    var startExit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2400)
        startExit = true
    }
    val exitAlpha by animateFloatAsState(
        targetValue = if (startExit) 0f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "exit_alpha"
    )

    LaunchedEffect(exitAlpha) {
        if (exitAlpha == 0f) {
            delay(60)
            onSplashFinished()
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
                            Color(0xFF1A140C),
                            DarkBg
                        ),
                        center = Offset(0.5f, 0.42f),
                        radius = 0.9f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scaleAnim)
                .alpha(alphaAnim)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = Accent.copy(alpha = glowAlpha * 0.22f),
                        shape = CircleShape
                    )
                    .scale(pulseScale)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "StreamFlixVIP",
                    tint = Accent,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "StreamFlix",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "VIP",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .width(42.dp)
                    .height(2.dp)
                    .background(Accent.copy(alpha = taglineAlpha))
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Seu cinema. Seu ritmo.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.62f * taglineAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}
