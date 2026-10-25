package com.streamflixvip.tv.ui.player

import android.app.Activity
import android.view.KeyEvent as AKeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.data.SubBackground
import com.streamflixvip.tv.data.SubColor
import com.streamflixvip.tv.data.SubSize
import com.streamflixvip.tv.data.TvSettingsStore
import com.streamflixvip.tv.data.nextOf
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.SubtitleSearchItem
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.ui.components.FadeImage
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.PanelItem
import com.streamflixvip.tv.ui.components.SidePanel
import com.streamflixvip.tv.ui.components.TmdbImg
import com.streamflixvip.tv.ui.components.TvButton
import com.streamflixvip.tv.ui.settings.SubtitleText
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tudo que o player precisa para abrir um filme/episodio. */
data class PlayerArgs(
    val tmdbId: Int,
    val mediaType: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val sources: List<VipSource>,
    val fromStart: Boolean = false,
)

private enum class PlayerPanel { NONE, SERVERS, SUBTITLES, AUDIO, SETTINGS }

@OptIn(UnstableApi::class)
private enum class ScreenMode(val mode: Int, val label: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Original"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Preencher (zoom)"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Esticar"),
}

private data class TrackOpt(val label: String, val groupIndex: Int, val trackIndex: Int, val language: String?, val selected: Boolean, val height: Int = 0)

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val SKIP_INTRO_MS = 85_000L
private const val START_TIMEOUT_MS = 30_000L
private const val NEXT_CARD_MS = 25_000L

@Composable
fun PlayerTvScreen(
    args: PlayerArgs,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)?,
) {
    key(args.tmdbId, args.mediaType, args.season, args.episode, args.sources.firstOrNull()?.source_url) {
        PlayerSession(args, onBack, onNextEpisode)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSession(
    args: PlayerArgs,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val toaster = LocalToaster.current
    val p = LocalPalette.current
    val settings by TvSettingsStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val library = remember { LocalLibraryStore(context) }
    val isSeries = args.mediaType == "tv"
    val sources = remember { args.sources.distinctBy { it.source_url } }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguages("pt", "por", "pt-BR"))
        }
    }
    val exo = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(streamingLoadControl())
            .build()
            .apply { playWhenReady = true }
    }

    // ── estado ───────────────────────────────────────────────────────────
    var sourceIndex by remember { mutableIntStateOf(0) }
    val failed = remember { mutableStateOf(setOf<Int>()) }
    var retryCount by remember { mutableIntStateOf(0) }
    var forceHls by remember { mutableStateOf(false) }
    var loadNonce by remember { mutableIntStateOf(0) }
    var firstReady by remember { mutableStateOf(false) }
    var everReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var ended by remember { mutableStateOf(false) }
    var pendingError by remember { mutableStateOf<PlaybackException?>(null) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var scrubNonce by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(PlayerPanel.NONE) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var screenMode by remember { mutableStateOf(ScreenMode.FIT) }
    var nextDismissed by remember { mutableStateOf(false) }
    var onlineCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var onlineLabel by remember { mutableStateOf<String?>(null) }
    var currentCue by remember { mutableStateOf<SubtitleCue?>(null) }
    var subOffsetMs by remember { mutableLongStateOf(0L) }
    var onlineResults by remember { mutableStateOf<List<SubtitleSearchItem>?>(null) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var autoSubsDone by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    val saved = remember {
        if (args.fromStart || args.tmdbId <= 0) null
        else library.getProgressFor(args.tmdbId, args.mediaType, args.season, args.episode)
            ?.takeIf { it.positionSeconds > 30 && it.progressFraction < 0.95f }
    }
    var startPositionMs by remember { mutableLongStateOf((saved?.positionSeconds ?: 0) * 1000L) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val panelFirst = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    val panelButtons = remember { PlayerPanel.entries.associateWith { FocusRequester() } }
    var focusReq by remember { mutableStateOf<FocusRequester?>(null) }
    var focusNonce by remember { mutableIntStateOf(0) }

    val current = sources.getOrNull(sourceIndex)

    fun requestFocusOn(r: FocusRequester) {
        focusReq = r
        focusNonce++
    }

    fun showControls(target: FocusRequester? = playFocus) {
        val wasHidden = !controlsVisible
        controlsVisible = true
        tick++
        if (wasHidden && target != null) requestFocusOn(target)
    }

    fun persist() {
        if (args.tmdbId <= 0) return
        val pos = exo.currentPosition.coerceAtLeast(0L)
        val dur = exo.duration
        if (dur <= 0L || dur == C.TIME_UNSET) return
        library.saveProgress(
            LocalWatchProgress(
                tmdbId = args.tmdbId,
                mediaType = args.mediaType,
                season = args.season,
                episode = args.episode,
                title = args.title,
                posterPath = args.posterPath,
                positionSeconds = (pos / 1000L).toInt(),
                durationSeconds = (dur / 1000L).toInt(),
                backdropPath = args.backdropPath,
            ),
        )
    }

    fun load(index: Int, positionMs: Long, hls: Boolean = false) {
        val src = sources.getOrNull(index) ?: return
        sourceIndex = index
        forceHls = hls
        firstReady = false
        isBuffering = true
        fatalError = null
        ended = false
        val url = src.resolvedPlaybackUrl(NetworkModule.ZEABUR_BASE_URL)
        runCatching {
            exo.setMediaSource(buildPlaybackMediaSource(url, hls))
            exo.prepare()
            if (positionMs > 0) exo.seekTo(positionMs)
            exo.playWhenReady = true
        }.onFailure { fatalError = "Não foi possível abrir este servidor." }
        loadNonce++
    }

    fun switchTo(index: Int, reason: String? = null) {
        val pos = if (everReady) exo.currentPosition.coerceAtLeast(0L) else startPositionMs
        startPositionMs = pos
        retryCount = 0
        reason?.let { toaster.show(it) }
        load(index, pos)
    }

    fun nextUntried(): Int? = sources.indices.firstOrNull { it != sourceIndex && it !in failed.value }

    fun seekBy(delta: Long) {
        val dur = exo.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + delta).coerceIn(0L, dur))
        tick++
    }

    fun togglePlay() {
        if (exo.isPlaying) exo.pause() else {
            if (ended) exo.seekTo(0)
            exo.play()
        }
    }

    fun goNext() {
        val next = onNextEpisode ?: return
        persist()
        next()
    }

    fun exit() {
        persist()
        onBack()
    }

    // ── ciclo de vida do ExoPlayer ───────────────────────────────────────
    DisposableEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    if (!firstReady) {
                        firstReady = true
                        retryCount = 0
                    }
                    everReady = true
                }
                if (state == Player.STATE_ENDED) ended = true
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
            }

            override fun onPlayerError(error: PlaybackException) {
                pendingError = error
            }
        }
        exo.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> persist()
                Lifecycle.Event.ON_STOP -> exo.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (sources.isNotEmpty()) {
            load(0, startPositionMs)
            if (saved != null) toaster.show("Continuando de ${formatTime(startPositionMs)}")
        } else {
            fatalError = "Nenhum servidor para reproduzir."
        }
        onDispose {
            runCatching { persist() }
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo.removeListener(listener)
            exo.release()
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // erros: tenta de novo, depois troca de servidor sozinho
    // Nao zera pendingError aqui: mudar a chave cancelaria este efeito no meio do
    // delay da reconexao e o video ficava parado no erro.
    LaunchedEffect(pendingError) {
        val err = pendingError ?: return@LaunchedEffect
        val code = err.errorCode
        val url = current?.resolvedPlaybackUrl(NetworkModule.ZEABUR_BASE_URL).orEmpty()
        val pos = if (everReady) exo.currentPosition.coerceAtLeast(0L) else startPositionMs
        when {
            !firstReady && !forceHls && isContainerError(code) && !isLikelyHls(url) -> {
                load(sourceIndex, pos, hls = true)
            }
            isNetworkError(code) && retryCount < 2 -> {
                retryCount++
                toaster.show("Reconectando…")
                delay(1500L * retryCount)
                load(sourceIndex, pos, forceHls)
            }
            else -> {
                failed.value = failed.value + sourceIndex
                val next = nextUntried()
                if (next != null) {
                    switchTo(next, "${current?.displayName ?: "Servidor"} falhou — tentando ${sources[next].displayName}")
                } else {
                    fatalError = friendlyPlaybackError(err)
                }
            }
        }
    }

    // servidor que nao comeca a tocar em 30s conta como falha
    LaunchedEffect(loadNonce) {
        if (loadNonce == 0) return@LaunchedEffect
        delay(START_TIMEOUT_MS)
        if (!firstReady && fatalError == null) {
            failed.value = failed.value + sourceIndex
            val next = nextUntried()
            if (next != null) switchTo(next, "Servidor lento — tentando ${sources[next].displayName}")
            else fatalError = "Os servidores não responderam a tempo."
        }
    }

    // relogio do player
    LaunchedEffect(Unit) {
        var lastSave = System.currentTimeMillis()
        while (isActive) {
            positionMs = exo.currentPosition.coerceAtLeast(0L)
            val d = exo.duration
            if (d > 0 && d != C.TIME_UNSET) durationMs = d
            if (onlineCues.isNotEmpty()) currentCue = cueAt(onlineCues, positionMs - subOffsetMs) else if (currentCue != null) currentCue = null
            if (exo.isPlaying && System.currentTimeMillis() - lastSave > 10_000) {
                persist()
                lastSave = System.currentTimeMillis()
            }
            delay(if (onlineCues.isNotEmpty()) 150 else 400)
        }
    }

    // esconde controles sozinho
    LaunchedEffect(controlsVisible, tick, panel, isPlaying, scrubMs) {
        if (controlsVisible && panel == PlayerPanel.NONE && isPlaying && scrubMs == null && fatalError == null) {
            delay(5000)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible && panel == PlayerPanel.NONE) {
            delay(30)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(focusNonce) {
        val r = focusReq ?: return@LaunchedEffect
        delay(60)
        runCatching { r.requestFocus() }
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(panel) {
        if (panel != PlayerPanel.NONE) {
            delay(90)
            runCatching { panelFirst.requestFocus() }
        }
    }
    LaunchedEffect(fatalError) {
        if (fatalError != null) {
            controlsVisible = false
            delay(80)
            runCatching { errorFocus.requestFocus() }
        }
    }

    // procura confirmada depois de soltar as setas
    LaunchedEffect(scrubNonce) {
        if (scrubMs == null) return@LaunchedEffect
        delay(650)
        scrubMs?.let { exo.seekTo(it) }
        scrubMs = null
    }

    fun scrub(forward: Boolean, repeat: Int) {
        val step = when {
            repeat > 20 -> 60_000L
            repeat > 6 -> 30_000L
            else -> 10_000L
        }
        val dur = durationMs.takeIf { it > 0 } ?: return seekBy(if (forward) step else -step)
        val base = scrubMs ?: exo.currentPosition
        scrubMs = (base + if (forward) step else -step).coerceIn(0L, dur)
        scrubNonce++
        tick++
    }

    // ── faixas ───────────────────────────────────────────────────────────
    fun listTracks(type: Int): List<TrackOpt> {
        val groups = exo.currentTracks.groups
        val out = mutableListOf<TrackOpt>()
        for (gi in groups.indices) {
            val g = groups[gi]
            if (g.type != type) continue
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                out += TrackOpt(
                    label = if (type == C.TRACK_TYPE_VIDEO) (if (f.height > 0) "${f.height}p" else "Faixa ${out.size + 1}")
                    else humanTrackLabel(f.label, f.language, out.size),
                    groupIndex = gi,
                    trackIndex = ti,
                    language = f.language,
                    selected = g.isTrackSelected(ti),
                    height = f.height,
                )
            }
        }
        return out
    }

    fun selectTrack(opt: TrackOpt?, type: Int) {
        val params = trackSelector.buildUponParameters()
        if (opt == null) {
            if (type == C.TRACK_TYPE_TEXT) params.clearOverridesOfType(type).setTrackTypeDisabled(type, true)
            else params.clearOverridesOfType(type)
        } else {
            val g = exo.currentTracks.groups.getOrNull(opt.groupIndex) ?: return
            params.setTrackTypeDisabled(type, false).clearOverridesOfType(type)
                .addOverride(TrackSelectionOverride(g.mediaTrackGroup, opt.trackIndex))
        }
        trackSelector.setParameters(params)
    }

    suspend fun searchOnline(): List<SubtitleSearchItem> {
        onlineLoading = true
        onlineError = null
        return try {
            val r = NetworkModule.subtitlesApi.search(
                tmdbId = args.tmdbId,
                season = if (isSeries) args.season else null,
                episode = if (isSeries) args.episode else null,
            )
            val list = r.results.filter { it.file_id != null || !it.url.isNullOrBlank() }.take(15)
            onlineResults = list
            if (list.isEmpty()) onlineError = r.error ?: "Nenhuma legenda PT-BR encontrada."
            list
        } catch (e: Exception) {
            onlineError = "Falha na busca de legendas."
            onlineResults = emptyList()
            emptyList()
        } finally {
            onlineLoading = false
        }
    }

    suspend fun applyOnline(item: SubtitleSearchItem): Boolean {
        onlineLoading = true
        onlineError = null
        return try {
            val r = NetworkModule.subtitlesApi.download(
                fileId = item.file_id,
                url = item.url,
                tmdbId = args.tmdbId,
                mediaType = if (isSeries) "tv" else "movie",
                season = if (isSeries) args.season else null,
                episode = if (isSeries) args.episode else null,
            )
            val cues = r.content?.let { parseSubtitleText(it) }.orEmpty()
            if (cues.isEmpty()) {
                onlineError = r.error ?: "Legenda vazia."
                false
            } else {
                selectTrack(null, C.TRACK_TYPE_TEXT)
                onlineCues = cues
                subOffsetMs = 0L
                onlineLabel = (item.release ?: "PT-BR").let { if (it.length > 32) it.take(32) + "…" else it }
                true
            }
        } catch (e: Exception) {
            onlineError = "Falha ao baixar a legenda."
            false
        } finally {
            onlineLoading = false
        }
    }

    // Legenda PT-BR automatica: so quando o audio e conhecido e nao e portugues
    LaunchedEffect(tracksVersion, firstReady) {
        if (autoSubsDone || !firstReady || !settings.autoSubtitles || args.tmdbId <= 0) return@LaunchedEffect
        val audio = listTracks(C.TRACK_TYPE_AUDIO)
        val selectedAudio = audio.firstOrNull { it.selected } ?: return@LaunchedEffect
        val lang = selectedAudio.language?.lowercase().orEmpty()
        if (lang.isBlank() || lang == "und" || isPortuguese(selectedAudio.language, selectedAudio.label)) {
            autoSubsDone = true
            return@LaunchedEffect
        }
        autoSubsDone = true
        val embeddedPt = listTracks(C.TRACK_TYPE_TEXT).firstOrNull { isPortuguese(it.language, it.label) }
        if (embeddedPt != null) {
            if (!embeddedPt.selected) selectTrack(embeddedPt, C.TRACK_TYPE_TEXT)
            toaster.show("Legenda em português ativada")
            return@LaunchedEffect
        }
        val found = searchOnline()
        if (found.isNotEmpty() && applyOnline(found.first())) toaster.show("Legenda PT-BR aplicada automaticamente")
    }

    // fim: filme sai, serie vai pro proximo (se ligado)
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        persist()
        if (isSeries && onNextEpisode != null && settings.autoplayNext && !nextDismissed) {
            goNext()
        } else if (!isSeries) {
            delay(800)
            exit()
        } else {
            showControls()
        }
        // ultimo episodio (ou falha ao achar o proximo): mostra os controles em vez de tela parada
        if (isSeries) {
            delay(2500)
            showControls()
        }
    }

    // derivedStateOf: a tela inteira nao recompoe a cada tique do relogio (TV box fraca)
    val showNextCard by remember {
        derivedStateOf {
            val rem = if (durationMs > 0) durationMs - positionMs else Long.MAX_VALUE
            isSeries && onNextEpisode != null && firstReady && !nextDismissed &&
                durationMs > 5 * 60_000 && rem in 1..NEXT_CARD_MS && panel == PlayerPanel.NONE
        }
    }
    LaunchedEffect(showNextCard) {
        if (showNextCard) {
            delay(80)
            runCatching { nextFocus.requestFocus() }
        }
    }

    BackHandler {
        when {
            showNextCard -> nextDismissed = true
            controlsVisible && fatalError == null && firstReady -> controlsVisible = false
            else -> exit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                val code = ev.nativeKeyEvent.keyCode
                val down = ev.type == KeyEventType.KeyDown
                // teclas de midia funcionam sempre
                when (code) {
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (down) { togglePlay(); showControls() }; return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_MEDIA_PLAY -> { if (down) { exo.play(); showControls() }; return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_MEDIA_PAUSE -> { if (down) { exo.pause(); showControls() }; return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) { seekBy(30_000); showControls() }; return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) { seekBy(-30_000); showControls() }; return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_MEDIA_NEXT -> { if (down && onNextEpisode != null) goNext(); return@onPreviewKeyEvent true }
                }
                if (panel != PlayerPanel.NONE || fatalError != null || showNextCard) return@onPreviewKeyEvent false
                if (controlsVisible) {
                    tick++
                    return@onPreviewKeyEvent false
                }
                when (code) {
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (!down) {
                            togglePlay()
                            showControls(playFocus)
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (down) {
                            scrub(code == AKeyEvent.KEYCODE_DPAD_RIGHT, ev.nativeKeyEvent.repeatCount)
                            showControls(seekFocus)
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (down) showControls(playFocus)
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    playerView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { v ->
                v.resizeMode = screenMode.mode
                v.subtitleView?.let { sv ->
                    applySubtitleStyle(sv, settings.subSize, settings.subColor, settings.subBackground)
                    sv.setBottomPaddingFraction(if (controlsVisible) 0.26f else 0.08f)
                }
            },
        )

        // tela de carregamento com a arte do titulo
        if (!everReady && fatalError == null) {
            Box(Modifier.fillMaxSize()) {
                FadeImage(TmdbImg.backdropLg(args.backdropPath), Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(args.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Conectando a ${current?.displayName ?: "servidor"}" + if (sources.size > 1) " (${sourceIndex + 1}/${sources.size})" else "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
            }
        } else if (isBuffering && fatalError == null) {
            CircularProgressIndicator(
                color = p.accent,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        // legenda online por cima do video
        val cue = currentCue
        if (cue != null) {
            Box(
                Modifier.fillMaxSize().padding(
                    start = 60.dp,
                    end = 60.dp,
                    top = 40.dp,
                    bottom = if (controlsVisible) 170.dp else 44.dp,
                ),
                contentAlignment = if (cue.top) Alignment.TopCenter else Alignment.BottomCenter,
            ) {
                SubtitleText(cueToAnnotated(cue.text), settings.subSize, settings.subColor, settings.subBackground)
            }
        }

        // ── controles ─────────────────────────────────────────────────────
        AnimatedVisibility(visible = controlsVisible && fatalError == null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)),
                    ),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))),
                    ),
                )
                // topo: titulo + servidor + relogio
                Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(args.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(
                                current?.displayName,
                                onlineLabel?.let { "Legenda: $it" },
                                if (speed != 1f) "${speed}x" else null,
                            ).joinToString("  ·  "),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    PlayerClock()
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp)) {
                    SeekBar(
                        positionMs = scrubMs ?: positionMs,
                        durationMs = durationMs,
                        scrubbing = scrubMs != null,
                        modifier = Modifier.focusRequester(seekFocus),
                        onScrub = { fwd, rep -> scrub(fwd, rep) },
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoundButton(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            big = true,
                            modifier = Modifier.focusRequester(playFocus),
                        ) { togglePlay(); tick++ }
                        RoundButton(Icons.Filled.Replay10) { seekBy(-10_000) }
                        RoundButton(Icons.Filled.Forward10) { seekBy(10_000) }
                        if (isSeries && positionMs < 10 * 60_000) {
                            TvButton("Pular abertura", onClick = { seekBy(SKIP_INTRO_MS) }, icon = Icons.Filled.FastForward, compact = true)
                        }
                        if (onNextEpisode != null) {
                            TvButton("Próximo episódio", onClick = { goNext() }, icon = Icons.Filled.SkipNext, compact = true)
                        }
                        Spacer(Modifier.weight(1f))
                        if (sources.size > 1) {
                            TvButton(
                                "Servidor",
                                onClick = { panel = PlayerPanel.SERVERS },
                                icon = Icons.Filled.Dns,
                                compact = true,
                                modifier = Modifier.focusRequester(panelButtons.getValue(PlayerPanel.SERVERS)),
                            )
                        }
                        TvButton(
                            "Legendas",
                            onClick = { panel = PlayerPanel.SUBTITLES },
                            icon = Icons.Filled.ClosedCaption,
                            compact = true,
                            modifier = Modifier.focusRequester(panelButtons.getValue(PlayerPanel.SUBTITLES)),
                        )
                        TvButton(
                            "Áudio",
                            onClick = { panel = PlayerPanel.AUDIO },
                            icon = Icons.Filled.Audiotrack,
                            compact = true,
                            modifier = Modifier.focusRequester(panelButtons.getValue(PlayerPanel.AUDIO)),
                        )
                        TvButton(
                            "Ajustes",
                            onClick = { panel = PlayerPanel.SETTINGS },
                            icon = Icons.Filled.Tune,
                            compact = true,
                            modifier = Modifier.focusRequester(panelButtons.getValue(PlayerPanel.SETTINGS)),
                        )
                    }
                }
            }
        }

        // cartao "proximo episodio"
        AnimatedVisibility(
            visible = showNextCard,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = if (controlsVisible) 150.dp else 48.dp),
        ) {
            Column(
                Modifier.width(320.dp).clip(RoundedCornerShape(16.dp)).background(p.bgElevated.copy(alpha = 0.95f))
                    .border(1.dp, p.glassBorder, RoundedCornerShape(16.dp)).padding(18.dp),
            ) {
                Text("A seguir", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Próximo episódio", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (settings.autoplayNext) "Começa em ${((durationMs - positionMs) / 1000).coerceAtLeast(0)}s" else "Reprodução automática desligada",
                    color = p.textMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("Assistir agora", onClick = { goNext() }, icon = Icons.Filled.SkipNext, primary = true, compact = true, modifier = Modifier.focusRequester(nextFocus))
                    TvButton("Ver créditos", onClick = { nextDismissed = true }, compact = true)
                }
            }
        }

        // erro final
        val err = fatalError
        if (err != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 560.dp)) {
                    Text("Não deu para reproduzir", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    if (sources.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text("Todos os ${sources.size} servidores foram testados.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Tentar de novo", onClick = {
                            failed.value = emptySet()
                            retryCount = 0
                            load(sourceIndex, startPositionMs.coerceAtLeast(positionMs))
                        }, icon = Icons.Filled.Refresh, primary = true, modifier = Modifier.focusRequester(errorFocus))
                        if (sources.size > 1) {
                            TvButton("Escolher servidor", onClick = {
                                failed.value = emptySet()
                                panel = PlayerPanel.SERVERS
                            }, icon = Icons.Filled.Dns)
                        }
                        TvButton("Voltar", onClick = { exit() })
                    }
                }
            }
        }

        // ── paineis ───────────────────────────────────────────────────────
        fun closePanel() {
            val back = panelButtons[panel]
            panel = PlayerPanel.NONE
            if (fatalError == null) {
                controlsVisible = true
                tick++
                back?.let { requestFocusOn(it) }
            } else {
                requestFocusOn(errorFocus)
            }
        }

        SidePanel(
            visible = panel == PlayerPanel.SERVERS,
            title = "Servidores",
            subtitle = "Troque se travar — o vídeo continua de onde parou",
            onDismiss = { closePanel() },
        ) {
            TvLazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sources.size) { i ->
                    val s = sources[i]
                    PanelItem(
                        title = s.displayName,
                        subtitle = when {
                            i == sourceIndex && fatalError == null -> "Tocando agora"
                            i in failed.value -> "Falhou nesta sessão"
                            else -> null
                        },
                        selected = i == sourceIndex,
                        leading = Icons.Filled.Dns,
                        trailingText = "${i + 1}",
                        modifier = if (i == 0) Modifier.focusRequester(panelFirst) else Modifier,
                        onClick = {
                            panel = PlayerPanel.NONE
                            failed.value = failed.value - i
                            switchTo(i)
                            controlsVisible = true
                            requestFocusOn(playFocus)
                        },
                    )
                }
            }
        }

        SidePanel(
            visible = panel == PlayerPanel.SUBTITLES,
            title = "Legendas",
            subtitle = onlineLabel?.let { "Online: $it" },
            onDismiss = { closePanel() },
        ) {
            val textTracks = remember(tracksVersion, onlineCues) { listTracks(C.TRACK_TYPE_TEXT) }
            val noneSelected = onlineCues.isEmpty() && textTracks.none { it.selected }
            TvLazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    PanelItem(
                        "Desligada",
                        selected = noneSelected,
                        modifier = Modifier.focusRequester(panelFirst),
                        onClick = {
                            selectTrack(null, C.TRACK_TYPE_TEXT)
                            onlineCues = emptyList()
                            onlineLabel = null
                            tracksVersion++
                        },
                    )
                }
                items(textTracks) { t ->
                    PanelItem(t.label, subtitle = "Do vídeo", selected = t.selected && onlineCues.isEmpty(), leading = Icons.Filled.ClosedCaption, onClick = {
                        onlineCues = emptyList()
                        onlineLabel = null
                        selectTrack(t, C.TRACK_TYPE_TEXT)
                        tracksVersion++
                    })
                }
                item { PanelHeader("Online · Português (BR)") }
                val results = onlineResults
                if (results == null) {
                    item {
                        PanelItem(
                            if (onlineLoading) "Buscando…" else "Buscar legendas online",
                            leading = Icons.Filled.Search,
                            onClick = { if (!onlineLoading) scope.launch { searchOnline() } },
                        )
                    }
                } else {
                    items(results) { r ->
                        PanelItem(
                            r.release ?: "Legenda PT-BR",
                            subtitle = listOfNotNull(r.source, if (r.hd) "HD" else null, if (r.downloads > 0) "${r.downloads} downloads" else null).joinToString(" · ").ifBlank { null },
                            selected = onlineLabel != null && (r.release ?: "PT-BR").startsWith(onlineLabel!!.removeSuffix("…")),
                            onClick = {
                                scope.launch {
                                    if (applyOnline(r)) {
                                        toaster.show("Legenda aplicada")
                                        tracksVersion++
                                    }
                                }
                            },
                        )
                    }
                    item {
                        PanelItem(if (onlineLoading) "Buscando…" else "Buscar de novo", leading = Icons.Filled.Refresh, onClick = {
                            if (!onlineLoading) scope.launch { searchOnline() }
                        })
                    }
                }
                onlineError?.let { e -> item { Text(e, color = p.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) } }
                if (onlineCues.isNotEmpty()) {
                    item { PanelHeader("Sincronia · ${formatOffset(subOffsetMs)}") }
                    item { PanelItem("Legenda mais cedo (−0,5s)", onClick = { subOffsetMs -= 500 }) }
                    item { PanelItem("Legenda mais tarde (+0,5s)", onClick = { subOffsetMs += 500 }) }
                    if (subOffsetMs != 0L) item { PanelItem("Zerar sincronia", onClick = { subOffsetMs = 0 }) }
                }
                item { PanelHeader("Aparência") }
                item {
                    PanelItem("Tamanho", trailingText = settings.subSize.label, onClick = {
                        TvSettingsStore.update { it.copy(subSize = nextOf(it.subSize, SubSize.entries)) }
                    })
                }
                item {
                    PanelItem("Cor", trailingText = settings.subColor.label, onClick = {
                        TvSettingsStore.update { it.copy(subColor = nextOf(it.subColor, SubColor.entries)) }
                    })
                }
                item {
                    PanelItem("Fundo", trailingText = settings.subBackground.label, onClick = {
                        TvSettingsStore.update { it.copy(subBackground = nextOf(it.subBackground, SubBackground.entries)) }
                    })
                }
            }
        }

        SidePanel(
            visible = panel == PlayerPanel.AUDIO,
            title = "Áudio",
            subtitle = "Idioma da faixa de som",
            onDismiss = { closePanel() },
        ) {
            val audio = remember(tracksVersion) { listTracks(C.TRACK_TYPE_AUDIO) }
            if (audio.isEmpty()) {
                Text("Este vídeo tem só uma faixa de áudio.", color = p.textMuted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                TvButton("Fechar", onClick = { closePanel() }, modifier = Modifier.focusRequester(panelFirst))
            } else {
                TvLazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(audio.size) { i ->
                        val a = audio[i]
                        PanelItem(
                            a.label,
                            selected = a.selected,
                            leading = Icons.Filled.Audiotrack,
                            modifier = if (i == 0) Modifier.focusRequester(panelFirst) else Modifier,
                            onClick = {
                                selectTrack(a, C.TRACK_TYPE_AUDIO)
                                tracksVersion++
                            },
                        )
                    }
                }
            }
        }

        SidePanel(
            visible = panel == PlayerPanel.SETTINGS,
            title = "Ajustes",
            onDismiss = { closePanel() },
        ) {
            val video = remember(tracksVersion) { listTracks(C.TRACK_TYPE_VIDEO).filter { it.height > 0 }.distinctBy { it.height }.sortedByDescending { it.height } }
            val hasOverride = remember(tracksVersion) {
                trackSelector.parameters.overrides.keys.any { it.type == C.TRACK_TYPE_VIDEO }
            }
            TvLazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { PanelHeader("Tela") }
                items(ScreenMode.entries.size) { i ->
                    val m = ScreenMode.entries[i]
                    PanelItem(
                        m.label,
                        selected = screenMode == m,
                        leading = Icons.Filled.AspectRatio,
                        modifier = if (i == 0) Modifier.focusRequester(panelFirst) else Modifier,
                        onClick = { screenMode = m },
                    )
                }
                item { PanelHeader("Velocidade") }
                items(SPEEDS) { s ->
                    PanelItem(
                        if (s == 1f) "Normal" else "${s}x".replace(".", ","),
                        selected = speed == s,
                        leading = Icons.Filled.Speed,
                        onClick = {
                            speed = s
                            exo.setPlaybackSpeed(s)
                        },
                    )
                }
                if (video.size > 1) {
                    item { PanelHeader("Qualidade") }
                    item {
                        PanelItem("Automática", selected = !hasOverride, leading = Icons.Filled.HighQuality, onClick = {
                            selectTrack(null, C.TRACK_TYPE_VIDEO)
                            tracksVersion++
                        })
                    }
                    items(video) { v ->
                        PanelItem(v.label, selected = hasOverride && v.selected, leading = Icons.Filled.HighQuality, onClick = {
                            selectTrack(v, C.TRACK_TYPE_VIDEO)
                            tracksVersion++
                        })
                    }
                }
                item { PanelHeader("Reprodução") }
                item {
                    PanelItem(
                        "Próximo episódio automático",
                        trailingText = if (settings.autoplayNext) "Ligado" else "Desligado",
                        leading = Icons.Filled.Settings,
                        onClick = { TvSettingsStore.update { it.copy(autoplayNext = !it.autoplayNext) } },
                    )
                }
            }
        }
    }
}

private fun formatOffset(ms: Long): String {
    if (ms == 0L) return "0,0s"
    val sign = if (ms > 0) "+" else "−"
    val abs = kotlin.math.abs(ms)
    return "$sign${abs / 1000},${(abs % 1000) / 100}s"
}

@Composable
private fun PanelHeader(text: String) {
    val p = LocalPalette.current
    Text(
        text.uppercase(),
        color = p.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    scrubbing: Boolean,
    modifier: Modifier,
    onScrub: (forward: Boolean, repeat: Int) -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.nativeKeyEvent.keyCode) {
                        AKeyEvent.KEYCODE_DPAD_LEFT -> { onScrub(false, ev.nativeKeyEvent.repeatCount); true }
                        AKeyEvent.KEYCODE_DPAD_RIGHT -> { onScrub(true, ev.nativeKeyEvent.repeatCount); true }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val barH = if (focused) 8.dp else 4.dp
            Box(Modifier.fillMaxWidth().height(barH).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(p.accent))
            }
            if (focused) {
                val thumb = 18.dp
                Box(
                    Modifier.offset(x = (maxWidth - thumb) * fraction).size(thumb).clip(CircleShape)
                        .background(Color.White).border(BorderStroke(3.dp, p.accent), CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                formatTime(positionMs),
                color = if (scrubbing) p.accent else Color.White,
                fontSize = if (scrubbing) 16.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (focused && !scrubbing) {
                Text("◀ ▶ para avançar ou voltar", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
            }
            Text(
                if (durationMs > 0) "-" + formatTime((durationMs - positionMs).coerceAtLeast(0)) else "--:--",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, modifier: Modifier = Modifier, big: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val size = if (big) 56.dp else 46.dp
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (big) p.accent else Color.White.copy(alpha = 0.14f),
            contentColor = if (big) p.onAccent else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(if (big) 30.dp else 24.dp))
        }
    }
}

@Composable
private fun PlayerClock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }
    Text(fmt.format(now), color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}
