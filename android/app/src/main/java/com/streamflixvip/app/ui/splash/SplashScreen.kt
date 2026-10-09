package com.streamflixvip.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val scaleAnim by animateFloatAsState(1f, tween(700, easing = EaseOutBack), label = "s")
    val alphaAnim by animateFloatAsState(1f, tween(600), label = "a")
    val pulse = rememberInfiniteTransition(label = "p")
    val ring by pulse.animateFloat(0.96f, 1.08f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "r")
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(500); show = true }
    val tag by animateFloatAsState(if (show) 1f else 0f, tween(500), label = "t")
    var exit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2200); exit = true }
    val exitA by animateFloatAsState(if (exit) 0f else 1f, tween(400), label = "e")
    LaunchedEffect(exitA) { if (exitA == 0f) { delay(40); onSplashFinished() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).alpha(exitA), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scaleAnim).alpha(alphaAnim)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp).scale(ring).border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)) {
                Box(Modifier.size(78.dp).background(Color(0xFF1A1A1A), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("StreamFlix", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("VIP", fontSize = 14.sp, letterSpacing = 6.sp, color = Color(0xFFBDBDBD), modifier = Modifier.alpha(tag))
            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(36.dp).height(2.dp).background(Color.White.copy(alpha = tag)))
            Spacer(Modifier.height(12.dp))
            Text("Seu cinema. Seu ritmo.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f * tag), textAlign = TextAlign.Center)
        }
    }
}
