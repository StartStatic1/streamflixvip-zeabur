package com.streamflixvip.app.ui.livetv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.network.LiveStreamOption
import kotlinx.coroutines.delay

private enum class LiveAspect(val label: String, val mode: Int) {
    FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

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
    var sourceLabel by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }
    var aspect by remember { mutableStateOf(LiveAspect.FIT) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Auto-esconde overlay depois de 4s
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(4000)
            controlsVisible = false
        }
    }

    val context = view.context
    val exoPlayer = remember {
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
        sourceLabel = option.label?.takeIf { it.isNotBlank() } ?: "Fonte ${streamIndex + 1}"

        val url = option.url
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to url,
                    "Connection" to "keep-alive",
                    "Icy-MetaData" to "1",
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
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { controlsVisible = !controlsVisible },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerShowTimeoutMs = 3500
                    controllerHideOnTouch = true
                    resizeMode = aspect.mode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        },
                    )
                    playerViewRef = this
                }
            },
            update = { pv ->
                pv.resizeMode = aspect.mode
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Botão voltar (sempre discreto no canto)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
            }
        }

        // Info discreta EMBAIXO da barra de progresso do player
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            ) {
                // Espaço para não colidir com a seek bar nativa do Exo (~48dp)
                Spacer(Modifier.height(40.dp))

                Text(
                    channelName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    sourceLabel + if (streams.size > 1) "  ·  ${streamIndex + 1}/${streams.size}" else "",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Proporção
                    LiveChip(
                        icon = Icons.Filled.AspectRatio,
                        label = aspect.label,
                        onClick = {
                            aspect = LiveAspect.entries[(aspect.ordinal + 1) % LiveAspect.entries.size]
                            controlsVisible = true
                        },
                    )

                    // Trocar fonte
                    if (streams.size > 1) {
                        LiveChip(
                            icon = Icons.Filled.SwapHoriz,
                            label = "Fonte",
                            onClick = {
                                streamIndex = (streamIndex + 1) % streams.size
                                isLoading = true
                                errorMessage = null
                                controlsVisible = true
                            },
                        )
                    }

                    // Recarregar
                    LiveChip(
                        icon = Icons.Filled.Refresh,
                        label = "Reload",
                        onClick = {
                            val current = streamIndex
                            streamIndex = -1
                            streamIndex = current.coerceAtLeast(0)
                            isLoading = true
                            errorMessage = null
                            controlsVisible = true
                        },
                    )
                }
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
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(msg, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                if (streams.size > 1) {
                    TextButton(onClick = {
                        streamIndex = 0
                        isLoading = true
                        errorMessage = null
                    }) { Text("Tentar de novo") }
                }
                TextButton(onClick = onBack) { Text("Voltar") }
            }
        }
    }
}

@Composable
private fun LiveChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
