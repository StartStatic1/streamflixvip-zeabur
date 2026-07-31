package com.streamflixvip.app.ui.livetv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.network.LiveStreamOption

/**
 * Player de canal ao vivo com fallback: se a fonte atual falhar,
 * tenta a próxima URL da lista (até 3 fontes).
 */
@Composable
fun LivePlayerScreen(
    channelName: String,
    streams: List<LiveStreamOption>,
    onBack: () -> Unit,
) {
    val view = LocalView.current
    val activity = view.context as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var streamIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusLabel by remember { mutableStateOf("") }

    val context = view.context
    val exoPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Connection" to "keep-alive",
                    "Icy-MetaData" to "1",
                ),
            )
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        isLoading = false
                        errorMessage = null
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("LivePlayer", "Erro stream $streamIndex: ${error.errorCodeName}", error)
                    val next = streamIndex + 1
                    if (next < streams.size) {
                        streamIndex = next
                        isLoading = true
                        errorMessage = null
                        statusLabel = "Tentando fonte ${next + 1}…"
                    } else {
                        isLoading = false
                        errorMessage = "Não foi possível abrir este canal em nenhuma fonte."
                    }
                }
            })
        }
    }

    LaunchedEffect(streamIndex, streams) {
        if (streams.isEmpty()) {
            errorMessage = "Canal sem URL de stream"
            isLoading = false
            return@LaunchedEffect
        }
        val option = streams.getOrNull(streamIndex) ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        statusLabel = option.label?.let { "Fonte: $it" } ?: "Fonte ${streamIndex + 1}"

        val url = option.url
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to url,
                    "Connection" to "keep-alive",
                ),
            )
        val mediaItem = MediaItem.fromUri(url)
        val source = if (url.contains(".m3u8") || url.contains("/live/")) {
            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpFactory, DefaultExtractorsFactory())
                .createMediaSource(mediaItem)
        }
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(channelName, color = Color.White, fontSize = 16.sp, maxLines = 1)
                if (statusLabel.isNotBlank()) {
                    Text(statusLabel, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
            if (streams.size > 1) {
                Text(
                    "${streamIndex + 1}/${streams.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        if (isLoading && errorMessage == null) {
            CircularProgressIndicator(
                color = Color(0xFF818CF8),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        errorMessage?.let { msg ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(msg, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                if (streams.size > 1) {
                    TextButton(onClick = {
                        streamIndex = 0
                        isLoading = true
                        errorMessage = null
                    }) {
                        Text("Tentar de novo")
                    }
                }
                TextButton(onClick = onBack) {
                    Text("Voltar")
                }
            }
        }
    }
}
