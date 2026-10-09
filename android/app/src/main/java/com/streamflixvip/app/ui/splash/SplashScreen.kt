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
    val Accent = Color(0xFFFFFFFF)
    val DarkBg = Color(0xFF0A0A0A)

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
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    var showTagline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(900); showTagline = true }
    val taglineAlpha by animateFloatAsState(
        targetValue = if (showTagline) 1f else 0f,
        animationSpec = tween(700),
        label = "tagline_alpha"
    )
    var startExit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2400); startExit = true }
    val exitAlpha by animateFloatAsState(
        targetValue = if (startExit) 0f else 1f,
        animationSpec = tween(450),
        label = "exit_alpha"
    )
    LaunchedEffect(exitAlpha) {
        if (exitAlpha == 0f) { delay(60); onSplashFinished() }
    }

    Box(Modifier.fillMaxSize().alpha(exitAlpha), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFF161616), DarkBg),
                    center = Offset(0.5f, 0.42f),
                    radius = 0.9f,
                ),
            ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scaleAnim).alpha(alphaAnim),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(96.dp).background(Color.White.copy(alpha = 0.06f), CircleShape).scale(pulseScale),
            ) {
                Icon(Icons.Default.PlayArrow, "StreamFlixVIP", tint = Accent, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("StreamFlix", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("VIP", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFFA3A3A3), letterSpacing = 6.sp)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(42.dp).height(2.dp).background(Color.White.copy(alpha = taglineAlpha)))
            Spacer(Modifier.height(14.dp))
            Text(
                "Seu cinema. Seu ritmo.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.62f * taglineAlpha),
                textAlign = TextAlign.Center,
            )
        }
    }
}
