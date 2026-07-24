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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
    val sources by remember { mutableStateOf(listOf(source)) }
    val focusRequester = remember { FocusRequester() }
    val pauseBtnFocus = remember { FocusRequester() }

    // Auto-hide dos controles após 5 segundos
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(5000)
            isVisible = false
        }
    }

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
                        TextButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                        Text(title, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }

                // Centro - Espaçador (área de D-pad para navegação)
                Spacer(modifier = Modifier.weight(1f))

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
                                },
                            )
                            PlayerControlBtn(
                                icon = Icons.Filled.Forward10,
                                onClick = {
                                    player?.let {
                                        val newPos = (it.currentPosition + 10_000).coerceAtMost(it.duration.coerceAtLeast(0))
                                        it.seekTo(newPos)
                                    }
                                },
                            )
                        }

                        // Tempo atual
                        player?.let { exoPlayer ->
                            val duration = exoPlayer.duration.coerceAtLeast(0)
                            val current = exoPlayer.currentPosition.coerceAtLeast(0)
                            Text(
                                "${formatTime(current)} / ${formatTime(duration)}",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }

                        // Direita: Legendas + Servidores
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerControlBtn(
                                icon = Icons.Filled.ClosedCaption,
                                onClick = {
                                    showSubtitlesPanel = !showSubtitlesPanel
                                    showServersPanel = false
                                },
                            )
                            PlayerControlBtn(
                                icon = Icons.Filled.VideoLibrary,
                                onClick = {
                                    showServersPanel = !showServersPanel
                                    showSubtitlesPanel = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Painel de Legendas
        AnimatedVisibility(
            visible = showSubtitlesPanel,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SubtitlesPanel(
                player = player,
                onClose = { showSubtitlesPanel = false },
            )
        }

        // Painel de Servidores
        AnimatedVisibility(
            visible = showServersPanel,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ServersPanel(
                sources = sources,
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

// ─── BOTÃO DE CONTROLE DO PLAYER ────────────────────────────────────────────────

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
        modifier = Modifier
            .size(48.dp)
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

// ─── PAINEL DE LEGENDAS ─────────────────────────────────────────────────────────

@Composable
private fun SubtitlesPanel(
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
            ) {
                Text("Legendas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                TextButton(onClick = onClose) {
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
                Text(
                    "Desligar",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = Color.White,
                )
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

// ─── PAINEL DE SERVIDORES ───────────────────────────────────────────────────────

@Composable
private fun ServersPanel(
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
            ) {
                Text("Escolher Servidor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                TextButton(onClick = onClose) {
                    Text("Fechar", color = Color(0xFFD4AF37))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            sources.forEach { srv ->
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

// ─── HELPERS ────────────────────────────────────────────────────────────────────

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
