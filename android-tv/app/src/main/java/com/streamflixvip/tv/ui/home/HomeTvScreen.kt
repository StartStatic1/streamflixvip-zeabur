package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.tv.material3.MaterialTheme
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

@Composable
fun HomeTvScreen(
    viewModel: HomeTvViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAll()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A10)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar fixa e estreita
            SidebarNav()

            // Conteúdo principal que ocupa o resto da tela
            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Carregando...", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 40.dp),
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

                        // Carrosséis
                        item { ContentRow("Em Alta", state.trendingItems, onItemClick) }
                        item { ContentRow("Filmes Populares", state.popularMovies, onItemClick) }
                        item { ContentRow("Séries Populares", state.popularSeries, onItemClick) }
                        item { ContentRow("Ação", state.actionItems, onItemClick) }
                        item { ContentRow("Comédia", state.comedyItems, onItemClick) }
                        item { ContentRow("Animes", state.animeItems, onItemClick) }
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
            .fillMaxHeight()
            .width(80.dp)
            .background(Color(0xFF12121A)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFD4AF37)),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(40.dp))
        SidebarIcon(Icons.Filled.Home, selected = true)
        SidebarIcon(Icons.Filled.Search)
        SidebarIcon(Icons.Filled.Person)
        SidebarIcon(Icons.Filled.Settings)
    }
}

@Composable
private fun SidebarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean = false) {
    Card(
        onClick = { },
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(48.dp),
        colors = CardDefaults.colors(
            containerColor = if (selected) Color(0xFFD4AF37).copy(alpha = 0.15f) else Color.Transparent,
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun HeroSection(hero: HeroItem, onPlayClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(460.dp)) {
        if (hero.backdropUrl != null) {
            AsyncImage(
                model = hero.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Gradientes mais agressivos para TV
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0A0A10), Color(0xFF0A0A10).copy(alpha = 0.9f), Color.Transparent),
                    endX = 1000f,
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xFF0A0A10)),
                    startY = 300f,
                ),
            ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(start = 48.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                hero.title,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                hero.year?.let { MetaPill(it) }
                hero.rating?.let { RatingPill(it) }
                hero.genre?.let { MetaPill(it) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            hero.overview?.let {
                Text(
                    it,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    modifier = Modifier.width(600.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                onClick = onPlayClick,
                modifier = Modifier.height(48.dp),
                colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)),
                shape = CardDefaults.shape(RoundedCornerShape(24.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Text("Assistir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 14.sp, color = Color.White)
    }
}

@Composable
private fun RatingPill(rating: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFFFC107), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            Text(" $rating", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ContentRow(title: String, items: List<TmdbItem>, onItemClick: (Int, String) -> Unit) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(top = 32.dp)) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items) { item ->
                PosterCard(item) { onItemClick(item.id, item.resolvedMediaType) }
            }
        }
    }
}

@Composable
private fun PosterCard(item: TmdbItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp).aspectRatio(2f / 3f),
        colors = CardDefaults.colors(containerColor = Color(0xFF1E1E2E)),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.poster_path?.let { "$TMDB_POSTER_W342$it" },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            item.displayRating?.let { rating ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xFFFFC107), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(rating, fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
