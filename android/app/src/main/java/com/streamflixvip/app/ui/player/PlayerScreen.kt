package com.streamflixvip.app.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.media.AudioManager
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.data.ProgressRepository
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.StreamUrlResolver
import com.streamflixvip.app.network.SubtitleSearchItem
import com.streamflixvip.app.network.VipSource
import kotlin.math.abs
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

private fun hideNativeSettingsButtonSafe(view: PlayerView) {
    view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.visibility = android.view.View.GONE
}

private fun isLikelyHls(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("type=m3u8") ||
        lower.contains("/hls/") || (lower.contains("playlist") && lower.contains("m3u"))
}

private data class TrackOption(val label: String, val group: TrackGroup, val trackIndex: Int)

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
) {
    val view = LocalView.current
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
    var controlsVisible by remember { mutableStateOf(true) }
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
    var nextCountdown by remember { mutableStateOf(10) }

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
    val gestureScope = rememberCoroutineScope()

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
                                subtitles += TrackOption(f.label ?: f.language ?: "Faixa ${i + 1}", group.mediaTrackGroup, i)
                            }
                            C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
                                audios += TrackOption(f.label ?: f.language ?: "Faixa ${i + 1}", group.mediaTrackGroup, i)
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
                }
            })
        }
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
                val src = direct.first()
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

    suspend fun playPreviousEpisode() {
        if (mediaType != "tv" || currentSeason <= 0 || currentEpisode <= 0) return
        if (currentSeason == 1 && currentEpisode == 1) {
            errorMessage = "Ja e o primeiro episodio"
            return
        }
        isLoadingNext = true
        showNextPrompt = false
        try {
            val tries = mutableListOf<Pair<Int, Int>>()
            if (currentEpisode > 1) tries += currentSeason to (currentEpisode - 1)
            if (currentSeason > 1) {
                for (e in 30 downTo 1) tries += (currentSeason - 1) to e
            }
            var played = false
            for ((s, e) in tries) {
                val resp = try {
                    NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, "tv", s, e)
                } catch (_: Exception) { continue }
                val direct = resp.sources.filter { it.isDirectPlayable }
                if (direct.isEmpty()) continue
                val src = direct.first()
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
            if (!played) errorMessage = "Sem episodio anterior disponivel"
        } finally {
            isLoadingNext = false
        }
    }

    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = "Desligada"
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            selectedSubtitleLabel = "Stream: ${option.label}"
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
            var vtt = content.replace("\r\n", "\n").replace("\r", "\n")
            if (!vtt.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
                vtt = "WEBVTT\n\n" + vtt.replace(",", ".")
            }
            val file = File(context.cacheDir, "os_${tmdbId}_${currentSeason}_${currentEpisode}.vtt")
            file.writeText(vtt, Charsets.UTF_8)

            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val wasPlaying = exoPlayer.playWhenReady

            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("pt")
                .setLabel(item.release ?: "Online PT-BR")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val dsFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to activeUrl,
                        "Connection" to "keep-alive",
                        "Icy-MetaData" to "1",
                    ),
                )
            val extFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            val videoItem = MediaItem.fromUri(activeUrl)
            val videoSource = if (isLikelyHls(activeUrl)) {
                HlsMediaSource.Factory(dsFactory).createMediaSource(videoItem)
            } else {
                ProgressiveMediaSource.Factory(dsFactory, extFactory).createMediaSource(videoItem)
            }
            val subtitleSource = SingleSampleMediaSource.Factory(dsFactory)
                .createMediaSource(subConfig, C.TIME_UNSET)
            val merged = MergingMediaSource(videoSource, subtitleSource)

            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage("pt")
                .setSelectUndeterminedTextLanguage(true)
                .build()

            exoPlayer.setMediaSource(merged)
            exoPlayer.prepare()
            exoPlayer.seekTo(pos)
            exoPlayer.playWhenReady = wasPlaying

            exoPlayer.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    val groups = tracks.groups
                    for (gi in 0 until groups.size) {
                        val group = groups[gi]
                        if (group.type != C.TRACK_TYPE_TEXT) continue
                        for (ti in 0 until group.length) {
                            if (!group.isTrackSupported(ti)) continue
                            trackSelector.parameters = trackSelector.parameters.buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .build()
                            exoPlayer.removeListener(this)
                            return
                        }
                    }
                }
            })

            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "..." else it }
            selectedSubtitleLabel = "Online: $short"
            onlineSubtitleApplied = true
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
            trackSelector.parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        } else {
            selectedAudioLabel = option.label
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

    LaunchedEffect(showNextPrompt) {
        if (!showNextPrompt) return@LaunchedEffect
        nextCountdown = 10
        while (nextCountdown > 0 && showNextPrompt) {
            delay(1000L)
            if (!showNextPrompt) return@LaunchedEffect
            nextCountdown -= 1
        }
        if (showNextPrompt && nextCountdown <= 0 && !isLoadingNext) {
            playNextEpisode()
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
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    controllerShowTimeoutMs = 2800
                    controllerHideOnTouch = true
                    playerViewRef = this
                    post {
                        showController()
                        hideNativeSettingsButtonSafe(this)
                    }
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
                    post { hideNativeSettingsButton() }
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                            hideNativeSettingsButton()
                        },
                    )
                }
            },
            update = { v -> v.resizeMode = aspectMode.resizeMode },
        )

        // Zonas de gesto: 28% esquerda = brilho, 28% direita = volume
        // Toque = controles; deslize vertical = ajusta (fluido, some sozinho)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.28f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalY = 0f
                        var dragging = false
                        val startLevel = brightnessLevel
                        drag(down.id) { change ->
                            totalY += change.positionChange().y
                            if (!dragging && abs(totalY) > 12f) {
                                dragging = true
                                gestureHideJob?.cancel()
                                gestureKind = "brightness"
                            }
                            if (dragging) {
                                change.consume()
                                val delta = -totalY / size.height.toFloat() * 1.35f
                                val next = (startLevel + delta).coerceIn(0.01f, 1f)
                                brightnessLevel = next
                                gestureValue = next
                                val act = context as? android.app.Activity
                                act?.window?.let { w ->
                                    val lp = w.attributes
                                    lp.screenBrightness = next
                                    w.attributes = lp
                                }
                            }
                        }
                        if (!dragging) {
                            val pv = playerViewRef
                            if (pv != null) {
                                if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        } else {
                            gestureHideJob?.cancel()
                            gestureHideJob = gestureScope.launch {
                                delay(800)
                                gestureKind = null
                            }
                        }
                    }
                },
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.28f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalY = 0f
                        var dragging = false
                        val startLevel = volumeLevel
                        drag(down.id) { change ->
                            totalY += change.positionChange().y
                            if (!dragging && abs(totalY) > 12f) {
                                dragging = true
                                gestureHideJob?.cancel()
                                gestureKind = "volume"
                            }
                            if (dragging) {
                                change.consume()
                                val delta = -totalY / size.height.toFloat() * 1.35f
                                val next = (startLevel + delta).coerceIn(0f, 1f)
                                volumeLevel = next
                                gestureValue = next
                                val vol = (next * maxVolume).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                            }
                        }
                        if (!dragging) {
                            val pv = playerViewRef
                            if (pv != null) {
                                if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        } else {
                            gestureHideJob?.cancel()
                            gestureHideJob = gestureScope.launch {
                                delay(800)
                                gestureKind = null
                            }
                        }
                    }
                },
        )

        if (gestureKind != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (gestureKind == "brightness") Icons.Filled.BrightnessHigh else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(gestureValue * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 16.sp,
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
            Column {
                Text(currentTitle, color = Color.White, fontSize = 15.sp, maxLines = 1)
                if (epLabel != null) {
                    Text(epLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mediaType == "tv" && currentEpisode > 0) {
                    val canPrev = !(currentSeason == 1 && currentEpisode == 1)
                    if (canPrev) {
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playPreviousEpisode() } },
                        ) {
                            Text(
                                "Anterior",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { if (!isLoadingNext) MainScope().launch { playNextEpisode() } },
                    ) {
                        Text(
                            if (isLoadingNext) "..." else "Proximo",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.clickable { settingsPanel = SettingsPanel.MAIN },
                ) {
                    Text("Ajustes", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
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
                    Text(
                        "S${currentSeason} E${currentEpisode + 1} · em ${nextCountdown}s",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { MainScope().launch { playNextEpisode() } },
                    ) {
                        Text("Agora", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showNextPrompt = false },
                    ) {
                        Text("Cancelar", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
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
