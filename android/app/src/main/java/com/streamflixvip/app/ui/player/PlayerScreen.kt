package com.streamflixvip.app.ui.player

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
import androidx.compose.material3.Icon
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
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

private enum class AspectMode(val label: String, val resizeMode: Int) {
    FIT("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun isLikelyHls(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("type=m3u8") ||
        lower.contains("/hls/") || (lower.contains("playlist") && lower.contains("m3u"))
}


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

private data class TrackOption(val label: String, val group: TrackGroup, val trackIndex: Int)

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
    val trackSelector = remember { DefaultTrackSelector(context) }

    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var subtitleOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var audioOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var qualityOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var selectedSubtitleLabel by remember { mutableStateOf("Desligada") }
    var onlineSubtitleResults by remember { mutableStateOf(listOf<SubtitleSearchItem>()) }
    var onlineSubtitlesLoading by remember { mutableStateOf(false) }
    var onlineSubtitlesError by remember { mutableStateOf<String?>(null) }
    var onlineSubtitleApplied by remember { mutableStateOf(false) }
    var selectedAudioLabel by remember { mutableStateOf("Padrao") }
    var selectedQualityLabel by remember { mutableStateOf("Automatico") }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var settingsPanel by remember { mutableStateOf(SettingsPanel.NONE) }
    var controlsVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryAttempt by remember { mutableStateOf(0) }
    var isRecovering by remember { mutableStateOf(false) }
    var activeUrl by remember { mutableStateOf(url) }
    var alternateSources by remember { mutableStateOf(emptyList<String>()) }
    var alternateIndex by remember { mutableStateOf(0) }
    var currentSeason by remember { mutableStateOf(season) }
    var currentEpisode by remember { mutableStateOf(episode) }
    var currentTitle by remember { mutableStateOf(title) }
    var isLoadingNext by remember { mutableStateOf(false) }
    var showNextPrompt by remember { mutableStateOf(false) }

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
            if (subPref.isBlank() || subPref == "off") {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                selectedSubtitleLabel = "Desligada"
            } else {
                val match = subs.firstOrNull { trackMatchesPref(it.label, subPref) }
                if (match != null) {
                    builder.setOverrideForType(TrackSelectionOverride(match.group, match.trackIndex))
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    selectedSubtitleLabel = "Stream: ${match.label}"
                }
            }
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
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setDefaultRequestProperties(mapOf("Referer" to url, "Connection" to "keep-alive", "Icy-MetaData" to "1"))
        val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory).setDataSourceFactory(httpDataSourceFactory)
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource = if (isLikelyHls(url)) {
            HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory).createMediaSource(mediaItem)
        }
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).setMediaSourceFactory(mediaSourceFactory).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
            if (resumeSeconds > 0) seekTo(resumeSeconds * 1000L)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    errorMessage = error.errorCodeName
                }
                override fun onTracksChanged(tracks: Tracks) {
                    val subtitles = mutableListOf<TrackOption>()
                    val audios = mutableListOf<TrackOption>()
                    val qualities = mutableListOf<TrackOption>()
                    for (group in tracks.groups) {
                        when (group.type) {
                            C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
                                subtitles += TrackOption(humanTrackLabel(f.label, f.language, i), group.mediaTrackGroup, i)
                            }
                            C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
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

        fun reloadWithUrl(streamUrl: String, resetPosition: Boolean = false) {
        try {
            val keepPos = if (resetPosition) 0L else exoPlayer.currentPosition.coerceAtLeast(0L)
            errorMessage = null
            isRecovering = true
            activeUrl = streamUrl
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(mapOf("Referer" to streamUrl, "Connection" to "keep-alive", "Icy-MetaData" to "1"))
            val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            val mediaItem = MediaItem.fromUri(streamUrl)
            val mediaSource = if (isLikelyHls(streamUrl)) {
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory).createMediaSource(mediaItem)
            }
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

    LaunchedEffect(errorMessage) {
        if (errorMessage == null || isRecovering) return@LaunchedEffect
        if (retryAttempt < 2) {
            retryAttempt += 1
            isRecovering = true
            delay(1200L * retryAttempt)
            val candidates = VipSource(source_url = url, source_label = null, priority = null)
                .candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
            reloadWithUrl(StreamUrlResolver.resolveFastest(candidates).ifBlank { activeUrl })
            delay(800)
            if (exoPlayer.playerError == null) errorMessage = null
            isRecovering = false
        }
    }

    suspend fun loadAlternateSources() {
        try {
            val resp = if (mediaType == "tv" && currentSeason > 0) {
                NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, "tv", currentSeason, currentEpisode)
            } else {
                NetworkModule.mediaSourcesApi.getMovieSources(tmdbId, mediaType)
            }
            alternateSources = resp.sources.filter { it.isDirectPlayable }
                .flatMap { it.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL) }
                .distinct().filter { it.isNotBlank() && it != activeUrl }
            alternateIndex = 0
        } catch (_: Exception) {
            alternateSources = emptyList()
        }
    }

    suspend fun playNextEpisode() {
        if (mediaType != "tv" || currentSeason <= 0 || currentEpisode <= 0) return
        isLoadingNext = true
        showNextPrompt = false
        try {
            val tries = listOf(currentSeason to (currentEpisode + 1), (currentSeason + 1) to 1)
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
                reloadWithUrl(playUrl, resetPosition = true)
                played = true
                break
            }
            if (!played) errorMessage = "Sem proximo episodio disponivel"
        } finally {
            isLoadingNext = false
        }
    }

    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
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
                onlineSubtitleResults = resp.results.filter { it.file_id != null }.take(12)
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
        val fileId = item.file_id ?: return
        onlineSubtitlesLoading = true
        onlineSubtitlesError = null
        try {
            val seasonArg = if (mediaType == "tv" && currentSeason > 0) currentSeason else null
            val episodeArg = if (mediaType == "tv" && currentEpisode > 0) currentEpisode else null
            val resp = NetworkModule.subtitlesApi.download(
                fileId = fileId,
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
            val file = File(context.cacheDir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")
            file.writeText(content)
            val pos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.playWhenReady
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("pt")
                .setLabel(item.release ?: "Online PT-BR")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(activeUrl)
                .setSubtitleConfigurations(listOf(subConfig))
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(pos)
            exoPlayer.playWhenReady = wasPlaying
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "…" else it }
            selectedSubtitleLabel = "Online: $short"
            onlineSubtitleApplied = true
            persistSubtitleKey("online")
            settingsPanel = SettingsPanel.MAIN
        } catch (e: Exception) {
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

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            if (userId != null && accessToken != null) persistCurrentPosition()
            if (mediaType == "tv" && currentEpisode > 0) {
                val dur = exoPlayer.duration
                val pos = exoPlayer.currentPosition
                if (dur > 0 && (dur - pos) in 1..30_000L && !showNextPrompt && !isLoadingNext) {
                    showNextPrompt = true
                }
            }
        }
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    playerViewRef = this
                    useController = true
                    controllerAutoShow = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    controllerShowTimeoutMs = 5000
                    controllerHideOnTouch = true
                    post { hideController() }
                    subtitleView?.let { sub ->
                        sub.setApplyEmbeddedStyles(false)
                        sub.setApplyEmbeddedFontSizes(false)
                        sub.setBottomPaddingFraction(0.08f)
                        sub.setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null,
                            ),
                        )
                        sub.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                    }
                    fun hideNativeSettingsButton() {
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.visibility = android.view.View.GONE
                    }
                    post {
                        hideNativeSettingsButton()
                        hideController()
                    }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            // Sincroniza quando o Exo esconde por timeout; nao forca false no show
                            if (visibility == android.view.View.VISIBLE) {
                                controlsVisible = true
                            } else if (visibility == android.view.View.GONE) {
                                controlsVisible = false
                            }
                            hideNativeSettingsButton()
                        },
                    )
                }
            },
            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )



        // SHOW_ONLY_WHEN_HIDDEN: toque so ABRE o menu.
        // Com menu aberto, PlayerView nativo cuida de pause/seek/timeline.
        // Fechar: timeout do Exo ou toque no video (controllerHideOnTouch).
        if (!controlsVisible && settingsPanel == SettingsPanel.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                playerViewRef?.showController()
                                controlsVisible = true
                            },
                        )
                    },
            )
        }

        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.28f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            gestureHideJob?.cancel()
                            gestureKind = "brightness"
                            gestureValue = brightnessLevel
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val delta = -dragAmount / size.height.toFloat()
                            brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                            gestureValue = brightnessLevel
                            val act = context as? android.app.Activity
                            act?.window?.let { w ->
                                val lp = w.attributes
                                lp.screenBrightness = brightnessLevel
                                w.attributes = lp
                            }
                        },
                        onDragEnd = {
                            gestureHideJob?.cancel()
                            gestureHideJob = MainScope().launch {
                                delay(900)
                                gestureKind = null
                            }
                        },
                        onDragCancel = { gestureKind = null },
                    )
                },
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.28f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            gestureHideJob?.cancel()
                            gestureKind = "volume"
                            gestureValue = volumeLevel
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val delta = -dragAmount / size.height.toFloat()
                            volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                            gestureValue = volumeLevel
                            val vol = (volumeLevel * maxVolume).toInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                        },
                        onDragEnd = {
                            gestureHideJob?.cancel()
                            gestureHideJob = MainScope().launch {
                                delay(900)
                                gestureKind = null
                            }
                        },
                        onDragCancel = { gestureKind = null },
                    )
                },
        )

        if (gestureKind != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (gestureKind == "brightness") Icons.Filled.BrightnessHigh else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        if (gestureKind == "brightness") {
                            "Brilho ${(gestureValue * 100).toInt()}%"
                        } else {
                            "Volume ${(gestureValue * 100).toInt()}%"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        if (errorMessage != null || isRecovering) {
            Surface(color = Color.Black.copy(alpha = 0.88f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRecovering && retryAttempt in 1..2) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                        Text("Tentando novamente ($retryAttempt/2)...", color = Color.White, textAlign = TextAlign.Center)
                    } else {
                        Text("Nao foi possivel reproduzir", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Text(errorMessage ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp), textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), modifier = Modifier.clickable {
                                retryAttempt = 0; errorMessage = null; isRecovering = true; reloadWithUrl(activeUrl)
                            }) {
                                Text("Tentar de novo", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                            Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable {
                                MainScope().launch {
                                    if (alternateSources.isEmpty()) loadAlternateSources()
                                    if (alternateSources.isEmpty()) { errorMessage = "Sem outra fonte disponivel"; return@launch }
                                    val next = alternateSources[alternateIndex % alternateSources.size]
                                    alternateIndex += 1; retryAttempt = 0; errorMessage = null; reloadWithUrl(next)
                                }
                            }) {
                                Text("Trocar servidor", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            val epLabel = if (mediaType == "tv" && currentSeason > 0 && currentEpisode > 0) {
                "S${currentSeason} E${currentEpisode}"
            } else null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable { onBack() },
                ) {
                    Text("Voltar", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                Column {
                    Text(currentTitle, color = Color.White, fontSize = 15.sp, maxLines = 1)
                    if (epLabel != null) {
                        Text(epLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                    }) {
                        Text(aspectMode.label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.SPEED
                    }) {
                        Text("${playbackSpeed}x", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.SUBTITLE
                    }) {
                        Text("Legendas", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    if (audioOptions.isNotEmpty()) {
                        Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                            settingsPanel = SettingsPanel.AUDIO
                        }) {
                            Text("Audio", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        MainScope().launch {
                            if (alternateSources.isEmpty()) loadAlternateSources()
                            if (alternateSources.isEmpty()) {
                                Toast.makeText(context, "Sem outra fonte", Toast.LENGTH_SHORT).show()
                            } else {
                                val next = alternateSources[alternateIndex % alternateSources.size]
                                alternateIndex += 1
                                reloadWithUrl(next)
                                Toast.makeText(context, "Trocando fonte…", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Fontes", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    if (mediaType == "tv" && currentEpisode > 0) {
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                            modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playNextEpisode() } },
                        ) {
                            Text(
                                if (isLoadingNext) "…" else "Proximo",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.MAIN
                    }) {
                        Text("Mais", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

        if (showNextPrompt && mediaType == "tv") {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 56.dp, start = 16.dp, end = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("S${currentSeason} E${currentEpisode + 1} · Assistir agora?", color = Color.White, fontSize = 13.sp)
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { MainScope().launch { playNextEpisode() } },
                    ) {
                        Text("Sim", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showNextPrompt = false },
                    ) {
                        Text("Nao", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
        }

        if (settingsPanel != SettingsPanel.NONE) {
            Box(modifier = Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { settingsPanel = SettingsPanel.NONE })
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 8.dp, bottom = 48.dp)
                    .width(280.dp)
                    .heightIn(max = 260.dp)
                    .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    when (settingsPanel) {
                        SettingsPanel.MAIN -> {
                            SettingsRow("Proporcao", aspectMode.label) {
                                aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Legenda", selectedSubtitleLabel) { settingsPanel = SettingsPanel.SUBTITLE }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            if (audioOptions.isNotEmpty()) {
                                SettingsRow("Audio", selectedAudioLabel) { settingsPanel = SettingsPanel.AUDIO }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            }
                            SettingsRow("Qualidade", selectedQualityLabel) { settingsPanel = SettingsPanel.QUALITY }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Velocidade", "${playbackSpeed}x") { settingsPanel = SettingsPanel.SPEED }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow("Abrir no VLC / player externo", "") {
                                settingsPanel = SettingsPanel.NONE
                                openInExternalPlayer(context, activeUrl)
                            }
                        }
                        SettingsPanel.SUBTITLE -> {
                            SubmenuHeader("Legenda") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem("Desligada", selectedSubtitleLabel == "Desligada" && !onlineSubtitleApplied) {
                                selectSubtitle(null); settingsPanel = SettingsPanel.MAIN
                            }
                            if (subtitleOptions.isNotEmpty()) {
                                Text(
                                    "Do stream",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                                subtitleOptions.forEach { o ->
                                    SubmenuItem(
                                        "Stream: ${o.label}",
                                        selectedSubtitleLabel == "Stream: ${o.label}" && !onlineSubtitleApplied,
                                    ) { selectSubtitle(o); settingsPanel = SettingsPanel.MAIN }
                                }
                            }
                            Text(
                                "Online PT-BR",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            if (onlineSubtitlesLoading) {
                                Text(
                                    "Buscando…",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            } else {
                                SubmenuItem("Buscar legendas online", false) {
                                    MainScope().launch { searchOnlineSubtitles() }
                                }
                            }
                            onlineSubtitlesError?.let { err ->
                                Text(
                                    err,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
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
                            SubmenuHeader("Audio") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem("Padrao", selectedAudioLabel == "Padrao") { selectAudio(null); settingsPanel = SettingsPanel.MAIN }
                            audioOptions.forEach { o ->
                                SubmenuItem(o.label, selectedAudioLabel == o.label) { selectAudio(o); settingsPanel = SettingsPanel.MAIN }
                            }
                        }
                        SettingsPanel.QUALITY -> {
                            SubmenuHeader("Qualidade") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem("Automatico", selectedQualityLabel == "Automatico") { selectQuality(null); settingsPanel = SettingsPanel.MAIN }
                            qualityOptions.forEach { o ->
                                SubmenuItem(o.label, selectedQualityLabel == o.label) { selectQuality(o); settingsPanel = SettingsPanel.MAIN }
                            }
                        }
                        SettingsPanel.SPEED -> {
                            SubmenuHeader("Velocidade") { settingsPanel = SettingsPanel.MAIN }
                            PLAYBACK_SPEEDS.forEach { speed ->
                                SubmenuItem("${speed}x", playbackSpeed == speed) {
                                    playbackSpeed = speed; exoPlayer.setPlaybackSpeed(speed); settingsPanel = SettingsPanel.MAIN
                                }
                            }
                        }
                        SettingsPanel.NONE -> {}
                    }
                }
            }
        }
    }
}

private enum class SettingsPanel { NONE, MAIN, SUBTITLE, AUDIO, QUALITY, SPEED }

@Composable
private fun SubmenuHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBack() }.padding(vertical = 10.dp),
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
        modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
        if (selected) Text("\u2713", color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp))
        Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
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
