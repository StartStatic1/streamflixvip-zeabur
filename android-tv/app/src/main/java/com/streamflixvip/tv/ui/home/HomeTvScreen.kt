package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"
private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280"

private val Bg = Color(0xFF0B0B12)
private val RailBg = Color(0xFF12121C)
private val Gold = Color(0xFFD4AF37)
private val TextMuted = Color(0xFFB0B0C0)

@Composable
fun HomeTvScreen(
    viewModel: HomeTvViewModel = viewModel(),
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {},
    onNavigateToMyList: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewModel.loadAll() }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) runCatching { playFocus.requestFocus() }
    }

    val hero = state.heroItems.firstOrNull()
        ?: state.trendingItems.firstOrNull()
        ?: state.popularMovies.firstOrNull()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(88.dp)
                    .background(RailBg)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Gold),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(40.dp))
                NavRailItem(Icons.Filled.Home, "Início", selected = true, onClick = {})
                Spacer(Modifier.height(20.dp))
                NavRailItem(Icons.Filled.Search, "Buscar", selected = false, onClick = onNavigateToSearch)
                Spacer(Modifier.height(20.dp))
                NavRailItem(Icons.Filled.Favorite, "Minha lista", selected = false, onClick = onNavigateToMyList)
                Spacer(Modifier.weight(1f))
                NavRailItem(Icons.Filled.Settings, "Conta", selected = false, onClick = onNavigateToAccount)
                Spacer(Modifier.height(16.dp))
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = Color.White)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp),
                ) {
                    item(key = "hero") {
                        if (hero != null) {
                            HeroBanner(
                                item = hero,
                                playFocus = playFocus,
                                onPlay = { onItemClick(hero.id, hero.resolvedMediaType) },
                                onDetails = { onItemClick(hero.id, hero.resolvedMediaType) },
                            )
                        }
                    }

                    if (state.continueWatching.isNotEmpty()) {
                        item(key = "continue") {
                            SectionTitle("Continuar assistindo")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(state.continueWatching, key = { "cw_${it.tmdbId}_${it.mediaType}" }) { entry ->
                                    ContinueWatchingCard(entry) {
                                        onItemClick(entry.tmdbId, entry.mediaType)
                                    }
                                }
                            }
                        }
                    }

                    item(key = "catalog") {
                        CatalogRow("Em Alta", state.trendingItems, onItemClick)
                        CatalogRow("Filmes Populares", state.popularMovies, onItemClick)
                        CatalogRow("Séries Populares", state.popularSeries, onItemClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(
    item: TmdbItem,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp),
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
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.92f),
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        0.75f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Bg,
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.48f)
                .padding(start = 36.dp, end = 16.dp, top = 28.dp, bottom = 24.dp),
        ) {
            Text(
                item.displayMediaLabel,
                color = Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                item.displayTitle,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item.displayRating?.let {
                    Text("★ $it", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                item.displayYear?.let {
                    Text(it, color = TextMuted, fontSize = 14.sp)
                }
            }
            item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(10.dp))
                Text(
                    overview,
                    color = TextMuted,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playFocus),
                    colors = ButtonDefaults.colors(
                        containerColor = Gold,
                        focusedContainerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Assistir", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDetails,
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Detalhes")
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
        focused -> Gold
        selected -> Gold.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val iconTint = when {
        focused || selected -> Gold
        else -> Color.White.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(
                if (focused) Gold.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(icon, contentDescription, tint = iconTint)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        Modifier.padding(start = 36.dp, end = 36.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Gold),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
private fun ContinueWatchingCard(entry: LocalWatchProgress, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(220.dp).height(124.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1A1A24),
            focusedContainerColor = Color(0xFF1A1A24),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Gold),
                shape = RoundedCornerShape(10.dp),
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
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.displaySubtitle?.let { sub ->
                    Text(sub, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(entry.progressFraction.coerceIn(0f, 1f))
                            .background(Gold),
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    posterPath: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(120.dp).aspectRatio(2f / 3f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1A1A24),
            focusedContainerColor = Color(0xFF1A1A24),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Gold),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        AsyncImage(
            model = posterPath?.let { "$TMDB_POSTER$it" },
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun CatalogRow(
    title: String,
    items: List<TmdbItem>,
    onItemClick: (Int, String) -> Unit,
) {
    if (items.isEmpty()) return
    Column(Modifier.padding(bottom = 4.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(
                    title = item.displayTitle,
                    posterPath = item.poster_path,
                    onClick = { onItemClick(item.id, item.resolvedMediaType) },
                )
            }
        }
    }
}
