package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.TvSettingsStore
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.ui.components.LoadingBox
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.MessageBox
import com.streamflixvip.tv.ui.components.PosterCard
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUiState(
    val items: List<TmdbItem> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

class CategoryViewModel : ViewModel() {
    private val _state = MutableStateFlow(CategoryUiState())
    val state = _state.asStateFlow()
    private var spec: RowSpec? = null
    private var kids = false

    fun start(spec: RowSpec, kidsMode: Boolean) {
        if (this.spec?.key == spec.key && kids == kidsMode && _state.value.items.isNotEmpty()) return
        this.spec = spec
        kids = kidsMode
        _state.value = CategoryUiState()
        loadMore()
    }

    fun loadMore() {
        val s = spec ?: return
        val st = _state.value
        if (st.loading || st.endReached || st.page >= 15) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val next = st.page + 1
            val got = HomeTvViewModel.fetchRow(s, next).let { if (kids) Catalog.kidsSafe(it) else it }
            _state.update { cur ->
                val merged = (cur.items + got).distinctBy { "${it.id}_${it.resolvedMediaType}" }
                cur.copy(
                    items = merged,
                    page = next,
                    loading = false,
                    endReached = got.isEmpty() && next > 1,
                    error = if (merged.isEmpty()) "Nada encontrado nesta categoria agora." else null,
                )
            }
        }
    }

    fun retry() {
        _state.value = CategoryUiState()
        loadMore()
    }
}

/** "Ver tudo" de uma fileira: grade com paginacao infinita. */
@Composable
fun CategoryTvScreen(
    rowKey: String,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val p = LocalPalette.current
    val toaster = LocalToaster.current
    val settings by TvSettingsStore.state.collectAsState()
    val spec = remember(rowKey) { Catalog.spec(rowKey) }
    val vm: CategoryViewModel = viewModel(key = "cat_$rowKey")
    val state by vm.state.collectAsState()
    val gridState = rememberTvLazyGridState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val library = remember { com.streamflixvip.tv.data.LocalLibraryStore(context) }
    val firstFocus = remember { FocusRequester() }
    var restoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(spec, settings.kidsMode) { vm.start(spec, settings.kidsMode) }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty()) {
            focusTarget = restoreKey ?: state.items.first().let { "${it.id}_${it.resolvedMediaType}" }
            focusNonce++
        }
    }

    // Mais uma pagina so quando a ultima linha visivel chega perto do fim (1 pedido por pagina)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 14
        }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
    }
    LaunchedEffect(state.items.size, state.loading) {
        // pagina veio so com repetidos (ou poucos itens): ainda perto do fim -> tenta a proxima
        if (state.loading || state.items.isEmpty()) return@LaunchedEffect
        val info = gridState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (last >= info.totalItemsCount - 14) vm.loadMore()
    }

    Column(Modifier.fillMaxSize().background(p.bg).padding(start = 48.dp, top = 30.dp, end = 40.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(spec.title, color = p.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(14.dp))
            if (state.items.isNotEmpty()) {
                Text("${state.items.size} títulos · segure OK para salvar na lista", color = p.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            state.items.isEmpty() && state.error != null -> MessageBox(
                title = "Ops!",
                message = state.error,
                actionLabel = "Tentar de novo",
                actionFocus = firstFocus,
                onAction = { vm.retry() },
                modifier = Modifier.fillMaxSize(),
            )
            state.items.isEmpty() -> LoadingBox(Modifier.fillMaxSize(), "Carregando…")
            else -> TvLazyVerticalGrid(
                state = gridState,
                columns = TvGridCells.Fixed(7),
                contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.items, key = { _, it -> "${it.id}_${it.resolvedMediaType}" }) { _, item ->
                    val key = "${item.id}_${item.resolvedMediaType}"
                    FocusTargetBox(key, focusTarget, focusNonce, { focusTarget = null }) { m ->
                        Column {
                            PosterCard(
                                title = item.displayTitle,
                                posterPath = item.poster_path,
                                width = 200.dp,
                                modifier = m.fillMaxWidth(),
                                badge = item.displayRating?.let { "★ $it" },
                                onFocus = { restoreKey = key },
                                onLongClick = {
                                    val added = library.toggleFavorite(
                                        com.streamflixvip.tv.data.LocalFavorite(item.id, item.resolvedMediaType, item.displayTitle, item.poster_path),
                                    )
                                    toaster.show(if (added) "Adicionado à Minha lista" else "Removido da Minha lista")
                                },
                                onClick = { onItemClick(item.id, item.resolvedMediaType) },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(item.displayTitle, color = p.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

