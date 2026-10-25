package com.streamflixvip.tv.ui.explore

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Text
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.ui.components.LoadingBox
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.MessageBox
import com.streamflixvip.tv.ui.components.PosterCard
import com.streamflixvip.tv.ui.components.TvChip
import com.streamflixvip.tv.ui.home.Catalog
import com.streamflixvip.tv.ui.shell.FocusSignal
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// ---------------------------------------------------------------------------
// Explorar: filmes e series por genero, ano/decada e ordem (TMDB /discover).
// ---------------------------------------------------------------------------

internal data class Genre(val id: Int?, val name: String)

internal val MOVIE_GENRES = listOf(
    Genre(null, "Todos"), Genre(28, "Ação"), Genre(12, "Aventura"), Genre(16, "Animação"), Genre(35, "Comédia"),
    Genre(80, "Crime"), Genre(99, "Documentário"), Genre(18, "Drama"), Genre(10751, "Família"), Genre(14, "Fantasia"),
    Genre(27, "Terror"), Genre(53, "Suspense"), Genre(9648, "Mistério"), Genre(10749, "Romance"),
    Genre(878, "Ficção científica"), Genre(36, "História"), Genre(10752, "Guerra"), Genre(37, "Faroeste"),
    Genre(10402, "Música"),
)

internal val TV_GENRES = listOf(
    Genre(null, "Todos"), Genre(10759, "Ação e aventura"), Genre(16, "Animação"), Genre(35, "Comédia"),
    Genre(80, "Crime"), Genre(99, "Documentário"), Genre(18, "Drama"), Genre(10751, "Família"),
    Genre(10762, "Infantil"), Genre(9648, "Mistério"), Genre(10764, "Reality"), Genre(10765, "Ficção e fantasia"),
    Genre(10766, "Novela"), Genre(10768, "Guerra e política"), Genre(37, "Faroeste"),
)

/** Ano exato ou decada (from..to). */
internal data class YearFilter(val label: String, val from: Int?, val to: Int?)

internal fun yearFilters(nowYear: Int = Calendar.getInstance().get(Calendar.YEAR)): List<YearFilter> = buildList {
    fun decadeLabel(d: Int) = if (d >= 2000) "${d}s" else "Anos ${d % 100}"
    add(YearFilter("Todos", null, null))
    for (y in nowYear downTo nowYear - 4) add(YearFilter("$y", y, y))
    val decade = (nowYear / 10) * 10
    add(YearFilter(decadeLabel(decade), decade, nowYear))
    var d = decade - 10
    while (d >= 1970) {
        add(YearFilter(decadeLabel(d), d, d + 9))
        d -= 10
    }
    add(YearFilter("Antigos", null, 1969))
}

internal enum class SortOption(val label: String) {
    POPULAR("Populares"),
    RATED("Mais bem avaliados"),
    NEWEST("Mais recentes"),
    VOTES("Mais votados"),
    AZ("A–Z"),
}

internal data class ExploreFilters(
    val tv: Boolean = false,
    val genre: Int? = null,
    val year: YearFilter = YearFilter("Todos", null, null),
    val sort: SortOption = SortOption.POPULAR,
)

/** Parametros do TMDB /discover para os filtros escolhidos. */
internal fun discoverParams(f: ExploreFilters, page: Int, today: String): Pair<String, Map<String, String>> {
    val m = linkedMapOf("include_adult" to "false")
    if (f.genre != null) m["with_genres"] = f.genre.toString()
    val dateField = if (f.tv) "first_air_date" else "primary_release_date"
    val (from, to) = f.year.from to f.year.to
    if (from != null && from == to) {
        m[if (f.tv) "first_air_date_year" else "primary_release_year"] = from.toString()
    } else {
        if (from != null) m["$dateField.gte"] = "$from-01-01"
        if (to != null) m["$dateField.lte"] = "$to-12-31"
    }
    when (f.sort) {
        SortOption.POPULAR -> m["sort_by"] = "popularity.desc"
        SortOption.RATED -> { m["sort_by"] = "vote_average.desc"; m["vote_count.gte"] = if (f.tv) "150" else "300" }
        SortOption.NEWEST -> {
            m["sort_by"] = "$dateField.desc"
            // so o que ja estreou (e com alguns votos, para nao vir lixo)
            if (!m.containsKey("$dateField.lte") && to == null) m["$dateField.lte"] = today
            m["vote_count.gte"] = "10"
        }
        SortOption.VOTES -> m["sort_by"] = "vote_count.desc"
        SortOption.AZ -> { m["sort_by"] = if (f.tv) "name.asc" else "title.asc"; m["vote_count.gte"] = "50" }
    }
    if (page > 1) m["page"] = page.toString()
    return (if (f.tv) "/discover/tv" else "/discover/movie") to m
}

data class ExploreUiState(
    val items: List<TmdbItem> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val total: Int = 0,
)

class ExploreViewModel : ViewModel() {
    private val _filters = MutableStateFlow(ExploreFilters())
    internal val filters = _filters.asStateFlow()
    private val _state = MutableStateFlow(ExploreUiState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var kids = false
    private var started = false

    fun start(kidsMode: Boolean) {
        if (started && kids == kidsMode) return
        started = true
        kids = kidsMode
        reload()
    }

    internal fun update(change: (ExploreFilters) -> ExploreFilters) {
        val next = change(_filters.value)
        if (next == _filters.value) return
        _filters.value = next
        reload()
    }

    private fun reload() {
        job?.cancel()
        _state.value = ExploreUiState(loading = false)
        loadMore()
    }

    fun loadMore() {
        val st = _state.value
        if (st.loading || st.endReached || st.page >= 25) return
        val f = _filters.value
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            // segura um pouco: varios cliques seguidos no controle viram 1 pedido
            if (st.page == 0) delay(250)
            val next = st.page + 1
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val (path, params) = discoverParams(f, next, today)
            val res = runCatching { NetworkModule.tmdbApi.requestMap(path, params) }
            val got = res.getOrNull()?.results.orEmpty()
                .map { it.copy(media_type = if (f.tv) "tv" else "movie") }
                .filter { !it.poster_path.isNullOrBlank() }
                .let { if (kids) Catalog.kidsSafe(it) else it }
            val totalPages = res.getOrNull()?.total_pages ?: 0
            _state.update { cur ->
                val merged = (cur.items + got).distinctBy { "${it.id}_${it.resolvedMediaType}" }
                cur.copy(
                    items = merged,
                    page = next,
                    loading = false,
                    endReached = (res.isSuccess && (got.isEmpty() || next >= totalPages)),
                    total = res.getOrNull()?.total_results ?: cur.total,
                    error = when {
                        merged.isNotEmpty() -> null
                        res.isFailure -> "Não foi possível carregar. Verifique sua internet."
                        else -> "Nada encontrado com esses filtros."
                    },
                )
            }
        }
    }

    fun retry() = reload()
}

/** Tela "Explorar": chips de tipo, genero, ano e ordem + grade com mais paginas. */
@Composable
fun ExploreTvScreen(
    focusSignal: FocusSignal,
    kidsMode: Boolean,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val p = LocalPalette.current
    val toaster = LocalToaster.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val library = remember { com.streamflixvip.tv.data.LocalLibraryStore(context) }
    val vm: ExploreViewModel = viewModel(key = "explore")
    val state by vm.state.collectAsState()
    val f by vm.filters.collectAsState()
    val gridState = rememberTvLazyGridState()
    val firstChip = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var handledToken by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val years = remember { yearFilters() }
    val genres = if (f.tv) TV_GENRES else MOVIE_GENRES

    LaunchedEffect(kidsMode) { vm.start(kidsMode) }
    LaunchedEffect(focusSignal.token) {
        if (handledToken == focusSignal.token) return@LaunchedEffect
        delay(60)
        if (runCatching { firstChip.requestFocus() }.isSuccess) handledToken = focusSignal.token
    }
    // filtro mudou: grade volta ao topo
    LaunchedEffect(f) { runCatching { gridState.scrollToItem(0) } }

    // mais uma pagina quando a grade chega perto do fim
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 14
        }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
    }
    LaunchedEffect(state.items.size, state.loading) {
        if (state.loading || state.items.isEmpty()) return@LaunchedEffect
        val info = gridState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (last >= info.totalItemsCount - 14) vm.loadMore()
    }

    Column(Modifier.fillMaxSize().background(p.bg).padding(start = 40.dp, top = 24.dp, end = 32.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Explorar", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(14.dp))
            val resume = buildString {
                append(if (f.tv) "Séries" else "Filmes")
                genres.firstOrNull { it.id == f.genre }?.takeIf { it.id != null }?.let { append(" · ${it.name}") }
                if (f.year.from != null || f.year.to != null) append(" · ${f.year.label}")
                append(" · ${f.sort.label}")
                if (state.total > 0) append(" · ${"%,d".format(state.total).replace(',', '.')} títulos")
            }
            Text(resume, color = p.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))

        FilterRow("Tipo") {
            item {
                TvChip("Filmes", selected = !f.tv, onClick = { vm.update { it.copy(tv = false, genre = null) } }, modifier = Modifier.focusRequester(firstChip))
            }
            item { TvChip("Séries", selected = f.tv, onClick = { vm.update { it.copy(tv = true, genre = null) } }) }
            item { Spacer(Modifier.width(20.dp)) }
            itemsIndexed(SortOption.entries.toList()) { _, s ->
                TvChip(s.label, selected = f.sort == s, onClick = { vm.update { it.copy(sort = s) } })
            }
        }
        FilterRow("Gênero") {
            itemsIndexed(genres, key = { _, g -> "g${g.id}" }) { _, g ->
                TvChip(g.name, selected = f.genre == g.id, onClick = { vm.update { it.copy(genre = g.id) } })
            }
        }
        FilterRow("Ano") {
            itemsIndexed(years, key = { _, y -> "y${y.label}" }) { _, y ->
                TvChip(y.label, selected = f.year == y, onClick = { vm.update { it.copy(year = y) } })
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.items.isEmpty() && state.error != null && !state.loading -> MessageBox(
                title = "Ops!",
                message = state.error,
                actionLabel = "Tentar de novo",
                actionFocus = retryFocus,
                onAction = { vm.retry() },
                modifier = Modifier.fillMaxSize(),
            )
            state.items.isEmpty() -> LoadingBox(Modifier.fillMaxSize(), "Carregando…")
            else -> TvLazyVerticalGrid(
                state = gridState,
                columns = TvGridCells.Fixed(7),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { "${it.id}_${it.resolvedMediaType}" }) { item ->
                    Column {
                        PosterCard(
                            title = item.displayTitle,
                            posterPath = item.poster_path,
                            width = 200.dp,
                            modifier = Modifier.fillMaxWidth(),
                            badge = item.displayRating?.let { "★ $it" },
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

@Composable
private fun FilterRow(label: String, content: androidx.tv.foundation.lazy.list.TvLazyListScope.() -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.textDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            content = content,
        )
    }
}
