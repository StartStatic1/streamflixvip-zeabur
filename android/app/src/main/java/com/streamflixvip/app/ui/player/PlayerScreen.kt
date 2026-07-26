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
import androidx.compose.runtime.collectAsState
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
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
// removido duplicata
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.data.ProgressRepository
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.StreamUrlResolver
import com.streamflixvip.app.network.VipSource
import com.startapp.sdk.adsbase.StartAppAd
import com.streamflixvip.app.data.VipStatusHolder
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

    // Mantém a tela ligada enquanto o player está aberto — sem essa flag
    // o timeout normal de brilho/bloqueio do celular escurece a tela no
    // meio do filme, mesmo com o vídeo tocando.
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Antes de montar o player de verdade, resolve qual dos dois backends
    // (Koyeb/Zeabur) responde mais rápido pra essa fonte específica — ver
    // StreamUrlResolver. Fontes de embed (WebView) não passam por essa
    // race: o iframe de terceiro não tem "backend nosso" nenhum por trás.
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
        // Só aparece por até ~6s (timeout do resolveFastest) enquanto os
        // dois backends competem — normalmente é bem mais rápido que
        // isso, já que HEAD é uma requisição leve.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Lógica de Anúncios Start.io:
    // Só carrega e mostra se o usuário NÃO for VIP.
    val isVip by VipStatusHolder.isVip.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(isVip) {
        if (!isVip) {
            // Carrega e mostra um anúncio intersticial (tela cheia) antes do filme começar
            StartAppAd.showAd(context)
        }
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
    var selectedAudioLabel by remember { mutableStateOf("Padrão") }
    var selectedQualityLabel by remember { mutableStateOf("Automático") }
    var playbackSpeed by remember { mutableStateOf(1f) }
    // Um único estado de "página" ativa no painel de configurações, em vez
    // de um bottom sheet vertical (que em tela forçada em paisagem cobria o
    // vídeo quase inteiro) com dropdowns empilhados dentro dele. NONE =
    // painel fechado, MAIN = lista principal, os outros = submenu daquela
    // opção (com botão de voltar pro MAIN).
    var settingsPanel by remember { mutableStateOf(SettingsPanel.NONE) }
    // Espelha a visibilidade dos controles nativos do ExoPlayer (que já
    // somem sozinhos após alguns segundos parado, e reaparecem com um
    // toque) — assim nossa barra de chips soma/aparece exatamente junto,
    // em vez de ficar fixa na tela competindo com o vídeo.
    var controlsVisible by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        // Injeta headers de player IPTV profissional para evitar erro 403 (Forbidden)
        // em provedores que exigem User-Agent conhecido (ex: Smarters/VLC).
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setDefaultRequestProperties(mapOf(
                "Referer" to url,
                "Connection" to "keep-alive",
                "Icy-MetaData" to "1"
            ))

        // Configura extratores para serem mais tolerantes com formatos IPTV (ex: TS dentro de MP4)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(httpDataSourceFactory)

        val mediaItem = MediaItem.fromUri(url)
        val mediaSource = if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
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
                // Retoma de onde a pessoa parou — só faz sentido pular pra
                // frente (valor > 0); vindo de "Assistir do início" o
                // parâmetro chega como 0 e não faz nada aqui.
                if (resumeSeconds > 0) {
                    seekTo(resumeSeconds * 1000L)
                }
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        android.util.Log.e("PlayerScreen", "Erro de reprodução: ${error.errorCodeName} (${error.errorCode})", error)
                        errorMessage = "Falha ao carregar vídeo: ${error.errorCodeName}"
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

        errorMessage?.let { msg ->
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        
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
            IconButton(onClick = { settingsPanel = SettingsPanel.MAIN }) {
                Text("⚙", fontSize = 22.sp, color = Color.White)
            }
        }

        if (settingsPanel != SettingsPanel.NONE) {
            // Scrim invisível cobrindo a tela toda só pra capturar o toque
            // "fora do painel" e fechar — mesmo comportamento de tocar fora
            // de um bottom sheet, sem precisar de um Dialog/Popup separado.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { settingsPanel = SettingsPanel.NONE },
            )

            // Painel compacto ancorado no canto inferior direito — mesma
            // posição do botão de engrenagem — no lugar do bottom sheet que
            // subia do fundo até quase o topo. Largura fixa e altura
            // limitada com rolagem, do jeito que cabe numa tela deitada
            // sem tampar o vídeo inteiro.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 8.dp, bottom = 48.dp)
                    .width(280.dp)
                    .heightIn(max = 260.dp)
                    .background(Color.Black.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    // Consome o toque dentro do painel pra não vazar pro
                    // scrim atrás e fechar sem querer.
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
                            SettingsRow(label = "Proporção", value = aspectMode.label) {
                                aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                            SettingsRow(label = "Legenda", value = selectedSubtitleLabel) {
                                settingsPanel = SettingsPanel.SUBTITLE
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                            if (audioOptions.isNotEmpty()) {
                                SettingsRow(label = "Áudio", value = selectedAudioLabel) {
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
                                openInExternalPlayer(context, url)
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
                                Text(
                                    "Sem legendas nesta fonte",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                        }

                        SettingsPanel.AUDIO -> {
                            SubmenuHeader(title = "Áudio") { settingsPanel = SettingsPanel.MAIN }
                            SubmenuItem(label = "Padrão", selected = selectedAudioLabel == "Padrão") {
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
                            SubmenuItem(label = "Automático", selected = selectedQualityLabel == "Automático") {
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
                                Text(
                                    "Só uma qualidade disponível",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
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

/** As "páginas" possíveis do painel de configurações do player. */
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
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 15.sp,
        )
        if (selected) {
            Text("✓", color = Color.White, fontSize = 15.sp)
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
