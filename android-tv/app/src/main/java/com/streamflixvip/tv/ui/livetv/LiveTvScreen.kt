package com.streamflixvip.tv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.LiveCategory
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.network.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0B0B14)
private val Accent = Color(0xFF00E5FF)
private val AccentSoft = Color(0xFF22D3EE)
private val CardBg = Color(0xFF151522)
private val TextMuted = Color(0xFFA1A1B5)

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val selectedCategoryId: String = "all",
    val sourcesUsed: Int = 0,
    val error: String? = null,
) {
    val filteredChannels: List<LiveChannel>
        get() = when {
            selectedCategoryId == "all" -> channels.filter { it.categoryId != "000" }
            else -> channels.filter { it.categoryId == selectedCategoryId }
        }
}

class LiveTvViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { NetworkModule.liveTvApi.getLiveTv() }
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            categories = res.categories.ifEmpty { listOf(LiveCategory("all", "Todos")) },
                            channels = res.channels,
                            sourcesUsed = res.sourcesUsed,
                            error = if (res.channels.isEmpty()) "Nenhum canal disponivel." else null,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Erro ao carregar canais")
                    }
                }
        }
    }

    fun selectCategory(id: String) {
        _uiState.update { it.copy(selectedCategoryId = id) }
    }
}

@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel = viewModel(),
    onChannelClick: (LiveChannel) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val list = state.filteredChannels
    val firstChannelFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedCategoryId, list.size) {
        if (list.isNotEmpty()) {
            listState.scrollToItem(0)
            runCatching { firstChannelFocus.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Accent.copy(alpha = 0.25f),
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                        shape = RoundedCornerShape(12.dp),
                    ),
                ),
                modifier = Modifier.size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TV ao vivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                if (!state.isLoading) {
                    Text(
                        "${list.size} canais" +
                            if (state.sourcesUsed > 0) " · ${state.sourcesUsed} fontes" else "",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Categorias (D-pad horizontal)
        if (state.categories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.categories, key = { it.id }) { cat ->
                    val selected = state.selectedCategoryId == cat.id
                    val count = when (cat.id) {
                        "all" -> state.channels.count { it.categoryId != "000" }
                        else -> state.channels.count { it.categoryId == cat.id }
                    }
                    var focused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { viewModel.selectCategory(cat.id) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = when {
                                selected -> Accent.copy(alpha = 0.35f)
                                focused -> Color.White.copy(alpha = 0.14f)
                                else -> Color.White.copy(alpha = 0.06f)
                            },
                            focusedContainerColor = Accent.copy(alpha = 0.4f),
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        ),
                        modifier = Modifier.onFocusChanged { focused = it.isFocused },
                    ) {
                        Text(
                            if (count > 0) "${cat.name} · $count" else cat.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            state.error != null && list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "", color = TextMuted, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = { viewModel.load() },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Accent.copy(alpha = 0.3f),
                            focusedContainerColor = Accent.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            "Tentar de novo",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum canal nesta categoria", color = TextMuted)
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(list, key = { _, ch -> ch.id }) { index, ch ->
                        ChannelRow(
                            channel = ch,
                            modifier = if (index == 0) Modifier.focusRequester(firstChannelFocus) else Modifier,
                            onClick = { onChannelClick(ch) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(72.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        colors = CardDefaults.colors(
            containerColor = CardBg,
            focusedContainerColor = Color(0xFF1C1C2E),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                shape = RoundedCornerShape(14.dp),
            ),
        ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.LiveTv, null, tint = AccentSoft, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val n = channel.streams.size
                Text(
                    if (n > 1) "$n fontes · fallback automatico" else "Ao vivo",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
    }
}
