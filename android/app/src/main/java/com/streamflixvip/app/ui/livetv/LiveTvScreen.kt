package com.streamflixvip.app.ui.livetv

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.LiveChannel

private val Accent = Color(0xFF2EE6D6)
private val AccentSoft = Color(0xFF0EA5E9)
private val CardBg = Color(0xFF14141C)
private val ScreenBg = Color(0xFF0A0A10)
private val SideBg = Color(0xFF0E0E16)
private val LogoPlate = Color(0xFFF3F4F6)

private val BadgeColors = listOf(
    Color(0xFF0EA5E9), Color(0xFF8B5CF6), Color(0xFF14B8A6),
    Color(0xFFF59E0B), Color(0xFF3B82F6), Color(0xFFEF4444),
)

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

private fun colorFor(name: String): Color {
    val i = (name.hashCode().and(0x7fffffff)) % BadgeColors.size
    return BadgeColors[i]
}

@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel = viewModel(),
    onChannelClick: (LiveChannel) -> Unit,
    onUpgradeClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val isVip by VipStatusHolder.isVip.collectAsState()

    if (!isVip) {
        LiveTvVipGate(onUpgradeClick = onUpgradeClick)
        return
    }

    val list = state.filteredChannels
    val selected = state.selectedChannel

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        // ── Player fixo no topo ──
        LiveInlinePlayer(
            channel = selected,
            isFavorite = selected?.id?.let { state.favoriteIds.contains(it) } == true,
            onToggleFavorite = { selected?.let { viewModel.toggleFavorite(it.id) } },
            onFullscreen = { selected?.let { onChannelClick(it) } },
        )

        // ── Busca ──
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearch,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (state.searchQuery.isEmpty()) {
                            Text("Buscar canais…", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
                        }
                        inner()
                    },
                )
            }
        }

        // ── Abas Canais / Favoritos ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabPill(
                label = "Canais",
                icon = { Icon(Icons.Filled.Tv, null, Modifier.size(16.dp), tint = it) },
                selected = state.tab == LiveTvTab.CHANNELS,
                onClick = { viewModel.setTab(LiveTvTab.CHANNELS) },
            )
            TabPill(
                label = "Favoritos",
                icon = { Icon(Icons.Filled.Favorite, null, Modifier.size(16.dp), tint = it) },
                selected = state.tab == LiveTvTab.FAVORITES,
                onClick = { viewModel.setTab(LiveTvTab.FAVORITES) },
            )
        }

        Spacer(Modifier.height(8.dp))

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            state.error != null && state.channels.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error ?: "", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = viewModel::load) { Text("Tentar de novo", color = Accent) }
                }
            }
            else -> {
                Row(Modifier.fillMaxSize()) {
                    // Categorias na esquerda (inclui Telecine, HBO, Premiere, Discovery)
                    if (state.tab == LiveTvTab.CHANNELS && state.searchQuery.isEmpty()) {
                        LazyColumn(
                            Modifier
                                .width(112.dp)
                                .fillMaxHeight()
                                .background(SideBg)
                                .padding(vertical = 4.dp),
                        ) {
                            items(state.categories, key = { it.id }) { cat ->
                                val sel = state.selectedCategoryId == cat.id
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectCategory(cat.id) }
                                        .background(if (sel) Accent.copy(alpha = 0.16f) else Color.Transparent)
                                        .padding(vertical = 11.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (sel) Accent else Color.Transparent),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        cat.name,
                                        color = if (sel) Accent else Color.White.copy(alpha = 0.55f),
                                        fontSize = 12.sp,
                                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                    if (list.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.tab == LiveTvTab.FAVORITES)
                                    "Nenhum favorito ainda.\nToque no coração no player."
                                else "Nenhum canal nesta categoria",
                                color = Color.White.copy(alpha = 0.45f),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            items(list, key = { it.id }) { channel ->
                                val isSel = channel.id == state.selectedChannelId
                                val isFav = state.favoriteIds.contains(channel.id)
                                ChannelRow(
                                    channel = channel,
                                    selected = isSel,
                                    isFavorite = isFav,
                                    onClick = { viewModel.selectChannel(channel) },
                                    onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                                    onOpenFullscreen = { onChannelClick(channel) },
                                )
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    icon: @Composable (Color) -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent
    val fg = if (selected) Accent else Color.White.copy(alpha = 0.45f)
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(fg)
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
    }
}

@Composable
private fun LiveInlinePlayer(
    channel: LiveChannel?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    val streams = channel?.streams.orEmpty()
    var streamIndex by remember(channel?.id) { mutableIntStateOf(0) }
    var loading by remember(channel?.id) { mutableStateOf(channel != null) }
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(channel?.id, streamIndex) {
        if (channel == null || streams.isEmpty()) {
            exoPlayer.stop()
            loading = false
            isPlaying = false
            return@LaunchedEffect
        }
        val url = streams.getOrNull(streamIndex)?.url ?: return@LaunchedEffect
        val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == url &&
            (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.isPlaying)
        ) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        isPlaying = false
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> loading = false
                    Player.STATE_BUFFERING -> {
                        if (!isPlaying && !exoPlayer.isPlaying) loading = true
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) loading = false
            }
            override fun onPlayerError(error: PlaybackException) {
                val next = streamIndex + 1
                if (next < streams.size) {
                    streamIndex = next
                    loading = true
                } else {
                    loading = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        if (channel != null && streams.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.LiveTv, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Selecione um canal", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            }
        }

        if (loading && !isPlaying && channel != null) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = Accent,
                strokeWidth = 2.dp,
            )
        }

        // Overlay inferior: nome + AO VIVO + ações
        if (channel != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Accent.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("AO VIVO", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        channel.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) Color(0xFFFF6B8A) else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onFullscreen, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Fullscreen, "Tela cheia", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    selected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenFullscreen: () -> Unit,
) {
    val border = if (selected) Accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f)
    val bg = if (selected) Accent.copy(alpha = 0.1f) else CardBg
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel, size = 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val nSources = channel.streams.size
            val hasManual = channel.streams.any { it.label.equals("Manual", ignoreCase = true) }
            when {
                selected -> Text("Tocando agora", color = Accent, fontSize = 11.sp)
                nSources > 1 -> Text(
                    buildString {
                        append(nSources)
                        append(" fontes")
                        if (hasManual) append(" · Manual")
                    },
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                )
                hasManual -> Text("Manual", color = Color(0xFF34D399), fontSize = 11.sp)
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                null,
                tint = if (isFavorite) Color(0xFFFF6B8A) else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (selected) {
            IconButton(onClick = onOpenFullscreen, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.PlayArrow, "Abrir", tint = Accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ChannelLogo(channel: LiveChannel, size: androidx.compose.ui.unit.Dp) {
    val logo = channel.logo
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(if (logo.isNullOrBlank()) colorFor(channel.name) else LogoPlate),
        contentAlignment = Alignment.Center,
    ) {
        if (!logo.isNullOrBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(initialsOf(channel.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LiveTvVipGate(onUpgradeClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("TV ao vivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Disponível para assinantes VIP.\nAssista canais ao vivo no celular.",
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(AccentSoft, Accent)))
                    .clickable(onClick = onUpgradeClick)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text("Assinar VIP", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
