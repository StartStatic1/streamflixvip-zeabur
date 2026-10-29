package com.streamflixvip.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.RecentStore
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.ui.components.LoadingBox
import com.streamflixvip.tv.ui.components.PosterCard
import com.streamflixvip.tv.ui.components.TvChip
import com.streamflixvip.tv.ui.home.Catalog
import com.streamflixvip.tv.ui.shell.FocusSignal
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

private val KEYS = listOf(
    "a", "b", "c", "d", "e", "f",
    "g", "h", "i", "j", "k", "l",
    "m", "n", "o", "p", "q", "r",
    "s", "t", "u", "v", "w", "x",
    "y", "z", "1", "2", "3", "4",
    "5", "6", "7", "8", "9", "0",
)

private val GENRES = listOf(
    0 to "Em alta", 28 to "Ação", 35 to "Comédia", 27 to "Terror", 18 to "Drama",
    10749 to "Romance", 878 to "Ficção", 16 to "Animação", 80 to "Crime", 99 to "Documentário",
)

private enum class SearchType(val label: String) { ALL("Tudo"), MOVIE("Filmes"), TV("Séries") }

/**
 * Busca com teclado na tela (funciona em qualquer controle, sem abrir o teclado
 * do Android) + teclado fisico/air mouse. Resultados aparecem enquanto digita.
 */
@Composable
fun SearchTvScreen(
    focusSignal: FocusSignal,
    kidsMode: Boolean,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val recentStore = remember { RecentStore(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(SearchType.ALL.name) }
    val type = SearchType.valueOf(typeName)
    var results by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var trending by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(recentStore.searches()) }
    var genreId by rememberSaveable { mutableIntStateOf(0) }
    val firstKey = remember { FocusRequester() }
    var handledToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusSignal.token) {
        if (handledToken == focusSignal.token) return@LaunchedEffect
        delay(60)
        if (runCatching { firstKey.requestFocus() }.isSuccess) handledToken = focusSignal.token
    }

    LaunchedEffect(kidsMode, genreId) {
        trending = runCatching {
            val path = if (kidsMode || genreId != 0) "/discover/movie" else "/trending/all/day"
            val params = when {
                kidsMode -> mapOf("with_genres" to "16", "sort_by" to "popularity.desc")
                genreId != 0 -> mapOf("with_genres" to genreId.toString(), "sort_by" to "popularity.desc", "include_adult" to "false")
                else -> emptyMap()
            }
            NetworkModule.tmdbApi.requestMap(path, params).results.orEmpty()
                .filter { it.media_type == null || it.media_type == "movie" || it.media_type == "tv" }
                .map { if ((kidsMode || genreId != 0) && it.media_type == null) it.copy(media_type = "movie") else it }
                // TMDB as vezes repete o mesmo titulo: chave repetida derruba a grade (crash)
                .distinctBy { "${it.resolvedMediaType}_${it.id}" }
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(query, type, kidsMode) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList(); loading = false; failed = false
            return@LaunchedEffect
        }
        loading = true
        delay(380)
        com.streamflixvip.tv.util.CrashLog.action("busca \"${q.take(30)}\" ${type.name}")
        val path = when (type) {
            SearchType.ALL -> "/search/multi"
            SearchType.MOVIE -> "/search/movie"
            SearchType.TV -> "/search/tv"
        }
        val res = runCatching {
            NetworkModule.tmdbApi.requestMap(path, mapOf("query" to q, "include_adult" to "false")).results.orEmpty()
        }
        failed = res.isFailure
        results = res.getOrDefault(emptyList())
            .filter { it.media_type == null || it.media_type == "movie" || it.media_type == "tv" }
            .map {
                if (it.media_type == null) it.copy(media_type = if (type == SearchType.TV) "tv" else "movie") else it
            }
            .filter { !it.poster_path.isNullOrBlank() }
            .distinctBy { "${it.resolvedMediaType}_${it.id}" }
            .let { list -> if (kidsMode) Catalog.kidsSafe(list).filter { it.isKidsFriendly } else list }
            .sortedByDescending { it.popularity ?: 0.0 }
        loading = false
    }

    fun type(ch: String) {
        if (query.length < 40) query += ch
    }

    Row(
        Modifier.fillMaxSize().padding(start = 28.dp, top = 24.dp, end = 24.dp)
            .onPreviewKeyEvent { ev ->
                // teclado fisico / air mouse: letras e numeros digitam direto
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val cp = ev.utf16CodePoint
                if (cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code || cp in '0'.code..'9'.code) {
                    type(cp.toChar().lowercase()); true
                } else if (ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                    query = query.dropLast(1); true
                } else if (cp == ' '.code) {
                    type(" "); true
                } else false
            },
    ) {
        // ── Teclado ─────────────────────────────────────────────────────
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(p.surface).border(1.5.dp, p.accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = p.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (query.isEmpty()) "Digite o nome…" else query + "▏",
                    color = if (query.isEmpty()) p.textDim else p.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            KEYS.chunked(6).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    row.forEachIndexed { i, k ->
                        KeyButton(
                            label = k.uppercase(),
                            modifier = Modifier.weight(1f).let { if (rowIndex == 0 && i == 0) it.focusRequester(firstKey) else it },
                            onClick = { type(k) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyButton(icon = Icons.Filled.SpaceBar, label = "Espaço", modifier = Modifier.weight(2f)) { type(" ") }
                KeyButton(icon = Icons.AutoMirrored.Filled.Backspace, label = "Apagar", modifier = Modifier.weight(2f)) { query = query.dropLast(1) }
                KeyButton(label = "Limpar", modifier = Modifier.weight(2f)) { query = "" }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchType.entries.forEach { t ->
                    TvChip(t.label, selected = t == type, onClick = { typeName = t.name })
                }
            }
            if (recents.isNotEmpty() && query.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Buscas recentes", color = p.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                recents.take(3).forEach { r ->
                    RecentRow(r) { query = r }
                }
            }
        }

        Spacer(Modifier.width(24.dp))

        // ── Resultados ──────────────────────────────────────────────────
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val showingResults = query.trim().length >= 2
            Text(
                when {
                    !showingResults -> if (kidsMode) "Sugestões para a criançada"
                        else GENRES.firstOrNull { it.first == genreId && it.first != 0 }?.second ?: "Em alta hoje"
                    loading -> "Buscando “${query.trim()}”…"
                    results.isEmpty() -> "Nada encontrado"
                    else -> "${results.size} resultados para “${query.trim()}”"
                },
                color = p.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp, end = 70.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!showingResults && !kidsMode) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GENRES.forEach { (id, label) ->
                        TvChip(label, selected = id == genreId, onClick = { genreId = id })
                    }
                }
            }
            val list = if (showingResults) results else trending
            when {
                showingResults && loading && results.isEmpty() -> LoadingBox(Modifier.fillMaxSize())
                showingResults && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (failed) "Sem conexão com o catálogo. Tente de novo."
                        else "Tente outro nome ou confira a escrita.",
                        color = p.textMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> TvLazyVerticalGrid(
                    columns = TvGridCells.Fixed(5),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 60.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(list, key = { _, it -> "${it.resolvedMediaType}_${it.id}" }) { _, item ->
                        Column {
                            PosterCard(
                                title = item.displayTitle,
                                posterPath = item.poster_path,
                                width = 200.dp,
                                modifier = Modifier.fillMaxWidth(),
                                badge = if (item.resolvedMediaType == "tv") "SÉRIE" else null,
                                onClick = {
                                    if (showingResults) {
                                        recentStore.addSearch(query)
                                        recents = recentStore.searches()
                                    }
                                    onItemClick(item.id, item.resolvedMediaType)
                                },
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

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = p.glass,
            contentColor = p.text,
            focusedContainerColor = p.accent,
            focusedContentColor = p.onAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, label, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecentRow(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = p.textMuted,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
