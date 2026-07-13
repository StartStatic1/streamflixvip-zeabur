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

/**
 * Tela de reprodução — implementa a decisão híbrida combinada:
 *
 * - Fonte direta (.mp4/.m3u8, incluindo as que passam pelo stream-proxy):
 *   toca em ExoPlayer NATIVO de verdade. É aqui que a diferença real de
 *   "app de verdade" aparece — controles nativos do Android, performance
 *   de decodificação de vídeo via MediaCodec do sistema, sem rastro de
 *   navegador embutido.
 *
 * - Fonte que é iframe de player de terceiro (ex: embed de parceiro que
 *   só expõe uma página HTML, não a URL do arquivo): cai pra WebView,
 *   mas ISOLADA nesta tela específica — o resto do app (Home, Detail,
 *   navegação) continua 100% nativo. O usuário só entra em "modo
 *   WebView" pontualmente, quando a fonte escolhida exige.
 */
@Composable
fun PlayerScreen(
    sourceUrl: String,
    isDirectPlayable: Boolean,
) {
    if (isDirectPlayable) {
        NativePlayer(url = sourceUrl)
    } else {
        EmbedWebView(url = sourceUrl)
    }
}

@Composable
private fun NativePlayer(url: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
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
