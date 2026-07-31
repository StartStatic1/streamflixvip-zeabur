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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.LiveChannel

private val Accent = Color(0xFF6366F1)
private val CardBg = Color(0xFF16161F)
private val CardBorder = Color.White.copy(alpha = 0.06f)

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
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0B12), Color(0xFF12121C), Color(0xFF0B0B12)),
                ),
            ),
    ) {
        // Header
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "TV ao vivo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (state.sourcesUsed > 0 && !state.isLoading) {
                        Text(
                            "${list.size} canais · ${state.sourcesUsed} fontes",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                        )
                    }
                }
                IconButton(onClick = { gridMode = !gridMode }) {
                    Icon(
                        if (gridMode) Icons.Filled.ViewList else Icons.Filled.ViewModule,
                        contentDescription = if (gridMode) "Lista" else "Grade",
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Buscar em todos os canais…", color = Color.White.copy(alpha = 0.35f))
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.5f))
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
                    focusedBorderColor = Accent.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    cursorColor = Accent,
                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                ),
            )
        }

        // Categorias (ocultas durante busca para não confundir)
        if (state.searchQuery.isBlank() && state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { cat ->
                    val selected = cat.id == state.selectedCategoryId
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectCategory(cat.id) },
                        label = {
                            Text(
                                cat.name.removePrefix("Canais | ").removePrefix("Canais |"),
                                maxLines = 1,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.06f),
                            labelColor = Color.White.copy(alpha = 0.75f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.White.copy(alpha = 0.08f),
                            selectedBorderColor = Accent,
                            enabled = true,
                            selected = selected,
                        ),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            state.error != null && state.channels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = Color.White.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.load() }) {
                            Text("Tentar novamente")
                        }
                    }
                }
            }
            list.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.searchQuery.isNotBlank()) "Nenhum canal para \"${state.searchQuery}\""
                        else "Nenhum canal nesta categoria",
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }
            gridMode -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { channel ->
                        ChannelGridCard(channel = channel, onClick = { onChannelClick(channel) })
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { channel ->
                        ChannelListCard(channel = channel, onClick = { onChannelClick(channel) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelGridCard(channel: LiveChannel, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            channel.name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (channel.streams.size > 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${channel.streams.size}·src",
                color = Accent.copy(alpha = 0.8f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ChannelListCard(channel: LiveChannel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(24.dp))
            }
        }
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
                .size(36.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(20.dp))
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
                    .background(Accent.copy(alpha = 0.15f))
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
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
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
