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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.network.LiveStreamOption
import kotlinx.coroutines.delay

private enum class LiveAspect(val label: String, val mode: Int) {
    FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/** HLS so com .m3u8 (ou path /live/ SEM .ts). .ts puro = Progressive. */
private fun isHlsUrl(url: String): Boolean {
    val u = url.lowercase()
    if (".m3u8" in u) return true
    if (u.endsWith(".ts") || ".ts?" in u || ".ts&" in u || "/ts/" in u) return false
    if ("/live/" in u) return true
    return false
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

    val qualityOrder = listOf("4K", "FHD", "HD", "STD", "SD")
    val orderedStreams = remember(streams) {
        streams.sortedBy { s ->
            val i = qualityOrder.indexOf(s.quality ?: "STD")
            if (i < 0) 50 else i
        }
    }
    var streamIndex by remember(orderedStreams) { mutableIntStateOf(0) }
    var selectedQuality by remember(orderedStreams) {
        mutableStateOf(orderedStreams.firstOrNull()?.quality)
    }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sourceLabel by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }
    var aspect by remember { mutableStateOf(LiveAspect.FIT) }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(4000)
            controlsVisible = false
        }
    }

    val context = view.context
    var reloadToken by remember { mutableIntStateOf(0) }
    var retryOnSame by remember { mutableIntStateOf(0) }
    var bufferingSince by remember { mutableStateOf<Long?>(null) }

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 2_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isLoading = false
                        errorMessage = null
                        bufferingSince = null
                        retryOnSame = 0
                    }
                    Player.STATE_BUFFERING -> {
                        isLoading = true
                        if (bufferingSince == null) bufferingSince = System.currentTimeMillis()
                    }
                    Player.STATE_IDLE, Player.STATE_ENDED -> {
                        if (playbackState == Player.STATE_ENDED && orderedStreams.isNotEmpty()) {
                            android.util.Log.w("LivePlayer", "Stream ended — reload mesma fonte")
                            reloadToken++
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("LivePlayer", "Erro stream $streamIndex (retry=$retryOnSame): ${error.errorCodeName}", error)
                if (retryOnSame < 2) {
                    retryOnSame++
                    isLoading = true
                    errorMessage = null
                    reloadToken++
                    return
                }
                retryOnSame = 0
                val next = streamIndex + 1
                if (next < orderedStreams.size) {
                    streamIndex = next
                    isLoading = true
                    errorMessage = null
                } else {
                    isLoading = false
                    errorMessage = "Nao foi possivel abrir este canal em nenhuma fonte."
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(bufferingSince, streamIndex, reloadToken) {
        val started = bufferingSince ?: return@LaunchedEffect
        while (true) {
            delay(1000)
            val since = bufferingSince ?: return@LaunchedEffect
            if (System.currentTimeMillis() - since < 12_000) continue
            android.util.Log.w("LivePlayer", "Stall >12s — reload fonte $streamIndex")
            bufferingSince = null
            if (retryOnSame < 2) {
                retryOnSame++
                reloadToken++
            } else {
                retryOnSame = 0
                val next = streamIndex + 1
                if (next < orderedStreams.size) {
                    streamIndex = next
                } else {
                    reloadToken++
                }
            }
            return@LaunchedEffect
        }
    }

    LaunchedEffect(streamIndex, streams, reloadToken) {
        if (orderedStreams.isEmpty()) {
            errorMessage = "Canal sem URL de stream"
            isLoading = false
            return@LaunchedEffect
        }
        val option = orderedStreams.getOrNull(streamIndex) ?: return@LaunchedEffect
        isLoading = true
        errorMessage = null
        bufferingSince = System.currentTimeMillis()
        sourceLabel = buildString {
            val q = option.quality?.takeIf { it.isNotBlank() }
            val lab = option.label?.takeIf { it.isNotBlank() }
            if (q != null) append(q)
            if (q != null && lab != null) append(" · ")
            append(lab ?: "Fonte ${streamIndex + 1}")
        }
        selectedQuality = option.quality ?: selectedQuality

        val url = option.url
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to url.substringBefore("?").substringBeforeLast("/") + "/",
                    "Connection" to "keep-alive",
                    "Accept" to "*/*",
                    "Icy-MetaData" to "1",
                ),
            )

        val mediaItem = MediaItem.fromUri(url)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                    or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                    or DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
            )
            .setConstantBitrateSeekingEnabled(true)

        val source = if (isHlsUrl(url)) {
            HlsMediaSource.Factory(httpFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpFactory, extractorsFactory)
                .createMediaSource(mediaItem)
        }
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
                    useController = false
                    resizeMode = aspect.mode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { pv ->
                pv.resizeMode = aspect.mode
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xE0EF4444))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "AO VIVO",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }

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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        ),
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            channelName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val qualities = remember(orderedStreams) {
                            qualityOrder.filter { q -> orderedStreams.any { it.quality == q } }
                        }
                        if (qualities.size > 1) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                qualities.forEach { q ->
                                    val selected = selectedQuality == q
                                    Text(
                                        text = q,
                                        color = if (selected) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (selected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.15f),
                                            )
                                            .clickable {
                                                selectedQuality = q
                                                val idx = orderedStreams.indexOfFirst { it.quality == q }
                                                if (idx >= 0) {
                                                    streamIndex = idx
                                                    retryOnSame = 0
                                                    isLoading = true
                                                    errorMessage = null
                                                    controlsVisible = true
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val isManual = sourceLabel.equals("Manual", ignoreCase = true)
                            Text(
                                sourceLabel.ifBlank { "Fonte ${streamIndex + 1}" },
                                color = if (isManual) Color(0xFF34D399) else Color(0xFFFBBF24),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (orderedStreams.size > 1) {
                                Text(
                                    "${streamIndex + 1}/${orderedStreams.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveChip(
                            icon = Icons.Filled.AspectRatio,
                            label = aspect.label,
                            onClick = {
                                aspect = LiveAspect.entries[(aspect.ordinal + 1) % LiveAspect.entries.size]
                                controlsVisible = true
                            },
                        )
                        if (orderedStreams.size > 1) {
                            LiveChip(
                                icon = Icons.Filled.SwapHoriz,
                                label = if (orderedStreams.size > 1) {
                                    "Fonte ${streamIndex + 1}/${orderedStreams.size}"
                                } else {
                                    "Fonte"
                                },
                                onClick = {
                                    streamIndex = (streamIndex + 1) % orderedStreams.size
                                    retryOnSame = 0
                                    isLoading = true
                                    errorMessage = null
                                    controlsVisible = true
                                },
                            )
                        }
                        LiveChip(
                            icon = Icons.Filled.Refresh,
                            label = "Reload",
                            onClick = {
                                retryOnSame = 0
                                isLoading = true
                                errorMessage = null
                                controlsVisible = true
                                reloadToken++
                            },
                        )
                    }
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
                if (orderedStreams.size > 1) {
                    TextButton(onClick = {
                        streamIndex = 0
                        retryOnSame = 0
                        isLoading = true
                        errorMessage = null
                        reloadToken++
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
