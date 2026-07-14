package com.streamflixvip.app.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

/** Velocidades de reprodução disponíveis — mesmo padrão que VLC/YouTube usam. */
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Uma opção de faixa (legenda, áudio ou qualidade de vídeo) disponível na mídia atual. */
private data class TrackOption(
    val label: String,
    val group: TrackGroup,
    val trackIndex: Int,
)

/**
 * Abre a URL atual num player externo instalado (VLC ou qualquer outro
 * app que registre suporte a vídeo) via Intent.ACTION_VIEW — mesmo
 * mecanismo que o Android usa pra "abrir com" em qualquer app. Se
 * nenhum player estiver instalado, mostra um aviso em vez de crashar.
 */
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

/**
 * Tela de reprodução — implementa a decisão híbrida combinada:
 *
 * - Fonte direta (.mp4/.m3u8, incluindo as que passam pelo stream-proxy):
 *   toca em ExoPlayer NATIVO de verdade, em tela cheia imersiva (barra de
 *   status/navegação do Android escondida), com controles próprios de
 *   proporção de tela, legenda, áudio, velocidade e qualidade — tudo
 *   escondendo junto com os controles nativos do player após alguns
 *   segundos parado, reaparecendo com um toque na tela. Também retoma de
 *   onde a pessoa parou e salva progresso periodicamente no Supabase.
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
    // Tela cheia imersiva: some com a barra de status e navegação do
    // Android enquanto o player está aberto (padrão de qualquer player de
    // vídeo — VLC, YouTube, Netflix). Restaura ao sair da tela, porque o
    // resto do app deve continuar mostrando as barras normalmente.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Força paisagem automaticamente ao abrir o player — antes disso só
    // ficava em tela cheia de verdade se o celular já estivesse deitado
    // fisicamente, o que obrigava o usuário a girar manualmente toda vez.
    // Restaura a orientação original (geralmente retrato) ao sair, pra não
    // deixar o resto do app preso em paisagem.
    val activity = view.context as? Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

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
    var audioOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var qualityOptions by remember { mutableStateOf(listOf<TrackOption>()) }
    var selectedSubtitleLabel by remember { mutableStateOf("Desligada") }
    var selectedAudioLabel by remember { mutableStateOf("Padrão") }
    var selectedQualityLabel by remember { mutableStateOf("Automático") }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    // Espelha a visibilidade dos controles nativos do ExoPlayer (que já
    // somem sozinhos após alguns segundos parado, e reaparecem com um
    // toque) — assim nossa barra de chips soma/aparece exatamente junto,
    // em vez de ficar fixa na tela competindo com o vídeo.
    var controlsVisible by remember { mutableStateOf(true) }

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
                        // Só mostra opção de trocar áudio se houver mais de
                        // uma faixa — a maioria das fontes tem só uma, e aí
                        // o menu não agrega nada (só teria "Padrão" mesmo).
                        audioOptions = if (audios.size > 1) audios else emptyList()
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

    fun selectAudio(option: TrackOption?) {
        trackSelector.parameters = if (option == null) {
            selectedAudioLabel = "Padrão"
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
                    // O layout padrão do controller do Media3 inclui um
                    // botão de "settings" (engrenagem, id exo_settings) no
                    // canto inferior direito — mas qualidade/áudio/legenda/
                    // velocidade já são escolhidos pelos nossos chips
                    // customizados (linha ~419 abaixo), então esse botão
                    // nativo fica redundante e reservava aquele espaço
                    // vazio ao lado do tempo. O Media3 não expõe um setter
                    // dedicado pra escondê-lo isoladamente, então achamos a
                    // View já inflada pelo id padrão e escondemos — chamando
                    // de novo a cada troca de visibilidade dos controles,
                    // porque o Media3 pode reinflar/reexibir esse botão
                    // sozinho (troca de faixa, rotação de tela) e ele
                    // voltaria a aparecer se escondêssemos só uma vez.
                    fun hideNativeSettingsButton() {
                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)
                            ?.visibility = android.view.View.GONE
                    }
                    post { hideNativeSettingsButton() }

                    // Espelha show/hide dos controles nativos (play/pause/seek)
                    // pra nossa barra de chips extra — assim os dois aparecem
                    // e somem juntos, com o mesmo toque na tela.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                            hideNativeSettingsButton()
                        },
                    )
                }
            },
            update = { view -> view.resizeMode = aspectMode.resizeMode },
        )

        // Botão único de configurações — substitui a engrenagem nativa do
        // ExoPlayer (escondida acima) na MESMA posição (canto inferior
        // direito, na altura da barra de tempo/seek), em vez da fileira de
        // chips que antes ficava solta acima de tudo. Toque abre um bottom
        // sheet com proporção, legenda, áudio, qualidade e mais — padrão
        // premium (YouTube/Netflix) em vez de poluir a tela com botões.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = { showSettingsSheet = true }) {
                Text("⚙", fontSize = 22.sp, color = Color.White)
            }
        }

        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                sheetState = sheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsRow(label = "Proporção", value = aspectMode.label) {
                        aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    SettingsRow(label = "Legenda", value = selectedSubtitleLabel) { showSubtitleMenu = true }
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
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    if (audioOptions.isNotEmpty()) {
                        SettingsRow(label = "Áudio", value = selectedAudioLabel) { showAudioMenu = true }
                        DropdownMenu(expanded = showAudioMenu, onDismissRequest = { showAudioMenu = false }) {
                            DropdownMenuItem(text = { Text("Padrão") }, onClick = {
                                selectAudio(null)
                                showAudioMenu = false
                            })
                            audioOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                    selectAudio(option)
                                    showAudioMenu = false
                                })
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }

                    SettingsRow(label = "Qualidade", value = selectedQualityLabel) { showQualityMenu = true }
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
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    SettingsRow(label = "Velocidade", value = "${playbackSpeed}x") { showSpeedMenu = true }
                    DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                        PLAYBACK_SPEEDS.forEach { speed ->
                            DropdownMenuItem(text = { Text("${speed}x") }, onClick = {
                                playbackSpeed = speed
                                exoPlayer.setPlaybackSpeed(speed)
                                showSpeedMenu = false
                            })
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    SettingsRow(label = "Abrir no VLC / player externo", value = "") {
                        showSettingsSheet = false
                        openInExternalPlayer(context, url)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
