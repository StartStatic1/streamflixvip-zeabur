package com.streamflixvip.app.ui.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.data.ProgressRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Salva a posição a cada 15s de reprodução — frequente o bastante pra não
 * perder muito progresso se o app fechar de repente, raro o bastante pra
 * não sobrecarregar o Supabase com requisições. */
private const val PROGRESS_SAVE_INTERVAL_MS = 15_000L

/** Modos de ajuste de tela disponíveis pro botão de proporção. */
private enum class AspectMode(val label: String, val resizeMode: Int) {
    FIT("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/** Uma opção de faixa (legenda ou qualidade de vídeo) disponível na mídia atual. */
private data class TrackOption(
    val label: String,
    val group: TrackGroup,
    val trackIndex: Int,
)

/**
 * Tela de reprodução — implementa a decisão híbrida combinada:
 *
 * - Fonte direta (.mp4/.m3u8, incluindo as que passam pelo stream-proxy):
 *   toca em ExoPlayer NATIVO de verdade, com controles próprios de
 *   proporção de tela, legenda e qualidade, além de retomar de onde a
 *   pessoa parou e salvar progresso periodicamente no Supabase.
 *
 * - Fonte que é iframe de player de terceiro (embed que só expõe HTML,
 *   não a URL do arquivo): cai pra WebView, isolada nesta tela. Esse
 *   caminho não tem controles próprios nem progresso salvo — não dá pra
 *   inspecionar ou controlar o que roda dentro do iframe de terceiro.
 */
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
    if (isDirectPlayable) {
        NativePlayer(
            url = sourceUrl,
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
        EmbedWebView(url = sourceUrl)
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
    var qualityOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var selectedSubtitleLabel by remember { mutableStateOf("Desligada") }
    var selectedQualityLabel by remember { mutableStateOf("Automático") }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
                // Retoma de onde a pessoa parou — só faz sentido pular pra
                // frente (valor > 0); vindo de "Assistir do início" o
                // parâmetro chega como 0 e não faz nada aqui.
                if (resumeSeconds > 0) {
                    seekTo(resumeSeconds * 1000L)
                }
                addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        val subtitles = mutableListOf<TrackOption>()
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
                        // Maior resolução primeiro, sem duplicar a mesma altura.
                        qualityOptions = qualities.distinctBy { it.label }.sortedByDescending {
                            it.label.removeSuffix("p").toIntOrNull() ?: 0
                        }
                    }
                })
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

    fun selectQuality(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedQualityLabel = "Automático"
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

    // Loop de salvamento periódico enquanto a tela do player está viva.
    LaunchedEffect(exoPlayer) {
        if (userId == null || accessToken == null) return@LaunchedEffect
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            persistCurrentPosition()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Salva a posição final ao sair da tela (voltar, trocar de
            // app, etc). Usa GlobalScope de propósito aqui: o escopo
            // normal da composable é cancelado assim que ela sai de tela,
            // o que impediria justamente essa última gravação de
            // completar. É um "best effort" pontual só pra este caso.
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

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view -> view.resizeMode = aspectMode.resizeMode },
        )

        // Barra de controles extras (proporção, legenda, qualidade) —
        // fica sobreposta no topo, por cima dos controles nativos do
        // ExoPlayer (play/pause/seek), que continuam funcionando normalmente.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            PlayerChip(text = aspectMode.label) {
                aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
            }

            Box {
                PlayerChip(text = "CC: $selectedSubtitleLabel") { showSubtitleMenu = true }
                DropdownMenu(expanded = showSubtitleMenu, onDismissRequest = { showSubtitleMenu = false }) {
                    DropdownMenuItem(text = { Text("Desligada") }, onClick = {
                        selectSubtitle(null)
                        showSubtitleMenu = false
                    })
                    subtitleOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                            selectSubtitle(option)
                            showSubtitleMenu = false
                        })
                    }
                    if (subtitleOptions.isEmpty()) {
                        DropdownMenuItem(text = { Text("Sem legendas nesta fonte", color = Color.Gray) }, onClick = {}, enabled = false)
                    }
                }
            }

            Box {
                PlayerChip(text = selectedQualityLabel) { showQualityMenu = true }
                DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                    DropdownMenuItem(text = { Text("Automático") }, onClick = {
                        selectQuality(null)
                        showQualityMenu = false
                    })
                    qualityOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                            selectQuality(option)
                            showQualityMenu = false
                        })
                    }
                    if (qualityOptions.isEmpty()) {
                        DropdownMenuItem(text = { Text("Só uma qualidade disponível", color = Color.Gray) }, onClick = {}, enabled = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.55f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier.padding(horizontal = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, color = Color.White)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWebView(url: String) {
    // WebView usado APENAS aqui, isolado — nenhuma outra tela do app
    // depende dele. Existe só porque alguns parceiros de embed só
    // expõem um <iframe>, não uma URL de arquivo que o ExoPlayer possa
    // consumir direto.
    Box(Modifier.fillMaxSize()) {
        var isLoading by remember { mutableStateOf(true) }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
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
