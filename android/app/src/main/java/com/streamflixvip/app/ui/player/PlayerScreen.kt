package com.streamflixvip.app.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.streamflixvip.app.network.VipSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
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
    return lower.contains(".m3u8") ||
        lower.contains("format=m3u8") ||
        lower.contains("type=m3u8") ||
        lower.contains("/hls/") ||
        (lower.contains("playlist") && lower.contains("m3u"))
}

private data class TrackOption(
    val label: String,
    val group: TrackGroup,
    val trackIndex: Int,
)

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
            val candidates = VipSource(
                source_url = sourceUrl,
                source_label = null,
                priority = null,
            ).candidatePlaybackUrls(
                koyebBaseUrl = BuildConfig.API_BASE_URL,
                zeaburBaseUrl = NetworkModule.ZEABUR_BASE_URL,
            )
            resolvedUrl = StreamUrlResolver.resolveFastest(candidates)
        }
    }

    val currentResolvedUrl = resolvedUrl
    if (currentResolvedUrl == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
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
    var selectedAudioLabel by remember { mutableStateOf("Padrao") }
    var selectedQualityLabel by remember { mutableStateOf("Automatico") }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var settingsPanel by remember { mutableStateOf(SettingsPanel.NONE) }
    var controlsVisible by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryAttempt by remember { mutableStateOf(0) }
    var isRecovering by remember { mutableStateOf(false) }
    var activeUrl by remember { mutableStateOf(url) }
    var alternateSources by remember { mutableStateOf<List<String>>(emptyList()) }
    var alternateIndex by remember { mutableStateOf(0) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to url,
                    "Connection" to "keep-alive",
                    "Icy-MetaData" to "1",
                ),
            )

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(httpDataSourceFactory)

        val mediaItem = MediaItem.fromUri(url)
        val mediaSource = if (isLikelyHls(url)) {
            HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem)
        }

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
                if (resumeSeconds > 0) seekTo(resumeSeconds * 1000L)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        android.util.Log.e("PlayerScreen", "Erro: ${error.errorCodeName} (${error.errorCode})", error)
                        errorMessage = error.errorCodeName
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val subtitles = mutableListOf<TrackOption>()
                        val audios = mutableListOf<TrackOption>()
                        val qualities = mutableListOf<TrackOption>()
                        for (group in tracks.groups) {
                            when (group.type) {
                                C.TRACK_TYPE_TEXT -> {
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        val label = format.label ?: format.language ?: "Faixa ${i + 1}"
                                        subtitles += TrackOption(label, group.mediaTrackGroup, i)
                                    }
                                }
                                C.TRACK_TYPE_AUDIO -> {
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        val label = format.label ?: format.language ?: "Faixa ${i + 1}"
                                        audios += TrackOption(label, group.mediaTrackGroup, i)
                                    }
                                }
                                C.TRACK_TYPE_VIDEO -> {
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        if (format.height > 0) {
                                            qualities += TrackOption("${format.height}p", group.mediaTrackGroup, i)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                        subtitleOptions = subtitles
                        audioOptions = if (audios.size > 1) audios else emptyList()
                        qualityOptions = qualities.distinctBy { it.label }.sortedByDescending {
                            it.label.removeSuffix("p").toIntOrNull() ?: 0
                        }
                    }
                })
            }
    }

    fun reloadWithUrl(streamUrl: String) {
        try {
            val keepPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            errorMessage = null
            isRecovering = true
            activeUrl = streamUrl
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to streamUrl,
                        "Connection" to "keep-alive",
                        "Icy-MetaData" to "1",
                    ),
                )
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
            val mediaItem = MediaItem.fromUri(streamUrl)
            val mediaSource = if (isLikelyHls(streamUrl)) {
                HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            if (keepPos > 1000L) exoPlayer.seekTo(keepPos)
            exoPlayer.playWhenReady = true
            isRecovering = false
        } catch (e: Exception) {
            android.util.Log.e("PlayerScreen", "reload falhou", e)
            isRecovering = false
            errorMessage = e.message ?: "Falha ao recarregar"
        }
    }

    LaunchedEffect(errorMessage) {
        val code = errorMessage ?: return@LaunchedEffect
        if (isRecovering) return@LaunchedEffect
        if (retryAttempt < 2) {
            retryAttempt += 1
            isRecovering = true
            delay(1200L * retryAttempt)
            val candidates = VipSource(source_url = url, source_label = null, priority = null)
                .candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
            val next = StreamUrlResolver.resolveFastest(candidates)
            reloadWithUrl(next.ifBlank { activeUrl })
            delay(800)
            if (exoPlayer.playerError == null) {
                errorMessage = null
            }
            isRecovering = false
        }
    }

    suspend fun loadAlternateSources() {
        try {
            val resp = if (mediaType == "tv" && season > 0) {
                NetworkModule.mediaSourcesApi.getEpisodeSources(tmdbId, "tv", season, episode)
            } else {
                NetworkModule.mediaSourcesApi.getMovieSources(tmdbId, mediaType)
            }
            alternateSources = resp.sources
                .filter { it.isDirectPlayable }
                .flatMap {
                    it.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
                }
                .distinct()
                .filter { it.isNotBlank() && it != activeUrl }
            alternateIndex = 0
        } catch (e: Exception) {
            android.util.Log.e("PlayerScreen", "alternates", e)
            alternateSources = emptyList()
        }
    }

    fun selectSubtitle(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedSubtitleLabel = "Desligada"
            trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            selectedSubtitleLabel = option.label
            trackSelector.parameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    fun selectAudio(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedAudioLabel = "Padrao"
            trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
        } else {
            selectedAudioLabel = option.label
            trackSelector.parameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
                .build()
        }
    }

    fun selectQuality(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedQualityLabel = "Automatico"
            trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        } else {
            selectedQualityLabel = option.label
            trackSelector.parameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        }
    }

    suspend fun persistCurrentPosition() {
        if (userId == null || accessToken == null) return
        val positionSeconds = (exoPlayer.currentPosition / 1000).toInt()
        val durationSeconds = (exoPlayer.duration / 1000).toInt()
        if (durationSeconds <= 0) return
        progressRepository.saveProgress(
            accessToken = accessToken,
            userId = userId,
            tmdbId = tmdbId,
            mediaType = mediaType,
            season = season,
            episode = episode,
            title = title,
            posterPath = posterPath,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
        )
    }

    LaunchedEffect(exoPlayer) {
        if (userId == null || accessToken == null) return@LaunchedEffect
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            persistCurrentPosition()
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
                            accessToken = accessToken,
                            userId = userId,
                            tmdbId = tmdbId,
                            mediaType = mediaType,
                            season = season,
                            episode = episode,
                            title = title,
                            posterPath = posterPath,
                            positionSeconds = positionSeconds,
                            durationSeconds = durationSeconds,
                        )
                    }
                }
            }
            exoPlayer.release()
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
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
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)
                            ?.visibility = android.view.View.GONE
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

        if (errorMessage != null || isRecovering) {
            Surface(
                color = Color.Black.copy(alpha = 0.88f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isRecovering && retryAttempt in 1..2) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                        Text("Tentando novamente ($retryAttempt/2)...", color = Color.White, textAlign = TextAlign.Center)
                    } else {
                        Text("Nao foi possivel reproduzir", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Text(
                            errorMessage ?: "",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                color = Color(0xFFE50914),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    retryAttempt = 0
                                    errorMessage = null
                                    isRecovering = true
                                    reloadWithUrl(activeUrl)
                                },
                            ) {
                                Text("Tentar de novo", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    MainScope().launch {
                                        if (alternateSources.isEmpty()) loadAlternateSources()
                                        if (alternateSources.isEmpty()) {
                                            errorMessage = "Sem outra fonte disponivel"
                                            return@launch
                                        }
                                        val next = alternateSources[alternateIndex % alternateSources.size]
                                        alternateIndex += 1
                                        retryAttempt = 0
                                        errorMessage = null
                                        reloadWithUrl(next)
                                    }
                                },
                            ) {
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
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = { settingsPanel = SettingsPanel.MAIN }) {
                Text("\u2699", fontSize = 22.sp, color = Color.White)
            }
        }

        if (settingsPanel != SettingsPanel.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { settingsPanel = SettingsPanel.NONE },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 8.dp, bottom = 48.dp)
                    .width(280.dp)
                    .heightIn(max = 260.dp)
                    .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    when (settingsPanel) {
                        SettingsPanel.MAIN -> {
                            SettingsRow(label = "Proporcao", value = aspectMode.label) {
                                aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow(label = "Legenda", value = selectedSubtitleLabel) {
                                settingsPanel = SettingsPanel.SUBTITLE
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            if (audioOptions.isNotEmpty()) {
                                SettingsRow(label = "Audio", value = selectedAudioLabel) {
                                    settingsPanel = SettingsPanel.AUDIO
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            }
                            SettingsRow(label = "Qualidade", value = selectedQualityLabel) {
                                settingsPanel = SettingsPanel.QUALITY
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow(label = "Velocidade", value = "${playbackSpeed}x") {
                                settingsPanel = SettingsPanel.SPEED
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            SettingsRow(label = "Abrir no VLC / player externo", value = "") {
                                settingsPanel = SettingsPanel.NONE
                                openInExternalPlayer(context, activeUrl)
                            }
                        }
                        SettingsPanel.SUBTITLE -> {
                            SubmenuHeader(title = "Legenda") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem(label = "Desligada", selected = selectedSubtitleLabel == "Desligada") {
                                selectSubtitle(null)
                                settingsPanel = SettingsPanel.MAIN
                            }
                            subtitleOptions.forEach { option ->
                                SubmenuItem(label = option.label, selected = selectedSubtitleLabel == option.label) {
                                    selectSubtitle(option)
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                            if (subtitleOptions.isEmpty()) {
                                Text("Sem legendas nesta fonte", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                            }
                        }
                        SettingsPanel.AUDIO -> {
                            SubmenuHeader(title = "Audio") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem(label = "Padrao", selected = selectedAudioLabel == "Padrao") {
                                selectAudio(null)
                                settingsPanel = SettingsPanel.MAIN
                            }
                            audioOptions.forEach { option ->
                                SubmenuItem(label = option.label, selected = selectedAudioLabel == option.label) {
                                    selectAudio(option)
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                        }
                        SettingsPanel.QUALITY -> {
                            SubmenuHeader(title = "Qualidade") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem(label = "Automatico", selected = selectedQualityLabel == "Automatico") {
                                selectQuality(null)
                                settingsPanel = SettingsPanel.MAIN
                            }
                            qualityOptions.forEach { option ->
                                SubmenuItem(label = option.label, selected = selectedQualityLabel == option.label) {
                                    selectQuality(option)
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                            if (qualityOptions.isEmpty()) {
                                Text("So uma qualidade disponivel", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                            }
                        }
                        SettingsPanel.SPEED -> {
                            SubmenuHeader(title = "Velocidade") { settingsPanel = SettingsPanel.MAIN }
                            PLAYBACK_SPEEDS.forEach { speed ->
                                SubmenuItem(label = "${speed}x", selected = playbackSpeed == speed) {
                                    playbackSpeed = speed
                                    exoPlayer.setPlaybackSpeed(speed)
                                    settingsPanel = SettingsPanel.MAIN
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onBack() }
            .padding(vertical = 10.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp))
        Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWebView(url: String) {
    Box(Modifier.fillMaxSize()) {
        var isLoading by remember { mutableStateOf(true) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    loadUrl(url)
                }
            },
        )
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
