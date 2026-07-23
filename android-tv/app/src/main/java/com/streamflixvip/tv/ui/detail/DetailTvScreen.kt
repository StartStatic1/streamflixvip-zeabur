package com.streamflixvip.tv.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.*

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

    LaunchedEffect(tmdbId, mediaType) { viewModel.loadDetails() }

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

        // SELETOR DE SERVIDORES ULTRA-MODERNO (Side Panel)
        if (state.showServerPicker) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f))) {
                Surface(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(400.dp),
                    colors = SurfaceDefaults.colors(containerColor = Color(0xFF12121A)),
                ) {
                    Column(modifier = Modifier.padding(32.dp)) {
                        Text("Escolha o Servidor", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Selecione a melhor conexão", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.sources) { source ->
                                ServerCard(source) {
                                    viewModel.pickServer(source) {
                                        onPlayClick(source, state.selectedSeason, 1, state.details?.name ?: state.details?.title ?: "")
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Card(
                            onClick = { viewModel.dismissServerPicker() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = CardDefaults.shape(RoundedCornerShape(25.dp))
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
private fun ServerCard(source: VipSource, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp).scale(scale).onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1E1E2E),
            focusedContainerColor = Color(0xFF2E2E3E)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(Color(0xFFD4AF37).copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFFD4AF37), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(source.displayName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Estável", color = Color(0xFF4CAF50), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.SignalCellularAlt, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                }
            }
            Box(modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("4K", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        item { HeroHeader(details, onBack) { onPlay(1, 1) } }

        if (cast.isNotEmpty()) {
            item { SectionTitle("Elenco Principal") }
            item { CastRow(cast) }
        }

        if (details.number_of_seasons != null) {
            item { SectionTitle("Temporadas") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items((1..details.number_of_seasons).toList()) { s ->
                        var isFocused by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)
                        Card(
                            onClick = { onSelectSeason(s) },
                            modifier = Modifier.scale(scale).onFocusChanged { isFocused = it.isFocused },
                            colors = CardDefaults.colors(
                                containerColor = if (s == selectedSeason) Color(0xFFD4AF37) else Color(0xFF1E1E2E),
                                focusedContainerColor = if (s == selectedSeason) Color(0xFFFFD700) else Color(0xFF2E2E3E)
                            ),
                            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
                        ) {
                            Text("T$s", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), 
                                 color = if (s == selectedSeason) Color.Black else Color.White)
                        }
                    }
                }
            }
            
            val episodes = seasonEpisodes[selectedSeason].orEmpty()
            items(episodes) { ep -> EpisodeItem(ep) { onPlay(selectedSeason, ep.episode_number) } }
        }

        if (similar.isNotEmpty()) {
            item { SectionTitle("Similares") }
            item { SimilarRow(similar, onOpenTitle) }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
private fun HeroHeader(details: TmdbResponse, onBack: () -> Unit, onPlay: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(440.dp)) {
        AsyncImage(model = details.backdrop_path?.let { "$TMDB_BACKDROP_W1280$it" }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0A0A10)), startY = 200f)))
        
        Column(modifier = Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Bottom) {
            Text(details.title ?: details.name ?: "", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                var isPlayFocused by remember { mutableStateOf(false) }
                val playScale by animateFloatAsState(if (isPlayFocused) 1.05f else 1f)
                Card(
                    onClick = onPlay, 
                    modifier = Modifier.scale(playScale).onFocusChanged { isPlayFocused = it.isFocused },
                    colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37), focusedContainerColor = Color(0xFFFFD700)), 
                    shape = CardDefaults.shape(RoundedCornerShape(24.dp))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Assistir Agora", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Card(onClick = onBack, colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f)), shape = CardDefaults.shape(RoundedCornerShape(24.dp))) {
                    Text("Voltar", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EpisodeItem(ep: TmdbEpisode, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp).scale(scale).onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(containerColor = Color(0xFF15151F), focusedContainerColor = Color(0xFF1E1E2E)),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = ep.still_path?.let { "$TMDB_STILL_W300$it" }, contentDescription = null, 
                       modifier = Modifier.width(160.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Text("E${ep.episode_number} - ${ep.name}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(ep.overview ?: "", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 48.dp, top = 40.dp, bottom = 16.dp))
}

@Composable
private fun CastRow(cast: List<TmdbCastMember>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        items(cast.take(12)) { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
                AsyncImage(model = m.profile_path?.let { "$TMDB_PROFILE_W185$it" }, contentDescription = null, 
                           modifier = Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Text(m.name, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SimilarRow(items: List<TmdbItem>, onOpen: (Int, String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        items(items.take(12)) { item ->
            var isFocused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(if (isFocused) 1.1f else 1f)
            Card(
                onClick = { onOpen(item.id, item.resolvedMediaType) }, 
                modifier = Modifier.width(140.dp).aspectRatio(2f/3f).scale(scale).onFocusChanged { isFocused = it.isFocused },
                shape = CardDefaults.shape(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(model = item.poster_path?.let { "$TMDB_POSTER_W342$it" }, contentDescription = null, contentScale = ContentScale.Crop)
            }
        }
    }
}
