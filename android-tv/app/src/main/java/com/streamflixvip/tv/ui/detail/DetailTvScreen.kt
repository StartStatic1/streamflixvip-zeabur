package com.streamflixvip.tv.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
    onPlayClick: (source: VipSource, sources: List<VipSource>, season: Int, episode: Int, title: String) -> Unit = { _, _, _, _, _ -> },
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
                CircularProgressIndicator(color = Color(0xFFD4AF37))
            }
            return@Box
        }

        if (state.showError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.showError!!, color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
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
            return@Box
        }

        val details = state.details ?: return@Box

        val backdropUrl = details.backdrop_path?.let { "$TMDB_BACKDROP_W1280$it" }
        val displayTitle = details.title ?: details.name ?: "Sem título"
        val isSeries = mediaType == "tv"
        val showPanel = state.showServerPicker && state.sources.size > 1

        // Layout full-screen: backdrop de fundo, conteúdo por cima. Filme cabe
        // 100% sem rolar (Row ocupa a tela toda); série ganha uma faixa rolável
        // de episódios abaixo, sem afetar o layout do filme.
        Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().let { if (isSeries) it.weight(1f) else it.fillMaxHeight() }) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // Backdrop cobrindo toda a área principal
                if (backdropUrl != null) {
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Gradiente esquerda->direita para legibilidade do texto
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0A0A10), Color(0xFF0A0A10).copy(alpha = 0.75f), Color.Transparent),
                            endX = 900f,
                        ),
                    ),
                )
                // Gradiente inferior para não cortar texto na base
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF0A0A10).copy(alpha = 0.9f)),
                            startY = 500f,
                        ),
                    ),
                )

                // Botão voltar
                var isBackFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                        .onFocusChanged { isBackFocused = it.isFocused },
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = if (isBackFocused) Color(0xFFD4AF37) else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Conteúdo: título, meta, sinopse e ações — ancorado embaixo,
                // sem depender de scroll pra caber na tela
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 48.dp)
                        .padding(bottom = 40.dp)
                        .widthIn(max = 760.dp),
                ) {
                    Text(displayTitle, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        (details.release_date ?: details.first_air_date)?.let { MetaPill(it.take(4)) }
                        details.displayRuntime?.let { MetaPill(it) }
                        details.vote_average?.let { if (it > 0) RatingPill("%.1f".format(it)) }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (genre in (details.genres?.map { it.name } ?: emptyList()).take(3)) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFD4AF37).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(genre, fontSize = 12.sp, color = Color(0xFFD4AF37))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    details.overview?.let {
                        Text(
                            it,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 3,
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))

                    // Ações: Assistir + Minha Lista + Detalhes (referência de layout)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MoviePlayButton(
                            onPlay = {
                                if (isSeries) {
                                    val season = state.selectedSeason
                                    viewModel.loadEpisodeSources(season, 1) { source ->
                                        val episodeTitle = state.seasonEpisodes[season]?.firstOrNull { it.episode_number == 1 }?.displayName
                                            ?: "Episódio 1"
                                        val fullTitle = "$displayTitle - S${season}E1 - $episodeTitle"
                                        onPlayClick(source, state.sources, season, 1, fullTitle)
                                    }
                                } else {
                                    viewModel.loadMovieSources { source ->
                                        onPlayClick(source, state.sources, 0, 0, displayTitle)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Painel de servidores: desliza pela direita, na mesma tela, sem scroll
            AnimatedVisibility(
                visible = showPanel,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            ) {
                ServerSidePanel(
                    sources = state.sources,
                    onClose = { viewModel.dismissServerPicker() },
                    onSelect = { source ->
                        viewModel.pickServer(source) {
                            if (isSeries) {
                                val season = state.selectedSeason
                                val episode = 1 // padrão consistente com o botão "Assistir Agora"
                                val episodeTitle = state.seasonEpisodes[season]?.firstOrNull { it.episode_number == episode }?.displayName
                                    ?: "Episódio $episode"
                                val fullTitle = "$displayTitle - S${season}E${episode} - $episodeTitle"
                                onPlayClick(source, state.sources, season, episode, fullTitle)
                            } else {
                                onPlayClick(source, state.sources, 0, 0, displayTitle)
                            }
                        }
                    },
                )
            }
        }

        // Faixa de episódios: só para séries, abaixo do bloco principal.
        // Filme não ganha essa faixa, então continua 100% sem rolar.
        if (isSeries) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A10)).padding(horizontal = 48.dp, vertical = 20.dp)) {
                SeriesEpisodesSection(
                    seasonEpisodes = state.seasonEpisodes,
                    selectedSeason = state.selectedSeason,
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onPlayEpisode = { season, episode ->
                        viewModel.loadEpisodeSources(season, episode) { source ->
                            val episodeTitle = state.seasonEpisodes[season]?.firstOrNull { it.episode_number == episode }?.displayName
                                ?: "Episódio $episode"
                            val fullTitle = "$displayTitle - S${season}E${episode} - $episodeTitle"
                            onPlayClick(source, season, episode, fullTitle)
                        }
                    },
                )
            }
        }
        }
    }
}

@Composable
private fun ServerSidePanel(
    sources: List<VipSource>,
    onClose: () -> Unit,
    onSelect: (VipSource) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(420.dp)
            .background(Color(0xFF12121A))
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Escolha o Servidor", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.width(36.dp).height(3.dp).background(Color(0xFFD4AF37)))
            }
            var isCloseFocused by remember { mutableStateOf(false) }
            IconButton(
                onClick = onClose,
                modifier = Modifier.onFocusChanged { isCloseFocused = it.isFocused },
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Fechar",
                    tint = if (isCloseFocused) Color(0xFFD4AF37) else Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(sources) { src ->
                ServerSourceCard(source = src, onClick = { onSelect(src) })
            }
        }
    }
}

@Composable
private fun ServerSourceCard(source: VipSource, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, label = "server_card_scale")

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1E1E2E),
            focusedContainerColor = Color(0xFFD4AF37),
        ),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isFocused) Color.Black.copy(alpha = 0.15f) else Color(0xFFD4AF37).copy(alpha = 0.15f),
                        RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color(0xFFD4AF37),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                source.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.weight(1f),
            )
            // Ícone de sinal decorativo (não indica qualidade real — VipSource
            // não expõe essa info de forma confiável, então não inventamos badge de 4K/1080p)
            Icon(
                Icons.Filled.SignalCellularAlt,
                contentDescription = null,
                tint = if (isFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

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

        // CORREÇÃO: Usar for loop
        for (episode in episodes) {
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
                    modifier = Modifier.width(140.dp).height(80.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(modifier = Modifier.width(140.dp).height(80.dp).background(Color.DarkGray, RoundedCornerShape(8.dp)))
            }
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("E${episode.episode_number} - ${episode.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                episode.overview?.let {
                    Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun MoviePlayButton(onPlay: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "play_scale")

    Card(
        onClick = onPlay,
        modifier = Modifier
            .height(54.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color(0xFFD4AF37),
            focusedContainerColor = Color(0xFFFFD700)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(27.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
            Text("Assistir Agora", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

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
