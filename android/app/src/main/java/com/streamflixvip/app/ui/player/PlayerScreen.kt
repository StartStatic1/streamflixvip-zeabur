package com.streamflixvip.app.ui.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.data.ProgressRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Salva a posição a cada 15s de reprodução — frequente o bastante pra não
 * perder muito progresso se o app fechar de repente, raro o bastante pra
 * não sobrecarregar o Supabase com requisições. */
private const val PROGRESS_SAVE_INTERVAL_MS = 15_000L

/**
 * Tela de reprodução — implementa a decisão híbrida combinada:
 *
 * - Fonte direta (.mp4/.m3u8, incluindo as que passam pelo stream-proxy):
 *   toca em ExoPlayer NATIVO de verdade. É aqui que a diferença real de
 *   "app de verdade" aparece — controles nativos do Android, performance
 *   de decodificação de vídeo via MediaCodec do sistema, sem rastro de
 *   navegador embutido. Também é aqui que o progresso de reprodução é
 *   salvo no Supabase, alimentando o carrossel "Continuar assistindo".
 *
 * - Fonte que é iframe de player de terceiro (ex: embed de parceiro que
 *   só expõe uma página HTML, não a URL do arquivo): cai pra WebView,
 *   isolada nesta tela. Não dá pra rastrear posição de reprodução dentro
 *   de um iframe de terceiro, então esse caminho não salva progresso.
 */
@Composable
fun PlayerScreen(
    sourceUrl: String,
    isDirectPlayable: Boolean,
    userId: String?,
    accessToken: String?,
    tmdbId: Int,
    mediaType: String,
    season: Int,
    episode: Int,
    title: String,
    posterPath: String?,
) {
    if (isDirectPlayable) {
        NativePlayer(
            url = sourceUrl,
            userId = userId,
            accessToken = accessToken,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
            posterPath = posterPath,
        )
    } else {
        EmbedWebView(url = sourceUrl)
    }
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
private fun NativePlayer(
    url: String,
    userId: String?,
    accessToken: String?,
    tmdbId: Int,
    mediaType: String,
    season: Int,
    episode: Int,
    title: String,
    posterPath: String?,
) {
    val context = LocalContext.current
    val progressRepository = remember { ProgressRepository() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    suspend fun persistCurrentPosition() {
        if (userId == null || accessToken == null) return
        val positionSeconds = (exoPlayer.currentPosition / 1000).toInt()
        val durationSeconds = (exoPlayer.duration / 1000).toInt()
        if (durationSeconds <= 0) return
        progressRepository.saveProgress(
            accessToken = accessToken,
            userId = userId,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
            posterPath = posterPath,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
        )
    }

    // Loop de salvamento periódico enquanto a tela do player está viva.
    LaunchedEffect(exoPlayer) {
        if (userId == null || accessToken == null) return@LaunchedEffect
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            persistCurrentPosition()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Salva a posição final ao sair da tela (voltar, trocar de
            // app, etc). Usa GlobalScope de propósito aqui: o escopo
            // normal da composable é cancelado assim que ela sai de tela,
            // o que impediria justamente essa última gravação de
            // completar. É um "best effort" pontual só pra este caso.
            if (userId != null && accessToken != null) {
                val positionSeconds = (exoPlayer.currentPosition / 1000).toInt()
                val durationSeconds = (exoPlayer.duration / 1000).toInt()
                if (durationSeconds > 0) {
                    GlobalScope.launch(Dispatchers.IO) {
                        progressRepository.saveProgress(
                            accessToken = accessToken,
                            userId = userId,
                            tmdbId = tmdbId,
                            mediaType = mediaType,
                            season = season,
                            episode = episode,
                            title = title,
                            posterPath = posterPath,
                            positionSeconds = positionSeconds,
                            durationSeconds = durationSeconds,
                        )
                    }
                }
            }
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWebView(url: String) {
    // WebView usado APENAS aqui, isolado — nenhuma outra tela do app
    // depende dele. Existe só porque alguns parceiros de embed só
    // expõem um <iframe>, não uma URL de arquivo que o ExoPlayer possa
    // consumir direto.
    Box(Modifier.fillMaxSize()) {
        var isLoading by remember { mutableStateOf(true) }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    loadUrl(url)
                }
            },
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
