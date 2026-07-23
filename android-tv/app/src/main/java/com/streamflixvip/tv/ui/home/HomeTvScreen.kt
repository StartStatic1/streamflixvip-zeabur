package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.TmdbItem

data class HeroItem(
    val item: TmdbItem,
    val backdropUrl: String?,
    val title: String,
    val overview: String?,
    val year: String?,
    val rating: String?,
    val genre: String?,
)

private const val TMDB_BACKDROP_W1280 = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"

/**
 * Tela Home da TV — design moderno com hero cinematográfico + carrosséis
 * horizontais por categoria. Inspirado nas referências (Streambox,
 * serivia, PlayBox): sidebar fixa, hero com gradiente duplo, cards com
 * badge de nota amarelo.
 */
@Composable
fun HomeTvScreen(
    viewModel: HomeTvViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAll()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A10)),
    ) {
        SidebarNav()

        Column(modifier = Modifier.weight(1f)) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Carregando...", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    val heroItem = state.heroItems.firstOrNull()
                    if (heroItem != null) {
                        item(key = "hero") {
                            HeroSection(
                                hero = HeroItem(
                                    item = heroItem,
                                    backdropUrl = heroItem.backdrop_path?.let { "$TMDB_BACKDROP_W1280$it" },
                                    title = heroItem.displayTitle,
                                    overview = heroItem.overview,
                                    year = heroItem.displayYear,
                                    rating = heroItem.displayRating,
                                    genre = heroItem.displayMediaLabel,
                                ),
                                onPlayClick = { onItemClick(heroItem.id, heroItem.resolvedMediaType) },
                            )
                        }
                    }

                    if (state.trendingItems.isNotEmpty()) {
                        item(key = "trending") {
                            ContentRow(title = "Em Alta", items = state.trendingItems, onItemClick = onItemClick)
                        }
                    }
                    if (state.popularMovies.isNotEmpty()) {
                        item(key = "movies") {
                            ContentRow(title = "Filmes Populares", items = state.popularMovies, onItemClick = onItemClick)
                        }
                    }
                    if (state.popularSeries.isNotEmpty()) {
                        item(key = "series") {
                            ContentRow(title = "Séries Populares", items = state.popularSeries, onItemClick = onItemClick)
                        }
                    }
                    if (state.actionItems.isNotEmpty()) {
                        item(key = "action") {
                            ContentRow(title = "Ação", items = state.actionItems, onItemClick = onItemClick)
                        }
                    }
                    if (state.comedyItems.isNotEmpty()) {
                        item(key = "comedy") {
                            ContentRow(title = "Comédia", items = state.comedyItems, onItemClick = onItemClick)
                        }
                    }
                    if (state.dramaItems.isNotEmpty()) {
                        item(key = "drama") {
                            ContentRow(title = "Drama", items = state.dramaItems, onItemClick = onItemClick)
                        }
                    }
                    if (state.horrorItems.isNotEmpty()) {
                        item(key = "horror") {
                            ContentRow(title = "Terror", items = state.horrorItems, onItemClick = onItemClick)
                        }
                    }
                    if (state.scifiItems.isNotEmpty()) {
                        item(key = "scifi") {
                            ContentRow(title = "Ficção Científica", items = state.scifiItems, onItemClick = onItemClick)
                        }
                    }
                    if (state.animeItems.isNotEmpty()) {
                        item(key = "anime") {
                            ContentRow(title = "Animes", items = state.animeItems, onItemClick = onItemClick)
                        }
                    }
                    if (state.familyItems.isNotEmpty()) {
                        item(key = "family") {
                            ContentRow(title = "Família", items = state.familyItems, onItemClick = onItemClick)
                        }
                    }

                    item(key = "spacer") {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarNav() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .width(72.dp)
            .background(Color(0xFF12121A)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFD4AF37)),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(28.dp))
        SidebarIcon(Icons.Filled.Home, "Início", selected = true)
        SidebarIcon(Icons.Filled.Search, "Buscar")
        SidebarIcon(Icons.Filled.Person, "Perfil")
        SidebarIcon(Icons.Filled.Settings, "Configurações")
    }
}

@Composable
private fun SidebarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
) {
    Card(
        onClick = { },
        modifier = Modifier
            .padding(vertical = 8.dp)
            .size(44.dp),
        colors = CardDefaults.colors(
            containerColor = if (selected) Color(0xFFD4AF37).copy(alpha = 0.15f) else Color.Transparent,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun HeroSection(hero: HeroItem, onPlayClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(440.dp)) {
        if (hero.backdropUrl != null) {
            AsyncImage(
                model = hero.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
        }

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0A0A10),
                        Color(0xFF0A0A10).copy(alpha = 0.92f),
                        Color(0xFF0A0A10).copy(alpha = 0.4f),
                        Color.Transparent,
                    ),
                    endX = 900f,
                ),
            ),
        )

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF0A0A10)),
                    startY = 280f,
                ),
            ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(start = 56.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            hero.genre?.let { genre ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFFD4AF37), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(genre, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                hero.title,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                modifier = Modifier.width(640.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hero.year?.let { MetaPill(it) }
                hero.genre?.let { MetaPill(it) }
                hero.rating?.let { RatingPill(it) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            hero.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    modifier = Modifier.width(560.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    onClick = onPlayClick,
                    modifier = Modifier.height(44.dp),
                    colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Text("Assistir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Card(
                    onClick = { },
                    modifier = Modifier.height(44.dp),
                    colors = CardDefaults.colors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Trailer", color = Color.White, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Card(
                    onClick = { },
                    modifier = Modifier.size(44.dp),
                    colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.12f)),
                    shape = CircleShape,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun RatingPill(rating: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFFFC107), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
            Text(" $rating", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ContentRow(title: String, items: List<TmdbItem>, onItemClick: (Int, String) -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items.take(20)) { item ->
                PosterCard(
                    item = item,
                    onClick = { onItemClick(item.id, item.resolvedMediaType) },
                )
            }
        }
    }
}

@Composable
private fun PosterCard(item: TmdbItem, onClick: () -> Unit) {
    Column(modifier = Modifier.width(150.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            colors = CardDefaults.colors(containerColor = Color(0xFF1E1E2E)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster_path?.let { "$TMDB_POSTER_W342$it" },
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                item.displayRating?.let { rating ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xFFFFC107), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
                            Text(" $rating", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            item.displayTitle,
            fontSize = 13.sp,
            maxLines = 1,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
