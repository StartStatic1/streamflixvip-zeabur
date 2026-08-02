package com.streamflixvip.app.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.LiveChannel

private val Accent = Color(0xFF818CF8)
private val AccentSoft = Color(0xFF6366F1)
private val CardBg = Color(0xFF14141C)
private val CardBorder = Color.White.copy(alpha = 0.07f)
private val LogoPlate = Color(0xFFF3F4F6)

private val AvatarPalette = listOf(
    Color(0xFF6366F1),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF14B8A6),
    Color(0xFFF59E0B),
    Color(0xFF3B82F6),
    Color(0xFFEF4444),
    Color(0xFF10B981),
)

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "TV"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

private fun colorFor(name: String): Color {
    val h = name.fold(0) { acc, c -> acc * 31 + c.code }
    return AvatarPalette[kotlin.math.abs(h) % AvatarPalette.size]
}

@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel = viewModel(),
    onChannelClick: (LiveChannel) -> Unit,
    onUpgradeClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val isVip by VipStatusHolder.isVip.collectAsState()
    var gridMode by remember { mutableStateOf(true) }

    if (!isVip) {
        LiveTvVipGate(onUpgradeClick = onUpgradeClick)
        return
    }

    val list = state.filteredChannels

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A10)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(AccentSoft.copy(alpha = 0.35f), Accent.copy(alpha = 0.15f)),
                            ),
                        )
                        .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "TV ao vivo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (!state.isLoading) {
                        Text(
                            if (state.sourcesUsed > 0) {
                                "${list.size} canais · ${state.sourcesUsed} fontes"
                            } else {
                                "${list.size} canais"
                            },
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                        )
                    }
                }
                IconButton(
                    onClick = { gridMode = !gridMode },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                ) {
                    Icon(
                        if (gridMode) Icons.Filled.ViewList else Icons.Filled.ViewModule,
                        contentDescription = if (gridMode) "Lista" else "Grade",
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Buscar em todos os canais…", color = Color.White.copy(alpha = 0.35f))
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.45f))
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Filled.Clear, null, tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    cursorColor = Accent,
                ),
            )
        }

        if (state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { cat ->
                    val selected = state.selectedCategoryId == cat.id && state.searchQuery.isEmpty()
                    val count = if (cat.id == "all") {
                        state.channels.size
                    } else {
                        state.channels.count { it.categoryId == cat.id }
                    }
                    Surface(
                        onClick = { viewModel.selectCategory(cat.id) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) AccentSoft else Color.White.copy(alpha = 0.06f),
                        border = if (selected) null else androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                        ),
                    ) {
                        Text(
                            if (count > 0) "${cat.name} · $count" else cat.name,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            state.error != null && list.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = viewModel::load) { Text("Tentar de novo") }
                    }
                }
            }
            list.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum canal encontrado", color = Color.White.copy(alpha = 0.5f))
                }
            }
            gridMode -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { ch ->
                        ChannelGridCard(ch) { onChannelClick(ch) }
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { ch ->
                        ChannelListCard(ch) { onChannelClick(ch) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(
    name: String,
    logo: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val initials = remember(name) { initialsOf(name) }
    val avatarColor = remember(name) { colorFor(name) }

    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LogoPlate)
            .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logo.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(logo)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = AccentSoft.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                error = {
                    InitialsBadge(initials, avatarColor)
                },
            )
        } else {
            InitialsBadge(initials, avatarColor)
        }
    }
}

@Composable
private fun InitialsBadge(initials: String, color: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun ChannelGridCard(channel: LiveChannel, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelLogo(
            name = channel.name,
            logo = channel.logo,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            channel.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (channel.streams.size > 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${channel.streams.size} fontes",
                color = Accent.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ChannelListCard(channel: LiveChannel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(
            name = channel.name,
            logo = channel.logo,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val n = channel.streams.size
            Text(
                if (n > 1) "$n fontes · fallback automático" else "Ao vivo",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AccentSoft.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun LiveTvVipGate(onUpgradeClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E), Color(0xFF0B0B12)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(AccentSoft.copy(alpha = 0.15f))
                    .border(1.dp, Accent.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, null, tint = Accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "TV ao vivo",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Exclusivo para assinantes VIP",
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Canais em HD, múltiplas fontes e troca automática se uma falhar.",
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentSoft),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Star, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Assinar VIP", fontWeight = FontWeight.Bold)
            }
        }
    }
}
