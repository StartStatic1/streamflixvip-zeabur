package com.streamflixvip.tv.ui.player

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.VipSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class AspectRatioMode(val mode: Int, val label: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Original"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Esticar"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom"),
}

@Composable
fun PlayerTvScreen(
    source: VipSource,
    sources: List<VipSource> = listOf(source),
    season: Int,
    episode: Int,
    title: String,
    tmdbId: Int = 0,
    mediaType: String = "movie",
    posterPath: String? = null,
    onBack: () -> Unit = {},
    onServerFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val libraryStore = remember { LocalLibraryStore(context) }
    val trackSelector = remember { DefaultTrackSelector(context) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentSource by remember { mutableStateOf(source) }
    val sourcesList = remember { sources.ifEmpty { listOf(source) } }
    var resumePositionMs by remember {
        val saved = if (tmdbId > 0) {
            libraryStore.getProgressFor(tmdbId, mediaType, season, episode)?.positionSeconds?.times(1000L) ?: 0L
        } else 0L
        mutableStateOf(saved)
    }
    var currentAspectRatio by remember { mutableStateOf(AspectRatioMode.FIT) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }

    fun persistProgress(exo: ExoPlayer?) {
        if (tmdbId <= 0 || exo == null) return
        val posMs = exo.currentPosition.coerceAtLeast(0L)
        val durMs = exo.duration
        if (durMs <= 0L || durMs == C.TIME_UNSET) return
        libraryStore.saveProgress(
            LocalWatchProgress(
                tmdbId = tmdbId, mediaType = mediaType, season = season, episode = episode,
                title = title, posterPath = posterPath,
                positionSeconds = (posMs / 1000L).toInt(),
                durationSeconds = (durMs / 1000L).toInt(),
            ),
        )
    }

    fun showControls() { controlsVisible = true; interactionTick++ }

    LaunchedEffect(player, tmdbId) {
        if (tmdbId <= 0) return@LaunchedEffect
        while (isActive) { delay(15_000); persistProgress(player) }
    }
    LaunchedEffect(controlsVisible, interactionTick) {
        if (controlsVisible) { delay(5000); controlsVisible = false }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { pauseFocus.requestFocus() }
    }

    DisposableEffect(context) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(context) {
        val exo = ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
        player = exo
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exo.pause()
                Lifecycle.Event.ON_DESTROY -> { exo.release(); player = null }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { persistProgress(exo) }
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo.release(); player = null
        }
    }

    LaunchedEffect(currentSource) {
        val exo = player ?: return@LaunchedEffect
        isLoading = true; playbackError = null; showControls()
        try {
            val url = withContext(Dispatchers.IO) {
                currentSource.resolvedPlaybackUrl(NetworkModule.ZEABUR_BASE_URL)
            }
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            if (resumePositionMs > 0) exo.seekTo(resumePositionMs)
            exo.playWhenReady = true
            isLoading = false
        } catch (e: Exception) {
            playbackError = "Erro: ${e.message}"; isLoading = false
        }
        exo.addListener(object : Player.Listener {
            override fun onIsLoadingChanged(v: Boolean) { isLoading = v }
            override fun onIsPlayingChanged(v: Boolean) { isPlaying = v }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Erro: ${error.message}"; isLoading = false; showControls()
            }
        })
    }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusable().focusRequester(rootFocus).onKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyUp) return@onKeyEvent false
            val isBack = ev.key == Key.Back || ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE
            if (isBack) {
                if (!controlsVisible) { showControls(); true } else false
            } else if (!controlsVisible) { showControls(); true } else false
        },
    ) {
        player?.let { exo ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exo
                        useController = false
                        resizeMode = currentAspectRatio.mode
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { it.resizeMode = currentAspectRatio.mode },
            )
        }
        AnimatedVisibility(visible = controlsVisible && playbackError == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(0.8f)).padding(16.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { if (isPlaying) player?.pause() else player?.play(); showControls() }, modifier = Modifier.focusRequester(pauseFocus)) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White)
                        }
                        IconButton(onClick = { player?.seekTo((player?.currentPosition ?: 0) - 10000); showControls() }) {
                            Icon(Icons.Filled.Replay10, null, tint = Color.White)
                        }
                        IconButton(onClick = { player?.seekTo((player?.currentPosition ?: 0) + 10000); showControls() }) {
                            Icon(Icons.Filled.Forward10, null, tint = Color.White)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val modes = AspectRatioMode.values()
                            val i = modes.indexOf(currentAspectRatio)
                            currentAspectRatio = modes[(i + 1) % modes.size]
                            showControls()
                        }) { Text(currentAspectRatio.label) }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(sourcesList) { srv ->
                                Button(onClick = {
                                    resumePositionMs = player?.currentPosition ?: 0L
                                    currentSource = srv
                                    showControls()
                                }) { Text(srv.displayName) }
                            }
                        }
                    }
                }
            }
        }
        if (isLoading && playbackError == null) {
            CircularProgressIndicator(color = Color(0xFFD4AF37), modifier = Modifier.align(Alignment.Center))
        }
        if (playbackError != null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(playbackError!!, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onServerFailed) { Text("Voltar") }
            }
        }
    }
}
