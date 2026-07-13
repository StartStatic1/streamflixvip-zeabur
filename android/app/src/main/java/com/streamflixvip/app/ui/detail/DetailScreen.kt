package com.streamflixvip.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbSeason
import com.streamflixvip.app.network.VipSource

private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

/**
 * Tela de Detalhes: backdrop, sinopse, gêneros — e a lista de fontes
 * disponíveis pra assistir. Pra filme, a lista de fontes já vem pronta.
 * Pra série, primeiro escolhe a temporada/episódio, e só então busca as
 * fontes daquele episódio específico (mesma lógica do site: cada
 * episódio tem suas próprias fontes cadastradas).
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onPlaySource: (source: VipSource, season: Int, episode: Int, title: String, posterPath: String?) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        is DetailUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DetailUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Não foi possível carregar este título.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadDetails() }) { Text("Tentar de novo") }
                }
            }
        }
        is DetailUiState.Success -> {
            DetailContent(state = s, onPlaySource = onPlaySource, onSelectEpisode = viewModel::loadEpisodeSources)
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailUiState.Success,
    onPlaySource: (source: VipSource, season: Int, episode: Int, title: String, posterPath: String?) -> Unit,
    onSelectEpisode: (season: Int, episode: Int) -> Unit,
) {
    val details = state.details
    val title = details.title ?: details.name ?: "Sem título"
    val posterPath = details.poster_path
    val backdropUrl = details.backdrop_path?.let { TMDB_BACKDROP_BASE + it }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                details.vote_average?.let {
                    Text("⭐ ${"%.1f".format(it)}", fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                details.overview?.let { overview ->
                    Text(overview, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }

        if (state.mediaType == "movie") {
            item {
                SourcesSection(
                    sources = state.movieSources,
                    onPlaySource = { source -> onPlaySource(source, 0, 0, title, posterPath) },
                )
            }
        } else {
            val seasons = details.seasons.orEmpty().filter { it.season_number > 0 } // ignora "specials" (temporada 0)
            item {
                Text(
                    "Temporadas",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(seasons) { season ->
                SeasonRow(season = season, onSelectEpisode = onSelectEpisode)
            }
            if (state.selectedSeason != null) {
                item {
                    SourcesSection(
                        sources = state.episodeSources,
                        onPlaySource = { source ->
                            onPlaySource(source, state.selectedSeason, state.selectedEpisode ?: 0, title, posterPath)
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun SeasonRow(
    season: TmdbSeason,
    onSelectEpisode: (season: Int, episode: Int) -> Unit,
) {
    // Versão simplificada: lista os episódios como números clicáveis.
    // Uma versão futura pode buscar título/sinopse de cada episódio via
    // /tv/{id}/season/{n} — por ora, número + toque já resolve o fluxo
    // essencial de "escolher episódio -> ver fontes -> assistir".
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(season.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..season.episode_count).forEach { epNum ->
                AssistChip(
                    onClick = { onSelectEpisode(season.season_number, epNum) },
                    label = { Text("Ep $epNum") },
                )
            }
        }
    }
}

@Composable
private fun SourcesSection(
    sources: List<VipSource>,
    onPlaySource: (VipSource) -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text("Servidores disponíveis", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (sources.isEmpty()) {
            Text("Nenhuma fonte cadastrada ainda.", fontSize = 13.sp)
        } else {
            sources.forEach { source ->
                SourceRow(source = source, onClick = { onPlaySource(source) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SourceRow(source: VipSource, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(source.displayName, fontSize = 14.sp)
        }
    }
}
