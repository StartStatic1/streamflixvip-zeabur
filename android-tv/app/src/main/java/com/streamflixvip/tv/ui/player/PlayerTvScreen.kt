package com.streamflixvip.tv.ui.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.tv.material3.*
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.BuildConfig
import kotlinx.coroutines.delay

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
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Efeito para atualizar progresso
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(1000)
        }
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    DisposableEffect(source) {
        val url = source.resolvedPlaybackUrl(apiBaseUrl)
        playbackUrl = url
        
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource = if (url.endsWith(".m3u8") || url.contains(".m3u8?")) {
            HlsMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(mediaItem)
        }
        
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = exoPlayer.duration
            }
        }
        exoPlayer.addListener(listener)
        
        onDispose { 
            exoPlayer.removeListener(listener)
            exoPlayer.release() 
        }
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
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // HUD PROFISSIONAL DE TV
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { controlsVisible = !controlsVisible }
        ) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                    
                    // Top Bar: Título e Voltar
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp),
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (season > 0) Text("S${season}E${episode}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }

                    // Bottom Bar: Controles e Progresso
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 60.dp, vertical = 40.dp)
                    ) {
                        // Barra de Progresso
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))) {
                            val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFFD4AF37), RoundedCornerShape(2.dp)))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatTime(currentPosition), color = Color.White, fontSize = 14.sp)
                                Text(" / " + formatTime(duration), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                            
                            // Controles Centrais
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                PlayerIconButton(Icons.Filled.Refresh) { exoPlayer.seekTo(currentPosition - 10000) }
                                
                                Surface(
                                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                    modifier = Modifier.size(64.dp),
                                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFFD4AF37))
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(if (isPlaying) Icons.Filled.Clear else Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                                    }
                                }
                                
                                PlayerIconButton(Icons.Filled.Refresh) { exoPlayer.seekTo(currentPosition + 10000) }
                            }
                            
                            // Settings
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                PlayerIconButton(Icons.Filled.Menu) { /* Abrir legendas */ }
                                PlayerIconButton(Icons.Filled.Settings) { /* Abrir settings */ }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs) else "%02d:%02d".format(mins, secs)
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
                loadUrl(url)
            }
        }
    )
}
