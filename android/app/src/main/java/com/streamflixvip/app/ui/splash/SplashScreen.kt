package com.streamflixvip.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflixvip.app.ui.brand.BrandMark
import com.streamflixvip.app.ui.theme.AccentStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Abertura curta (~1 s): marca + nome com um brilho passando.
 * Enquanto anima, [preload] roda em paralelo (sessão, conexão, catálogo).
 * Sai quando os dois terminam — no máximo 2,2 s, mesmo sem internet.
 */
@Composable
fun SplashScreen(onSplashFinished: () -> Unit, preload: suspend () -> Unit = {}) {
    val accent = AccentStore.current
    val logo = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val sweep = remember { Animatable(-0.3f) }
    val bar = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            val work = async { runCatching { withTimeoutOrNull(2_000) { preload() } } }
            launch { logo.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            launch { delay(220); word.animateTo(1f, tween(420)) }
            launch { delay(380); sweep.animateTo(1.3f, tween(700, easing = LinearEasing)) }
            launch { bar.animateTo(0.9f, tween(1800, easing = FastOutSlowInEasing)) }
            delay(950)
            work.await()
            bar.animateTo(1f, tween(120))
            exit.animateTo(0f, tween(220))
        }
        onSplashFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.16f), Color(0xFF0A0A0A)), radius = 1400f))
            .alpha(exit.value),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(96.dp, accent.accent, accent.accent2, Modifier.scale(0.7f + 0.3f * logo.value).alpha(logo.value))
            Spacer(Modifier.height(22.dp))
            Text(
                "STREAMFLIX",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                color = Color.White,
                modifier = Modifier
                    .alpha(word.value)
                    .drawWithContent {
                        drawContent()
                        val x = size.width * sweep.value
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                start = Offset(x - 60f, 0f), end = Offset(x + 60f, size.height),
                            ),
                        )
                    },
            )
            Text("VIP", fontSize = 12.sp, letterSpacing = 8.sp, color = accent.accent, modifier = Modifier.alpha(word.value).padding(top = 2.dp))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp).width(120.dp).height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(Modifier.fillMaxWidth(bar.value).height(3.dp).background(accent.accent))
        }
    }
}
