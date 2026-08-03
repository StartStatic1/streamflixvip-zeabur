package com.streamflixvip.tv.ui.livetv

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamflixvip.tv.network.LiveStreamOption
import kotlinx.coroutines.delay

private enum class LiveAspect(val label: String, val mode: Int) {
    FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/**
 * Player de TV ao vivo para controle remoto:
 * - Sem barra de tempo / seek (live)
 * - Nome do canal bem visivel
 * - OK / Centro = mostrar/ocultar controles
 * - Esquerda/Direita nos chips = aspect e fonte
 * - Fallback automatico se a fonte falhar
 */
@Composable
fun LivePlayerTvScreen(
    channelName: String,
    streams: List<LiveStreamOption>,
    onBack: () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(onBack = onBack)

    var streamIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sourceLabel by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }
    var aspect by remember { mutableStateOf(LiveAspect.FIT) }

    val aspectFocus = remember { FocusRequester() }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            runCatching { aspectFocus.requestFocus() }
            delay(5000)
            controlsVisible = false
        }
    }

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
                    val next = streamIndex + 1
                    if (next < streams.size) {
                        streamIndex = next
                        isLoading = true
                        errorMessage = null
                    } else {
                        isLoading = false
                        errorMessage = "Nao foi possivel abrir este canal em nenhuma fonte."
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
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    -> {
                        controlsVisible = !controlsVisible
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    -> {
                        controlsVisible = true
                        false
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // live: sem seek / tempo
                    resizeMode = aspect.mode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { it.resizeMode = aspect.mode },
            modifier = Modifier.fillMaxSize(),
        )

        // Badge AO VIVO (sempre sutil no topo)
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE0EF4444))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
            Spacer(Modifier.width(8.dp))
            Text("AO VIVO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // Controles inferiores — nome + aspect + fonte
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    )
                    .padding(start = 32.dp, end = 32.dp, top = 48.dp, bottom = 28.dp),
            ) {
                Text(
                    channelName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(sourceLabel)
                        if (streams.size > 1) {
                            append("  ·  ")
                            append(streamIndex + 1)
                            append('/')
                            append(streams.size)
                        }
                        append("  ·  OK = controles")
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LiveTvChip(
                        icon = Icons.Filled.AspectRatio,
                        label = aspect.label,
                        modifier = Modifier.focusRequester(aspectFocus),
                        onClick = {
                            aspect = LiveAspect.entries[(aspect.ordinal + 1) % LiveAspect.entries.size]
                            controlsVisible = true
                        },
                    )
                    if (streams.size > 1) {
                        LiveTvChip(
                            icon = Icons.Filled.SwapHoriz,
                            label = "Fonte ${streamIndex + 1}/${streams.size}",
                            onClick = {
                                streamIndex = (streamIndex + 1) % streams.size
                                isLoading = true
                                errorMessage = null
                                controlsVisible = true
                            },
                        )
                    }
                    LiveTvChip(
                        icon = Icons.Filled.Refresh,
                        label = "Reload",
                        onClick = {
                            val cur = streamIndex
                            streamIndex = -1
                            streamIndex = cur.coerceAtLeast(0)
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
                color = Color(0xFF00E5FF),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        errorMessage?.let { msg ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.9f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(msg, color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (streams.size > 1) {
                        LiveTvChip(
                            icon = Icons.Filled.Refresh,
                            label = "Tentar de novo",
                            onClick = {
                                streamIndex = 0
                                isLoading = true
                                errorMessage = null
                            },
                        )
                    }
                    LiveTvChip(
                        icon = Icons.Filled.Refresh,
                        label = "Voltar",
                        onClick = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.35f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF)),
                shape = RoundedCornerShape(24.dp),
            ),
        ),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
