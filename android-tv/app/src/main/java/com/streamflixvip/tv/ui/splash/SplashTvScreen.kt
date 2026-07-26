package com.streamflixvip.tv.ui.splash

import android.media.MediaPlayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
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
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"
private const val GOLD = 0xFFD4AF37

/**
 * Splash da TV: fundo com fileiras de pôsteres (filmes/séries em alta,
 * puxados do TMDB) rolando devagar em direções alternadas, logo
 * StreamFlixVIP surgindo no centro por cima, e um efeito sonoro curto se
 * `res/raw/splash_whoosh` existir no projeto (opcional — se não existir,
 * a splash funciona normal e silenciosa, sem quebrar o build).
 *
 * Duração mínima de 2.2s mesmo que os pôsteres carreguem rápido, pra não
 * ficar um "pisca" incômodo; se demorar mais que isso pra buscar os
 * pôsteres, mostra a splash só com o logo até finalizar.
 */
@Composable
fun SplashTvScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var posterRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    // Toca o som da splash uma única vez, se o arquivo existir em res/raw.
    DisposableEffect(Unit) {
        var player: MediaPlayer? = null
        val soundId = context.resources.getIdentifier("splash_whoosh", "raw", context.packageName)
        if (soundId != 0) {
            player = MediaPlayer.create(context, soundId)
            player?.setOnCompletionListener { it.release() }
            player?.start()
        }
        onDispose {
            player?.release()
        }
    }

    LaunchedEffect(Unit) {
        val minDuration = async { delay(2200) }
        val posters = withContext(Dispatchers.IO) {
            runCatching {
                val trending = NetworkModule.tmdbApi.request(path = "/trending/all/week").results.orEmpty()
                val paths = trending.mapNotNull { it.poster_path }.filter { it.isNotBlank() }
                if (paths.isEmpty()) emptyList() else paths.chunked((paths.size / 3).coerceAtLeast(1)).take(3)
            }.getOrDefault(emptyList())
        }
        posterRows = posters
        minDuration.await()
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        if (posterRows.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                posterRows.forEachIndexed { index, row ->
                    PosterMarqueeRow(
                        posterPaths = row,
                        reverse = index % 2 == 1,
                        durationMs = 26000 + index * 4000,
                    )
                }
            }
            // Escurece por cima dos pôsteres pra o logo ficar legível
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0A0A10).copy(alpha = 0.55f), Color(0xFF0A0A10).copy(alpha = 0.92f)),
                    ),
                ),
            )
        } else {
            CircularProgressIndicator(color = Color(GOLD), modifier = Modifier.align(Alignment.Center))
        }

        SplashLogo(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun PosterMarqueeRow(posterPaths: List<String>, reverse: Boolean, durationMs: Int) {
    if (posterPaths.isEmpty()) return
    // Duplica a lista pra criar loop contínuo sem "costura" visível.
    val looped = posterPaths + posterPaths
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
            .height(160.dp)
            .graphicsLayer {
                val widthPx = size.width.takeIf { it > 0f } ?: 1f
                translationX = offset * widthPx * 0.5f
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        looped.forEach { path ->
            AsyncImage(
                model = "$TMDB_POSTER_W342$path",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(110.dp).height(160.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun SplashLogo(modifier: Modifier = Modifier) {
    val scaleAnim by animateFloatAsState(targetValue = 1f, animationSpec = tween(700), label = "logo_scale")
    val alphaAnim by animateFloatAsState(targetValue = 1f, animationSpec = tween(900), label = "logo_alpha")

    Column(
        modifier = modifier.scale(0.85f + 0.15f * scaleAnim).alpha(alphaAnim),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(GOLD)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF0A0A10), modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "StreamFlix",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            "VIP",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color(GOLD),
            textAlign = TextAlign.Center,
        )
    }
}
