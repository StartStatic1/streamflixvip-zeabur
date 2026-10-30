package com.streamflixvip.app.ui.player

import com.streamflixvip.app.data.ResumePlaybackCache

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.media.AudioManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Icon
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.common.AudioAttributes
import androidx.media3.datasource.HttpDataSource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.data.ProgressRepository
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.StreamUrlResolver
import com.streamflixvip.app.network.SubtitleSearchItem
import com.streamflixvip.app.network.VipSource
import java.io.File
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PROGRESS_SAVE_INTERVAL_MS = 15_000L

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private enum class AspectMode(val label: String, val resizeMode: Int) {
    FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun isLikelyHls(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("type=m3u8") ||
        lower.contains("/hls/") || lower.contains("mpegurl") || lower.contains("index.m3u") ||
        (lower.contains("playlist") && (lower.contains("m3u") || lower.contains("/hls/"))) ||
        (lower.contains("pengu.uk") && lower.contains("/hls/"))
}

private fun playbackHeaders(url: String): Map<String, String> {
    val host = try { java.net.URI(url).host.orEmpty() } catch (_: Exception) { "" }
    val origin = when {
        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.contains("hakunaymatata", ignoreCase = true) -> "https://mzfi.me/"
        host.contains("mzfi.me", ignoreCase = true) -> "https://mzfi.me/"
        host.contains("streamflixvip", ignoreCase = true) -> "https://mzfi.me/"
        host.isNotBlank() -> "https://$host/"
        else -> url
    }
    return mapOf(
        "Referer" to origin,
        "Accept" to "*/*",
        "Connection" to "keep-alive",
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun playbackHttpFactory(url: String): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
        .setDefaultRequestProperties(playbackHeaders(url))


private fun isLikelyDash(url: String): Boolean {
    val lower = url.lowercase()
    return lower.substringBefore('?').endsWith(".mpd") || lower.contains("format=mpd")
}

/** Fonte certa para a URL: HLS, DASH ou arquivo (mp4/mkv/ts). forceHls = 2a tentativa. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun buildPlaybackMediaSource(url: String, forceHls: Boolean = false): MediaSource {
    val http = playbackHttpFactory(url)
    return when {
        forceHls || isLikelyHls(url) -> HlsMediaSource.Factory(http).createMediaSource(
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
        )
        isLikelyDash(url) -> DashMediaSource.Factory(http).createMediaSource(
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_MPD).build(),
        )
        else -> ProgressiveMediaSource.Factory(
            http,
            DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
        ).createMediaSource(MediaItem.fromUri(url))
    }
}

/**
 * Buffer pensado para servidores IPTV/addons: comeca a tocar com 1,5s,
 * volta de travada com 3s e guarda ate 90s a frente (limite de memoria padrao do Exo).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun streamingLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(25_000, 90_000, 1_500, 3_000)
    .build()

private val NETWORK_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_TIMEOUT,
)

private fun isContainerError(code: Int): Boolean =
    code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
        code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun friendlyPlaybackError(error: PlaybackException): String {
    val code = error.errorCode
    val name = error.errorCodeName ?: ""
    return when {
        name.contains("DECODING", ignoreCase = true) || name.contains("DECODER", ignoreCase = true) ->
            "Este aparelho nao consegue decodificar este video (codec). Toque em Trocar servidor ou abra no VLC."
        code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            "O servidor recusou o video" + (if (http != null) " (HTTP $http)" else "") + ". Tente outro servidor."
        }
        code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Video nao encontrado neste servidor. Tente outro servidor."
        code in NETWORK_ERROR_CODES ->
            "Sem resposta do servidor do video. Verifique sua internet ou troque de servidor."
        isContainerError(code) || name.contains("PARSING", ignoreCase = true) ->
            "Formato de video nao suportado neste servidor. Tente outro servidor."
        else -> "Erro ao reproduzir ($name). Tente outro servidor."
    }
}

private const val SEEK_STEP_MS = 10_000L

private const val SERIES_PREFS = "streamflix_series_prefs"

private fun detectSourceMode(label: String?): String {
    val n = (label ?: "").lowercase()
    val leg = listOf("leg", "legend", "legendado", "legendada", "sub ", "subs", "subtitle").any { n.contains(it) }
    val dub = listOf("dub", "dublado", "dublada", "dual").any { n.contains(it) }
    return when {
        leg && !dub -> "leg"
        dub && !leg -> "dub"
        else -> "any"
    }
}

private fun sourceModeScore(label: String?, preferred: String): Int {
    if (preferred == "any") return 0
    val m = detectSourceMode(label)
    return when {
        m == preferred -> 100
        m == "any" -> 40
        else -> 0
    }
}

private fun normKey(s: String?): String =
    (s ?: "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

private fun trackMatchesPref(label: String, pref: String): Boolean {
    if (pref.isBlank() || pref == "off") return false
    val a = normKey(label)
    val b = normKey(pref)
    if (a == b || a.contains(b) || b.contains(a)) return true
    val ptHints = listOf("pt", "por", "portugues", "portuguese", "brazil", "br")
    val enHints = listOf("en", "eng", "english", "ingles")
    if (ptHints.any { b.contains(it) } && ptHints.any { a.contains(it) }) return true
    if (enHints.any { b.contains(it) } && enHints.any { a.contains(it) }) return true
    return false
}

private fun seriesPrefs(context: android.content.Context) =
    context.getSharedPreferences(SERIES_PREFS, android.content.Context.MODE_PRIVATE)

private fun loadSeriesPref(context: android.content.Context, tmdbId: Int, key: String, default: String = ""): String =
    seriesPrefs(context).getString("${tmdbId}_$key", default) ?: default

private fun saveSeriesPref(context: android.content.Context, tmdbId: Int, key: String, value: String) {
    seriesPrefs(context).edit().putString("${tmdbId}_$key", value).apply()
}

private data class TrackOption(val label: String, val group: TrackGroup, val trackIndex: Int, val language: String? = null)

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String, val top: Boolean = false)

private fun humanTrackLabel(label: String?, language: String?, index: Int): String {
    val raw = (label ?: language ?: "").trim()
    val low = raw.lowercase()
    if (raw.isBlank() || low in setOf("und", "undefined", "null", "unknown")) {
        return "Faixa ${index + 1}"
    }
    val map = mapOf(
        "pt" to "Portugues",
        "por" to "Portugues",
        "pt-br" to "Portugues (BR)",
        "pt-pt" to "Portugues (PT)",
        "en" to "Ingles",
        "eng" to "Ingles",
        "es" to "Espanhol",
        "spa" to "Espanhol",
        "fr" to "Frances",
        "de" to "Alemao",
        "it" to "Italiano",
        "ja" to "Japones",
        "ko" to "Coreano",
        "zh" to "Chines",
        "ru" to "Russo",
    )
    map[low]?.let { return it }
    // language codes like por-BR
    val base = low.split("-", "_").firstOrNull() ?: low
    map[base]?.let { return it }
    return raw
}


private fun openInExternalPlayer(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum player externo instalado (ex: VLC).", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PlayerScreen(
    sourceUrl: String,
    isDirectPlayable: Boolean,
    userId: String?,
    accessToken: String?,
    tmdbId: Int,
    mediaType: String,
    season: Int,
    episode: Int,
    title: String,
    posterPath: String?,
    resumeSeconds: Int = 0,
    onBack: () -> Unit = {},
) {
    val view = LocalView.current
    androidx.compose.runtime.LaunchedEffect(sourceUrl, tmdbId, season, episode) {
        ResumePlaybackCache.init(view.context)
        if (sourceUrl.isNotBlank()) {
            ResumePlaybackCache.put(tmdbId, mediaType, season, episode, sourceUrl, isDirectPlayable)
        }
    }
    BackHandler { onBack() }
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val activity = view.context as? Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var resolvedUrl by remember(sourceUrl) { mutableStateOf<String?>(if (isDirectPlayable) null else sourceUrl) }
    LaunchedEffect(sourceUrl, isDirectPlayable) {
        if (isDirectPlayable) {
            val candidates = VipSource(source_url = sourceUrl, source_label = null, priority = null)
                .candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
            resolvedUrl = StreamUrlResolver.resolveFastest(candidates)
        }
    }

    val currentResolvedUrl = resolvedUrl
    if (currentResolvedUrl == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (isDirectPlayable) {
        NativePlayer(
            url = currentResolvedUrl,
            userId = userId,
            accessToken = accessToken,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
            posterPath = posterPath,
            resumeSeconds = resumeSeconds,
            onBack = onBack,
        )
    } else {
        EmbedWebView(url = currentResolvedUrl)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(DelicateCoroutinesApi::class)
@Composable
private fun NativePlayer(
    url: String,
    userId: String?,
    accessToken: String?,
    tmdbId: Int,
    mediaType: String,
    season: Int,
    episode: Int,
    title: String,
    posterPath: String?,
    resumeSeconds: Int,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val progressRepository = remember { ProgressRepository() }
    // Portugues primeiro (audio e legenda) quando o video tem mais de uma faixa
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguages("pt-BR", "pt", "por")
                .setPreferredTextLanguages("pt-BR", "pt", "por")
                .build()
        }
    }
    var subStyle by remember { mutableStateOf(SubtitleStylePrefs.load(context)) }
    var currentAudioLang by remember { mutableStateOf<String?>(null) }

    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var subtitleOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var audioOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var qualityOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var selectedSubtitleLabel by remember { mutableStateOf("Desligada") }
    var onlineSubtitleResults by remember { mutableStateOf(listOf<SubtitleSearchItem>()) }
    var onlineSubtitlesLoading by remember { mutableStateOf(false) }
    var onlineSubtitlesError by remember { mutableStateOf<String?>(null) }
    var onlineSubtitleApplied by remember { mutableStateOf(false) }
    var subtitleOffsetMs by remember {
        mutableStateOf(loadSeriesPref(context, tmdbId, "sub_offset", "0").toLongOrNull() ?: 0L)
    }
    var onlineSubtitleRaw by remember { mutableStateOf<String?>(null) }
    var overlayCues by remember { mutableStateOf(listOf<SubtitleCue>()) }
    var overlaySubText by remember { mutableStateOf<String?>(null) }
    var overlaySubTop by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf(0L) }
    var scrubDuration by remember { mutableStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var selectedAudioLabel by remember { mutableStateOf("Padrao") }
    var selectedQualityLabel by remember { mutableStateOf("Automatico") }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var settingsPanel by remember { mutableStateOf(SettingsPanel.NONE) }
    var controlsVisible by remember { mutableStateOf(false) }
    // Controle remoto (TV): so muda o comportamento depois que uma tecla do controle e usada
    var usingRemote by remember { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(false) }
    var focusChipsPending by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val chipsFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryAttempt by remember { mutableStateOf(0) }
    var playbackErrorTick by remember { mutableStateOf(0) }
    var lastPlaybackError by remember { mutableStateOf<PlaybackException?>(null) }
    var hlsFallbackTried by remember { mutableStateOf(false) }
    val triedUrls = remember { mutableSetOf<String>() }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var isRecovering by remember { mutableStateOf(false) }
    var activeUrl by remember { mutableStateOf(url) }
    var alternateSources by remember { mutableStateOf(emptyList<String>()) }
    var alternateLabels by remember { mutableStateOf(mapOf<String, String>()) }
    var alternateIndex by remember { mutableStateOf(0) }
    var currentSeason by remember { mutableStateOf(season) }
    var currentEpisode by remember { mutableStateOf(episode) }
    var currentTitle by remember { mutableStateOf(title) }
    var isLoadingNext by remember { mutableStateOf(false) }

    var showNextPrompt by remember { mutableStateOf(false) }
    var nextDismissedFor by remember { mutableStateOf(-1) }

    // Gestos: esquerda = brilho, direita = volume
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var brightnessLevel by remember {
        val cur = (context as? android.app.Activity)?.window?.attributes?.screenBrightness ?: -1f
        mutableStateOf(if (cur in 0f..1f) cur else 0.5f)
    }
    var volumeLevel by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var gestureKind by remember { mutableStateOf<String?>(null) }
    var gestureValue by remember { mutableStateOf(0f) }
    var gestureHideJob by remember { mutableStateOf<Job?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    var preferredSourceMode by remember {
        mutableStateOf(loadSeriesPref(context, tmdbId, "source_mode", "any"))
    }
    var preferredAudioKey by remember {
        mutableStateOf(loadSeriesPref(context, tmdbId, "audio", ""))
    }
    var preferredSubtitleKey by remember {
        mutableStateOf(loadSeriesPref(context, tmdbId, "subtitle", ""))
    }
    var pendingReapplyTracks by remember { mutableStateOf(false) }

    fun persistSourceMode(mode: String) {
        preferredSourceMode = mode
        saveSeriesPref(context, tmdbId, "source_mode", mode)
    }
    fun persistAudioKey(key: String) {
        preferredAudioKey = key
        saveSeriesPref(context, tmdbId, "audio", key)
    }
    fun persistSubtitleKey(key: String) {
        preferredSubtitleKey = key
        saveSeriesPref(context, tmdbId, "subtitle", key)
    }

    fun reapplyTrackPreferences(
        subs: List<TrackOption> = subtitleOptions,
        audios: List<TrackOption> = audioOptions,
    ) {
        val builder = trackSelector.parameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        if (!onlineSubtitleApplied) {
            val subPref = preferredSubtitleKey
            when {
                subPref.isBlank() -> {
                    // Sem escolha salva: audio estrangeiro + legenda PT no video = liga sozinha
                    val lang = currentAudioLang?.lowercase().orEmpty()
                    val audioForeign = lang.isNotBlank() && lang != "und" && !lang.startsWith("pt") && !lang.startsWith("por")
                    val ptSub = if (audioForeign) subs.firstOrNull { isPortugueseTrack(it.language, it.label) } else null
                    if (ptSub != null) {
                        builder.setOverrideForType(TrackSelectionOverride(ptSub.group, ptSub.trackIndex))
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        selectedSubtitleLabel = "Stream: ${ptSub.label}"
                    } else {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        selectedSubtitleLabel = "Desligada"
                    }
                }
                subPref == "off" -> {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    selectedSubtitleLabel = "Desligada"
                }
                subPref == "online" -> {
                    // Legenda externa em carga / re-prepare — nao desligar texto
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage("pt")
                }
                else -> {
                    val match = subs.firstOrNull { trackMatchesPref(it.label, subPref) }
                    if (match != null) {
                        builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        selectedSubtitleLabel = "Stream: ${match.label}"
                    }
                }
            }
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
        }
        val audioPref = preferredAudioKey
        if (audioPref.isNotBlank() && audioPref != "default") {
            val match = audios.firstOrNull { trackMatchesPref(it.label, audioPref) }
            if (match != null) {
                builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                selectedAudioLabel = match.label
            }
        }
        trackSelector.parameters = builder.build()
    }


    val exoPlayer = remember {
        val httpDataSourceFactory = playbackHttpFactory(url)
        val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory).setDataSourceFactory(httpDataSourceFactory)
        val mediaSource = buildPlaybackMediaSource(url)
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(streamingLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true, // pausa em ligacao / outro app tocando audio
            )
            .setHandleAudioBecomingNoisy(true) // pausa ao tirar o fone
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
            if (resumeSeconds > 0) seekTo(resumeSeconds * 1000L)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    // Tratado no LaunchedEffect(playbackErrorTick): tenta de novo, HLS, outro servidor.
                    lastPlaybackError = error
                    playbackErrorTick += 1
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) retryAttempt = 0
                }
                override fun onTracksChanged(tracks: Tracks) {
                    val subtitles = mutableListOf<TrackOption>()
                    val audios = mutableListOf<TrackOption>()
                    val qualities = mutableListOf<TrackOption>()
                    for (group in tracks.groups) {
                        when (group.type) {
                            C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
                                subtitles += TrackOption(humanTrackLabel(f.label, f.language, i), group.mediaTrackGroup, i, f.language)
                            }
                            C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
                                if (group.isTrackSelected(i)) currentAudioLang = f.language
                                audios += TrackOption(humanTrackLabel(f.label, f.language, i), group.mediaTrackGroup, i)
                            }
                            C.TRACK_TYPE_VIDEO -> for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
                                if (f.height > 0) qualities += TrackOption("${f.height}p", group.mediaTrackGroup, i)
                            }
                            else -> {}
                        }
                    }
                    subtitleOptions = subtitles
                    audioOptions = if (audios.size > 1) audios else emptyList()
                    qualityOptions = qualities.distinctBy { it.label }.sortedByDescending { it.label.removeSuffix("p").toIntOrNull() ?: 0 }
                    pendingReapplyTracks = true
                }
            })
        }
    }

    LaunchedEffect(pendingReapplyTracks, subtitleOptions, audioOptions) {
        if (!pendingReapplyTracks) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        reapplyTrackPreferences(subtitleOptions, audioOptions)
        pendingReapplyTracks = false
    }

        fun reloadWithUrl(streamUrl: String, resetPosition: Boolean = false, forceHls: Boolean = false) {
        try {
            val keepPos = if (resetPosition) 0L else exoPlayer.currentPosition.coerceAtLeast(0L)
            errorMessage = null
            isRecovering = true
            if (streamUrl != activeUrl) hlsFallbackTried = false
            activeUrl = streamUrl
            val mediaSource = buildPlaybackMediaSource(streamUrl, forceHls)
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            pendingReapplyTracks = true
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            if (keepPos > 1000L) exoPlayer.seekTo(keepPos) else if (resetPosition) exoPlayer.seekTo(0)
            exoPlayer.playWhenReady = true
            isRecovering = false
        } catch (e: Exception) {
            isRecovering = false
            errorMessage = e.message ?: "Falha ao recarregar"
        }
    }

    suspend fun loadAlternateSources() {
        try {
            val resp = if (mediaType == "tv" && currentSeason > 0) {
                NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, "tv", currentSeason, currentEpisode)
            } else {
                NetworkModule.mediaSourcesApi.getMovieSources(tmdbId, mediaType)
            }
            val labeled = mutableListOf<Pair<String, String>>()
            for (src in resp.sources.filter { it.isDirectPlayable }) {
                val nome = src.displayName.ifBlank { "Servidor" }
                for (u in src.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)) {
                    if (u.isNotBlank() && u != activeUrl && labeled.none { it.first == u }) {
                        labeled += u to nome
                    }
                }
            }
            alternateSources = labeled.map { it.first }
            alternateLabels = labeled.associate { it.first to it.second }
            alternateIndex = 0
        } catch (_: Exception) {
            alternateSources = emptyList()
        }
    }

    LaunchedEffect(url, tmdbId) {
        try { loadAlternateSources() } catch (_: Exception) {}
    }

    // Recuperacao automatica de erro de reproducao:
    // 1) ao vivo atrasado -> volta pro ao vivo; 2) formato nao reconhecido -> tenta como HLS;
    // 3) falha de rede -> tenta a mesma fonte 2x (mantendo a posicao);
    // 4) proxima fonte ainda nao testada (sem ficar indo e voltando); 5) mostra erro claro.
    LaunchedEffect(playbackErrorTick) {
        if (playbackErrorTick == 0) return@LaunchedEffect
        val error = lastPlaybackError ?: return@LaunchedEffect
        val code = error.errorCode
        if (code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            exoPlayer.seekToDefaultPosition()
            exoPlayer.prepare()
            return@LaunchedEffect
        }
        isRecovering = true
        if (!hlsFallbackTried && isContainerError(code) && !isLikelyHls(activeUrl)) {
            hlsFallbackTried = true
            reloadWithUrl(activeUrl, forceHls = true)
            return@LaunchedEffect
        }
        if (code in NETWORK_ERROR_CODES && retryAttempt < 2) {
            retryAttempt += 1
            delay(1500L * retryAttempt)
            reloadWithUrl(activeUrl)
            return@LaunchedEffect
        }
        triedUrls += activeUrl
        if (alternateSources.isEmpty()) {
            try { loadAlternateSources() } catch (_: Exception) {}
        }
        val next = alternateSources.firstOrNull { it.isNotBlank() && it !in triedUrls }
        if (next != null) {
            retryAttempt = 0
            val nome = alternateLabels[next]
            Toast.makeText(
                context,
                if (nome.isNullOrBlank()) "Trocando de servidor..." else "Trocando para $nome...",
                Toast.LENGTH_SHORT,
            ).show()
            reloadWithUrl(next)
            return@LaunchedEffect
        }
        isRecovering = false
        errorMessage = friendlyPlaybackError(error)
    }

    fun seekBy(deltaMs: Long) {
        val dur = exoPlayer.duration
        var target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        if (dur > 0) target = target.coerceAtMost((dur - 1_000L).coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        seekFeedback = if (deltaMs >= 0) "+${deltaMs / 1000}s" else "-${-deltaMs / 1000}s"
        gestureHideJob?.cancel()
        gestureKind = "seek"
        gestureHideJob = MainScope().launch {
            delay(700)
            gestureKind = null
        }
    }

    suspend fun playEpisodeFrom(tries: List<Pair<Int, Int>>) {
        if (mediaType != "tv" || currentSeason <= 0 || currentEpisode <= 0) return
        isLoadingNext = true
        showNextPrompt = false
        try {
            var played = false
            for ((s, e) in tries) {
                val resp = try {
                    NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, "tv", s, e)
                } catch (_: Exception) { continue }
                val direct = resp.sources.filter { it.isDirectPlayable }
                if (direct.isEmpty()) continue
                val ranked = direct.sortedWith(
                    compareByDescending<VipSource> { sourceModeScore(it.source_label, preferredSourceMode) }
                        .thenByDescending { it.priority ?: 0 },
                )
                val src = ranked.first()
                val mode = detectSourceMode(src.source_label)
                if (mode != "any") persistSourceMode(mode)
                val urls = src.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
                val playUrl = StreamUrlResolver.resolveFastest(urls).ifBlank { src.resolvedPlaybackUrl(BuildConfig.API_BASE_URL) }
                if (playUrl.isBlank()) continue
                currentSeason = s
                currentEpisode = e
                currentTitle = "$title — S${s}E${e}"
                // fontes alternativas eram do episodio anterior
                triedUrls.clear()
                alternateSources = emptyList()
                alternateLabels = emptyMap()
                retryAttempt = 0
                reloadWithUrl(playUrl, resetPosition = true)
                played = true
                break
            }
            if (!played) errorMessage = "Episódio sem fonte disponível no momento"
        } catch (e: Exception) {
            errorMessage = "Não foi possível abrir o episódio"
        } finally {
            isLoadingNext = false
        }
    }

    suspend fun playNextEpisode() = playEpisodeFrom(listOf(currentSeason to (currentEpisode + 1), (currentSeason + 1) to 1))
    suspend fun playEpisode(s: Int, e: Int) = playEpisodeFrom(listOf(s to e))


    fun reapplySubtitleOffset(newOffset: Long) {
        subtitleOffsetMs = newOffset
        saveSeriesPref(context, tmdbId, "sub_offset", newOffset.toString())
        val raw = onlineSubtitleRaw
        if (raw.isNullOrBlank() || !onlineSubtitleApplied) {
            Toast.makeText(context, "Sync só para legenda online", Toast.LENGTH_SHORT).show()
            return
        }
        // O atraso e aplicado na hora de mostrar (antes o ajuste reescrevia o texto
        // com uma regex que nunca casava, entao o Sync nao fazia nada).
        val sign = if (newOffset >= 0) "+" else ""
        Toast.makeText(context, "Sync ${sign}${newOffset / 1000.0}s", Toast.LENGTH_SHORT).show()
    }

    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        overlayCues = emptyList()
        overlaySubText = null
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = "Desligada"
            persistSubtitleKey("off")
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selectedSubtitleLabel = "Stream: ${option.label}"
            persistSubtitleKey(option.label)
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }

    suspend fun searchOnlineSubtitles() {
        onlineSubtitlesLoading = true
        onlineSubtitlesError = null
        try {
            val seasonArg = if (mediaType == "tv" && currentSeason > 0) currentSeason else null
            val episodeArg = if (mediaType == "tv" && currentEpisode > 0) currentEpisode else null
            val resp = NetworkModule.subtitlesApi.search(
                tmdbId = tmdbId,
                season = seasonArg,
                episode = episodeArg,
            )
            if (!resp.error.isNullOrBlank()) {
                onlineSubtitlesError = resp.error
                onlineSubtitleResults = emptyList()
            } else {
                onlineSubtitleResults = resp.results.filter { it.file_id != null || !it.url.isNullOrBlank() }.take(12)
                if (onlineSubtitleResults.isEmpty()) {
                    onlineSubtitlesError = "Nenhuma legenda PT-BR encontrada"
                }
            }
        } catch (e: Exception) {
            onlineSubtitlesError = e.message ?: "Falha na busca"
            onlineSubtitleResults = emptyList()
        } finally {
            onlineSubtitlesLoading = false
        }
    }

    suspend fun applyOnlineSubtitle(item: SubtitleSearchItem) {
        val fileId = item.file_id
        val directUrl = item.url
        if (fileId == null && directUrl.isNullOrBlank()) {
            onlineSubtitlesError = "Legenda sem arquivo"
            return
        }
        onlineSubtitlesLoading = true
        onlineSubtitlesError = null
        try {
            val seasonArg = if (mediaType == "tv" && currentSeason > 0) currentSeason else null
            val episodeArg = if (mediaType == "tv" && currentEpisode > 0) currentEpisode else null
            val resp = NetworkModule.subtitlesApi.download(
                fileId = fileId,
                url = directUrl,
                tmdbId = tmdbId,
                mediaType = if (mediaType == "tv") "tv" else "movie",
                season = seasonArg,
                episode = episodeArg,
            )
            val content = resp.content
            if (content.isNullOrBlank()) {
                onlineSubtitlesError = resp.error ?: "Legenda vazia"
                return
            }
            onlineSubtitleRaw = content
            // Overlay: nao remonta o video (evita tela preta)
            overlayCues = parseSubtitleCues(content)
            onlineSubtitleApplied = true
            persistSubtitleKey("online")
            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "…" else it }
            selectedSubtitleLabel = "Online: $short"
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            settingsPanel = SettingsPanel.MAIN
        } catch (e: Exception) {
            onlineSubtitleApplied = false
            onlineSubtitlesError = e.message ?: "Falha ao baixar legenda"
        } finally {
            onlineSubtitlesLoading = false
        }
    }

    fun selectAudio(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedAudioLabel = "Padrao"
            persistAudioKey("default")
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        } else {
            selectedAudioLabel = option.label
            persistAudioKey(option.label)
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).build()
        }
    }
    fun selectQuality(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedQualityLabel = "Automatico"
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        } else {
            selectedQualityLabel = option.label
            trackSelector.parameters.buildUpon().setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex)).setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        }
    }

    suspend fun persistCurrentPosition() {
        if (userId == null || accessToken == null) return
        val positionSeconds = (exoPlayer.currentPosition / 1000).toInt()
        val durationSeconds = (exoPlayer.duration / 1000).toInt()
        if (durationSeconds <= 0) return
        progressRepository.saveProgress(
            accessToken, userId, tmdbId, mediaType, currentSeason, currentEpisode,
            currentTitle, posterPath, positionSeconds, durationSeconds,
        )
    }

    // Ao abrir menu Legendas, busca PT-BR online automaticamente se lista vazia
    LaunchedEffect(settingsPanel) {
        if (settingsPanel == SettingsPanel.SUBTITLE &&
            onlineSubtitleResults.isEmpty() &&
            !onlineSubtitlesLoading
        ) {
            searchOnlineSubtitles()
        }
    }

    LaunchedEffect(exoPlayer, overlayCues, onlineSubtitleApplied) {
        while (true) {
            if (onlineSubtitleApplied && overlayCues.isNotEmpty()) {
                // atraso positivo = legenda aparece depois
                val pos = exoPlayer.currentPosition - subtitleOffsetMs
                val active = overlayCues.filter { pos >= it.startMs && pos < it.endMs }
                overlaySubText = if (active.isEmpty()) null else active.joinToString("\n") { it.text }
                overlaySubTop = active.any { it.top }
            } else if (overlaySubText != null) {
                overlaySubText = null
            }
            delay(200)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                scrubPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0) scrubDuration = d
            }
            delay(400)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            if (userId != null && accessToken != null) persistCurrentPosition()
            if (mediaType == "tv" && currentEpisode > 0) {
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0 && (dur - pos) in 1..30_000L && !showNextPrompt && !isLoadingNext &&
                    nextDismissedFor != currentSeason * 10_000 + currentEpisode) {
                    showNextPrompt = true
                }
            }
        }
    }

    // App em segundo plano: pausa (antes o audio seguia tocando) e salva onde parou.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
                if (userId != null && accessToken != null) {
                    val positionSeconds = (exoPlayer.currentPosition / 1000).toInt()
                    val durationSeconds = (exoPlayer.duration / 1000).toInt()
                    if (durationSeconds > 0) {
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                progressRepository.saveProgress(
                                    accessToken, userId, tmdbId, mediaType, currentSeason, currentEpisode,
                                    currentTitle, posterPath, positionSeconds, durationSeconds,
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (userId != null && accessToken != null) {
                val positionSeconds = (exoPlayer.currentPosition / 1000).toInt()
                val durationSeconds = (exoPlayer.duration / 1000).toInt()
                if (durationSeconds > 0) {
                    GlobalScope.launch(Dispatchers.IO) {
                        progressRepository.saveProgress(
                            accessToken, userId, tmdbId, mediaType, currentSeason, currentEpisode,
                            currentTitle, posterPath, positionSeconds, durationSeconds,
                        )
                    }
                }
            }
            exoPlayer.release()
        }
    }

    // ---- Estado da interface nova (controles próprios, sem a barra nativa do Exo) ----
    val accent = com.streamflixvip.app.ui.theme.AccentStore.current.accent
    var locked by remember { mutableStateOf(false) }
    var lockHintVisible by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableStateOf(0) }
    var isPlayingUi by remember { mutableStateOf(true) }
    var isBufferingUi by remember { mutableStateOf(true) }
    var bufferedUi by remember { mutableStateOf(0L) }
    var scrubTarget by remember { mutableStateOf(0L) }
    var inPip by remember { mutableStateOf(false) }
    var sleepTimer by remember { mutableStateOf(SleepTimer.OFF) }
    var sleepEndsAt by remember { mutableStateOf(0L) }
    var nextCountdown by remember { mutableStateOf(0) }
    var episodeList by remember { mutableStateOf(listOf<com.streamflixvip.app.network.TmdbEpisode>()) }
    var episodesLoading by remember { mutableStateOf(false) }
    var seekRippleLeft by remember { mutableStateOf(false) }
    var seekRippleTick by remember { mutableStateOf(0) }
    val playFocus = remember { FocusRequester() }
    val isSeries = mediaType == "tv" && currentEpisode > 0
    val NEXT_COUNTDOWN = 10

    fun poke() { interactionTick += 1 }
    fun showControls() { controlsVisible = true; poke() }
    fun togglePlay() { runCatching { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }; poke() }
    fun openPanel(p: SettingsPanel) { settingsPanel = p; poke() }

    // estado do player para os controles (barato: 4x por segundo)
    LaunchedEffect(exoPlayer) {
        while (true) {
            runCatching {
                isPlayingUi = exoPlayer.isPlaying || (exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_BUFFERING)
                isBufferingUi = exoPlayer.playbackState == Player.STATE_BUFFERING
                bufferedUi = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            }
            delay(250)
        }
    }
    // esconde os controles sozinho (4 s parado, tocando, sem menu aberto)
    LaunchedEffect(controlsVisible, interactionTick, settingsPanel, isScrubbing, isPlayingUi) {
        if (controlsVisible && settingsPanel == SettingsPanel.NONE && !isScrubbing && isPlayingUi) {
            delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(lockHintVisible) { if (lockHintVisible) { delay(2_500); lockHintVisible = false } }

    // Picture-in-picture: some com os controles enquanto está na janelinha
    DisposableEffect(activityOf(context)) {
        val act = activityOf(context) as? androidx.activity.ComponentActivity
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            if (inPip) { controlsVisible = false; settingsPanel = SettingsPanel.NONE }
        }
        runCatching { act?.addOnPictureInPictureModeChangedListener(listener) }
        onDispose { runCatching { act?.removeOnPictureInPictureModeChangedListener(listener) } }
    }
    fun enterPip() {
        val act = activityOf(context) ?: return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Toast.makeText(context, "Janela flutuante precisa do Android 8 ou mais novo", Toast.LENGTH_SHORT).show(); return
        }
        runCatching {
            val fmt = exoPlayer.videoFormat
            val ratio = if (fmt != null && fmt.width > 0 && fmt.height > 0) {
                android.util.Rational(fmt.width, fmt.height).let { r ->
                    // o Android só aceita entre 1:2,39 e 2,39:1
                    val v = r.toFloat()
                    if (v in 0.42f..2.39f) r else android.util.Rational(16, 9)
                }
            } else android.util.Rational(16, 9)
            act.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        }.onFailure { Toast.makeText(context, "Janela flutuante indisponível neste aparelho", Toast.LENGTH_SHORT).show() }
    }

    // timer para dormir
    LaunchedEffect(sleepTimer, sleepEndsAt) {
        if (sleepTimer.minutes > 0 && sleepEndsAt > 0) {
            val wait = sleepEndsAt - android.os.SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            runCatching { exoPlayer.pause() }
            sleepTimer = SleepTimer.OFF; sleepEndsAt = 0L
            Toast.makeText(context, "Timer: vídeo pausado. Boa noite!", Toast.LENGTH_LONG).show()
        }
    }

    // episódios da temporada (aba Episódios)
    LaunchedEffect(settingsPanel, currentSeason) {
        if (settingsPanel == SettingsPanel.EPISODES && isSeries && episodeList.isEmpty() && !episodesLoading) {
            episodesLoading = true
            episodeList = runCatching { com.streamflixvip.app.data.CatalogRepository().getSeasonEpisodes(tmdbId, currentSeason) }.getOrDefault(emptyList())
            episodesLoading = false
        }
    }
    LaunchedEffect(currentSeason) { episodeList = emptyList() }

    // próximo episódio: contagem regressiva e toca sozinho
    LaunchedEffect(showNextPrompt) {
        if (!showNextPrompt || !isSeries) return@LaunchedEffect
        if (sleepTimer == SleepTimer.END) {
            // timer "fim do episódio": não emenda o próximo
            showNextPrompt = false
            return@LaunchedEffect
        }
        nextCountdown = NEXT_COUNTDOWN
        while (nextCountdown > 0 && showNextPrompt) { delay(1_000); nextCountdown -= 1 }
        if (showNextPrompt && !isLoadingNext) playNextEpisode()
    }
    // fim do vídeo: série emenda o próximo; timer "fim do episódio" pausa
    DisposableEffect(exoPlayer) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    if (sleepTimer == SleepTimer.END) {
                        sleepTimer = SleepTimer.OFF
                        Toast.makeText(context, "Timer: fim do episódio. Boa noite!", Toast.LENGTH_LONG).show()
                    } else if (isSeries && !isLoadingNext) {
                        showNextPrompt = false
                        MainScope().launch { playNextEpisode() }
                    } else {
                        controlsVisible = true
                    }
                }
            }
        }
        exoPlayer.addListener(l)
        onDispose { runCatching { exoPlayer.removeListener(l) } }
    }

    // ---- Controle remoto / D-pad ----
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(controlsVisible, focusChipsPending) {
        if (controlsVisible && focusChipsPending && settingsPanel == SettingsPanel.NONE) {
            delay(120)
            runCatching { playFocus.requestFocus() }
            focusChipsPending = false
        }
    }
    LaunchedEffect(controlsVisible, settingsPanel, errorMessage, showNextPrompt) {
        if (settingsPanel == SettingsPanel.NONE && !controlsVisible && errorMessage == null && !showNextPrompt) {
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(settingsPanel) {
        if (settingsPanel != SettingsPanel.NONE && usingRemote) {
            delay(80)
            runCatching { panelFocus.requestFocus() }
        } else if (settingsPanel == SettingsPanel.NONE && usingRemote && controlsVisible) {
            delay(80)
            runCatching { playFocus.requestFocus() }
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && usingRemote) {
            delay(120)
            runCatching { errorFocus.requestFocus() }
        }
    }
    LaunchedEffect(showNextPrompt) {
        if (showNextPrompt && usingRemote) {
            delay(120)
            runCatching { nextFocus.requestFocus() }
        }
    }
    // Voltar: painel -> (tela travada: só avisa) -> controles (remoto) -> sai do player
    BackHandler(enabled = settingsPanel != SettingsPanel.NONE) {
        settingsPanel = when (settingsPanel) {
            SettingsPanel.QUALITY, SettingsPanel.SUB_STYLE -> SettingsPanel.MAIN
            else -> SettingsPanel.NONE
        }
    }
    BackHandler(enabled = settingsPanel == SettingsPanel.NONE && locked) { lockHintVisible = true }
    BackHandler(enabled = settingsPanel == SettingsPanel.NONE && !locked && controlsVisible && usingRemote) {
        controlsVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                val code = ev.nativeKeyEvent.keyCode
                val isRemoteKey = ev.key in REMOTE_KEYS
                if (!isRemoteKey) return@onPreviewKeyEvent false
                if (ev.type == KeyEventType.KeyDown) {
                    usingRemote = true
                    poke()
                }
                if (locked) {
                    if (ev.type == KeyEventType.KeyDown) lockHintVisible = true
                    // travado: OK destrava (no controle não há como tocar no cadeado)
                    if (ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionCenter || ev.key == Key.Enter)) { locked = false; showControls() }
                    return@onPreviewKeyEvent ev.key != Key.Back
                }
                when (code) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (ev.type == KeyEventType.KeyDown) togglePlay()
                        return@onPreviewKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        if (ev.type == KeyEventType.KeyDown) exoPlayer.play()
                        return@onPreviewKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (ev.type == KeyEventType.KeyDown) exoPlayer.pause()
                        return@onPreviewKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (ev.type == KeyEventType.KeyDown) seekBy(SEEK_STEP_MS)
                        return@onPreviewKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (ev.type == KeyEventType.KeyDown) seekBy(-SEEK_STEP_MS)
                        return@onPreviewKeyEvent true
                    }
                }
                if (settingsPanel != SettingsPanel.NONE || errorMessage != null || showNextPrompt) {
                    return@onPreviewKeyEvent false
                }
                if (controlsVisible && !rootFocused) return@onPreviewKeyEvent false
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                when (ev.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        togglePlay(); showControls(); focusChipsPending = true
                        true
                    }
                    Key.DirectionLeft -> { seekBy(-SEEK_STEP_MS); true }
                    Key.DirectionRight -> { seekBy(SEEK_STEP_MS); true }
                    Key.DirectionUp, Key.DirectionDown -> { showControls(); focusChipsPending = true; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    playerViewRef = this
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    // controles agora são nossos (Compose): linha do tempo e menus novos
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    subtitleView?.let { sub ->
                        sub.setBottomPaddingFraction(0.08f)
                        applySubtitleStyle(sub, subStyle)
                    }
                }
            },
            update = { v ->
                v.resizeMode = aspectMode.resizeMode
                v.subtitleView?.let { applySubtitleStyle(it, subStyle) }
            },
        )

        // Legenda online em overlay (sem remount / sem tela preta)
        val subLine = overlaySubText
        if (!subLine.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (overlaySubTop) 36.dp else 0.dp, bottom = if (overlaySubTop) 0.dp else if (controlsVisible) 130.dp else 72.dp)
                    .navigationBarsPadding(),
                contentAlignment = if (overlaySubTop) Alignment.TopCenter else Alignment.BottomCenter,
            ) {
                OverlaySubtitleText(subLine, subStyle)
            }
        }

        // ---- Gestos: 3 faixas. Toque mostra/esconde; toque duplo nas laterais = ±10 s;
        // arrastar na esquerda = brilho, na direita = volume. Travado: só mostra o cadeado.
        if (!inPip) Row(Modifier.fillMaxSize()) {
            listOf(0, 1, 2).forEach { zone ->
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(if (zone == 1) 0.4f else 0.3f)
                        .pointerInput(locked, zone) {
                            detectTapGestures(
                                onTap = {
                                    if (locked) { lockHintVisible = true; return@detectTapGestures }
                                    if (settingsPanel != SettingsPanel.NONE) { settingsPanel = SettingsPanel.NONE; return@detectTapGestures }
                                    usingRemote = false
                                    controlsVisible = !controlsVisible
                                    if (controlsVisible) poke()
                                },
                                onDoubleTap = {
                                    if (locked) return@detectTapGestures
                                    when (zone) {
                                        0 -> { seekBy(-SEEK_STEP_MS); seekRippleLeft = true; seekRippleTick += 1 }
                                        2 -> { seekBy(SEEK_STEP_MS); seekRippleLeft = false; seekRippleTick += 1 }
                                        else -> togglePlay()
                                    }
                                },
                            )
                        }
                        .then(
                            if (zone == 1) Modifier else Modifier.pointerInput(locked, zone) {
                                if (locked) return@pointerInput
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        gestureHideJob?.cancel()
                                        gestureKind = if (zone == 0) "brightness" else "volume"
                                        gestureValue = if (zone == 0) brightnessLevel else volumeLevel
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        val delta = -dragAmount / size.height.toFloat()
                                        if (zone == 0) {
                                            brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                                            gestureValue = brightnessLevel
                                            (context as? android.app.Activity)?.window?.let { w ->
                                                val lp = w.attributes
                                                lp.screenBrightness = brightnessLevel
                                                w.attributes = lp
                                            }
                                        } else {
                                            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                            gestureValue = volumeLevel
                                            runCatching {
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * maxVolume).toInt().coerceIn(0, maxVolume), 0)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        gestureHideJob?.cancel()
                                        gestureHideJob = MainScope().launch { delay(900); gestureKind = null }
                                    },
                                    onDragCancel = { gestureKind = null },
                                )
                            },
                        ),
                )
            }
        }

        // toque duplo: onda "−10 s / +10 s" no lado tocado
        if (seekRippleTick > 0) {
            var rippleVisible by remember(seekRippleTick) { mutableStateOf(true) }
            LaunchedEffect(seekRippleTick) { delay(650); rippleVisible = false }
            if (rippleVisible) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(0.3f)
                        .align(if (seekRippleLeft) Alignment.CenterStart else Alignment.CenterEnd)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                if (seekRippleLeft) listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)
                                else listOf(Color.Transparent, Color.White.copy(alpha = 0.18f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (seekRippleLeft) Icons.Filled.FastRewind else Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(34.dp))
                        Text(if (seekRippleLeft) "−10 s" else "+10 s", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        if (gestureKind == "brightness" || gestureKind == "volume") {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (gestureKind == "brightness") Icons.Filled.BrightnessHigh else Icons.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Box(Modifier.width(110.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(gestureValue.coerceIn(0f, 1f)).height(4.dp).background(accent))
                    }
                    Text("${(gestureValue * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                }
            }
        } else if (gestureKind == "seek" && !controlsVisible && seekRippleTick == 0) {
            Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp)) {
                Text(seekFeedback ?: "", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }

        // carregando (sem a rodinha nativa): spinner discreto no meio
        if (isBufferingUi && errorMessage == null && !isRecovering && !controlsVisible) {
            CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
        }

        if (errorMessage != null || isRecovering) {
            Surface(color = Color.Black.copy(alpha = 0.9f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRecovering) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.padding(bottom = 12.dp))
                        Text(
                            if (retryAttempt > 0) "Tentando novamente ($retryAttempt/2)..." else "Carregando...",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text("Não foi possível reproduzir", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Text(errorMessage ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp), textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChromeChip("Tentar de novo", Icons.Filled.Refresh, {
                                retryAttempt = 0; triedUrls.clear(); errorMessage = null; isRecovering = true; reloadWithUrl(activeUrl)
                            }, highlighted = true, accent = accent, focusRequester = errorFocus)
                            ChromeChip("Trocar servidor", Icons.Filled.Dns, {
                                MainScope().launch {
                                    if (alternateSources.isEmpty()) loadAlternateSources()
                                    if (alternateSources.isEmpty()) { errorMessage = "Sem outra fonte disponível"; return@launch }
                                    triedUrls += activeUrl
                                    val next = alternateSources.firstOrNull { it !in triedUrls }
                                        ?: alternateSources[alternateIndex % alternateSources.size]
                                    alternateIndex += 1; retryAttempt = 0; errorMessage = null; reloadWithUrl(next)
                                }
                            })
                            ChromeChip("Sair", null, onBack)
                        }
                    }
                }
            }
        }

        // ---- Tela travada: só o cadeado aparece (toque nele destrava) ----
        if (locked && lockHintVisible && !inPip) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChromeIconButton(Icons.Filled.Lock, "Destravar", { locked = false; showControls() }, size = 56.dp, filled = true, accent = accent)
                Text("Toque para destravar", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // ---- Controles ----
        AnimatedVisibility(
            visible = controlsVisible && !locked && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // sombras em cima e embaixo para os textos aparecerem em qualquer cena
                Box(
                    Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
                )
                Box(
                    Modifier.fillMaxWidth().height(190.dp).align(Alignment.BottomCenter)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
                )

                // topo: voltar, título, travar, janela flutuante
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChromeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack, size = 40.dp, iconSize = 22.dp)
                    Column(Modifier.weight(1f)) {
                        Text(currentTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        val sub = buildList {
                            if (isSeries) add("Temporada $currentSeason · Episódio $currentEpisode")
                            alternateLabels[activeUrl]?.let { add(it) }
                            if (sleepTimer != SleepTimer.OFF) add("Timer: ${sleepTimer.label}")
                        }.joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                    }
                    ChromeIconButton(Icons.Filled.LockOpen, "Travar tela", { locked = true; controlsVisible = false; settingsPanel = SettingsPanel.NONE; lockHintVisible = true }, size = 40.dp, iconSize = 20.dp)
                    ChromeIconButton(Icons.Filled.PictureInPictureAlt, "Janela flutuante", { enterPip() }, size = 40.dp, iconSize = 20.dp)
                    ChromeIconButton(Icons.Filled.Settings, "Ajustes", { openPanel(SettingsPanel.MAIN) }, size = 40.dp, iconSize = 20.dp)
                }

                // meio: voltar 10 s, play/pausa grande, avançar 10 s
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                ) {
                    ChromeIconButton(Icons.Filled.Replay10, "Voltar 10 segundos", { seekBy(-SEEK_STEP_MS); poke() }, size = 56.dp, iconSize = 32.dp)
                    Box(contentAlignment = Alignment.Center) {
                        ChromeIconButton(
                            if (isPlayingUi) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlayingUi) "Pausar" else "Tocar",
                            { togglePlay() },
                            size = 76.dp, iconSize = 44.dp, filled = true, accent = Color.White,
                            focusRequester = playFocus,
                        )
                        if (isBufferingUi) CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(82.dp))
                    }
                    ChromeIconButton(Icons.Filled.Forward10, "Avançar 10 segundos", { seekBy(SEEK_STEP_MS); poke() }, size = 56.dp, iconSize = 32.dp)
                }

                // baixo: linha do tempo + atalhos
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    val shownPos = if (isScrubbing) scrubTarget else scrubPosition
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chromeTime(shownPos), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        PlayerTimeline(
                            position = scrubPosition,
                            buffered = bufferedUi,
                            duration = scrubDuration,
                            accent = accent,
                            onScrub = { s, t -> isScrubbing = s; scrubTarget = t; poke() },
                            onSeek = { t -> runCatching { exoPlayer.seekTo(t) }; scrubPosition = t; poke() },
                            onInteract = { poke() },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(
                            if (scrubDuration > 0) "-" + chromeTime((scrubDuration - shownPos).coerceAtLeast(0)) else "--:--",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isSeries) ChromeChip("Episódios", Icons.Filled.VideoLibrary, { openPanel(SettingsPanel.EPISODES) })
                        ChromeChip(
                            if (selectedSubtitleLabel == "Desligada" && !onlineSubtitleApplied) "Legendas" else "Legendas ✓",
                            Icons.Filled.Subtitles, { openPanel(SettingsPanel.SUBTITLE) },
                        )
                        if (audioOptions.isNotEmpty()) ChromeChip("Áudio", Icons.Filled.Audiotrack, { openPanel(SettingsPanel.AUDIO) })
                        ChromeChip("Servidor", Icons.Filled.Dns, {
                            MainScope().launch {
                                if (alternateSources.isEmpty()) loadAlternateSources()
                                openPanel(SettingsPanel.SOURCES)
                            }
                        })
                        ChromeChip(if (playbackSpeed == 1f) "Velocidade" else "${playbackSpeed}x", Icons.Filled.Speed, { openPanel(SettingsPanel.SPEED) }, highlighted = playbackSpeed != 1f, accent = accent)
                        ChromeChip(aspectMode.label, Icons.Filled.AspectRatio, {
                            aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]; poke()
                        })
                        if (isSeries) ChromeChip(if (isLoadingNext) "Carregando…" else "Próximo", Icons.Filled.SkipNext, {
                            if (!isLoadingNext) MainScope().launch { playNextEpisode() }
                        }, highlighted = true, accent = accent)
                    }
                }
            }
        }

        if (showNextPrompt && isSeries && !inPip) {
            NextEpisodeCard(
                label = "T$currentSeason · Episódio ${currentEpisode + 1}",
                secondsLeft = nextCountdown,
                totalSeconds = NEXT_COUNTDOWN,
                accent = accent,
                onPlay = { MainScope().launch { playNextEpisode() } },
                onCancel = { showNextPrompt = false; nextDismissedFor = currentSeason * 10_000 + currentEpisode },
                playFocus = nextFocus,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = if (controlsVisible) 120.dp else 24.dp),
            )
        }

        // ---- Painel lateral com abas ----
        if (settingsPanel != SettingsPanel.NONE && !inPip) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).focusProperties { canFocus = false }.noRippleClick { settingsPanel = SettingsPanel.NONE })
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(
                        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.let { sw ->
                            (sw * 0.4f).coerceAtLeast(320f).coerceAtMost(sw * 0.92f).coerceAtMost(460f).dp
                        },
                    )
                    .background(Color(0xF20E0F13))
                    .border(1.dp, Color.White.copy(alpha = 0.08f))
                    .focusProperties { canFocus = false }
                    .noRippleClick {}
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                val tabs = buildList {
                    if (isSeries) add(SettingsPanel.EPISODES to "Episódios")
                    add(SettingsPanel.SUBTITLE to "Legendas")
                    if (audioOptions.isNotEmpty()) add(SettingsPanel.AUDIO to "Áudio")
                    add(SettingsPanel.SOURCES to "Servidor")
                    add(SettingsPanel.SPEED to "Velocidade")
                    add(SettingsPanel.MAIN to "Mais")
                }
                val selTab = when (settingsPanel) {
                    SettingsPanel.QUALITY, SettingsPanel.SUB_STYLE, SettingsPanel.SLEEP -> SettingsPanel.MAIN
                    else -> settingsPanel
                }
                PanelTabs(tabs, selTab, accent, { settingsPanel = it; poke() }, firstFocus = panelFocus)
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 6.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (settingsPanel) {
                        SettingsPanel.EPISODES -> {
                            if (episodesLoading) {
                                Text("Carregando episódios…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                            } else if (episodeList.isEmpty()) {
                                Text("Lista de episódios indisponível", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                            }
                            episodeList.forEach { ep ->
                                val cur = ep.episode_number == currentEpisode
                                SubmenuItem(
                                    "${ep.episode_number}. ${ep.displayName}" + (ep.displayRuntime?.let { " · $it" } ?: ""),
                                    cur,
                                ) {
                                    if (!cur) {
                                        settingsPanel = SettingsPanel.NONE
                                        MainScope().launch { playEpisode(currentSeason, ep.episode_number) }
                                    }
                                }
                            }
                        }
                        SettingsPanel.MAIN -> {
                            SettingsRow("Tela", aspectMode.label) {
                                aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Qualidade", selectedQualityLabel) { settingsPanel = SettingsPanel.QUALITY }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Timer para dormir", sleepTimer.label) { settingsPanel = SettingsPanel.SLEEP }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Sincronia da legenda", color = Color.White, fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).clickable { reapplySubtitleOffset(subtitleOffsetMs - 500L) }) {
                                        Text("−0.5s", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                    Text(
                                        (if (subtitleOffsetMs > 0) "+${subtitleOffsetMs / 1000.0}s" else "${subtitleOffsetMs / 1000.0}s").replace(".0s", "s"),
                                        color = accent,
                                        fontSize = 13.sp,
                                    )
                                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)).clickable { reapplySubtitleOffset(subtitleOffsetMs + 500L) }) {
                                        Text("+0.5s", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Aparência da legenda", "${subStyle.size.label} · ${subStyle.color.label}") {
                                settingsPanel = SettingsPanel.SUB_STYLE
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Abrir no VLC / player externo", "") {
                                settingsPanel = SettingsPanel.NONE
                                openInExternalPlayer(context, activeUrl)
                            }
                        }
                        SettingsPanel.SLEEP -> {
                            SubmenuHeader("Timer para dormir") { settingsPanel = SettingsPanel.MAIN }
                            SleepTimer.entries.forEach { t ->
                                SubmenuItem(t.label, sleepTimer == t) {
                                    sleepTimer = t
                                    sleepEndsAt = if (t.minutes > 0) android.os.SystemClock.elapsedRealtime() + t.minutes * 60_000L else 0L
                                    settingsPanel = SettingsPanel.MAIN
                                    if (t != SleepTimer.OFF) Toast.makeText(context, "Timer: ${t.label}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        SettingsPanel.SUBTITLE -> {
                            SubmenuItem("Desligada", selectedSubtitleLabel == "Desligada" && !onlineSubtitleApplied) {
                                selectSubtitle(null); settingsPanel = SettingsPanel.NONE
                            }
                            if (subtitleOptions.isNotEmpty()) {
                                Text("DO VÍDEO", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                                subtitleOptions.forEach { o ->
                                    SubmenuItem(o.label, selectedSubtitleLabel == "Stream: ${o.label}" && !onlineSubtitleApplied) {
                                        selectSubtitle(o); settingsPanel = SettingsPanel.NONE
                                    }
                                }
                            }
                            Text("ONLINE PT-BR", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                            if (onlineSubtitlesLoading) {
                                Text("Buscando…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                SubmenuItem("Buscar legendas online", false) { MainScope().launch { searchOnlineSubtitles() } }
                            }
                            onlineSubtitlesError?.let { err ->
                                Text(err, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            onlineSubtitleResults.forEach { item ->
                                val label = buildString {
                                    append(item.release ?: "Legenda")
                                    if (item.downloads > 0) append(" · ${item.downloads} dl")
                                    if (item.hd) append(" · HD")
                                }.let { if (it.length > 42) it.take(42) + "…" else it }
                                SubmenuItem(label, selectedSubtitleLabel.startsWith("Online:") && onlineSubtitleApplied) {
                                    MainScope().launch { applyOnlineSubtitle(item) }
                                }
                            }
                        }
                        SettingsPanel.AUDIO -> {
                            SubmenuItem("Padrão", selectedAudioLabel == "Padrao") { selectAudio(null); settingsPanel = SettingsPanel.NONE }
                            audioOptions.forEach { o ->
                                SubmenuItem(o.label, selectedAudioLabel == o.label) { selectAudio(o); settingsPanel = SettingsPanel.NONE }
                            }
                        }
                        SettingsPanel.QUALITY -> {
                            SubmenuHeader("Qualidade") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem("Automático", selectedQualityLabel == "Automatico") { selectQuality(null); settingsPanel = SettingsPanel.MAIN }
                            qualityOptions.forEach { o ->
                                SubmenuItem(o.label, selectedQualityLabel == o.label) { selectQuality(o); settingsPanel = SettingsPanel.MAIN }
                            }
                        }
                        SettingsPanel.SPEED -> {
                            PLAYBACK_SPEEDS.forEach { speed ->
                                SubmenuItem(if (speed == 1f) "Normal (1x)" else "${speed}x", playbackSpeed == speed) {
                                    playbackSpeed = speed; runCatching { exoPlayer.setPlaybackSpeed(speed) }; settingsPanel = SettingsPanel.NONE
                                }
                            }
                        }
                        SettingsPanel.SOURCES -> {
                            Text(
                                "Se travar, o app já troca de servidor sozinho.",
                                color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp),
                            )
                            SubmenuItem("Atual: " + (alternateLabels[activeUrl] ?: prettyFonteLabel(activeUrl, 0)), true) { settingsPanel = SettingsPanel.NONE }
                            if (alternateSources.isEmpty()) {
                                Text("Nenhuma outra fonte", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                alternateSources.forEachIndexed { i, src ->
                                    val label = alternateLabels[src] ?: prettyFonteLabel(src, i)
                                    SubmenuItem("${i + 1}. $label", false) {
                                        alternateIndex = i + 1
                                        reloadWithUrl(src)
                                        settingsPanel = SettingsPanel.NONE
                                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        SettingsPanel.SUB_STYLE -> {
                            SubmenuHeader("Aparência da legenda") { settingsPanel = SettingsPanel.MAIN }
                            SettingsRow("Tamanho", subStyle.size.label) {
                                subStyle = subStyle.copy(size = nextOf(subStyle.size, SubSize.entries))
                                SubtitleStylePrefs.save(context, subStyle)
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Cor", subStyle.color.label) {
                                subStyle = subStyle.copy(color = nextOf(subStyle.color, SubColor.entries))
                                SubtitleStylePrefs.save(context, subStyle)
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Fundo", subStyle.background.label) {
                                subStyle = subStyle.copy(background = nextOf(subStyle.background, SubBackground.entries))
                                SubtitleStylePrefs.save(context, subStyle)
                            }
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                OverlaySubtitleText("Exemplo de <i>legenda</i>", subStyle)
                            }
                        }
                        SettingsPanel.NONE -> {}
                    }
                }
            }
        }
    }
}

private fun activityOf(context: android.content.Context): Activity? {
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun prettyFonteLabel(url: String, index: Int): String {
    val host = try { java.net.URI(url).host.orEmpty().lowercase() } catch (_: Exception) { "" }
    return when {
        host.contains("streamflixvip") || host.contains("hakunaymatata") -> "Hyper"
        host.contains("goldvip") || host.contains("sventank") || host.contains("cineduo") || host.contains("kraps") -> "Goldvip"
        host.contains("guindex") -> "Guindex"
        host.contains("flixhub") -> "FlixHub"
                else -> "Fonte ${index + 1}"
    }
}

private val REMOTE_KEYS = setOf(
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar,
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.MediaFastForward, Key.MediaRewind,
)

/** Fundo/borda quando o item esta com foco do controle remoto (no toque nao aparece). */
@Composable
private fun Modifier.tvFocus(shape: androidx.compose.ui.graphics.Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier
                    .background(Color.White.copy(alpha = 0.22f), shape)
                    .border(2.dp, Color.White, shape)
            } else Modifier,
        )
}

private enum class SettingsPanel { NONE, MAIN, SUBTITLE, AUDIO, QUALITY, SPEED, SOURCES, SUB_STYLE, EPISODES, SLEEP }

private fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}


private fun parseSubtitleCues(content: String): List<SubtitleCue> = parseSubtitleText(content)




@Composable
private fun SubmenuHeader(title: String, focus: FocusRequester? = null, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .tvFocus(RoundedCornerShape(6.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBack() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\u2190", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
        Text(title, color = Color.White, fontSize = 16.sp)
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
}

@Composable
private fun SubmenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(6.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = 0.75f), fontSize = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Text("\u2713", color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun SettingsRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().tvFocus(RoundedCornerShape(6.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
        Text("  ›", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWebView(url: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        var isLoading by remember { mutableStateOf(true) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { isLoading = false }
                    }
                    loadUrl(url)
                }
            },
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun isPortugueseTrack(language: String?, label: String?): Boolean {
    val l = language?.lowercase().orEmpty()
    return l.startsWith("pt") || l.startsWith("por") || l == "pob" ||
        (label ?: "").contains("portugu", ignoreCase = true)
}

@Composable
private fun OverlaySubtitleText(text: String, style: SubtitleStyle) {
    val annotated = cueToAnnotated(text)
    val size = style.size.sp.sp
    val shadow = when (style.background) {
        SubBackground.SHADOW -> androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(3f, 3f), 6f)
        else -> null
    }
    val base = androidx.compose.ui.text.TextStyle(fontSize = size, textAlign = TextAlign.Center, shadow = shadow)
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            if (style.background == SubBackground.OUTLINE) {
                Text(
                    text = annotated,
                    color = Color.Black,
                    style = base.copy(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)),
                )
            }
            Text(text = annotated, color = style.composeColor, style = base)
        }
    }
    if (style.background == SubBackground.BOX) {
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { content() }
        }
    } else {
        Box(Modifier.padding(horizontal = 16.dp)) { content() }
    }
}
