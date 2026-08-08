package com.streamflixvip.tv.ui.livetv

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LiveTv
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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.network.LiveStreamOption
import kotlinx.coroutines.delay

private enum class LiveAspect(val label: String, val mode: Int) {
    FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

@Composable
fun LivePlayerTvScreen(
    channelName: String,
    streams: List<LiveStreamOption>,
    onBack: () -> Unit,
    channelList: List<LiveChannel> = emptyList(),
    initialChannelIndex: Int = 0,
    onChannelChanged: ((LiveChannel) -> Unit)? = null,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var currentIndex by remember(initialChannelIndex) {
        mutableIntStateOf(initialChannelIndex.coerceIn(0, (channelList.size - 1).coerceAtLeast(0)))
    }
    val activeChannel = channelList.getOrNull(currentIndex)
    val activeName = activeChannel?.name ?: channelName
    val activeStreams = (activeChannel?.streams?.takeIf { it.isNotEmpty() } ?: streams)
        .sortedBy { it.priority ?: 100 }

    var streamIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sourceLabel by remember { mutableStateOf("") }
    var controlsVisible by remember { mutableStateOf(true) }
    var channelListVisible by remember { mutableStateOf(false) }
    var aspectMenuVisible by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(LiveAspect.FIT) }
    var hideToken by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val aspectFocus = remember { FocusRequester() }

    BackHandler {
        when {
            channelListVisible -> channelListVisible = false
            aspectMenuVisible -> aspectMenuVisible = false
            else -> onBack()
        }
    }

    // Auto-hide. Nao roubar foco do chip atual a cada interacao —
    // so foca Aspect na PRIMEIRA vez que os controles abrem.
    var didInitialChipFocus by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, hideToken, channelListVisible, aspectMenuVisible) {
        if (controlsVisible && !channelListVisible && !aspectMenuVisible) {
            if (!didInitialChipFocus) {
                delay(40)
                runCatching { aspectFocus.requestFocus() }
                didInitialChipFocus = true
            }
            delay(10000)
            controlsVisible = false
            aspectMenuVisible = false
            didInitialChipFocus = false
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(controlsVisible, channelListVisible) {
        if (!controlsVisible && !channelListVisible) {
            didInitialChipFocus = false
            delay(30)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) { isLoading = false; errorMessage = null }
            }
            override fun onPlayerError(error: PlaybackException) {
                val next = streamIndex + 1
                if (next < activeStreams.size) {
                    streamIndex = next; isLoading = true; errorMessage = null
                } else {
                    isLoading = false
                    errorMessage = "Nao foi possivel abrir este canal em nenhuma fonte."
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentIndex) {
        streamIndex = 0; isLoading = true; errorMessage = null
        activeChannel?.let { onChannelChanged?.invoke(it) }
    }

    LaunchedEffect(streamIndex, activeStreams, currentIndex) {
        if (activeStreams.isEmpty()) {
            errorMessage = "Canal sem URL de stream"; isLoading = false; return@LaunchedEffect
        }
        val option = activeStreams.getOrNull(streamIndex) ?: return@LaunchedEffect
        isLoading = true; errorMessage = null
        sourceLabel = option.label?.takeIf { it.isNotBlank() } ?: "Fonte ${streamIndex + 1}"
        val url = option.url
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(12000)
            .setDefaultRequestProperties(mapOf("Referer" to url, "Connection" to "keep-alive", "Icy-MetaData" to "1"))
        val mediaItem = MediaItem.fromUri(url)
        val source = if (url.contains(".m3u8") || url.contains("/live/")) {
            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpFactory, DefaultExtractorsFactory()).createMediaSource(mediaItem)
        }
        exoPlayer.setMediaSource(source); exoPlayer.prepare(); exoPlayer.playWhenReady = true

        // Timeout: se nao ficar READY em 14s, tenta proxima fonte
        delay(14000)
        if (isLoading && streamIndex < activeStreams.size - 1) {
            streamIndex += 1
        } else if (isLoading && streamIndex >= activeStreams.size - 1) {
            isLoading = false
            errorMessage = "Nao foi possivel abrir este canal em nenhuma fonte."
        }
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    fun showControls() { controlsVisible = true; hideToken++ }
    fun switchChannel(delta: Int) {
        if (channelList.isEmpty()) return
        currentIndex = (currentIndex + delta).mod(channelList.size)
        showControls(); channelListVisible = false
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                if (channelListVisible) {
                    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                        channelListVisible = false; return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                when (code) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (!controlsVisible) showControls() else {
                            controlsVisible = !controlsVisible
                            if (!controlsVisible) runCatching { rootFocus.requestFocus() }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                        if (!controlsVisible) { switchChannel(+1); true } else { showControls(); false }
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!controlsVisible) { switchChannel(-1); true } else { showControls(); false }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!controlsVisible) { showControls(); true } else { showControls(); false }
                    }
                    KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> {
                        channelListVisible = !channelListVisible
                        if (channelListVisible) controlsVisible = true
                        true
                    }
                    else -> if (!controlsVisible) { showControls(); true } else false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = aspect.mode
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.resizeMode = aspect.mode },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            Modifier.align(Alignment.TopEnd).padding(20.dp).clip(RoundedCornerShape(20.dp))
                .background(Color(0xE0EF4444)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(8.dp))
            Text("AO VIVO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))),
                ).padding(start = 32.dp, end = 32.dp, top = 48.dp, bottom = 28.dp),
            ) {
                Text(activeName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(sourceLabel)
                        if (activeStreams.size > 1) append("  ·  ${streamIndex + 1}/${activeStreams.size}")
                        if (channelList.size > 1) append("  ·  ${currentIndex + 1}/${channelList.size} canais")
                        append("  ·  OK controles · ↑↓ canal · MENU lista")
                    },
                    color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 2,
                )
                Spacer(Modifier.height(18.dp))
                if (aspectMenuVisible) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiveAspect.entries.forEach { mode ->
                            val selected = aspect == mode
                            LiveTvChip(
                                Icons.Filled.AspectRatio,
                                mode.label,
                                selected = selected,
                            ) {
                                aspect = mode
                                aspectMenuVisible = false
                                showControls()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveTvChip(
                        Icons.Filled.AspectRatio,
                        if (aspectMenuVisible) "Fechar" else aspect.label,
                        Modifier.focusRequester(aspectFocus),
                        selected = aspectMenuVisible,
                    ) {
                        aspectMenuVisible = !aspectMenuVisible
                        showControls()
                    }
                    if (channelList.isNotEmpty()) {
                        if (channelList.size > 1) {
                            LiveTvChip(Icons.Filled.KeyboardArrowUp, "CH+") {
                                aspectMenuVisible = false
                                switchChannel(+1)
                            }
                            LiveTvChip(Icons.Filled.KeyboardArrowDown, "CH-") {
                                aspectMenuVisible = false
                                switchChannel(-1)
                            }
                        }
                        LiveTvChip(Icons.Filled.List, "Lista") {
                            aspectMenuVisible = false
                            channelListVisible = true
                            showControls()
                        }
                    }
                    if (activeStreams.size > 1) {
                        LiveTvChip(
                            Icons.Filled.SwapHoriz,
                            "Fonte " + (streamIndex + 1).toString() + "/" + activeStreams.size.toString(),
                        ) {
                            streamIndex = (streamIndex + 1) % activeStreams.size
                            isLoading = true
                            errorMessage = null
                            showControls()
                        }
                    }
                    LiveTvChip(Icons.Filled.Refresh, "Reload") {
                        val cur = streamIndex
                        streamIndex = -1
                        streamIndex = cur.coerceAtLeast(0)
                        isLoading = true
                        errorMessage = null
                        showControls()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = channelListVisible && channelList.isNotEmpty(),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(360.dp),
        ) {
            val listState = rememberLazyListState()
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(channelListVisible, currentIndex) {
                if (!channelListVisible) return@LaunchedEffect
                runCatching { listState.scrollToItem(currentIndex.coerceIn(0, channelList.lastIndex.coerceAtLeast(0))) }
                delay(120)
                runCatching { firstFocus.requestFocus() }
            }
            Column(
                Modifier.fillMaxSize().background(Color(0xF00B0B14))
                    .border(1.dp, Color.White.copy(alpha = 0.08f)).padding(vertical = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.List, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Canais", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text("${currentIndex + 1}/${channelList.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(channelList, key = { _, ch -> ch.id }) { index, ch ->
                        val selected = index == currentIndex
                        Surface(
                            onClick = { currentIndex = index; channelListVisible = false; showControls() },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (selected) Color(0xFF00E5FF).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                                focusedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.35f),
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF)), shape = RoundedCornerShape(10.dp)),
                            ),
                            modifier = if (selected) Modifier.focusRequester(firstFocus) else Modifier,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                    if (!ch.logo.isNullOrBlank()) {
                                        AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
                                    } else {
                                        Icon(Icons.Filled.LiveTv, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(ch.name, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Text("OK seleciona · Voltar fecha", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        if (isLoading && errorMessage == null) {
            CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.align(Alignment.Center))
        }
        errorMessage?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).padding(32.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.9f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(msg, color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (activeStreams.size > 1) {
                        LiveTvChip(Icons.Filled.Refresh, "Tentar de novo") { streamIndex = 0; isLoading = true; errorMessage = null }
                    }
                    if (channelList.size > 1) {
                        LiveTvChip(Icons.Filled.KeyboardArrowUp, "Prox. canal") { switchChannel(+1) }
                    }
                    LiveTvChip(Icons.Filled.Refresh, "Voltar", onClick = onBack)
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
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF00E5FF).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.40f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF)), shape = RoundedCornerShape(24.dp)),
        ),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
