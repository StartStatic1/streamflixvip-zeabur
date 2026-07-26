package com.streamflixvip.tv.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

// Enum para os modos de Aspect Ratio
enum class AspectRatioMode(val mode: Int, val label: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Original"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Esticar"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom"),
    FIXED_HEIGHT(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, "Alt. Fixa"),
    FIXED_WIDTH(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "Larg. Fixa")
}

enum class BottomPanelType { ASPECT, SUBTITLES, SERVERS }

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

    // --- Estados do Player ---
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentSource by remember { mutableStateOf(source) }
    val sourcesList by remember { mutableStateOf(sources.ifEmpty { listOf(source) }) }
    // Guarda a posição antes de trocar de servidor (ver troca em
    // BottomPanelType.SERVERS), pra retomar de onde parou em vez de
    // reiniciar do zero — sem isso, trocar de servidor no meio do filme
    // te jogava de volta pro início.
    var resumePositionMs by remember { mutableStateOf(0L) }
    
    // --- Estado do Aspect Ratio ---
    var currentAspectRatio by remember { mutableStateOf(AspectRatioMode.FIT) }

    // --- Estado dos painéis minimalistas ---
    var activeBottomPanel by remember { mutableStateOf<BottomPanelType?>(null) }

    // --- Visibilidade e Foco ---
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val rootFocusRequester = remember { FocusRequester() }
    val pauseBtnFocus = remember { FocusRequester() }

    fun showControls() {
        controlsVisible = true
        interactionTick++
    }

    // Auto-hide: esconde se não houver interação por 5s e nenhum painel estiver aberto
    LaunchedEffect(controlsVisible, interactionTick, activeBottomPanel) {
        if (controlsVisible && activeBottomPanel == null) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Gerenciamento de Foco ao mostrar controles
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && activeBottomPanel == null) {
            runCatching { pauseBtnFocus.requestFocus() }
        }
    }

    // Ciclo de vida do ExoPlayer
    DisposableEffect(context) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
        player = exoPlayer

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {}
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

    // Carregamento do Stream
    LaunchedEffect(currentSource) {
        val exoPlayer = player ?: return@LaunchedEffect
        isLoading = true
        playbackError = null
        showControls()
        activeBottomPanel = null

        try {
            val resolvedUrl = withContext(Dispatchers.IO) {
                currentSource.resolvedPlaybackUrl(NetworkModule.ZEABUR_BASE_URL)
            }
            val mediaItem = MediaItem.fromUri(resolvedUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (resumePositionMs > 0) {
                exoPlayer.seekTo(resumePositionMs)
            }
            exoPlayer.playWhenReady = true
            isLoading = false
        } catch (e: Exception) {
            playbackError = "Erro ao carregar: ${e.message}"
            isLoading = false
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsLoadingChanged(isLoadingNow: Boolean) { isLoading = isLoadingNow }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Erro de reprodução: ${error.message}"
                isLoading = false
                showControls()
            }
        })
    }

    LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .focusRequester(rootFocusRequester)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false

                val isBack = keyEvent.key == Key.Back || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE
                val isDpadOrCenter = when (keyEvent.key) {
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> true
                    else -> keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                }

                when {
                    isBack -> {
                        when {
                            activeBottomPanel != null -> {
                                activeBottomPanel = null
                                showControls()
                                true
                            }
                            !controlsVisible -> {
                                showControls()
                                true
                            }
                            else -> false // Propaga para sair da tela
                        }
                    }
                    !controlsVisible && isDpadOrCenter -> {
                        showControls()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Camada do Vídeo
        player?.let { exoPlayer ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = false
                        resizeMode = currentAspectRatio.mode
                        // Configurações de legenda removendo chamadas problemáticas
                        subtitleView?.setApplyEmbeddedFontSizes(false)
                        subtitleView?.setApplyEmbeddedStyles(false)
                        // Removido: subtitleView?.setFixedTextSize(subtitleView.getTextSize() * 1.2f) para evitar erro de compilação
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.resizeMode = currentAspectRatio.mode },
            )
        }

        // Overlay de Controles (Barra Inferior)
        AnimatedVisibility(
            visible = controlsVisible && playbackError == null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(vertical = 16.dp)
            ) {
                // Título
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Barra de Tempo
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    var currentPosMs by remember { mutableStateOf(0L) }
                    var durationMs by remember { mutableStateOf(0L) }
                    LaunchedEffect(player, controlsVisible) {
                        while (controlsVisible) {
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
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botões
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerControlBtn(
                            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            onClick = { if (isPlaying) player?.pause() else player?.play(); showControls() },
                            focusRequester = pauseBtnFocus
                        )
                        PlayerControlBtn(icon = Icons.Filled.Replay10, onClick = { player?.seekTo((player?.currentPosition ?: 0) - 10000); showControls() })
                        PlayerControlBtn(icon = Icons.Filled.Forward10, onClick = { player?.seekTo((player?.currentPosition ?: 0) + 10000); showControls() })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuActionBtn(
                            label = currentAspectRatio.label,
                            icon = Icons.Filled.AspectRatio,
                            isSelected = activeBottomPanel == BottomPanelType.ASPECT,
                            onClick = { activeBottomPanel = if (activeBottomPanel == BottomPanelType.ASPECT) null else BottomPanelType.ASPECT; showControls() }
                        )
                        MenuActionBtn(
                            label = "Legendas",
                            icon = Icons.Filled.ClosedCaption,
                            isSelected = activeBottomPanel == BottomPanelType.SUBTITLES,
                            onClick = { activeBottomPanel = if (activeBottomPanel == BottomPanelType.SUBTITLES) null else BottomPanelType.SUBTITLES; showControls() }
                        )
                        MenuActionBtn(
                            label = "Servidores",
                            icon = Icons.Filled.Dns,
                            isSelected = activeBottomPanel == BottomPanelType.SERVERS,
                            onClick = { activeBottomPanel = if (activeBottomPanel == BottomPanelType.SERVERS) null else BottomPanelType.SERVERS; showControls() }
                        )
                    }
                }

                // Abas
                AnimatedVisibility(visible = activeBottomPanel != null) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.15f)))
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            when (activeBottomPanel) {
                                BottomPanelType.ASPECT -> {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(AspectRatioMode.values()) { mode ->
                                            MenuOptionChip(
                                                label = mode.label,
                                                isSelected = currentAspectRatio == mode,
                                                onClick = { currentAspectRatio = mode; activeBottomPanel = null }
                                            )
                                        }
                                    }
                                }
                                BottomPanelType.SUBTITLES -> {
                                    Text("Legendas Internas: Padrão", color = Color.White, fontSize = 14.sp)
                                }
                                BottomPanelType.SERVERS -> {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(sourcesList) { srv ->
                                            MenuOptionChip(
                                                label = srv.displayName,
                                                isSelected = srv.source_url == currentSource.source_url,
                                                onClick = {
                                                    resumePositionMs = player?.currentPosition ?: 0L
                                                    currentSource = srv
                                                    activeBottomPanel = null
                                                }
                                            )
                                        }
                                    }
                                }
                                null -> {}
                            }
                        }
                    }
                }
            }
        }

        // Loading/Erro
        if (isLoading && playbackError == null) {
            CircularProgressIndicator(color = Color(0xFFD4AF37), modifier = Modifier.align(Alignment.Center))
        }
        
        if (playbackError != null) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(playbackError!!, color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onServerFailed) { Text("Voltar", color = Color.Black) }
            }
        }
    }
}

@Composable
fun MenuActionBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFFD4AF37).copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (isFocused || isSelected) Color(0xFFD4AF37) else Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = if (isFocused || isSelected) Color(0xFFD4AF37) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MenuOptionChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (isSelected) Color(0xFFD4AF37).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlayerControlBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.15f else 1f),
        colors = IconButtonDefaults.colors(focusedContainerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Icon(icon, contentDescription = null, tint = if (isFocused) Color(0xFFD4AF37) else Color.White, modifier = Modifier.size(32.dp))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
