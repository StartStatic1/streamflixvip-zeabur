package com.streamflixvip.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.streamflixvip.tv.data.LocalFavorite
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.ui.components.FadeImage
import com.streamflixvip.tv.ui.components.LandscapeCard
import com.streamflixvip.tv.ui.components.LoadingBox
import com.streamflixvip.tv.ui.components.MessageBox
import com.streamflixvip.tv.ui.components.PosterCard
import com.streamflixvip.tv.ui.components.SectionTitle
import com.streamflixvip.tv.ui.components.TmdbImg
import com.streamflixvip.tv.ui.shell.FocusSignal
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** O que aparece na "vitrine" do topo: muda conforme o cartao em foco. */
data class Spotlight(
    val title: String,
    val backdrop: String?,
    val label: String?,
    val rating: String? = null,
    val meta: List<String> = emptyList(),
    val overview: String? = null,
    val progress: Float? = null,
)

fun TmdbItem.toSpotlight() = Spotlight(
    title = displayTitle,
    backdrop = TmdbImg.backdropLg(backdrop_path),
    label = displayMediaLabel,
    rating = displayRating,
    meta = listOfNotNull(displayYear),
    overview = overview,
)

fun LocalWatchProgress.toSpotlight() = Spotlight(
    title = title.substringBefore(" · "),
    backdrop = TmdbImg.backdropLg(backdropPath),
    label = "CONTINUAR ASSISTINDO",
    meta = listOfNotNull(
        displaySubtitle,
        if (remainingMinutes > 0) "faltam $remainingMinutes min" else null,
    ),
    progress = progressFraction,
)

fun LocalFavorite.toSpotlight() = Spotlight(
    title = title,
    backdrop = null,
    label = "NA MINHA LISTA",
    meta = listOf(if (mediaType == "tv") "Série" else "Filme"),
)

private const val CONTINUE_KEY = "continue"
private const val MYLIST_KEY = "mylist"

/**
 * Tela de navegacao (Inicio / Filmes / Series / Infantil) no estilo TV moderna:
 * vitrine fixa no topo com a arte do titulo em foco e fileiras rolando por baixo
 * (a fileira focada sempre sobe para o mesmo lugar — o olho nao precisa procurar).
 */
@Composable
fun HomeTvScreen(
    section: BrowseSection,
    focusSignal: FocusSignal,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
    onContinueClick: (LocalWatchProgress) -> Unit,
    onSeeAll: (rowKey: String) -> Unit,
    onToast: (String) -> Unit,
) {
    val viewModel: HomeTvViewModel = viewModel(key = "browse_${section.name}")
    val state by viewModel.uiState.collectAsState()
    val p = LocalPalette.current

    LaunchedEffect(section) { viewModel.load(section) }
    // Trocou de perfil: recarrega "Continuar assistindo" e Minha lista do perfil novo.
    val activeProfileId = com.streamflixvip.tv.profiles.ProfileStore.active.collectAsState().value?.id
    LaunchedEffect(activeProfileId) { viewModel.refreshLibrary() }

    // Linhas visiveis (na ordem): destaques, continuar, minha lista, catalogo...
    val showLibraryRows = section == BrowseSection.HOME || section == BrowseSection.KIDS
    val rows: List<HomeRow> = remember(state, showLibraryRows) {
        buildList {
            state.specs.forEachIndexed { index, spec ->
                val items = state.rows[spec.key].orEmpty()
                if (items.isNotEmpty()) add(HomeRow.Catalog(spec, items))
                if (index == 0 && showLibraryRows) {
                    if (state.continueWatching.isNotEmpty()) add(HomeRow.Continue(state.continueWatching))
                    if (state.favorites.isNotEmpty()) add(HomeRow.MyList(state.favorites))
                }
            }
            if (state.rows[state.specs.firstOrNull()?.key].isNullOrEmpty() && showLibraryRows) {
                // destaques ainda nao chegaram: biblioteca aparece primeiro mesmo assim
                if (state.continueWatching.isNotEmpty() && none { it is HomeRow.Continue }) add(0, HomeRow.Continue(state.continueWatching))
            }
        }
    }

    // ── Vitrine ──────────────────────────────────────────────────────────
    var candidate by remember { mutableStateOf<Spotlight?>(null) }
    var spotlight by remember { mutableStateOf<Spotlight?>(null) }
    var backdrop by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rows.isNotEmpty()) {
        if (spotlight == null) {
            val first = (rows.firstOrNull() as? HomeRow.Catalog)?.items?.firstOrNull()
            if (first != null) {
                spotlight = first.toSpotlight()
                backdrop = spotlight?.backdrop
            }
        }
    }
    LaunchedEffect(candidate) {
        val c = candidate ?: return@LaunchedEffect
        delay(260)
        spotlight = c
        if (c.backdrop != null) backdrop = c.backdrop
    }

    // ── Foco: restaura o ultimo cartao (ou o primeiro) quando o casco pede ──
    var restoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    var handledToken by remember { mutableIntStateOf(0) }
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusNonce by remember { mutableIntStateOf(0) }
    val listState = rememberTvLazyListState()
    val scope = rememberCoroutineScope()
    val retryFocus = remember { FocusRequester() }
    val firstKey = rows.firstOrNull()?.let { firstCardKey(it) }

    LaunchedEffect(focusSignal.token, firstKey, state.error) {
        if (handledToken == focusSignal.token) return@LaunchedEffect
        if (state.error != null) {
            delay(60)
            if (runCatching { retryFocus.requestFocus() }.isSuccess) handledToken = focusSignal.token
            return@LaunchedEffect
        }
        if (firstKey == null) return@LaunchedEffect
        handledToken = focusSignal.token
        val rk = restoreKey
        if (rk != null) {
            val ri = rows.indexOfFirst { rk.startsWith(it.key + ":") }
            if (ri >= 0) runCatching { listState.scrollToItem(ri) }
        }
        focusTarget = rk ?: firstKey
        focusNonce++
        // se o cartao lembrado nao estiver na tela, cai para o primeiro
        delay(450)
        if (focusTarget != null) {
            runCatching { listState.scrollToItem(0) }
            focusTarget = firstKey
            focusNonce++
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Arte de fundo (lado direito) com degradês
        Crossfade(targetState = backdrop, animationSpec = tween(450), label = "backdrop") { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    FadeImage(
                        url,
                        Modifier.align(Alignment.TopEnd).fillMaxWidth(0.78f).aspectRatio(16f / 9f),
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to p.bg,
                    0.28f to p.bg,
                    0.62f to p.bg.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.38f to p.bg.copy(alpha = 0.55f),
                    0.56f to p.bg,
                    1f to p.bg,
                ),
            ),
        )

        when {
            state.error != null && rows.isEmpty() -> MessageBox(
                title = "Ops! Nada carregou",
                message = state.error,
                actionLabel = "Tentar de novo",
                actionFocus = retryFocus,
                onAction = { viewModel.retry() },
                modifier = Modifier.fillMaxSize(),
            )
            rows.isEmpty() -> LoadingBox(Modifier.fillMaxSize(), "Montando sua vitrine…")
            else -> Column(Modifier.fillMaxSize()) {
                SpotlightInfo(
                    spotlight,
                    Modifier.fillMaxWidth().height(236.dp).clipToBounds()
                        .padding(start = 44.dp, top = 18.dp, end = 120.dp, bottom = 12.dp),
                )
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // o foco cai no CARTÃO; deixamos ~64dp acima dele para o título da fileira nunca sumir
                val pivot = (64f / maxHeight.value.coerceAtLeast(120f)).coerceIn(0f, 0.45f)
                TvLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    pivotOffsets = PivotOffsets(parentFraction = pivot),
                ) {
                    rows.forEachIndexed { rowIndex, row ->
                        item(key = row.key, contentType = row.contentType) {
                          // fileira em foco sempre inteira no topo (titulo + cartoes), nos dois sentidos
                          Box(Modifier.padding(top = 10.dp)) {
                            HomeRowView(
                                row = row,
                                focusTarget = focusTarget,
                                focusNonce = focusNonce,
                                onFocusConsumed = { focusTarget = null },
                                onCardFocusedRaw = { key, s ->
                                    restoreKey = key
                                    candidate = s
                                },
                                onItemClick = onItemClick,
                                onItemLongClick = { item ->
                                    val added = viewModel.toggleFavorite(item)
                                    onToast(if (added) "Adicionado à Minha lista" else "Removido da Minha lista")
                                },
                                onContinueClick = onContinueClick,
                                onContinueLongClick = { entry ->
                                    viewModel.removeFromContinue(entry)
                                    restoreKey = null
                                    onToast("Removido de Continuar assistindo")
                                },
                                onSeeAll = onSeeAll,
                            )
                          }
                        }
                    }
                }
                }
            }
        }
    }
}

private sealed class HomeRow(val key: String, val contentType: String) {
    class Catalog(val spec: RowSpec, val items: List<TmdbItem>) : HomeRow(spec.key, spec.style.name)
    class Continue(val entries: List<LocalWatchProgress>) : HomeRow(CONTINUE_KEY, "continue")
    class MyList(val favorites: List<LocalFavorite>) : HomeRow(MYLIST_KEY, "mylist")
}

private fun firstCardKey(row: HomeRow): String? = when (row) {
    is HomeRow.Catalog -> row.items.firstOrNull()?.let { "${row.key}:${it.id}" }
    is HomeRow.Continue -> row.entries.firstOrNull()?.let { "${row.key}:${it.tmdbId}_${it.mediaType}" }
    is HomeRow.MyList -> row.favorites.firstOrNull()?.let { "${row.key}:${it.tmdbId}_${it.mediaType}" }
}

@Composable
private fun SpotlightInfo(s: Spotlight?, modifier: Modifier) {
    val p = LocalPalette.current
    if (s == null) {
        Box(modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        s.label?.let {
            Text(it, color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            s.title,
            color = p.text,
            fontSize = if (s.title.length > 28) 24.sp else 30.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            lineHeight = 34.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.58f),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.rating?.let {
                Icon(Icons.Filled.Star, null, tint = p.star, modifier = Modifier.size(14.dp))
                Text(it, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(x = (-6).dp))
            }
            s.meta.forEach { Text(it, color = p.textMuted, fontSize = 13.sp) }
        }
        if (s.progress != null && s.progress > 0f) {
            Spacer(Modifier.height(6.dp))
            com.streamflixvip.tv.ui.components.ProgressLine(s.progress, Modifier.width(220.dp), height = 4.dp)
        }
        s.overview?.takeIf { it.isNotBlank() }?.let { full ->
            // corta no fim de uma palavra (nunca "pressã…")
            val it = if (full.length <= 190) full else full.take(190).substringBeforeLast(' ').trimEnd(',', '.', ';', ':', '-') + "…"
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = p.text.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.52f),
            )
        }
    }
}

@Composable
private fun HomeRowView(
    row: HomeRow,
    focusTarget: String?,
    focusNonce: Int,
    onFocusConsumed: () -> Unit,
    onCardFocusedRaw: (key: String, spotlight: Spotlight) -> Unit,
    onItemClick: (Int, String) -> Unit,
    onItemLongClick: (TmdbItem) -> Unit,
    onContinueClick: (LocalWatchProgress) -> Unit,
    onContinueLongClick: (LocalWatchProgress) -> Unit,
    onSeeAll: (String) -> Unit,
) {
    val rowState = rememberTvLazyListState()
    val title = when (row) {
        is HomeRow.Catalog -> row.spec.title
        is HomeRow.Continue -> "Continuar assistindo"
        is HomeRow.MyList -> "Minha lista"
    }
    val hint = when (row) {
        is HomeRow.Continue -> "segure OK para remover"
        is HomeRow.MyList -> "${row.favorites.size} títulos"
        else -> null
    }
    val onCardFocused: (String, Spotlight) -> Unit = { k, sp ->
        onCardFocusedRaw(k, sp.copy(label = listOfNotNull(title, sp.label).joinToString(" · ")))
    }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title, Modifier.padding(start = 44.dp, bottom = 8.dp), trailing = hint)
        TvLazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 44.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            pivotOffsets = PivotOffsets(parentFraction = 0.06f),
        ) {
            when (row) {
                is HomeRow.Catalog -> {
                    // filme e serie podem ter o mesmo id no TMDB (linha "Destaques" = trending/all):
                    // chave so com id repetia e derrubava o app ao rolar a fileira
                    val list = row.items.distinctBy { "${it.resolvedMediaType}_${it.id}" }
                        .take(if (row.spec.style == RowStyle.TOP10) 10 else 20)
                    itemsIndexed(list, key = { _, it -> "${it.resolvedMediaType}_${it.id}" }) { index, item ->
                        val key = "${row.key}:${item.resolvedMediaType}_${item.id}"
                        FocusTargetBox(key, focusTarget, focusNonce, onFocusConsumed) { mod ->
                            when (row.spec.style) {
                                RowStyle.FEATURED -> LandscapeCard(
                                    title = item.displayTitle,
                                    imageUrl = TmdbImg.backdrop(item.backdrop_path),
                                    subtitle = listOfNotNull(item.displayMediaLabel.lowercase().replaceFirstChar { it.uppercase() }, item.displayYear).joinToString(" · "),
                                    width = 300.dp,
                                    modifier = mod,
                                    onFocus = { onCardFocused(key, item.toSpotlight()) },
                                    onLongClick = { onItemLongClick(item) },
                                    onClick = { onItemClick(item.id, item.resolvedMediaType) },
                                )
                                RowStyle.TOP10 -> if (index < 10) {
                                    Top10Card(index + 1) {
                                        PosterCard(
                                            title = item.displayTitle,
                                            posterPath = item.poster_path,
                                            width = 120.dp,
                                            modifier = mod,
                                            onFocus = { onCardFocused(key, item.toSpotlight()) },
                                            onLongClick = { onItemLongClick(item) },
                                            onClick = { onItemClick(item.id, item.resolvedMediaType) },
                                        )
                                    }
                                } else {
                                    PosterCard(
                                        title = item.displayTitle,
                                        posterPath = item.poster_path,
                                        width = 120.dp,
                                        modifier = mod,
                                        onFocus = { onCardFocused(key, item.toSpotlight()) },
                                        onLongClick = { onItemLongClick(item) },
                                        onClick = { onItemClick(item.id, item.resolvedMediaType) },
                                    )
                                }
                                RowStyle.POSTER -> PosterCard(
                                    title = item.displayTitle,
                                    posterPath = item.poster_path,
                                    width = 120.dp,
                                    modifier = mod,
                                    onFocus = { onCardFocused(key, item.toSpotlight()) },
                                    onLongClick = { onItemLongClick(item) },
                                    onClick = { onItemClick(item.id, item.resolvedMediaType) },
                                )
                            }
                        }
                    }
                    if (row.items.size >= 8 && row.spec.style != RowStyle.TOP10) {
                        item(key = "see_all") {
                            SeeAllCard(featured = row.spec.style == RowStyle.FEATURED) { onSeeAll(row.spec.key) }
                        }
                    }
                }
                is HomeRow.Continue -> {
                    itemsIndexed(row.entries.distinctBy { "${it.tmdbId}_${it.mediaType}" }, key = { _, it -> "${it.tmdbId}_${it.mediaType}" }) { _, entry ->
                        val key = "${row.key}:${entry.tmdbId}_${entry.mediaType}"
                        FocusTargetBox(key, focusTarget, focusNonce, onFocusConsumed) { mod ->
                            LandscapeCard(
                                title = entry.title.substringBefore(" · "),
                                imageUrl = TmdbImg.backdrop(entry.backdropPath) ?: TmdbImg.backdrop(entry.posterPath),
                                subtitle = listOfNotNull(
                                    entry.displaySubtitle,
                                    if (entry.remainingMinutes > 0) "faltam ${entry.remainingMinutes} min" else null,
                                ).joinToString(" · ").ifBlank { null },
                                progress = entry.progressFraction,
                                showPlayIcon = true,
                                width = 248.dp,
                                modifier = mod,
                                onFocus = { onCardFocused(key, entry.toSpotlight()) },
                                onLongClick = { onContinueLongClick(entry) },
                                onClick = { onContinueClick(entry) },
                            )
                        }
                    }
                }
                is HomeRow.MyList -> {
                    itemsIndexed(row.favorites.distinctBy { "${it.tmdbId}_${it.mediaType}" }, key = { _, it -> "${it.tmdbId}_${it.mediaType}" }) { _, fav ->
                        val key = "${row.key}:${fav.tmdbId}_${fav.mediaType}"
                        FocusTargetBox(key, focusTarget, focusNonce, onFocusConsumed) { mod ->
                            PosterCard(
                                title = fav.title,
                                posterPath = fav.posterPath,
                                width = 120.dp,
                                modifier = mod,
                                onFocus = { onCardFocused(key, fav.toSpotlight()) },
                                onClick = { onItemClick(fav.tmdbId, fav.mediaType) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Liga um FocusRequester ao cartao e pede foco quando ele e o alvo atual. */
@Composable
fun FocusTargetBox(
    key: String,
    focusTarget: String?,
    focusNonce: Int,
    onFocusConsumed: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val requester = remember { FocusRequester() }
    if (focusTarget == key) {
        LaunchedEffect(focusNonce) {
            delay(40)
            if (runCatching { requester.requestFocus() }.isSuccess) onFocusConsumed()
        }
    }
    content(Modifier.focusRequester(requester))
}

@Composable
private fun Top10Card(rank: Int, poster: @Composable () -> Unit) {
    val p = LocalPalette.current
    // número e pôster lado a lado (sem sobrepor), largura fixa por posição
    Row(verticalAlignment = Alignment.Bottom) {
        Box(Modifier.width(if (rank >= 10) 104.dp else 62.dp).height(180.dp), contentAlignment = Alignment.BottomEnd) {
            Text(
                "$rank",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(p.accent, p.accent.copy(alpha = 0.15f))),
                    fontSize = 104.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-6).sp,
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(y = 14.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        poster()
    }
}

@Composable
private fun SeeAllCard(featured: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        modifier = if (featured) Modifier.width(170.dp).height(169.dp) else Modifier.width(120.dp).aspectRatio(2f / 3f),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(containerColor = p.glass, focusedContainerColor = Color.White),
        border = CardDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, p.glassBorder), shape = shape),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, p.accent), shape = shape),
        ),
    ) {
        var focused by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().onFocusChanged { focused = it.hasFocus }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = p.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text("Ver tudo", color = p.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
