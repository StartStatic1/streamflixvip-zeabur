package com.streamflixvip.tv.ui.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.BuildConfig

@OptIn(UnstableApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerTvScreen(
    source: VipSource,
    season: Int = 0,
    episode: Int = 0,
    title: String = "Sem título",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val apiBaseUrl = BuildConfig.API_BASE_URL

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(
                    DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().apply {
                        setDefaultRequestProperties(mapOf("User-Agent" to "VLC/3.0.20", "Referer" to apiBaseUrl))
                    })
                )
            )
            .build()
    }

    var playbackUrl by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(source) {
        val url = source.resolvedPlaybackUrl(apiBaseUrl)
        playbackUrl = url
        
        if (url.endsWith(".m3u8") || url.contains(".m3u8?")) {
            val hlsSource = HlsMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(MediaItem.fromUri(url))
            exoPlayer.setMediaSource(hlsSource)
        } else {
            val progSource = ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(MediaItem.fromUri(url))
            exoPlayer.setMediaSource(progSource)
        }
        
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!source.isDirectPlayable && playbackUrl != null) {
            EmbedWebView(url = playbackUrl!!)
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.ASPECT_RATIO_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay de Controles Simples e Funcional para TV
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { controlsVisible = !controlsVisible }
        ) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                    Column(modifier = Modifier.align(Alignment.TopStart).padding(40.dp)) {
                        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (season > 0) Text("S${season}E${episode}", fontSize = 18.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                    
                    Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // Botão Voltar focado no D-pad
                        Surface(
                            onClick = onBack,
                            modifier = Modifier.height(50.dp).padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(25.dp),
                            color = Color(0xFFD4AF37)
                        ) {
                            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                                Text("Sair do Player", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWebView(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.101 Mobile Safari/537.36"
                loadUrl(url)
            }
        }
    )
}
