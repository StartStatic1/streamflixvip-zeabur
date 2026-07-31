package com.streamflixvip.tv.ui.player

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
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

private val Accent = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)
private val Glass = Color.White.copy(alpha = 0.10f)
private val GlassBorder = Color.White.copy(alpha = 0.16f)
private val TextMuted = Color(0xFFB0B0C0)

private fun formatTime(ms: Long): String {
    if (ms <= 0L || ms == C.TIME_UNSET) return "0:00"
    val totalSec = (ms / 1000L).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

data class TrackOption(val label: String, val groupIndex: Int, val trackIndex: Int)

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
    onNextEpisode: (() -> Unit)? = null,
) {
    // Chave de sessão: troca de EP/servidor reinicia estado do player
    val sessionKey = "$tmdbId|$mediaType|$season|$episode|${source.source_url}"

    key(sessionKey) {
        PlayerSession(
            source = source,
            sources = sources,
            season = season,
            episode = episode,
            title = title,
            tmdbId = tmdbId,
            mediaType = mediaType,
            posterPath = posterPath,
            onBack = onBack,
            onServerFailed = onServerFailed,
            onNextEpisode = onNextEpisode,
        )
    }
}

@Composable
private fun PlayerSession(
    source: VipSource,
    sources: List<VipSource>,
    season: Int,
    episode: Int,
    title: String,
    tmdbId: Int,
    mediaType: String,
    posterPath: String?,
    onBack: () -> Unit,
    onServerFailed: () -> Unit,
    onNextEpisode: (() -> Unit)?,
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
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var progressFocused by remember { mutableStateOf(false) }
    var subtitleOptions by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var qualityOptions by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val progressFocus = remember { FocusRequester() }

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

    fun refreshTracks(tracks: Tracks) {
        val subs = mutableListOf<TrackOption>()
        val quals = mutableListOf<TrackOption>()
        for (gi in 0 until tracks.groups.size) {
            val group = tracks.groups[gi]
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                when (group.type) {
                    C.TRACK_TYPE_TEXT -> {
                        val lang = format.language ?: format.label ?: "Legenda ${subs.size + 1}"
                        subs.add(TrackOption(lang, gi, ti))
                    }
                    C.TRACK_TYPE_VIDEO -> {
                        val h = format.height
                        if (h > 0) quals.add(TrackOption("${h}p", gi, ti))
                    }
                }
            }
        }
        subtitleOptions = listOf(TrackOption("Desligada", -1, -1)) + subs.distinctBy { it.label }
        qualityOptions = listOf(TrackOption("Auto", -1, -1)) + quals.sortedByDescending {
            it.label.removeSuffix("p").toIntOrNull() ?: 0
        }.distinctBy { it.label }
    }

    fun selectTrack(option: TrackOption, type: Int) {
        val params = trackSelector.buildUponParameters()
        if (option.groupIndex < 0) {
            if (type == C.TRACK_TYPE_TEXT) {
                trackSelector.setParameters(params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true))
            } else {
                trackSelector.setParameters(params.clearOverridesOfType(C.TRACK_TYPE_VIDEO))
            }
            return
        }
        val exo = player ?: return
        val tracks = exo.currentTracks
        if (option.groupIndex >= tracks.groups.size) return
        val group = tracks.groups[option.groupIndex].mediaTrackGroup
        val override = TrackSelectionOverride(group, listOf(option.trackIndex))
        trackSelector.setParameters(
            params.setTrackTypeDisabled(type, false).clearOverridesOfType(type).addOverride(override),
        )
    }

    LaunchedEffect(player, tmdbId, season, episode) {
        if (tmdbId <= 0) return@LaunchedEffect
        while (isActive) { delay(15_000); persistProgress(player) }
    }
    LaunchedEffect(player) {
        while (isActive) {
            val exo = player
            if (exo != null) {
                positionMs = exo.currentPosition.coerceAtLeast(0L)
                val d = exo.duration
                if (d > 0 && d != C.TIME_UNSET) durationMs = d
            }
            delay(500)
        }
    }
    LaunchedEffect(controlsVisible, interactionTick, showSubtitleMenu, showQualityMenu) {
        if (controlsVisible && !showSubtitleMenu && !showQualityMenu) {
            delay(5000); controlsVisible = false
        }
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
            override fun onTracksChanged(tracks: Tracks) { refreshTracks(tracks) }
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
                when {
                    showSubtitleMenu -> { showSubtitleMenu = false; true }
                    showQualityMenu -> { showQualityMenu = false; true }
                    !controlsVisible -> { showControls(); true }
                    else -> false
                }
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
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { it.resizeMode = currentAspectRatio.mode },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                val fraction = if (durationMs > 0) {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(positionMs), color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (progressFocused) 8.dp else 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .then(
                                if (progressFocused) Modifier.border(1.dp, AccentSoft, RoundedCornerShape(3.dp))
                                else Modifier,
                            )
                            .focusRequester(progressFocus)
                            .onFocusChanged { progressFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { ev ->
                                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                val exo = player ?: return@onKeyEvent false
                                val step = 10_000L
                                when (ev.key) {
                                    Key.DirectionLeft -> {
                                        exo.seekTo((exo.currentPosition - step).coerceAtLeast(0L))
                                        showControls(); true
                                    }
                                    Key.DirectionRight -> {
                                        val max = if (exo.duration > 0 && exo.duration != C.TIME_UNSET) exo.duration else Long.MAX_VALUE
                                        exo.seekTo((exo.currentPosition + step).coerceAtMost(max))
                                        showControls(); true
                                    }
                                    else -> false
                                }
                            },
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(AccentSoft))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatTime(durationMs), color = TextMuted, fontSize = 11.sp)
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isPlaying) player?.pause() else player?.play()
                                showControls()
                            },
                            modifier = Modifier.focusRequester(pauseFocus),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null,
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = {
                            player?.seekTo((player?.currentPosition ?: 0) - 10_000)
                            showControls()
                        }) {
                            Icon(Icons.Filled.Replay10, null, tint = Color.White)
                        }
                        IconButton(onClick = {
                            player?.seekTo((player?.currentPosition ?: 0) + 10_000)
                            showControls()
                        }) {
                            Icon(Icons.Filled.Forward10, null, tint = Color.White)
                        }

                        // Avanço fixo (intros variam — Rick & Morty tem cold open)
                        CompactChip("+30s") {
                            player?.seekTo((player?.currentPosition ?: 0) + 30_000)
                            showControls()
                        }
                        CompactChip("+90s") {
                            player?.seekTo((player?.currentPosition ?: 0) + 90_000)
                            showControls()
                        }

                        if (onNextEpisode != null && mediaType == "tv") {
                            CompactChip("Próximo EP", icon = true) {
                                persistProgress(player)
                                onNextEpisode()
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (subtitleOptions.size > 1) {
                            CompactChip("Legenda") {
                                showSubtitleMenu = !showSubtitleMenu
                                showQualityMenu = false
                                showControls()
                            }
                        }
                        if (qualityOptions.size > 1) {
                            CompactChip("Qualidade") {
                                showQualityMenu = !showQualityMenu
                                showSubtitleMenu = false
                                showControls()
                            }
                        }
                        CompactChip(currentAspectRatio.label) {
                            val modes = AspectRatioMode.values()
                            val i = modes.indexOf(currentAspectRatio)
                            currentAspectRatio = modes[(i + 1) % modes.size]
                            showControls()
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(sourcesList) { srv ->
                                val selected = srv.source_url == currentSource.source_url
                                CompactChip(text = srv.displayName.take(18), selected = selected) {
                                    resumePositionMs = player?.currentPosition ?: 0L
                                    currentSource = srv
                                    showControls()
                                }
                            }
                        }
                    }
                }

                if (showSubtitleMenu) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(subtitleOptions) { opt ->
                            CompactChip(opt.label) {
                                selectTrack(opt, C.TRACK_TYPE_TEXT)
                                showSubtitleMenu = false
                                showControls()
                            }
                        }
                    }
                }
                if (showQualityMenu) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(qualityOptions) { opt ->
                            CompactChip(opt.label) {
                                selectTrack(opt, C.TRACK_TYPE_VIDEO)
                                showQualityMenu = false
                                showControls()
                            }
                        }
                    }
                }
            }
        }

        if (isLoading && playbackError == null) {
            CircularProgressIndicator(color = AccentSoft, modifier = Modifier.align(Alignment.Center))
        }
        if (playbackError != null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(playbackError!!, color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onServerFailed,
                    colors = ButtonDefaults.colors(
                        containerColor = Accent,
                        focusedContainerColor = AccentSoft,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                ) { Text("Voltar") }
            }
        }
    }
}

@Composable
private fun CompactChip(
    text: String,
    selected: Boolean = false,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = when {
                selected -> Accent.copy(alpha = 0.5f)
                focused -> Glass.copy(alpha = 0.2f)
                else -> Glass
            },
            focusedContainerColor = Accent.copy(alpha = 0.45f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        modifier = Modifier
            .height(34.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                1.dp,
                if (selected || focused) AccentSoft else GlassBorder,
                RoundedCornerShape(999.dp),
            ),
    ) {
        if (icon) {
            Icon(Icons.Filled.SkipNext, null, Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, fontSize = 11.sp, maxLines = 1)
    }
}
