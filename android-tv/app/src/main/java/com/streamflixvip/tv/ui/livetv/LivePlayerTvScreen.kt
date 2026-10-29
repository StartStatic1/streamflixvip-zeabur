package com.streamflixvip.tv.ui.livetv

import android.app.Activity
import android.view.KeyEvent as AKeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.RecentStore
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.ui.components.FadeImage
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.PanelItem
import com.streamflixvip.tv.ui.components.SidePanel
import com.streamflixvip.tv.ui.components.TvButton
import com.streamflixvip.tv.ui.home.FocusTargetBox
import com.streamflixvip.tv.ui.player.isNetworkError
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
private enum class LiveAspect(val label: String, val mode: Int) {
    FIT("Original", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Preencher (zoom)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

private const val LIVE_START_TIMEOUT_MS = 14_000L
private const val LIVE_STALL_MS = 10_000L

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LivePlayerTvScreen(
    channels: List<LiveChannel>,
    initialIndex: Int,
    epgNow: Map<String, String>,
    epgNext: Map<String, String>,
    onChannelChanged: (index: Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val p = LocalPalette.current
    val toaster = LocalToaster.current
    val recent = remember { RecentStore(context) }

    var index by remember { mutableIntStateOf(initialIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    val channel = channels.getOrNull(index)
    val streams = remember(channel?.id) { channel?.let { orderStreams(it.streams) }.orEmpty() }
    val streamsNow by rememberUpdatedState(streams)
    var streamIndex by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(true) }
    // navegar pela faixa sem trocar de canal (OK confirma)
    var browseIndex by remember { mutableStateOf<Int?>(null) }
    var infoTick by remember { mutableIntStateOf(0) }
    var listVisible by remember { mutableStateOf(false) }
    var optionsVisible by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(LiveAspect.FIT) }
    var digits by remember { mutableStateOf("") }
    var isFavorite by remember(channel?.id) { mutableStateOf(channel?.let { LiveTvFavoritesStore.isFavorite(context, it.id) } ?: false) }

    val rootFocus = remember { FocusRequester() }
    val optionsFirst = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    var listFocusTarget by remember { mutableStateOf<String?>(null) }
    var listFocusNonce by remember { mutableIntStateOf(0) }
    val listState = rememberTvLazyListState()

    val exo = remember {
        ExoPlayer.Builder(context).setLoadControl(liveLoadControl()).build().apply { playWhenReady = true }
    }

    fun showInfo() {
        infoVisible = true
        infoTick++
    }

    fun zap(delta: Int) {
        if (channels.isEmpty()) return
        index = (index + delta).mod(channels.size)
        showInfo()
    }

    fun goTo(i: Int) {
        if (i in channels.indices) {
            index = i
            showInfo()
        }
    }

    // Fora da tela (HOME do Fire TV, outro app): nada toca nem troca de fonte.
    // O aviso vem direto do ciclo de vida (com o app parado o Compose nao recompoe).
    val foreground = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    var playedOk by remember { mutableStateOf(false) }
    var errorRetries by remember { mutableIntStateOf(0) }

    fun nextSourceOrFail(msgToast: String, msgFinal: String) {
        if (!foreground.get()) return
        streamsNow.getOrNull(streamIndex)?.let { LiveHostHealth.markFail(it.url) }
        if (streamIndex + 1 < streamsNow.size) {
            streamIndex++
            toaster.show(msgToast)
        } else {
            loading = false
            error = msgFinal
        }
    }

    DisposableEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    loading = false
                    error = null
                    playedOk = true
                    streamsNow.getOrNull(streamIndex)?.let { LiveHostHealth.markOk(it.url) }
                }
                if (state == Player.STATE_BUFFERING) loading = true
            }

            override fun onPlayerError(e: PlaybackException) {
                if (!foreground.get()) return
                // ficou para tras da janela ao vivo: volta para o ponto ao vivo, mesma fonte
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    return
                }
                // ja estava tocando e caiu a rede: reconecta na mesma fonte (2x) antes de trocar
                if (playedOk && errorRetries < 2 && isNetworkError(e.errorCode)) {
                    errorRetries++
                    toaster.show("Reconectando…")
                    reloadNonce++
                    return
                }
                nextSourceOrFail("Fonte falhou — tentando outra", "Este canal não abriu em nenhuma fonte agora.")
            }
        }
        exo.addListener(listener)
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> {
                    foreground.set(false)
                    exo.playWhenReady = false
                    exo.stop() // solta rede e decodificador; nada de audio na HOME
                }
                Lifecycle.Event.ON_START -> if (!foreground.getAndSet(true)) {
                    // volta ao vivo do zero na mesma fonte
                    errorRetries = 0
                    reloadNonce++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo.removeListener(listener)
            exo.release()
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // troca de canal
    LaunchedEffect(channel?.id) {
        val ch = channel ?: return@LaunchedEffect
        streamIndex = 0
        errorRetries = 0
        reloadNonce++
        recent.addLiveChannel(ch.id)
        onChannelChanged(index)
    }

    // abre a fonte atual (tempo limite para comecar) e vigia travadas
    LaunchedEffect(reloadNonce, streamIndex) {
        if (reloadNonce == 0) return@LaunchedEffect
        if (!foreground.get()) return@LaunchedEffect
        val opt = streams.getOrNull(streamIndex)
        if (opt == null) {
            loading = false
            error = "Canal sem fonte de vídeo."
            return@LaunchedEffect
        }
        loading = true
        error = null
        playedOk = false
        val opened = runCatching {
            exo.setMediaSource(buildLiveMediaSource(opt.url))
            exo.prepare()
            exo.playWhenReady = true
        }.isSuccess
        if (!opened) {
            nextSourceOrFail("Fonte falhou — tentando outra", "Este canal não abriu em nenhuma fonte agora.")
            return@LaunchedEffect
        }
        val startedAt = System.currentTimeMillis()
        var stalledSince = 0L
        var reconnects = 0
        while (true) {
            delay(1_000)
            if (!foreground.get()) return@LaunchedEffect
            val now = System.currentTimeMillis()
            val state = exo.playbackState
            if (!playedOk) {
                if (now - startedAt > LIVE_START_TIMEOUT_MS && error == null) {
                    nextSourceOrFail("Fonte lenta — tentando outra", "O canal demorou demais para responder.")
                    return@LaunchedEffect
                }
                continue
            }
            if (state == Player.STATE_READY && exo.isPlaying) {
                stalledSince = 0L
                continue
            }
            if (state == Player.STATE_BUFFERING || (state == Player.STATE_READY && exo.playWhenReady && !exo.isPlaying)) {
                if (stalledSince == 0L) stalledSince = now
                if (now - stalledSince > LIVE_STALL_MS) {
                    stalledSince = 0L
                    if (reconnects < 2) {
                        // travou: reconecta no ponto ao vivo
                        reconnects++
                        toaster.show("Sinal travou — reconectando")
                        runCatching {
                            exo.setMediaSource(buildLiveMediaSource(opt.url))
                            exo.prepare()
                            exo.playWhenReady = true
                        }
                    } else {
                        nextSourceOrFail("Sinal instável — tentando outra fonte", "O canal está travando em todas as fontes.")
                        return@LaunchedEffect
                    }
                }
            } else {
                stalledSince = 0L
            }
        }
    }

    LaunchedEffect(infoVisible, infoTick) {
        if (infoVisible) {
            delay(if (browseIndex != null) 8000 else 5000)
            infoVisible = false
            browseIndex = null
        }
    }

    // numero do canal pelo controle
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && n in 1..channels.size) goTo(n - 1) else toaster.show("Canal $n não existe nesta lista")
    }

    LaunchedEffect(listVisible) {
        if (listVisible) {
            runCatching { listState.scrollToItem((index - 3).coerceAtLeast(0)) }
            listFocusTarget = channels.getOrNull(index)?.id
            listFocusNonce++
        } else if (!optionsVisible) {
            delay(40)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(optionsVisible) {
        if (optionsVisible) {
            delay(90)
            runCatching { optionsFirst.requestFocus() }
        } else if (!listVisible) {
            delay(40)
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            delay(80)
            runCatching { errorFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { rootFocus.requestFocus() }
    }

    BackHandler {
        when {
            listVisible -> listVisible = false
            browseIndex != null -> { browseIndex = null; infoVisible = false }
            digits.isNotEmpty() -> digits = ""
            else -> onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = ev.nativeKeyEvent.keyCode
                if (code in AKeyEvent.KEYCODE_0..AKeyEvent.KEYCODE_9) {
                    if (digits.length < 4) digits += (code - AKeyEvent.KEYCODE_0).toString()
                    return@onPreviewKeyEvent true
                }
                when (code) {
                    AKeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return@onPreviewKeyEvent true }
                }
                if (listVisible || optionsVisible || error != null) return@onPreviewKeyEvent false
                when (code) {
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (channels.isNotEmpty()) {
                            val d = if (code == AKeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                            val b = browseIndex
                            browseIndex = if (b == null || !infoVisible) index else (b + d).mod(channels.size)
                            showInfo()
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (ev.nativeKeyEvent.repeatCount == 0) {
                            val b = browseIndex
                            if (b != null && infoVisible) {
                                browseIndex = null
                                if (b != index) goTo(b) else showInfo()
                            } else listVisible = true
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (ev.nativeKeyEvent.repeatCount == 0) { browseIndex = null; listVisible = true }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_MENU, AKeyEvent.KEYCODE_INFO -> {
                        optionsVisible = true
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.resizeMode = aspect.mode },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading && error == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = p.accent, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                if (streams.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Text("Fonte ${streamIndex + 1} de ${streams.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        // numero digitado
        if (digits.isNotEmpty()) {
            Text(
                digits,
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopEnd).padding(40.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }

        // faixa de informacoes (aparece ao trocar de canal)
        AnimatedVisibility(
            visible = infoVisible && !listVisible && channel != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val shownIndex = browseIndex ?: index
            val ch = channels.getOrNull(shownIndex) ?: channel ?: return@AnimatedVisibility
            val browsing = browseIndex != null && shownIndex != index
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                    .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(p.bgElevated.copy(alpha = 0.88f))
                        .border(1.dp, if (browsing) p.accent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▲", color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                        Text("%03d".format(shownIndex + 1), color = p.accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("▼", color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Box(
                        Modifier.size(width = 110.dp, height = 66.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!ch.logo.isNullOrBlank()) FadeImage(ch.logo, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                        else Text(ch.name.take(2).uppercase(), color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (shownIndex == index && isFavorite) {
                                Spacer(Modifier.width(8.dp))
                                androidx.tv.material3.Icon(Icons.Filled.Star, null, tint = p.star, modifier = Modifier.size(18.dp))
                            }
                            val qs = ch.streams.mapNotNull { it.quality?.takeIf { q -> q.isNotBlank() } }.distinct()
                            qs.take(4).forEach { q ->
                                Spacer(Modifier.width(6.dp))
                                Text(q, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            if (ch.streams.any { it.leg == true }) {
                                Spacer(Modifier.width(6.dp))
                                Text("LEG", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF34D399)).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val now = epgNow[ch.id]
                        Text(
                            if (now != null) "Agora · $now" else "Sem programação disponível",
                            color = Color.White.copy(alpha = 0.88f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        // barra decorativa de "ao vivo" (a grade não informa horários)
                        Box(Modifier.padding(vertical = 6.dp).width(260.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
                            Box(Modifier.fillMaxHeight().width(130.dp).background(p.accent))
                        }
                        epgNext[ch.id]?.let {
                            Text("A seguir · $it", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!browsing) streams.getOrNull(streamIndex)?.let {
                            Text(streamLabel(it, streamIndex), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        LiveClock()
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (browsing) "OK assistir  ·  ▲▼ navegar  ·  Voltar fecha" else "▲▼ ver canais  ·  OK lista  ·  ▶ opções",
                            color = if (browsing) p.accent else Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // lista de canais (esquerda)
        AnimatedVisibility(
            visible = listVisible,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Column(
                Modifier.width(400.dp).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(p.bg.copy(alpha = 0.97f), p.bg.copy(alpha = 0.85f))))
                    .padding(horizontal = 18.dp, vertical = 22.dp)
                    .focusProperties { exit = { com.streamflixvip.tv.ui.components.trapArrows(it) } }
                    .focusGroup(),
            ) {
                Text("Canais", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${channels.size} nesta lista", color = p.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                TvLazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                        FocusTargetBox(c.id, listFocusTarget, listFocusNonce, { listFocusTarget = null }) { m ->
                            PanelItem(
                                title = "%03d  ${c.name}".format(i + 1),
                                subtitle = epgNow[c.id],
                                selected = i == index,
                                modifier = m,
                                onClick = {
                                    listVisible = false
                                    if (i != index) goTo(i)
                                },
                            )
                        }
                    }
                }
            }
        }

        // erro
        val err = error
        if (err != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(channel?.name ?: "Canal", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(err, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Tentar de novo", onClick = {
                            streamIndex = 0
                            reloadNonce++
                        }, icon = Icons.Filled.Refresh, primary = true, modifier = Modifier.focusRequester(errorFocus))
                        TvButton("Próximo canal", onClick = { zap(1) })
                        TvButton("Lista de canais", onClick = {
                            error = null
                            listVisible = true
                        }, icon = Icons.AutoMirrored.Filled.ViewList)
                    }
                }
            }
        }

        SidePanel(
            visible = optionsVisible,
            title = channel?.name ?: "Opções",
            subtitle = "Fonte, tela e favoritos",
            onDismiss = { optionsVisible = false },
        ) {
            TvLazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    PanelItem(
                        if (isFavorite) "Tirar dos favoritos" else "Adicionar aos favoritos",
                        leading = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        modifier = Modifier.focusRequester(optionsFirst),
                        onClick = {
                            val ch = channel ?: return@PanelItem
                            isFavorite = LiveTvFavoritesStore.toggle(context, ch.id)
                            toaster.show(if (isFavorite) "Adicionado aos favoritos" else "Removido dos favoritos")
                        },
                    )
                }
                if (streams.size > 1) {
                    item { OptionHeader("Fonte") }
                    itemsIndexed(streams) { i, s ->
                        PanelItem(
                            streamLabel(s, i),
                            selected = i == streamIndex,
                            leading = Icons.Filled.Dns,
                            onClick = {
                                streamIndex = i
                                reloadNonce++
                                optionsVisible = false
                            },
                        )
                    }
                }
                item { OptionHeader("Tela") }
                itemsIndexed(LiveAspect.entries) { _, a ->
                    PanelItem(a.label, selected = aspect == a, leading = Icons.Filled.AspectRatio, onClick = { aspect = a })
                }
            }
        }
    }
}

@Composable
private fun OptionHeader(text: String) {
    val p = LocalPalette.current
    Text(
        text.uppercase(),
        color = p.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun LiveClock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }
    Text(fmt.format(now), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}
