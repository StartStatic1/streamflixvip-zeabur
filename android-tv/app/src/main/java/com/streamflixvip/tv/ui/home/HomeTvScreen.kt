package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.streamflixvip.tv.data.LocalFavorite
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.network.TmdbItem

private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"

@Composable
fun HomeTvScreen(
    viewModel: HomeTvViewModel = viewModel(),
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadAll() }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxHeight().width(84.dp).background(Color(0xFF12121A)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFD4AF37)), contentAlignment = Alignment.Center) {
                    Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(Modifier.height(36.dp))
                IconButton(onClick = {}) { Icon(Icons.Filled.Home, null, tint = Color(0xFFD4AF37)) }
                IconButton(onClick = onNavigateToSearch) { Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.4f)) }
                IconButton(onClick = onNavigateToAccount) { Icon(Icons.Filled.Favorite, null, tint = Color.White.copy(alpha = 0.4f)) }
                IconButton(onClick = onNavigateToAccount) { Icon(Icons.Filled.Settings, null, tint = Color.White.copy(alpha = 0.4f)) }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFD4AF37))
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = Color.White)
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
                    if (state.continueWatching.isNotEmpty()) {
                        item {
                            Text("Continuar assistindo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(48.dp, 16.dp, 48.dp, 8.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(state.continueWatching) { e ->
                                    Card(onClick = { onItemClick(e.tmdbId, e.mediaType) }, modifier = Modifier.width(220.dp).height(140.dp)) {
                                        Box {
                                            AsyncImage(e.posterPath?.let { "$TMDB_POSTER$it" }, e.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            Text(e.title, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.favorites.isNotEmpty()) {
                        item {
                            Text("Minha lista", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(48.dp, 16.dp, 48.dp, 8.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(state.favorites) { e ->
                                    Card(onClick = { onItemClick(e.tmdbId, e.mediaType) }, modifier = Modifier.width(148.dp).aspectRatio(2f / 3f)) {
                                        AsyncImage(e.posterPath?.let { "$TMDB_POSTER$it" }, e.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        CatalogRow("Em Alta", state.trendingItems, onItemClick)
                        CatalogRow("Filmes Populares", state.popularMovies, onItemClick)
                        CatalogRow("Series Populares", state.popularSeries, onItemClick)
                        CatalogRow("Acao", state.actionItems, onItemClick)
                        CatalogRow("Comedia", state.comedyItems, onItemClick)
                        CatalogRow("Drama", state.dramaItems, onItemClick)
                        CatalogRow("Terror", state.horrorItems, onItemClick)
                        CatalogRow("Ficcao", state.scifiItems, onItemClick)
                        CatalogRow("Animes", state.animeItems, onItemClick)
                        CatalogRow("Familia", state.familyItems, onItemClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(title: String, items: List<TmdbItem>, onItemClick: (Int, String) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 16.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item ->
                Card(onClick = { onItemClick(item.id, item.resolvedMediaType) }, modifier = Modifier.width(148.dp).aspectRatio(2f / 3f)) {
                    AsyncImage(item.poster_path?.let { "$TMDB_POSTER$it" }, item.displayTitle, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}
