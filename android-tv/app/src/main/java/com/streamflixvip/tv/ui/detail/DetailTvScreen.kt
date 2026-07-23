package com.streamflixvip.tv.ui.detail

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.TmdbCastMember
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.network.TmdbResponse
import com.streamflixvip.tv.network.VipSource

private const val TMDB_BACKDROP_W1280 = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"
private const val TMDB_PROFILE_W185 = "https://image.tmdb.org/t/p/w185"
private const val TMDB_STILL_W300 = "https://image.tmdb.org/t/p/w300"

@Composable
fun DetailTvScreen(
    tmdbId: Int,
    mediaType: String,
    onPlayClick: (source: VipSource, season: Int, episode: Int, title: String) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit = {},
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onPlayTrailer: (trailerKey: String) -> Unit = {},
) {
    val viewModel = remember(tmdbId, mediaType) { DetailTvViewModel(tmdbId, mediaType) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(tmdbId, mediaType) {
        viewModel.loadDetails()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Carregando...", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
            }
        } else if (state.details != null) {
            DetailContent(
                details = state.details!!,
                seasonEpisodes = state.seasonEpisodes,
                selectedSeason = state.selectedSeason,
                similar = state.similar,
                cast = state.cast,
                onBack = onBack,
                onPlay = { s, e ->
                    if (mediaType == "movie") viewModel.loadMovieSources { onPlayClick(it, 0, 0, state.details!!.title ?: "") }
                    else viewModel.loadEpisodeSources(s, e) { onPlayClick(it, s, e, state.details!!.name ?: "") }
                },
                onSelectSeason = viewModel::selectSeason,
                onOpenTitle = onOpenTitle,
            )
        }

        // SELETOR DE SERVIDORES MODERNO (Side Panel)
        if (state.showServerPicker) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(360.dp),
                    color = Color(0xFF15151F),
                ) {
                    Column(modifier = Modifier.padding(32.dp)) {
                        Text("Escolha o Servidor", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.sources) { source ->
                                Card(
                                    onClick = {
                                        viewModel.pickServer(source) {
                                            onPlayClick(source, state.selectedSeason, 1, state.details?.name ?: state.details?.title ?: "")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    colors = CardDefaults.colors(containerColor = Color(0xFF232330)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFFD4AF37), modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(source.displayName, color = Color.White, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Card(
                            onClick = { viewModel.dismissServerPicker() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Cancelar", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    details: TmdbResponse,
    seasonEpisodes: Map<Int, List<TmdbEpisode>>,
    selectedSeason: Int,
    similar: List<TmdbItem>,
    cast: List<TmdbCastMember>,
    onBack: () -> Unit,
    onPlay: (Int, Int) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onOpenTitle: (Int, String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            HeroHeader(details, onBack) { onPlay(1, 1) }
        }

        if (cast.isNotEmpty()) {
            item { SectionTitle("Elenco Principal") }
            item { CastRow(cast) }
        }

        if (details.number_of_seasons != null) {
            item { SectionTitle("Temporadas") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items((1..details.number_of_seasons).toList()) { s ->
                        Card(
                            onClick = { onSelectSeason(s) },
                            colors = CardDefaults.colors(
                                containerColor = if (s == selectedSeason) Color(0xFFD4AF37) else Color(0xFF1E1E2E)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("T$s", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), 
                                 color = if (s == selectedSeason) Color.Black else Color.White)
                        }
                    }
                }
            }
            
            val episodes = seasonEpisodes[selectedSeason].orEmpty()
            items(episodes) { ep ->
                EpisodeItem(ep) { onPlay(selectedSeason, ep.episode_number) }
            }
        }

        if (similar.isNotEmpty()) {
            item { SectionTitle("Similares") }
            item { SimilarRow(similar, onOpenTitle) }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }
}

@Composable
private fun HeroHeader(details: TmdbResponse, onBack: () -> Unit, onPlay: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
        AsyncImage(
            model = details.backdrop_path?.let { "$TMDB_BACKDROP_W1280$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0A0A10)), startY = 200f)
        ))
        
        Column(modifier = Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Bottom) {
            Text(details.title ?: details.name ?: "", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(onClick = onPlay, colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)), shape = RoundedCornerShape(24.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Assistir Agora", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Card(onClick = onBack, colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f)), shape = RoundedCornerShape(24.dp)) {
                    Text("Voltar", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EpisodeItem(ep: TmdbEpisode, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 6.dp),
        colors = CardDefaults.colors(containerColor = Color(0xFF15151F)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ep.still_path?.let { "$TMDB_STILL_W300$it" },
                contentDescription = null,
                modifier = Modifier.width(140.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text("E${ep.episode_number} - ${ep.name}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(ep.overview ?: "", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 48.dp, top = 32.dp, bottom = 12.dp))
}

@Composable
private fun CastRow(cast: List<TmdbCastMember>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(cast.take(12)) { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
                AsyncImage(model = m.profile_path?.let { "$TMDB_PROFILE_W185$it" }, contentDescription = null, 
                           modifier = Modifier.size(70.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Text(m.name, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SimilarRow(items: List<TmdbItem>, onOpen: (Int, String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items.take(12)) { item ->
            Card(onClick = { onOpen(item.id, item.resolvedMediaType) }, modifier = Modifier.width(130.dp).aspectRatio(2f/3f)) {
                AsyncImage(model = item.poster_path?.let { "$TMDB_POSTER_W342$it" }, contentDescription = null, contentScale = ContentScale.Crop)
            }
        }
    }
}
