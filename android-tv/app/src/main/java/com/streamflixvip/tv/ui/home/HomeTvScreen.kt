package com.streamflixvip.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.network.TmdbItem
import kotlinx.coroutines.delay

private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"
private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w780"
private const val TMDB_BACKDROP_LG = "https://image.tmdb.org/t/p/w1280"

private val Bg = Color(0xFF0B0B14)
private val RailBg = Color(0xFF10101A)
private val Accent = Color(0xFF00E5FF)
private val AccentSoft = Color(0xFF22D3EE)
private val PlayBlue = Color(0xFF00B4D8)
private val PlayFocus = Color(0xFF00E5FF)
private val TextMuted = Color(0xFFA1A1B5)
private val Glass = Color.White.copy(alpha = 0.08f)
private val GlassBorder = Color.White.copy(alpha = 0.14f)
private val GlassStrong = Color.White.copy(alpha = 0.12f)

private const val CONTINUE_VISIBLE = 6

@Composable
fun HomeTvScreen(
    viewModel: HomeTvViewModel = viewModel(),
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onContinueClick: (LocalWatchProgress) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToLiveTv: () -> Unit = {},
    onNavigateToMyList: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onExploreCategory: (category: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewModel.loadAll() }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) runCatching { playFocus.requestFocus() }
    }

    val heroPool = remember(state.heroItems, state.trendingItems) {
        (state.heroItems + state.trendingItems).distinctBy { it.id }.take(8)
    }
    var heroIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(heroPool) {
        if (heroPool.size <= 1) return@LaunchedEffect
        while (true) {
            delay(8000)
            heroIndex = (heroIndex + 1) % heroPool.size
        }
    }
    val hero = heroPool.getOrNull(heroIndex.coerceIn(0, (heroPool.size - 1).coerceAtLeast(0)))

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (hero != null) {
            val bgPath = hero.backdrop_path ?: hero.poster_path
            if (bgPath != null) {
                AsyncImage(
                    model = "$TMDB_BACKDROP_LG$bgPath",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(48.dp),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0xCC0B0B14),
                            0.45f to Color(0xE60B0B14),
                            1f to Color(0xF20B0B14),
                        ),
                    ),
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(88.dp)
                    .background(RailBg.copy(alpha = 0.92f))
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Spacer(Modifier.height(28.dp))
                NavRailItem(Icons.Filled.Home, "Início", selected = true, onClick = {})
                Spacer(Modifier.height(14.dp))
                NavRailItem(Icons.Filled.LiveTv, "TV", selected = false, onClick = onNavigateToLiveTv)
                Spacer(Modifier.height(14.dp))
                NavRailItem(Icons.Filled.Search, "Buscar", selected = false, onClick = onNavigateToSearch)
                Spacer(Modifier.height(14.dp))
                NavRailItem(Icons.Filled.Favorite, "Lista", selected = false, onClick = onNavigateToMyList)
                Spacer(Modifier.weight(1f))
                NavRailItem(Icons.Filled.Settings, "Conta", selected = false, onClick = onNavigateToAccount)
                Spacer(Modifier.height(12.dp))
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentSoft)
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = Color.White)
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
                    ) {
                        if (hero != null) {
                            item(key = "hero") {
                                AnimatedContent(
                                    targetState = hero,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "hero",
                                ) { h ->
                                    StreamlyHero(
                                        item = h,
                                        playFocus = playFocus,
                                        onPlay = { onItemClick(h.id, h.resolvedMediaType) },
                                        onDetails = { onItemClick(h.id, h.resolvedMediaType) },
                                    )
                                }
                            }
                        }

                        item(key = "chips") {
                            Spacer(Modifier.height(10.dp))
                            ExploreChips(
                                onCategory = onExploreCategory,
                                onSearch = onNavigateToSearch,
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        item(key = "continue") {
                            ContinueSection(
                                entries = state.continueWatching,
                                featuredFallback = emptyList(),
                                onContinueClick = onContinueClick,
                                onFeaturedClick = onItemClick,
                                onSeeAll = { onExploreCategory("continue") },
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        if (state.trendingItems.isNotEmpty()) {
                            item(key = "trending") {
                                CatalogRow(
                                    title = "Em alta",
                                    items = state.trendingItems,
                                    onItemClick = onItemClick,
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                        if (state.popularMovies.isNotEmpty()) {
                            item(key = "movies") {
                                CatalogRow(
                                    title = "Filmes populares",
                                    items = state.popularMovies,
                                    onItemClick = onItemClick,
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                        if (state.popularSeries.isNotEmpty()) {
                            item(key = "series") {
                                CatalogRow(
                                    title = "Séries populares",
                                    items = state.popularSeries,
                                    onItemClick = onItemClick,
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                        if (state.actionItems.isNotEmpty()) {
                            item(key = "action") {
                                CatalogRow(
                                    title = "Ação",
                                    items = state.actionItems,
                                    onItemClick = onItemClick,
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                        if (state.comedyItems.isNotEmpty()) {
                            item(key = "comedy") {
                                CatalogRow(
                                    title = "Comédia",
                                    items = state.comedyItems,
                                    onItemClick = onItemClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    title: String,
    items: List<TmdbItem>,
    onItemClick: (Int, String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items.take(18), key = { "${title}_${it.id}" }) { item ->
                FeaturedMiniCard(
                    title = item.displayTitle,
                    posterPath = item.poster_path,
                    onClick = { onItemClick(item.id, item.resolvedMediaType) },
                )
            }
        }
    }
}

@Composable
private fun StreamlyHero(
    item: TmdbItem,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 4.dp)
            .height(236.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                item.displayMediaLabel,
                color = AccentSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                item.displayTitle,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 30.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item.displayRating?.let {
                    Text("IMDb $it", color = Color(0xFFFBBF24), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                item.displayYear?.let {
                    Text(it, color = TextMuted, fontSize = 13.sp)
                }
            }
            item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    overview,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playFocus),
                    colors = ButtonDefaults.colors(
                        containerColor = PlayBlue,
                        focusedContainerColor = PlayFocus,
                        contentColor = Color.Black,
                        focusedContentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Assistir", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Button(
                    onClick = onDetails,
                    colors = ButtonDefaults.colors(
                        containerColor = GlassStrong,
                        focusedContainerColor = Color.White.copy(alpha = 0.22f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Info, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Detalhes", fontSize = 14.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .background(Color(0xFF151522)),
        ) {
            AsyncImage(
                model = (item.backdrop_path ?: item.poster_path)?.let { path ->
                    if (item.backdrop_path != null) "$TMDB_BACKDROP$path" else "$TMDB_POSTER$path"
                },
                contentDescription = item.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Bg.copy(alpha = 0.4f), Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun ExploreChips(
    onCategory: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val chips = listOf(
        "explorar" to "Explorar",
        "trending" to "Em Alta",
        "cinema" to "No Cinema",
        "upcoming" to "Em breve",
        "series" to "Séries",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(chips, key = { it.first }) { (key, label) ->
            var focused by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (key == "explorar") onSearch() else onCategory(key)
                },
                colors = ButtonDefaults.colors(
                    containerColor = if (focused) Accent.copy(alpha = 0.35f) else Glass,
                    focusedContainerColor = Accent.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    focusedContentColor = Color.White,
                ),
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .border(
                        width = if (focused) 1.5.dp else 1.dp,
                        color = if (focused) AccentSoft else GlassBorder,
                        shape = RoundedCornerShape(999.dp),
                    ),
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ContinueSection(
    entries: List<LocalWatchProgress>,
    featuredFallback: List<TmdbItem>,
    onContinueClick: (LocalWatchProgress) -> Unit,
    onFeaturedClick: (Int, String) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (entries.isEmpty() && featuredFallback.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (entries.isNotEmpty()) "Continuar assistindo" else "Recomendados pra você",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            if (entries.size > CONTINUE_VISIBLE) {
                var focused by remember { mutableStateOf(false) }
                Button(
                    onClick = onSeeAll,
                    colors = ButtonDefaults.colors(
                        containerColor = Glass,
                        focusedContainerColor = GlassStrong,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    modifier = Modifier.onFocusChanged { focused = it.isFocused },
                ) {
                    Text(
                        "Ver todos",
                        fontSize = 12.sp,
                        color = if (focused) AccentSoft else TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            entries.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        entries.take(CONTINUE_VISIBLE),
                        key = { "cw_${it.tmdbId}_${it.mediaType}" },
                    ) { entry ->
                        ContinueWatchingCard(entry) { onContinueClick(entry) }
                    }
                }
            }
            featuredFallback.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(featuredFallback, key = { "fb_${it.id}" }) { item ->
                        FeaturedMiniCard(
                            title = item.displayTitle,
                            posterPath = item.poster_path,
                            onClick = { onFeaturedClick(item.id, item.resolvedMediaType) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRailItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        focused -> AccentSoft
        selected -> Accent.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val iconTint = when {
        focused || selected -> AccentSoft
        else -> Color.White.copy(alpha = 0.45f)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .background(
                    if (focused) Accent.copy(alpha = 0.18f) else Color.Transparent,
                    RoundedCornerShape(12.dp),
                )
                .onFocusChanged { focused = it.isFocused },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.IconButton(onClick = onClick) {
                Icon(icon, contentDescription, tint = iconTint)
            }
        }
        if (focused || selected) {
            Text(
                contentDescription,
                color = AccentSoft,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(entry: LocalWatchProgress, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(168.dp).height(96.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF151522),
            focusedContainerColor = Color(0xFF151522),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentSoft),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = entry.posterPath?.let { "$TMDB_POSTER$it" },
                contentDescription = entry.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.4f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.displaySubtitle?.let { sub ->
                    Text(sub, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(entry.progressFraction.coerceIn(0f, 1f))
                            .background(AccentSoft),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedMiniCard(
    title: String,
    posterPath: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(112.dp).aspectRatio(2f / 3f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF151522),
            focusedContainerColor = Color(0xFF151522),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentSoft),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        Box {
            AsyncImage(
                model = posterPath?.let { "$TMDB_POSTER$it" },
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            )
            Text(
                title,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
        }
    }
}
