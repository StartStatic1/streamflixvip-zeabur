package com.streamflixvip.tv.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.VipSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PlayerTvScreen(
    source: VipSource,
    sources: List<VipSource> = listOf(source),
    season: Int,
    episode: Int,
    title: String,
    onBack: () -> Unit = {},
    onServerFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estado do player
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isVisible by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var showSubtitlesPanel by remember { mutableStateOf(false) }
    var showServersPanel by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentSource by remember { mutableStateOf(source) }
    val sourcesList by remember { mutableStateOf(sources.ifEmpty { listOf(source) }) }
    val focusRequester = remember { FocusRequester() }
    val pauseBtnFocus = remember { FocusRequester() }

    // Ao reexibir a barra, devolve o foco pro botão de play/pause (padrão de player de TV).
    // Roda só quando isVisible passa de false -> true, não a cada interação.
    LaunchedEffect(isVisible) {
        if (isVisible) {
            pauseBtnFocus.requestFocus()
        }
    }

    // Contador que reinicia toda vez que há interação com os controles (troca de valor
    // força o LaunchedEffect abaixo a reiniciar a contagem de 5s do zero).
    var hideTrigger by remember { mutableStateOf(0) }

    // Auto-hide dos controles após 5 segundos parado. Reinicia sempre que isVisible vira true
    // OU quando hideTrigger muda (ou seja, toda interação com os controles adia o sumiço),
    // mas sem roubar o foco do botão que o usuário estiver navegando.
    LaunchedEffect(isVisible, hideTrigger) {
        if (isVisible) {
            delay(5000)
            isVisible = false
            // Ao esconder, devolve o foco pro Box principal, que é quem escuta
            // o próximo toque do D-pad via onKeyEvent (senão o foco fica "perdido"
            // no botão que acabou de sumir da árvore de composição)
            focusRequester.requestFocus()
        }
    }

    // Chame isto de dentro de qualquer ação de controle para adiar o auto-hide
    val resetHideTimer = { hideTrigger++ }

    // Criar e destruir player com lifecycle
    DisposableEffect(context) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
        player = exoPlayer

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> { /* não resume automaticamente */ }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.release()
                    player = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            player = null
        }
    }

    // Carregar URL do stream
    LaunchedEffect(currentSource) {
        val exoPlayer = player ?: return@LaunchedEffect
        isLoading = true
        playbackError = null
        isVisible = true
        showSubtitlesPanel = false
        showServersPanel = false

        try {
            val resolvedUrl = withContext(Dispatchers.IO) {
                currentSource.resolvedPlaybackUrl(NetworkModule.ZEABUR_BASE_URL)
            }
            val mediaItem = MediaItem.fromUri(resolvedUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            isLoading = false
        } catch (e: Exception) {
            playbackError = "Erro ao carregar: ${e.message}"
            isLoading = false
        }

        // Listener de erros do player
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsLoadingChanged(isLoadingNow: Boolean) {
                isLoading = isLoadingNow
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Erro de reprodução: ${error.message}"
                isLoading = false
                isVisible = true
            }
        })
    }

    // Focar o player ao entrar
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Container principal com D-pad focus
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    isVisible = true
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyUp) {
                    return@onKeyEvent false
                }

                val isBack = keyEvent.key == Key.Back
                val isDpadOrCenter = when (keyEvent.key) {
                    Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter,
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> true
                    else -> false
                }

                if (isBack) {
                    if (isVisible) {
                        // Barra já visível: Back sai do player normalmente.
                        false
                    } else {
                        // Barra escondida: primeiro Back só reexibe os controles,
                        // não sai do player.
                        isVisible = true
                        true // consome o evento
                    }
                } else if (!isVisible && isDpadOrCenter) {
                    // Controles escondidos + qualquer tecla de navegação/OK:
                    // primeiro toque só reexibe, sem disparar a ação do botão por baixo.
                    isVisible = true
                    true
                } else if (isVisible && isDpadOrCenter) {
                    // Controles já visíveis: deixa a tecla seguir normalmente,
                    // mas adia o auto-hide porque houve interação.
                    resetHideTimer()
                    false
                } else {
                    false
                }
            },
    ) {
        // PlayerView (vídeo)
        player?.let { exoPlayer ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = exoPlayer
                },
            )
        }

        // Loading indicator
        AnimatedVisibility(
            visible = isLoading && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFD4AF37))
            }
        }

        // Erro de reprodução
        AnimatedVisibility(
            visible = playbackError != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        playbackError ?: "Erro desconhecido",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            onClick = onServerFailed,
                            colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)),
                            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
                        ) {
                            Text(
                                "Voltar",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // OVERLAY DE CONTROLES
        AnimatedVisibility(
            visible = isVisible && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Topo - Título
                Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color(0xFF0A0A10).copy(alpha = 0.8f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.1f)),
                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                        Text(title, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }

                // Centro - Espaçador (área de D-pad para navegação)
                Box(modifier = Modifier.fillMaxWidth().weight(1f))

                // Bottom - Barra de progresso + botões
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF0A0A10).copy(alpha = 0.85f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Esquerda: Pausa/Play + Seek
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerControlBtn(
                                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                onClick = {
                                    if (isPlaying) player?.pause() else player?.play()
                                    isPlaying = !isPlaying
                                    resetHideTimer()
                                },
                                focusRequester = pauseBtnFocus,
                            )
                            PlayerControlBtn(
                                icon = Icons.Filled.Replay10,
                                onClick = {
                                    player?.let {
                                        val newPos = (it.currentPosition - 10_000).coerceAtLeast(0)
                                        it.seekTo(newPos)
                                    }
                                    resetHideTimer()
                                },
                            )
                            PlayerControlBtn(
                                icon = Icons.Filled.Forward10,
                                onClick = {
                                    player?.let {
                                        val newPos = (it.currentPosition + 10_000).coerceAtMost(it.duration.coerceAtLeast(0))
                                        it.seekTo(newPos)
                                    }
                                    resetHideTimer()
                                },
                            )
                        }

                        // Tempo atual (atualiza a cada segundo enquanto a barra está visível)
                        var currentPosMs by remember { mutableStateOf(0L) }
                        var durationMs by remember { mutableStateOf(0L) }
                        LaunchedEffect(player, isVisible) {
                            while (isVisible) {
                                player?.let {
                                    currentPosMs = it.currentPosition.coerceAtLeast(0)
                                    durationMs = it.duration.coerceAtLeast(0)
                                }
                                delay(500)
                            }
                        }
                        Text(
                            "${formatTime(currentPosMs)} / ${formatTime(durationMs)}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                        )

                        // Direita: Legendas + Servidores
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerControlBtn(
                                icon = Icons.Filled.ClosedCaption,
                                onClick = {
                                    showSubtitlesPanel = !showSubtitlesPanel
                                    showServersPanel = false
                                    resetHideTimer()
                                },
                            )
                            PlayerControlBtn(
                                icon = Icons.Filled.VideoLibrary,
                                onClick = {
                                    showServersPanel = !showServersPanel
                                    showSubtitlesPanel = false
                                    resetHideTimer()
                                },
                            )
                        }
                    }
                }
            }
        }

        // Painel de Legendas
        if (showSubtitlesPanel) {
            SubtitlesPanel(
                player = player,
                onClose = { showSubtitlesPanel = false },
            )
        }

        // Painel de Servidores
        if (showServersPanel) {
            ServersPanel(
                sources = sourcesList,
                currentSource = currentSource,
                onClose = { showServersPanel = false },
                onSelect = { newSource ->
                    showServersPanel = false
                    currentSource = newSource
                },
            )
        }
    }
}

@Composable
private fun PlayerControlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "btn_scale")

    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color(0xFFD4AF37).copy(alpha = 0.25f),
            focusedContentColor = Color(0xFFD4AF37),
        ),
        modifier = Modifier
            .size(48.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isFocused) Color(0xFFD4AF37) else Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun BoxScope.SubtitlesPanel(
    player: ExoPlayer?,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.4f)
            .align(Alignment.Center)
            .padding(horizontal = 60.dp)
            .background(Color(0xFF1E1E2E), RoundedCornerShape(12.dp))
            .padding(24.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Legendas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Text("Fechar", color = Color(0xFFD4AF37))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Opção: Desligar legendas
            var isFocusedOff by remember { mutableStateOf(false) }
            Card(
                onClick = {
                    player?.let {
                        it.setTrackSelectionParameters(
                            androidx.media3.common.TrackSelectionParameters.DEFAULT.buildUpon().build()
                        )
                    }
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(vertical = 4.dp)
                    .onFocusChanged { isFocusedOff = it.isFocused },
                colors = CardDefaults.colors(
                    containerColor = if (isFocusedOff) Color(0xFF2E2E3E) else Color(0xFF15151C),
                ),
                shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text(
                        "Desligar",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = Color.White,
                    )
                }
            }

            Text(
                "Legendas externas não disponíveis neste formato.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.ServersPanel(
    sources: List<VipSource>,
    currentSource: VipSource,
    onClose: () -> Unit,
    onSelect: (VipSource) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .align(Alignment.Center)
            .padding(horizontal = 60.dp)
            .background(Color(0xFF1E1E2E), RoundedCornerShape(12.dp))
            .padding(24.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Escolher Servidor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Text("Fechar", color = Color(0xFFD4AF37))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // CORREÇÃO: Usar for loop em vez de forEach para evitar erro de contexto Composable
            for (srv in sources) {
                ServerItem(
                    srv = srv,
                    isCurrent = srv.source_url == currentSource.source_url,
                    onSelect = { onSelect(srv) }
                )
            }
        }
    }
}

@Composable
private fun ServerItem(
    srv: VipSource,
    isCurrent: Boolean,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isCurrent) Color(0xFFD4AF37).copy(alpha = 0.2f)
            else if (isFocused) Color(0xFF2E2E3E)
            else Color(0xFF15151C),
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Dns,
                    contentDescription = null,
                    tint = Color(0xFFD4AF37),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(srv.displayName, fontSize = 15.sp, color = Color.White)
            }
            if (isCurrent) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Atual", tint = Color(0xFFD4AF37), modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
