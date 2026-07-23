package com.streamflixvip.tv.ui.detail

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbCastMember
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.network.TmdbResponse
import com.streamflixvip.tv.network.VipSource

private const val TMDB_BACKDROP_W1280 = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"
private const val TMDB_PROFILE_W185 = "https://image.tmdb.org/t/p/w185"
private const val TMDB_STILL_W300 = "https://image.tmdb.org/t/p/w300"

/**
 * Tela de Detalhe de TV — design cinematográfico com backdrop grande,
 * abas de informações (Sinopse, Elenco, Temporadas, Similares), trailer
 * embutido e fluxo completo de fontes/servidores para assistir.
 *
 * Segue o padrão dos prints de referência: hero com backdrop, pills de
 * metadado, abas horizontais para trocar entre seções, cards circulares
 * pro elenco, cards de episódio com still.
 */
@Composable
fun DetailTvScreen(
    tmdbId: Int,
    mediaType: String,
    onPlayClick: (source: VipSource, season: Int, episode: Int, title: String) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit = {},
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onPlayTrailer: (trailerKey: String) -> Unit = {},
) {
    val viewModel = remember(tmdbId, mediaType) {
        DetailTvViewModel(tmdbId, mediaType)
    }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(tmdbId, mediaType) {
        viewModel.loadDetails()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Carregando...", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                }
            }
            state.showError != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Erro ao carregar", color = Color.Red, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.showError ?: "", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
            }
            state.details != null -> {
                DetailContent(
                    details = state.details!!,
                    seasonEpisodes = state.seasonEpisodes,
                    selectedSeason = state.selectedSeason,
                    similar = state.similar,
                    cast = state.cast,
                    trailerKey = state.trailerKey,
                    onBack = onBack,
                    onPlay = { season, episode ->
                        handlePlay(
                            details = state.details!!,
                            season = season,
                            episode = episode,
                            viewModel = viewModel,
                            onPlayClick = onPlayClick,
                        )
                    },
                    onPlayMovie = {
                        handleMoviePlay(
                            details = state.details!!,
                            viewModel = viewModel,
                            onPlayClick = onPlayClick,
                        )
                    },
                    onSelectSeason = viewModel::selectSeason,
                    onExpandEpisode = viewModel::expandEpisode,
                    onOpenTitle = onOpenTitle,
                    onPlayTrailer = onPlayTrailer,
                )
            }
        }

        // Server picker
        if (state.showServerPicker) {
            ServerPickerSheet(
                sources = state.sources,
                onDismiss = viewModel::dismissServerPicker,
                onSelect = { source ->
                    viewModel.pickServer(source) {
                        // Placeholder — o caller (MainTvActivity) trata
                    }
                },
            )
        }
    }
}

private fun handlePlay(
    details: TmdbResponse,
    season: Int,
    episode: Int,
    viewModel: DetailTvViewModel,
    onPlayClick: (VipSource, Int, Int, String) -> Unit,
) {
    val title = details.name ?: details.title ?: "Sem título"
    viewModel.loadEpisodeSources(season, episode) { source ->
        onPlayClick(source, season, episode, title)
    }
}

private fun handleMoviePlay(
    details: TmdbResponse,
    viewModel: DetailTvViewModel,
    onPlayClick: (VipSource, Int, Int, String) -> Unit,
) {
    val title = details.title ?: details.name ?: "Sem título"
    viewModel.loadMovieSources { source ->
        onPlayClick(source, 0, 0, title)
    }
}

/** Conteúdo principal da tela de detalhe. */
@Composable
private fun DetailContent(
    details: TmdbResponse,
    seasonEpisodes: Map<Int, List<TmdbEpisode>>,
    selectedSeason: Int,
    similar: List<TmdbItem>,
    cast: List<TmdbCastMember>,
    trailerKey: String?,
    onBack: () -> Unit,
    onPlay: (season: Int, episode: Int) -> Unit,
    onPlayMovie: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onExpandEpisode: (Int) -> Unit,
    onOpenTitle: (Int, String) -> Unit,
    onPlayTrailer: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        // ── Hero com backdrop ──
        val backdropUrl = details.backdrop_path?.let { "$TMDB_BACKDROP_W1280$it" }
        val posterUrl = details.poster_path?.let { "$TMDB_POSTER_W342$it" }
        val isMovie = details.title != null
        val title = details.title ?: details.name ?: "Sem título"
        val year = (details.release_date ?: details.first_air_date)?.take(4)
        val rating = details.vote_average?.takeIf { it > 0 }?.let { "%.1f".format(it) }
        val runtime = details.displayRuntime

        item(key = "hero") {
            HeroBackdrop(
                backdropUrl = backdropUrl,
                posterUrl = posterUrl,
                title = title,
                year = year,
                rating = rating,
                runtime = runtime,
                genres = details.genres.orEmpty().map { it.name },
                overview = details.overview,
                tagline = details.tagline,
                onBack = onBack,
                onPlay = {
                    if (isMovie) onPlayMovie() else onPlay(1, 1)
                },
                trailerKey = trailerKey,
                onPlayTrailer = onPlayTrailer,
            )
        }

        // ── Abas de conteúdo ──
        if (cast.isNotEmpty() || similar.isNotEmpty() || seasonEpisodes.isNotEmpty()) {
            item(key = "tabTitle") {
                DetailSectionTitle("Informações")
            }

            // Elenco
            if (cast.isNotEmpty()) {
                item(key = "cast") {
                    CastSection(cast = cast)
                }
            }

            // Temporadas e episódios (só séries)
            if (!isMovie && seasonEpisodes.isNotEmpty()) {
                items(
                    items = seasonEpisodes.keys.sorted(),
                    key = { it },
                ) { seasonNum ->
                    SeasonSection(
                        seasonNumber = seasonNum,
                        episodes = seasonEpisodes[seasonNum].orEmpty(),
                        isSelected = seasonNum == selectedSeason,
                        onSelect = { onSelectSeason(seasonNum) },
                        onPlayEpisode = { episode -> onPlay(seasonNum, episode) },
                        onExpand = onExpandEpisode,
                    )
                }
            }

            // Similares
            if (similar.isNotEmpty()) {
                item(key = "similar_title") {
                    DetailSectionTitle("Títulos Similares")
                }
                item(key = "similar") {
                    SimilarRow(
                        items = similar,
                        onOpenTitle = onOpenTitle,
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/** Hero com backdrop + poster sobreposto + info + botões. */
@Composable
private fun HeroBackdrop(
    backdropUrl: String?,
    posterUrl: String?,
    title: String,
    year: String?,
    rating: String?,
    runtime: String?,
    genres: List<String>,
    overview: String?,
    tagline: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    trailerKey: String?,
    onPlayTrailer: (String) -> Unit,
) {
    Column {
        // Backdrop area
        Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
            }

            // Gradientes para legibilidade
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0A0A10),
                            Color(0xFF0A0A10).copy(alpha = 0.88f),
                            Color(0xFF0A0A10).copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        endX = 850f,
                    ),
                ),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0A0A10)),
                        startY = 250f,
                    ),
                ),
            )

            // Botão voltar
            Card(
                onClick = onBack,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .size(40.dp),
                colors = CardDefaults.colors(containerColor = Color.Black.copy(alpha = 0.5f)),
                shape = CardDefaults.shape(shape = CircleShape),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Conteúdo do hero
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, bottom = 32.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Poster sobreposto
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Info ao lado do poster
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    tagline?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            fontSize = 14.sp,
                            color = Color(0xFFD4AF37),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    Text(
                        title,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        year?.let { MetaPillDetail(it) }
                        runtime?.let { MetaPillDetail(it) }
                        genres.take(2).forEach { MetaPillDetail(it) }
                        rating?.let { RatingPillDetail(it) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    overview?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 3,
                            modifier = Modifier.widthIn(max = 480.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botões
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Card(
                            onClick = onPlay,
                            modifier = Modifier.height(42.dp),
                            colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)),
                            shape = CardDefaults.shape(shape = RoundedCornerShape(21.dp)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Assistir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        if (trailerKey != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Card(
                                onClick = { onPlayTrailer(trailerKey) },
                                modifier = Modifier.height(42.dp),
                                colors = CardDefaults.colors(containerColor = Color.Transparent),
                                shape = CardDefaults.shape(shape = RoundedCornerShape(21.dp)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 22.dp, vertical = 8.dp)
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(21.dp)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Trailer", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaPillDetail(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun RatingPillDetail(rating: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFFFC107), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
            Text(" $rating", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DetailSectionTitle(title: String) {
    Text(
        title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(start = 48.dp, top = 28.dp, bottom = 12.dp),
    )
}

/** Seção do elenco — cards circulares com foto + nome + personagem. */
@Composable
private fun CastSection(cast: List<TmdbCastMember>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(cast.take(15)) { member ->
            Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = member.profile_path?.let { "$TMDB_PROFILE_W185$it" },
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    member.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    member.character ?: "",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Seção de temporada com episódios — header selecionável + lista expansível. */
@Composable
private fun SeasonSection(
    seasonNumber: Int,
    episodes: List<TmdbEpisode>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onExpand: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 48.dp, top = 16.dp, end = 48.dp)) {
        // Header da temporada
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Temporada $seasonNumber",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .background(
                        if (isSelected) Color(0xFFD4AF37).copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${episodes.size} episódios",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }

        // Episódios (só mostra se selecionado)
        if (isSelected) {
            episodes.forEach { episode ->
                EpisodeCard(
                    episode = episode,
                    seasonNumber = seasonNumber,
                    onPlay = { onPlayEpisode(episode.episode_number) },
                    onExpand = { onExpand(episode.episode_number) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** Card de episódio com still, número, título, sinopse e duração. */
@Composable
private fun EpisodeCard(
    episode: TmdbEpisode,
    seasonNumber: Int,
    onPlay: () -> Unit,
    onExpand: () -> Unit,
) {
    Card(
        onClick = onExpand,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.colors(containerColor = Color(0xFF15151F)),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Still do episódio
            val stillUrl = episode.still_path?.let { "$TMDB_STILL_W300$it" }
            if (stillUrl != null) {
                AsyncImage(
                    model = stillUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(160.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "E${episode.episode_number}",
                        fontSize = 13.sp,
                        color = Color(0xFFD4AF37),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        episode.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                episode.displayRuntime?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Sinopse truncada
                episode.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botão play
                Card(
                    onClick = onPlay,
                    modifier = Modifier.height(34.dp),
                    colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(17.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp),
                        )
                        Text("Assistir", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Linha de títulos similares com carrossel horizontal. */
@Composable
private fun SimilarRow(items: List<TmdbItem>, onOpenTitle: (Int, String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items.take(15)) { item ->
            Column(modifier = Modifier.width(130.dp)) {
                Card(
                    onClick = { onOpenTitle(item.id, item.resolvedMediaType) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    colors = CardDefaults.colors(containerColor = Color(0xFF1E1E2E)),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
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
                                    .padding(5.dp)
                                    .background(Color(0xFFFFC107), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(9.dp),
                                    )
                                    Text(" $rating", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    item.displayTitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Sheet de seleção de servidor (bottom sheet no estilo TV). */
@Composable
private fun ServerPickerSheet(
    sources: List<VipSource>,
    onDismiss: () -> Unit,
    onSelect: (VipSource) -> Unit,
) {
    // Backdrop scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        // Painel central
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(420.dp)
                .background(Color(0xFF1A1A28), RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text(
                "Escolha o servidor",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            sources.forEachIndexed { index, source ->
                Card(
                    onClick = { onSelect(source) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.colors(containerColor = Color(0xFF252536)),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "S${index + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD4AF37),
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(
                            source.displayName,
                            fontSize = 15.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.colors(containerColor = Color.Transparent),
                shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }
    }
}
