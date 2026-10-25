package com.streamflixvip.tv.ui.livetv

import android.app.Application
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.TvSettingsStore
import com.streamflixvip.tv.network.LiveCategory
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.ui.components.FadeImage
import com.streamflixvip.tv.ui.components.LoadingBox
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.MessageBox
import com.streamflixvip.tv.ui.components.MetaTag
import com.streamflixvip.tv.ui.home.FocusTargetBox
import com.streamflixvip.tv.ui.shell.FocusSignal
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveTvScreen(
    focusSignal: FocusSignal,
    kidsMode: Boolean,
    onChannelClick: (channel: LiveChannel, list: List<LiveChannel>, index: Int, epgNow: Map<String, String>, epgNext: Map<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val toaster = LocalToaster.current
    val settings by TvSettingsStore.state.collectAsState()
    val vm: LiveTvViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))
    val state by vm.uiState.collectAsState()

    LaunchedEffect(kidsMode) { vm.start(kidsMode) }

    val channels = state.visibleChannels
    var focusedChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var restoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusNonce by remember { mutableIntStateOf(0) }
    var handledToken by remember { mutableIntStateOf(0) }
    val retryFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberTvLazyListState()
    // ao abrir um canal, solta a previa antes: TV box fraca tem 1 decodificador so
    var openingChannel by remember { mutableStateOf(false) }
    val shellLifecycle = LocalLifecycleOwner.current
    DisposableEffect(shellLifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_START) openingChannel = false }
        shellLifecycle.lifecycle.addObserver(obs)
        onDispose { shellLifecycle.lifecycle.removeObserver(obs) }
    }

    // categorias: virtuais (favoritos, recentes, todos) + as da API
    val cats: List<LiveCategory> = remember(state.categories, state.favoriteIds, state.recentIds, state.channels) {
        buildList {
            if (state.countFor(CAT_FAV) > 0) add(LiveCategory(CAT_FAV, "Favoritos"))
            if (state.countFor(CAT_RECENT) > 0) add(LiveCategory(CAT_RECENT, "Recentes"))
            add(LiveCategory(CAT_ALL, "Todos os canais"))
            addAll(state.categories)
        }
    }

    // foco pedido pelo menu lateral / ao voltar do player
    val firstKey = channels.firstOrNull()?.let { "ch:${it.id}" }
    LaunchedEffect(focusSignal.token, firstKey, state.error) {
        if (handledToken == focusSignal.token) return@LaunchedEffect
        if (state.error != null && channels.isEmpty()) {
            delay(60)
            if (runCatching { retryFocus.requestFocus() }.isSuccess) handledToken = focusSignal.token
            return@LaunchedEffect
        }
        if (firstKey == null) return@LaunchedEffect
        handledToken = focusSignal.token
        vm.refreshRecents()
        val k = restoreKey?.takeIf { key -> channels.any { "ch:${it.id}" == key } } ?: firstKey
        val idx = channels.indexOfFirst { "ch:${it.id}" == k }.coerceAtLeast(0)
        runCatching { listState.scrollToItem(idx) }
        focusTarget = k
        focusNonce++
    }

    var lastFilter by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedCategoryId, state.query) {
        val f = state.selectedCategoryId + "|" + state.query
        if (lastFilter != null && lastFilter != f) runCatching { listState.scrollToItem(0) }
        lastFilter = f
    }

    Column(Modifier.fillMaxSize().padding(start = 28.dp, top = 22.dp, end = 28.dp, bottom = 16.dp)) {
        // ── topo ─────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LiveTv, null, tint = p.live, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text("TV ao vivo", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            if (state.channels.isNotEmpty()) {
                Text("${state.channels.size} canais", color = p.textMuted, fontSize = 13.sp)
            }
            if (kidsMode) {
                Spacer(Modifier.width(10.dp))
                MetaTag("INFANTIL", highlight = true)
            }
            Spacer(Modifier.weight(1f))
            ChannelSearchField(
                query = state.query,
                onQuery = vm::setQuery,
                modifier = Modifier.width(300.dp).padding(end = 70.dp).focusRequester(searchFocus),
            )
        }
        Spacer(Modifier.height(14.dp))

        when {
            state.isLoading && state.channels.isEmpty() -> LoadingBox(Modifier.fillMaxSize(), "Carregando canais…")
            state.channels.isEmpty() -> MessageBox(
                title = "Sem canais",
                message = state.error,
                actionLabel = "Tentar de novo",
                actionFocus = retryFocus,
                onAction = { vm.load() },
                modifier = Modifier.fillMaxSize(),
            )
            else -> Row(Modifier.fillMaxSize()) {
                // categorias
                TvLazyColumn(
                    modifier = Modifier.width(210.dp).fillMaxHeight().focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(cats, key = { _, c -> c.id }) { _, c ->
                        CategoryItem(
                            category = c,
                            count = state.countFor(c.id),
                            selected = c.id == state.selectedCategoryId,
                            onSelect = {
                                if (c.id != state.selectedCategoryId) {
                                    vm.selectCategory(c.id)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))

                // canais
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (channels.isEmpty()) {
                        Text(
                            if (state.query.isNotBlank()) "Nenhum canal com “${state.query}”." else "Nenhum canal nesta categoria.",
                            color = p.textMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        TvLazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                        ) {
                            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                                val key = "ch:${ch.id}"
                                FocusTargetBox(key, focusTarget, focusNonce, { focusTarget = null }) { m ->
                                    ChannelRow(
                                        number = index + 1,
                                        channel = ch,
                                        now = state.epgNow[ch.id],
                                        favorite = ch.id in state.favoriteIds,
                                        modifier = m,
                                        onFocus = {
                                            focusedChannel = ch
                                            restoreKey = key
                                        },
                                        onClick = {
                                            openingChannel = true
                                            onChannelClick(ch, channels, index, state.epgNow, state.epgNext)
                                        },
                                        onLongClick = {
                                            val fav = vm.toggleFavorite(ch)
                                            toaster.show(if (fav) "${ch.name} nos favoritos" else "${ch.name} saiu dos favoritos")
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))

                // previa
                PreviewPane(
                    channel = focusedChannel?.takeIf { f -> channels.any { it.id == f.id } } ?: channels.firstOrNull(),
                    now = focusedChannel?.let { state.epgNow[it.id] },
                    next = focusedChannel?.let { state.epgNext[it.id] },
                    livePreview = settings.livePreview && !openingChannel,
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(category: LiveCategory, count: Int, selected: Boolean, onSelect: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    // escolhe a categoria ao parar o foco nela (sem precisar apertar OK)
    LaunchedEffect(focused) {
        if (focused && !selected) {
            delay(350)
            onSelect()
        }
    }
    val icon = when (category.id) {
        CAT_FAV -> Icons.Filled.Star
        CAT_RECENT -> Icons.Filled.History
        CAT_ALL -> Icons.AutoMirrored.Filled.ViewList
        else -> null
    }
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) p.accent.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) p.accent else p.text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                category.name,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("$count", fontSize = 11.sp, color = if (focused) Color.Black.copy(alpha = 0.6f) else p.textDim)
        }
    }
}

@Composable
private fun ChannelRow(
    number: Int,
    channel: LiveChannel,
    now: String?,
    favorite: Boolean,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().height(62.dp).onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocus()
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = p.surface.copy(alpha = 0.7f),
            contentColor = p.text,
            focusedContainerColor = p.surfaceHi,
            focusedContentColor = p.text,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, p.accent), shape = RoundedCornerShape(12.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%03d".format(number),
                color = if (focused) p.accent else p.textDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(38.dp),
            )
            Box(
                Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    FadeImage(channel.logo, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                } else {
                    Icon(Icons.Filled.LiveTv, null, tint = p.textDim, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!now.isNullOrBlank()) {
                    Text(now, fontSize = 12.sp, color = p.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (favorite) {
                Icon(Icons.Filled.Star, null, tint = p.star, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPane(
    channel: LiveChannel?,
    now: String?,
    next: String?,
    livePreview: Boolean,
    modifier: Modifier,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewReady by remember { mutableStateOf(false) }
    val previewForeground = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    var previewNonce by remember { mutableIntStateOf(0) }
    val exo = remember(livePreview) {
        if (!livePreview) null else ExoPlayer.Builder(context).setLoadControl(liveLoadControl()).build().apply {
            volume = 0f
            playWhenReady = true
        }
    }
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) previewReady = true
            }

            override fun onPlayerError(error: PlaybackException) {
                previewReady = false
            }
        }
        exo?.addListener(listener)
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> {
                    previewForeground.set(false)
                    previewReady = false
                    exo?.playWhenReady = false
                    exo?.stop()
                }
                // de volta: abre a previa de novo
                Lifecycle.Event.ON_START -> if (!previewForeground.getAndSet(true)) previewNonce++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo?.removeListener(listener)
            exo?.release()
        }
    }
    // espera o foco parar no canal antes de abrir a previa (economiza rede)
    LaunchedEffect(channel?.id, exo, previewNonce) {
        val player = exo ?: return@LaunchedEffect
        previewReady = false
        player.stop()
        val stream = channel?.let { orderStreams(it.streams).firstOrNull() } ?: return@LaunchedEffect
        delay(900)
        if (!previewForeground.get()) return@LaunchedEffect // HOME apertado no meio: nao abre
        runCatching {
            player.setMediaSource(buildLiveMediaSource(stream.url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(p.surface)
                .border(1.dp, p.glassBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel != null && !channel.logo.isNullOrBlank()) {
                FadeImage(channel.logo, Modifier.fillMaxSize().padding(28.dp), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Filled.LiveTv, null, tint = p.textDim, modifier = Modifier.size(40.dp))
            }
            if (exo != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exo
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isFocusable = false
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize().then(if (previewReady) Modifier else Modifier.size(1.dp)),
                )
            }
            if (exo != null) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(p.live)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (previewReady) "● AO VIVO" else "PRÉVIA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (channel != null) {
            Text(channel.name, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            EpgLine("AGORA", now ?: "Programação indisponível", highlight = true)
            if (!next.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                EpgLine("A SEGUIR", next, highlight = false)
            }
            Spacer(Modifier.height(12.dp))
            val qualities = channel.streams.mapNotNull { it.quality?.uppercase()?.takeIf { q -> q.isNotBlank() } }.distinct()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                qualities.take(4).forEach { MetaTag(it) }
                if (channel.streams.size > 1) MetaTag("${channel.streams.size} fontes")
            }
            Spacer(Modifier.weight(1f))
            Text("OK assistir  ·  segure OK para favoritar", color = p.textDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EpgLine(label: String, text: String, highlight: Boolean) {
    val p = LocalPalette.current
    Column {
        Text(label, color = if (highlight) p.live else p.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(text, color = if (highlight) p.text else p.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Campo de busca: so abre o teclado quando o usuario aperta OK. */
@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val boxFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) {
            delay(30)
            runCatching { fieldFocus.requestFocus() }
        }
    }
    Surface(
        onClick = { editing = true },
        modifier = modifier.focusRequester(boxFocus).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = p.glass,
            contentColor = p.text,
            focusedContainerColor = p.surfaceHi,
            focusedContentColor = p.text,
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, p.glassBorder), shape = RoundedCornerShape(50)),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, p.accent), shape = RoundedCornerShape(50)),
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = p.textMuted)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty() && !editing) Text("Buscar canal", color = p.textMuted, fontSize = 13.sp)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        editing = false
                        runCatching { boxFocus.requestFocus() }
                    }, onDone = {
                        editing = false
                        runCatching { boxFocus.requestFocus() }
                    }),
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing) editing = false },
                )
            }
            if (query.isNotEmpty() && !editing) {
                Icon(Icons.Filled.Close, null, Modifier.size(16.dp), tint = p.textMuted)
            }
        }
    }
}
