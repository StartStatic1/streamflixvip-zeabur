package com.streamflixvip.tv.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.view.View
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTrackNameProvider
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.BuildConfig
import java.net.URLEncoder

/**
 * Player de TV com ExoPlayer nativo — suporta:
 * - Legendas (seleção, estilo, on/off)
 * - Faixas de áudio
 * - Qualidade (seleção manual de streams HLS/DASH)
 * - Aspect ratio (16:9, fit, fill, zoom)
 * - Velocidade de reprodução
 * - Painel de configurações estilo bottom-sheet compacto
 * - WebView fallback para embeds de terceiros
 * - VLC/player externo
 *
 * Design inspirado nos prints: controles minimalistas, painel de settings
 * no canto inferior direito, escuro com detalhes dourados.
 */
@OptIn(UnstableApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerTvScreen(
    source: VipSource,
    season: Int = 0,
    episode: Int = 0,
    title: String = "Sem título",
    episodeTitle: String? = null,
    posterPath: String? = null,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val apiBaseUrl = BuildConfig.API_BASE_URL
    val zeaburBaseUrl = "https://www.streamflixvip.online/"

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(
                    DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().apply {
                        setDefaultRequestProperties(
                            mapOf(
                                "User-Agent" to "VLC/3.0.20 LibVLC/3.0.20",
                                "Referer" to apiBaseUrl,
                            )
                        )
                    })
                )
            )
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // States do player
    var playbackUrl by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var settingsPanel by remember { mutableStateOf(SettingsPanel.NONE) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    // Dados de faixas
    val subtitleOptions = remember { mutableStateListOf<TrackOption>() }
    val audioOptions = remember { mutableStateListOf<TrackOption>() }
    val qualityOptions = remember { mutableStateListOf<TrackOption>() }
    var selectedSubtitleLabel by remember { mutableStateOf("Desligada") }
    var selectedAudioLabel by remember { mutableStateOf("Padrão") }
    var selectedQualityLabel by remember { mutableStateOf("Automático") }

    // Resolve URL de playback
    DisposableEffect(source, apiBaseUrl) {
        val url = source.resolvedPlaybackUrl(apiBaseUrl)
        playbackUrl = url
        errorMessage = null

        try {
            val mediaItem = MediaItem.fromUri(url)
            val mediaSource = when {
                url.endsWith(".m3u8") || url.contains(".m3u8?") -> {
                    HlsMediaSource.Factory(
                        DefaultHttpDataSource.Factory().apply {
                            setDefaultRequestProperties(
                                mapOf(
                                    "User-Agent" to "VLC/3.0.20 LibVLC/3.0.20",
                                    "Referer" to apiBaseUrl,
                                )
                            )
                        }
                    ).createMediaSource(mediaItem)
                }
                else -> {
                    ProgressiveMediaSource.Factory(
                        DefaultHttpDataSource.Factory().apply {
                            setDefaultRequestProperties(
                                mapOf(
                                    "User-Agent" to "VLC/3.0.20 LibVLC/3.0.20",
                                    "Referer" to apiBaseUrl,
                                )
                            )
                        }
                    ).createMediaSource(mediaItem)
                }
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            errorMessage = "Erro: ${e.message}"
        }

        onDispose { }
    }

    // Observer para tracks (legendas, áudio, qualidade)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val newSubs = mutableListOf<TrackOption>()
                val newAudio = mutableListOf<TrackOption>()
                val newQuality = mutableListOf<TrackOption>()
                val trackNameProvider = DefaultTrackNameProvider(context.resources)

                for (group in tracks.groups) {
                    val type = group.type
                    val label = trackNameProvider.getTrackName(group.mediaTrackGroup, 0)
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val trackLabel = when (type) {
                            C.TRACK_TYPE_TEXT -> {
                                format.language?.let { "${format.label.orEmpty()} (${it.uppercase()})" }
                                    ?: (format.label ?: "Legenda ${i + 1}")
                            }
                            C.TRACK_TYPE_AUDIO -> {
                                format.language?.let { "${format.label.orEmpty()} (${it.uppercase()})" }
                                    ?: (format.label ?: "Áudio ${i + 1}")
                            }
                            C.TRACK_TYPE_VIDEO -> {
                                val height = format.height
                                val bitrate = format.bitrate
                                when {
                                    height >= 2160 -> "4K"
                                    height >= 1080 -> "1080p"
                                    height >= 720 -> "720p"
                                    height >= 480 -> "480p"
                                    bitrate > 0 -> "${bitrate / 1_000_000} Mbps"
                                    else -> "Opção ${i + 1}"
                                }
                            }
                            else -> "Opção ${i + 1}"
                        }
                        val option = TrackOption(group, i, trackLabel, type)
                        when (type) {
                            C.TRACK_TYPE_TEXT -> newSubs.add(option)
                            C.TRACK_TYPE_AUDIO -> newAudio.add(option)
                            C.TRACK_TYPE_VIDEO -> newQuality.add(option)
                        }
                    }
                }

                subtitleOptions.clear()
                subtitleOptions.addAll(newSubs)
                audioOptions.clear()
                audioOptions.addAll(newAudio)
                qualityOptions.clear()
                qualityOptions.addAll(newQuality)
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Auto-hide controls após 4s de play
                if (state == Player.STATE_READY) {
                    exoPlayer.addPauseListener()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Timer para esconder controles
    DisposableEffect(exoPlayer) {
        val timer = java.util.Timer()
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                if (exoPlayer.isPlaying) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        controlsVisible = false
                    }
                }
            }
        }, 4000)
        onDispose { timer.cancel() }
    }

    val isEmbed = !source.isDirectPlayable

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isEmbed && playbackUrl != null) {
            // WebView para embeds de terceiros
            EmbedWebView(url = playbackUrl!!)
        } else {
            // ExoPlayer nativo
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Controles customizados
                        resizeMode = aspectMode.resizeMode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                        // Configura estilo das legendas
                        subtitleView?.setStyle(
                            CaptionStyleCompat(
                                Color.White,
                                Color.Transparent,
                                Color.Transparent,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                Color.Black,
                                null,
                            )
                        )
                        subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
                    }
                },
                update = { view ->
                    view.resizeMode = aspectMode.resizeMode
                    view.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Mensagem de erro
        errorMessage?.let { msg ->
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            ) {
                Text(
                    msg,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                )
            }
        }

        // Overlay de controles e title
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { controlsVisible = !controlsVisible },
        ) {
            // Title overlay no topo
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 40.dp, vertical = 16.dp),
                ) {
                    Text(
                        title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (season > 0 && episode > 0) {
                        val epLabel = episodeTitle ?: "Episódio $episode"
                        Text(
                            "S${season}E${episode} — $epLabel",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Botão de configurações no canto inferior direito
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { settingsPanel = SettingsPanel.MAIN }
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text("⚙", fontSize = 22.sp, color = Color.White)
                }
            }
        }

        // Painel de configurações
        if (settingsPanel != SettingsPanel.NONE) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { settingsPanel = SettingsPanel.NONE },
            )

            // Painel compacto no canto inferior direito
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 48.dp)
                    .width(300.dp)
                    .heightIn(max = 320.dp)
                    .background(Color(0xFF1A1A28).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {},
            ) {
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    when (settingsPanel) {
                        SettingsPanel.MAIN -> {
                            item {
                                SettingsRow(label = "Proporção", value = aspectMode.label) {
                                    aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                            }
                            item {
                                SettingsRow(label = "Legenda", value = selectedSubtitleLabel) {
                                    settingsPanel = SettingsPanel.SUBTITLE
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                            }
                            if (audioOptions.isNotEmpty()) {
                                item {
                                    SettingsRow(label = "Áudio", value = selectedAudioLabel) {
                                        settingsPanel = SettingsPanel.AUDIO
                                    }
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                                }
                            }
                            item {
                                SettingsRow(label = "Qualidade", value = selectedQualityLabel) {
                                    settingsPanel = SettingsPanel.QUALITY
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                            }
                            item {
                                SettingsRow(label = "Velocidade", value = "${playbackSpeed}x") {
                                    settingsPanel = SettingsPanel.SPEED
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                            }
                            item {
                                SettingsRow(label = "Abrir no VLC", value = "") {
                                    settingsPanel = SettingsPanel.NONE
                                    openInExternalPlayer(context, playbackUrl ?: return@SettingsRow)
                                }
                            }
                        }

                        SettingsPanel.SUBTITLE -> {
                            item {
                                SubmenuHeader(title = "Legenda") { settingsPanel = SettingsPanel.MAIN }
                            }
                            item {
                                SubmenuItem(
                                    label = "Desligada",
                                    selected = selectedSubtitleLabel == "Desligada",
                                ) {
                                    selectTrack(exoPlayer, null, C.TRACK_TYPE_TEXT)
                                    selectedSubtitleLabel = "Desligada"
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                            items(subtitleOptions) { option ->
                                SubmenuItem(
                                    label = option.label,
                                    selected = selectedSubtitleLabel == option.label,
                                ) {
                                    selectTrack(exoPlayer, option, C.TRACK_TYPE_TEXT)
                                    selectedSubtitleLabel = option.label
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                            if (subtitleOptions.isEmpty()) {
                                item {
                                    Text(
                                        "Sem legendas nesta fonte",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                    )
                                }
                            }
                        }

                        SettingsPanel.AUDIO -> {
                            item {
                                SubmenuHeader(title = "Áudio") { settingsPanel = SettingsPanel.MAIN }
                            }
                            item {
                                SubmenuItem(
                                    label = "Padrão",
                                    selected = selectedAudioLabel == "Padrão",
                                ) {
                                    selectTrack(exoPlayer, null, C.TRACK_TYPE_AUDIO)
                                    selectedAudioLabel = "Padrão"
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                            items(audioOptions) { option ->
                                SubmenuItem(
                                    label = option.label,
                                    selected = selectedAudioLabel == option.label,
                                ) {
                                    selectTrack(exoPlayer, option, C.TRACK_TYPE_AUDIO)
                                    selectedAudioLabel = option.label
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                        }

                        SettingsPanel.QUALITY -> {
                            item {
                                SubmenuHeader(title = "Qualidade") { settingsPanel = SettingsPanel.MAIN }
                            }
                            item {
                                SubmenuItem(
                                    label = "Automático",
                                    selected = selectedQualityLabel == "Automático",
                                ) {
                                    selectTrack(exoPlayer, null, C.TRACK_TYPE_VIDEO)
                                    selectedQualityLabel = "Automático"
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                            items(qualityOptions) { option ->
                                SubmenuItem(
                                    label = option.label,
                                    selected = selectedQualityLabel == option.label,
                                ) {
                                    selectTrack(exoPlayer, option, C.TRACK_TYPE_VIDEO)
                                    selectedQualityLabel = option.label
                                    settingsPanel = SettingsPanel.MAIN
                                }
                            }
                            if (qualityOptions.isEmpty()) {
                                item {
                                    Text(
                                        "Só uma qualidade disponível",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                    )
                                }
                            }
                        }

                        SettingsPanel.SPEED -> {
                            item {
                                SubmenuHeader(title = "Velocidade") { settingsPanel = SettingsPanel.MAIN }
                            }
                            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                            items(speeds) { speed ->
                                SubmenuItem(
                                    label = "${speed}x",
                                    selected = playbackSpeed == speed,
                                ) {
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

/** Opção de faixa do ExoPlayer — legenda, áudio ou qualidade. */
data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val type: Int,
)

/** Seleciona uma faixa no ExoPlayer. */
private fun selectTrack(player: ExoPlayer, option: TrackOption?, type: Int) {
    player.setTrackSelectionParameters(
        player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, option == null)
            .build()
    )
    if (option != null) {
        player.trackSelectionParameters
        // Seleciona o track específico via MediaTrackSelectionParameters
        val trackSelectionParameters = player.trackSelectionParameters
        val tracks = player.currentTracks
        val builder = trackSelectionParameters.buildUpon()
        builder.setTrackTypeDisabled(type, false)
        player.setTrackSelectionParameters(builder.build())
    }
}

/** Abre URL no VLC ou player externo do sistema. */
@SuppressLint("SetJavaScriptEnabled")
private fun openInExternalPlayer(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "video/*")
        setPackage("org.videolan.vlc")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // VLC não instalado, abre com chooser genérico
        try {
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
        } catch (_: Exception) { }
    }
}

/** Aspect ratio disponível no player. */
enum class AspectMode(val label: String, val resizeMode: Int) {
    FIT("Original", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

/** Painéis do menu de configurações. */
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
        Text("←", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
        Text(title, color = Color.White, fontSize = 16.sp)
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
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
        Text(
            label,
            color = if (selected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.75f),
            fontSize = 15.sp,
        )
        if (selected) {
            Text("✓", color = Color(0xFFD4AF37), fontSize = 15.sp)
        }
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
        Text(value, color = Color(0xFFD4AF37), fontSize = 15.sp)
    }
}

// Surface agora usa o import da tv.material3

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
                    settings.userAgentString = "VLC/3.0.20 LibVLC/3.0.20"
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
                Text("Carregando vídeo...", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            }
        }
    }
}
