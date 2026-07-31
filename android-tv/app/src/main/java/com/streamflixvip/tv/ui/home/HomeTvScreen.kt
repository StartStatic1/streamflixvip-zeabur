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
private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w780"

private val Bg = Color(0xFF0E0E16)
private val RailBg = Color(0xFF12121C)
private val Gold = Color(0xFFD4AF37)
private val TextMuted = Color(0xFFA8A8B8)
private val PlayBlue = Color(0xFF3B82F6)
private val PlayPurple = Color(0xFF7C3AED)

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
                    .width(80.dp)
                    .background(RailBg)
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Gold),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Spacer(Modifier.height(36.dp))
                NavRailItem(Icons.Filled.Home, "Início", selected = true, onClick = {})
                Spacer(Modifier.height(18.dp))
                NavRailItem(Icons.Filled.Search, "Buscar", selected = false, onClick = onNavigateToSearch)
                Spacer(Modifier.height(18.dp))
                NavRailItem(Icons.Filled.Favorite, "Minha lista", selected = false, onClick = onNavigateToMyList)
                Spacer(Modifier.weight(1f))
                NavRailItem(Icons.Filled.Settings, "Conta", selected = false, onClick = onNavigateToAccount)
                Spacer(Modifier.height(12.dp))
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
                    contentPadding = PaddingValues(top = 20.dp, bottom = 64.dp),
                ) {
                    // Hero estilo streamly: texto | card imagem (não full-bleed)
                    item(key = "hero") {
                        if (hero != null) {
                            StreamlyHero(
                                item = hero,
                                playFocus = playFocus,
                                onPlay = { onItemClick(hero.id, hero.resolvedMediaType) },
                                onDetails = { onItemClick(hero.id, hero.resolvedMediaType) },
                            )
                        }
                    }

                    if (state.continueWatching.isNotEmpty()) {
                        item(key = "continue") {
                            SectionHeader("Continuar assistindo")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
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

/**
 * Layout streamly: coluna de texto à esquerda + imagem arredondada à direita.
 * Não ocupa a tela inteira como backdrop.
 */
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
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 8.dp)
            .height(230.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Texto (esquerda) ──
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                item.displayMediaLabel,
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
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
                    Text("IMDb $it", color = Color(0xFFF5C518), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                        focusedContainerColor = PlayPurple,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Assistir", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Button(
                    onClick = onDetails,
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White.copy(alpha = 0.28f),
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

        // ── Imagem em card arredondado (direita) — como streamly ──
        Box(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A28)),
        ) {
            AsyncImage(
                model = (item.backdrop_path ?: item.poster_path)?.let { path ->
                    if (item.backdrop_path != null) "$TMDB_BACKDROP$path" else "$TMDB_POSTER$path"
                },
                contentDescription = item.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // sombra suave na borda esquerda do card
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Bg.copy(alpha = 0.35f), Color.Transparent),
                        ),
                    ),
            )
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
            .size(46.dp)
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
private fun SectionHeader(text: String) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun ContinueWatchingCard(entry: LocalWatchProgress, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(168.dp).height(96.dp),
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
        modifier = Modifier.width(112.dp).aspectRatio(2f / 3f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1A1A24),
            focusedContainerColor = Color(0xFF1A1A24),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Gold),
                shape = RoundedCornerShape(8.dp),
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
    Column(Modifier.padding(bottom = 2.dp)) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
