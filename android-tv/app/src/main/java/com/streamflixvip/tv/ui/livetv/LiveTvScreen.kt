package com.streamflixvip.tv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
private val SideBg = Color(0xFF10101C)

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val selectedCategoryId: String = "all",
    val searchQuery: String = "",
    val sourcesUsed: Int = 0,
    val error: String? = null,
    val favoriteIds: Set<String> = emptySet(),
    val showFavoritesOnly: Boolean = false,
) {
    val filteredChannels: List<LiveChannel>
        get() {
            var list = when {
                selectedCategoryId == "all" -> channels.filter { it.categoryId != "000" }
                else -> channels.filter { it.categoryId == selectedCategoryId }
            }
            if (showFavoritesOnly) {
                list = list.filter { favoriteIds.contains(it.id) }
            }
            val q = searchQuery.trim()
            if (q.isNotEmpty()) {
                list = list.filter { it.name.contains(q, ignoreCase = true) }
            }
            if (!showFavoritesOnly && favoriteIds.isNotEmpty()) {
                list = list.sortedByDescending { favoriteIds.contains(it.id) }
            }
            return list
        }
}

class LiveTvViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    fun syncFavorites(ids: Set<String>) {
        _uiState.update { it.copy(favoriteIds = ids) }
    }
    fun toggleShowFavorites() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }
    fun setFavoriteIds(ids: Set<String>) {
        _uiState.update { it.copy(favoriteIds = ids) }
    }

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { NetworkModule.liveTvApi.getLiveTv() }
                .onSuccess { res ->
                    fun isAdultCat(id: String?, name: String?): Boolean {
                        val n = (name ?: "").lowercase()
                        if (id == "000" || id == "00") return true
                        val keys = listOf("adult", "xxx", "porn", "erotic", "onlyfans", "+18", "18+", "adulto", "sexy")
                        return keys.any { n.contains(it) }
                    }
                    val cats = res.categories
                        .filter { !isAdultCat(it.id, it.name) }
                        .ifEmpty { listOf(LiveCategory("all", "Todos")) }
                    val chans = res.channels.filter { !isAdultCat(it.categoryId, it.name) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            categories = cats,
                            channels = chans,
                            sourcesUsed = res.sourcesUsed,
                            error = if (chans.isEmpty()) "Nenhum canal disponivel." else null,
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

    fun setSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}

@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel = viewModel(),
    onChannelClick: (channel: LiveChannel, filteredList: List<LiveChannel>, indexInList: Int) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.syncFavorites(LiveTvFavoritesStore.getIds(context))
    }
    val list = state.filteredChannels
    val firstChannelFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val catListState = rememberLazyListState()

    LaunchedEffect(state.selectedCategoryId, state.searchQuery, list.size) {
        if (list.isNotEmpty()) {
            listState.scrollToItem(0)
            runCatching { firstChannelFocus.requestFocus() }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        // Sidebar categorias (vertical, padrao IPTV TV)
        Column(
            Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(SideBg)
                .padding(vertical = 16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onBack,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Accent.copy(alpha = 0.25f),
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                            shape = RoundedCornerShape(10.dp),
                        ),
                    ),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text("TV ao vivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { viewModel.toggleShowFavorites() },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (state.showFavoritesOnly) Accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                    focusedContainerColor = Accent.copy(alpha = 0.35f),
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                        shape = RoundedCornerShape(10.dp),
                    ),
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(44.dp),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.showFavoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = if (state.showFavoritesOnly) Accent else Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Favoritos", color = Color.White, fontSize = 14.sp, fontWeight = if (state.showFavoritesOnly) FontWeight.Bold else FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    if (state.favoriteIds.isNotEmpty()) {
                        Text("${state.favoriteIds.size}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.categories.isNotEmpty()) {
                LazyColumn(
                    state = catListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(state.categories, key = { it.id }) { cat ->
                        val selected = state.selectedCategoryId == cat.id
                        val count = when (cat.id) {
                            "all" -> state.channels.count { it.categoryId != "000" }
                            else -> state.channels.count { it.categoryId == cat.id }
                        }
                        Surface(
                            onClick = { viewModel.selectCategory(cat.id) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = when {
                                    selected -> Accent.copy(alpha = 0.28f)
                                    else -> Color.Transparent
                                },
                                focusedContainerColor = Accent.copy(alpha = 0.4f),
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                                    shape = RoundedCornerShape(10.dp),
                                ),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    cat.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (count > 0) {
                                    Text(
                                        "$count",
                                        color = if (selected) Accent else TextMuted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Conteudo principal: busca + lista
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.setSearch(it) },
                    onClear = { viewModel.setSearch("") },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocus),
                )
                Spacer(Modifier.width(16.dp))
                if (!state.isLoading) {
                    Text(
                        "${list.size} canais" +
                            if (state.sourcesUsed > 0) " · ${state.sourcesUsed} fontes" else "",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                state.error != null && list.isEmpty() && state.searchQuery.isBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.searchQuery.isNotBlank()) "Nenhum canal para \"${state.searchQuery}\""
                        else "Nenhum canal nesta categoria",
                        color = TextMuted,
                    )
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
                                isFavorite = state.favoriteIds.contains(ch.id),
                                modifier = if (index == 0) Modifier.focusRequester(firstChannelFocus) else Modifier,
                                onClick = { onChannelClick(ch, list, index) },
                                onToggleFavorite = {
                                    LiveTvFavoritesStore.toggle(context, ch.id)
                                    viewModel.setFavoriteIds(LiveTvFavoritesStore.getIds(context))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Accent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp)
            .onFocusChanged { focused = it.isFocused },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = if (focused) Accent else TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Pesquisar canal…", color = TextMuted, fontSize = 15.sp)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Surface(
                onClick = onClear,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Accent.copy(alpha = 0.25f),
                ),
                modifier = Modifier.size(32.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Clear, "Limpar", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
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
            Surface(
                onClick = onToggleFavorite,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Accent.copy(alpha = 0.2f)),
                modifier = Modifier.size(40.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorito", tint = if (isFavorite) Accent else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
    }
}
