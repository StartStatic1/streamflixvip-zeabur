package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.network.TmdbItem

private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"

private val Bg = Color(0xFF0B0B12)
private val RailBg = Color(0xFF12121C)
private val Gold = Color(0xFFD4AF37)
private val TextMuted = Color(0xFF9A9AAA)

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
    LaunchedEffect(Unit) { viewModel.loadAll() }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxSize()) {
            // Rail — coração = Minha lista | engrenagem = Conta (rotas diferentes)
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
                    // top generoso: ao voltar o scroll, título "Em Alta" não cola na borda
                    contentPadding = PaddingValues(top = 36.dp, bottom = 80.dp),
                ) {
                    if (state.continueWatching.isNotEmpty()) {
                        item(key = "continue") {
                            SectionTitle("Continuar assistindo")
                            // padding vertical evita card "comido" pelo scale do foco
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(state.continueWatching, key = { "cw_${it.tmdbId}_${it.mediaType}" }) { entry ->
                                    ContinueWatchingCard(entry) {
                                        onItemClick(entry.tmdbId, entry.mediaType)
                                    }
                                }
                            }
                        }
                    }
                    if (state.favorites.isNotEmpty()) {
                        item(key = "fav_preview") {
                            SectionTitle("Minha lista")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.favorites.take(12), key = { "fav_${it.tmdbId}_${it.mediaType}" }) { fav ->
                                    PosterCard(
                                        title = fav.title,
                                        posterPath = fav.posterPath,
                                        onClick = { onItemClick(fav.tmdbId, fav.mediaType) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "catalog") {
                        CatalogRow("Em Alta", state.trendingItems, onItemClick)
                        CatalogRow("Filmes Populares", state.popularMovies, onItemClick)
                        CatalogRow("Séries Populares", state.popularSeries, onItemClick)
                        CatalogRow("Ação", state.actionItems, onItemClick)
                        CatalogRow("Comédia", state.comedyItems, onItemClick)
                        CatalogRow("Drama", state.dramaItems, onItemClick)
                        CatalogRow("Terror", state.horrorItems, onItemClick)
                        CatalogRow("Ficção", state.scifiItems, onItemClick)
                        CatalogRow("Animes", state.animeItems, onItemClick)
                        CatalogRow("Família", state.familyItems, onItemClick)
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
        Modifier.padding(start = 40.dp, end = 40.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Gold),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}

@Composable
private fun ContinueWatchingCard(entry: LocalWatchProgress, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(280.dp).height(158.dp),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1A1A24),
            focusedContainerColor = Color(0xFF1A1A24),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Gold),
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
                    .fillMaxWidth()
                    .height(78.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.displaySubtitle?.let { sub ->
                    Text(sub, color = TextMuted, fontSize = 12.sp, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
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
        modifier = Modifier.width(124.dp).aspectRatio(2f / 3f),
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
    Column(Modifier.padding(bottom = 6.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
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
