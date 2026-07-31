package com.streamflixvip.tv.ui.splash

import android.media.MediaPlayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"

private val Bg = Color(0xFF0B0B14)
private val Accent = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)
private val Cyan = Color(0xFF22D3EE)

/**
 * Splash cinema: fileiras de pôsteres + logo glass indigo.
 * Antes de sair, revalida a ativação no servidor — assim, após reinstalar,
 * se o device_id ainda estiver ativo, vai direto pra home.
 *
 * Som opcional: coloque `res/raw/splash_whoosh.mp3` (ou .ogg) para intro.
 */
@Composable
fun SplashTvScreen(
    activationManager: TvActivationManager,
    onFinished: (isActivated: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var posterRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var statusText by remember { mutableStateOf("Carregando catálogo…") }

    DisposableEffect(Unit) {
        var player: MediaPlayer? = null
        val soundId = context.resources.getIdentifier("splash_whoosh", "raw", context.packageName)
        if (soundId != 0) {
            player = MediaPlayer.create(context, soundId)
            player?.setOnCompletionListener { it.release() }
            player?.start()
        }
        onDispose { player?.release() }
    }

    LaunchedEffect(Unit) {
        val minDuration = async { delay(2400) }

        val postersJob = async(Dispatchers.IO) {
            runCatching {
                val trending = NetworkModule.tmdbApi.request(path = "/trending/all/week").results.orEmpty()
                val paths = trending.mapNotNull { it.poster_path }.filter { it.isNotBlank() }
                if (paths.isEmpty()) emptyList()
                else paths.chunked((paths.size / 3).coerceAtLeast(1)).take(3)
            }.getOrDefault(emptyList())
        }

        statusText = "Verificando ativação…"
        val revalidateJob = async { activationManager.revalidate() }

        posterRows = postersJob.await()
        statusText = if (posterRows.isNotEmpty()) "Bem-vindo" else "Preparando…"

        minDuration.await()
        val active = revalidateJob.await()
        onFinished(active)
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        if (posterRows.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                posterRows.forEachIndexed { index, row ->
                    PosterMarqueeRow(
                        posterPaths = row,
                        reverse = index % 2 == 1,
                        durationMs = 28000 + index * 3500,
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x660B0B14),
                            Color(0xCC0B0B14),
                            Bg,
                        ),
                    ),
                ),
            )
            // vinheta lateral cinema
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Bg.copy(alpha = 0.85f),
                        0.2f to Color.Transparent,
                        0.8f to Color.Transparent,
                        1f to Bg.copy(alpha = 0.85f),
                    ),
                ),
            )
        }

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SplashLogo()
            Spacer(Modifier.height(28.dp))
            if (posterRows.isEmpty()) {
                CircularProgressIndicator(
                    color = AccentSoft,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                statusText,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PosterMarqueeRow(posterPaths: List<String>, reverse: Boolean, durationMs: Int) {
    if (posterPaths.isEmpty()) return
    val looped = posterPaths + posterPaths + posterPaths
    val transition = rememberInfiniteTransition(label = "marquee")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reverse) -1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "marquee_x",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .graphicsLayer {
                val widthPx = size.width.takeIf { it > 0f } ?: 1f
                translationX = offset * widthPx * 0.45f
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        looped.forEach { path ->
            AsyncImage(
                model = "$TMDB_POSTER_W342$path",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(104.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun SplashLogo() {
    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900),
        label = "logo_scale",
    )
    val alphaAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1100),
        label = "logo_alpha",
    )

    Column(
        modifier = Modifier
            .scale(0.88f + 0.12f * scaleAnim)
            .alpha(alphaAnim),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(listOf(Accent, Cyan.copy(alpha = 0.85f))),
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(46.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "StreamFlix",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp,
        )
        Text(
            "VIP",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentSoft,
            textAlign = TextAlign.Center,
            letterSpacing = 6.sp,
        )
    }
}
