package com.streamflixvip.tv.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.network.VipSource

private const val TMDB_BACKDROP_W1280 = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"

@Composable
fun DetailTvScreen(
    tmdbId: Int,
    mediaType: String,
    viewModel: DetailTvViewModel = viewModel(factory = DetailTvViewModelFactory(tmdbId, mediaType)),
    onPlayClick: (source: VipSource, season: Int, episode: Int, title: String) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit = {},
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(tmdbId) {
        viewModel.loadDetails()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (state.showError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.showError, color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        onClick = { viewModel.retryLoad() },
                        colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)),
                        shape = CardDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Text("Tentar novamente", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return
        }

        val details = state.details ?: return

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            // HERO SECTION com backdrop
            item {
                DetailHero(
                    backdropUrl = details.backdrop_path?.let { "$TMDB_BACKDROP_W1280$it" },
                    title = details.title ?: details.name ?: "Sem título",
                    overview = details.overview,
                    year = details.release_date ?: details.first_air_date,
                    runtime = details.displayRuntime,
                    rating = details.vote_average,
                    genres = details.genres?.map { it.name } ?: emptyList(),
                    onBack = onBack,
                    isSeries = mediaType == "tv",
                    seasonEpisodes = state.seasonEpisodes,
                    selectedSeason = state.selectedSeason,
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onPlayEpisode = { season, episode ->
                        viewModel.loadEpisodeSources(season, episode) { source ->
                            val episodeTitle = state.seasonEpisodes[season]?.firstOrNull { it.episode_number == episode }?.displayName
                                ?: "Episódio $episode"
                            val fullTitle = "${details.title ?: details.name} - S${season}E${episode} - $episodeTitle"
                            onPlayClick(source, season, episode, fullTitle)
                        }
                    },
                    onPlayMovie = {
                        viewModel.loadMovieSources { source ->
                            val movieTitle = details.title ?: details.name ?: "Filme"
                            onPlayClick(source, 0, 0, movieTitle)
                        }
                    },
                    onOpenTitle = onOpenTitle,
                )
            }

            // Server Picker (quando múltiplos servidores)
            if (state.showServerPicker && state.sources.size > 1) {
                item {
                    ServerPickerRow(
                        sources = state.sources,
                        onClose = { viewModel.dismissServerPicker() },
                        onSelect = { source ->
                            viewModel.pickServer(source) {
                                // Re-chama com o source selecionado
                                if (mediaType == "tv") {
                                    viewModel.loadEpisodeSources(
                                        state.selectedSeason,
                                        state.selectedSeason,
                                        onPlayClick
                                    )
                                }
                            }
                        },
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

// ─── HERO DETAIL ────────────────────────────────────────────────────────────────

@Composable
private fun DetailHero(
    backdropUrl: String?,
    title: String,
    overview: String?,
    year: String?,
    runtime: String?,
    rating: Double?,
    genres: List<String>,
    onBack: () -> Unit,
    isSeries: Boolean,
    seasonEpisodes: Map<Int, List<TmdbEpisode>>,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (season: Int, episode: Int) -> Unit,
    onPlayMovie: () -> Unit,
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
) {
    Column {
        // Backdrop com gradiente
        Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Gradiente superior
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A0A10), Color.Transparent),
                        endY = 150f,
                    ),
                ),
            )
            // Gradiente inferior
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0A0A10)),
                        startY = 180f,
                    ),
                ),
            )

            // Botão voltar
            var isBackFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .onFocusChanged { isBackFocused = it.isFocused },
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = if (isBackFocused) Color(0xFFD4AF37) else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Conteúdo abaixo do backdrop
        Column(modifier = Modifier.padding(horizontal = 48.dp)) {
            // Título
            Text(title, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(modifier = Modifier.height(12.dp))

            // Metadados
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                year?.let { MetaPill(it.take(4)) }
                runtime?.let { MetaPill(it) }
                rating?.let { if (it > 0) RatingPill("%.1f".format(it)) }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Gêneros
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.take(4).forEach { genre ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD4AF37).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(genre, fontSize = 12.sp, color = Color(0xFFD4AF37))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Sinopse
            overview?.let {
                Text(
                    it,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 5,
                    modifier = Modifier.widthIn(max = 700.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Botão Assistir / Episódios
            if (isSeries) {
                SeriesEpisodesSection(
                    seasonEpisodes = seasonEpisodes,
                    selectedSeason = selectedSeason,
                    onSelectSeason = onSelectSeason,
                    onPlayEpisode = onPlayEpisode,
                )
            } else {
                MoviePlayButton(onPlay = onPlayMovie)
            }

            // Mais Informações (substitui Elenco e Trailer)
            Spacer(modifier = Modifier.height(24.dp))
            MoreInfoSection(year = year?.take(4), runtime = runtime, rating = rating, genres = genres)
        }
    }
}

// ─── SEÇÃO DE EPISÓDIOS ─────────────────────────────────────────────────────────

@Composable
private fun SeriesEpisodesSection(
    seasonEpisodes: Map<Int, List<TmdbEpisode>>,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (season: Int, episode: Int) -> Unit,
) {
    if (seasonEpisodes.isEmpty()) {
        Text("Carregando episódios...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        return
    }

    Column {
        // Seletor de temporada
        Text("Temporadas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(seasonEpisodes.keys.sorted()) { season ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = season == selectedSeason
                val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "season_scale")

                Card(
                    onClick = { onSelectSeason(season) },
                    modifier = Modifier
                        .height(40.dp)
                        .scale(scale)
                        .onFocusChanged { isFocused = it.isFocused },
                    colors = CardDefaults.colors(
                        containerColor = if (isSelected) Color(0xFFD4AF37) else Color(0xFF1E1E2E),
                        focusedContainerColor = Color(0xFFFFD700)
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
                ) {
                    Text(
                        "T$season",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Lista de episódios
        val episodes = seasonEpisodes[selectedSeason] ?: emptyList()
        if (episodes.isEmpty()) {
            Text("Nenhum episódio disponível", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            return
        }

        Text("Episódios", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.height(8.dp))

        episodes.forEach { episode ->
            EpisodeItem(
                episode = episode,
                onPlay = { onPlayEpisode(selectedSeason, episode.episode_number) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EpisodeItem(episode: TmdbEpisode, onPlay: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isFocused) Color(0xFF2E2E3E) else Color(0xFF15151C),
            focusedContainerColor = Color(0xFF2E2E3E)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            if (episode.still_path != null) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/w300${episode.still_path}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2E2E3E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "S${episode.episode_number.toString().padStart(2, '0')}. ${episode.displayName}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                )
                episode.overview?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 2,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    episode.displayRuntime?.let {
                        Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                    episode.air_date?.let {
                        Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }

            // Ícone de play
            Icon(
                Icons.Filled.PlayCircleFilled,
                contentDescription = "Assistir",
                tint = if (isFocused) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

// ─── BOTÃO FILME ────────────────────────────────────────────────────────────────

@Composable
private fun MoviePlayButton(onPlay: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "movie_play_scale")

    Card(
        onClick = onPlay,
        modifier = Modifier
            .height(48.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color(0xFFD4AF37),
            focusedContainerColor = Color(0xFFFFD700)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(24.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Text("Assistir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ─── MAIS INFORMAÇÕES (substitui Elenco e Trailer) ──────────────────────────────

@Composable
private fun MoreInfoSection(
    year: String?,
    runtime: String?,
    rating: Double?,
    genres: List<String>,
) {
    Column {
        Text("Mais Informações", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF15151C), RoundedCornerShape(12.dp)).padding(20.dp)) {
            if (year != null) {
                InfoRow("Ano", year)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (runtime != null) {
                InfoRow("Duração", runtime)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (rating != null && rating > 0) {
                InfoRow("Avaliação", "${"%.1f".format(rating)} / 10")
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (genres.isNotEmpty()) {
                InfoRow("Gêneros", genres.joinToString(", "))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
        Text(value, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

// ─── SERVER PICKER ──────────────────────────────────────────────────────────────

@Composable
private fun ServerPickerRow(
    sources: List<VipSource>,
    onClose: () -> Unit,
    onSelect: (VipSource) -> Unit,
    onBack: () -> Unit,
) {
    // Server picker como overlay modal
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .padding(top = 16.dp)
            .background(Color(0xFF1E1E2E), RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Escolha o Servidor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton(onClick = onClose) {
                Text("Fechar", color = Color(0xFFD4AF37))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        sources.forEach { source ->
            var isFocused by remember { mutableStateOf(false) }
            Card(
                onClick = { onSelect(source) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(vertical = 4.dp)
                    .onFocusChanged { isFocused = it.isFocused },
                colors = CardDefaults.colors(
                    containerColor = if (isFocused) Color(0xFF2E2E3E) else Color(0xFF15151C),
                    focusedContainerColor = Color(0xFF2E2E3E)
                ),
                shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(source.displayName, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─── PILLS ──────────────────────────────────────────────────────────────────────

@Composable
private fun MetaPill(text: String) {
    Box(modifier = Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 14.sp, color = Color.White)
    }
}

@Composable
private fun RatingPill(rating: String) {
    Box(modifier = Modifier.background(Color(0xFFFFC107), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            Text(" $rating", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}
