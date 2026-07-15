package com.streamflixvip.app.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbSeason
import com.streamflixvip.app.network.VipSource

private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
private const val TMDB_STILL_BASE = "https://image.tmdb.org/t/p/w300"

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
            DetailContent(
                state = s,
                onPlaySource = onPlaySource,
                onSelectEpisode = { season, episode, title, posterPath ->
                    viewModel.loadEpisodeSources(season, episode) { source ->
                        // Fonte única: já dispara o player direto, sem
                        // exigir escolher servidor manualmente.
                        onPlaySource(source, season, episode, title, posterPath)
                    }
                },
                onToggleSeason = viewModel::expandSeason,
                onDismissServerPicker = viewModel::closeServerPicker,
            )
        }
    }
}

// ModalBottomSheet e rememberModalBottomSheetState ainda são marcados como
// @ExperimentalMaterial3Api pela própria biblioteca do Compose (podem
// mudar de assinatura em versões futuras) — o OptIn abaixo é a forma
// padrão de reconhecer isso e permitir o uso mesmo assim, já que é a
// via oficial (não um workaround) para abrir bottom sheets no Material3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    state: DetailUiState.Success,
    onPlaySource: (source: VipSource, season: Int, episode: Int, title: String, posterPath: String?) -> Unit,
    onSelectEpisode: (season: Int, episode: Int, title: String, posterPath: String?) -> Unit,
    onToggleSeason: (season: Int) -> Unit,
    onDismissServerPicker: () -> Unit,
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
            // Filme continua com a seção fixa de servidores — não há
            // conceito de "1 toque, já sabe o que tocar" aqui, porque não
            // existe um card de episódio anterior pra já disparar o play;
            // esta é a primeira e única decisão da tela.
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
                SeasonAccordion(
                    season = season,
                    isExpanded = state.expandedSeason == season.season_number,
                    episodes = if (state.expandedSeason == season.season_number) state.episodesOfExpandedSeason else emptyList(),
                    isLoadingEpisodes = state.isLoadingEpisodes && state.expandedSeason == season.season_number,
                    selectedSeason = state.selectedSeason,
                    selectedEpisode = state.selectedEpisode,
                    loadingEpisodeNumber = if (state.isLoadingEpisodeSources) state.selectedEpisode else null,
                    onToggle = { onToggleSeason(season.season_number) },
                    onSelectEpisode = { epNum -> onSelectEpisode(season.season_number, epNum, title, posterPath) },
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    // Só abre quando o episódio tocado tem 2+ servidores — ver a
    // decisão em DetailViewModel.loadEpisodeSources. Fonte única já
    // tocou direto e nunca chega a marcar showServerPickerForEpisode.
    if (state.mediaType == "tv" && state.showServerPickerForEpisode != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismissServerPicker,
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Episódio ${state.showServerPickerForEpisode} · Escolha o servidor",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
                Column(Modifier.padding(horizontal = 20.dp)) {
                    state.episodeSources.forEachIndexed { index, source ->
                        SourceRow(
                            source = source,
                            isRecommended = index == 0,
                            onClick = {
                                onPlaySource(
                                    source,
                                    state.selectedSeason ?: 0,
                                    state.selectedEpisode ?: 0,
                                    title,
                                    posterPath,
                                )
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonAccordion(
    season: TmdbSeason,
    isExpanded: Boolean,
    episodes: List<TmdbEpisode>,
    isLoadingEpisodes: Boolean,
    selectedSeason: Int?,
    selectedEpisode: Int?,
    loadingEpisodeNumber: Int?,
    onToggle: () -> Unit,
    onSelectEpisode: (episode: Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize() // anima a expansão/recolhimento em vez de "saltar" de tamanho
    ) {
        // Cabeçalho da temporada: nome + contagem de episódios + seta indicando estado.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(season.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${season.episode_count} episódios",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Recolher temporada" else "Expandir temporada",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isExpanded) {
            if (isLoadingEpisodes) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else if (episodes.isEmpty()) {
                // Fallback: se a TMDB não trouxe detalhe da temporada, ainda dá pra
                // escolher o episódio por número — melhor que travar a tela.
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    (1..season.episode_count).forEach { epNum ->
                        SimpleEpisodeRow(
                            episodeNumber = epNum,
                            isSelected = selectedSeason == season.season_number && selectedEpisode == epNum,
                            isLoading = selectedSeason == season.season_number && loadingEpisodeNumber == epNum,
                            onClick = { onSelectEpisode(epNum) },
                        )
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    episodes.forEach { ep ->
                        EpisodeCard(
                            episode = ep,
                            isSelected = selectedSeason == season.season_number && selectedEpisode == ep.episode_number,
                            isLoading = selectedSeason == season.season_number && loadingEpisodeNumber == ep.episode_number,
                            onClick = { onSelectEpisode(ep.episode_number) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/**
 * Card de episódio no padrão dos grandes apps de streaming: thumbnail 16:9,
 * número + título, duração, e sinopse resumida em 2 linhas. Um toque já
 * busca as fontes e decide sozinho — toca direto se só houver 1 servidor,
 * ou abre o seletor se houver mais de um (ver DetailViewModel). O ícone
 * de play deixa de ser decorativo: enquanto busca, vira um spinner, dando
 * feedback real de "algo está acontecendo" pro toque que a pessoa deu.
 */
@Composable
private fun EpisodeCard(
    episode: TmdbEpisode,
    isSelected: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (episode.still_path != null) {
                AsyncImage(
                    model = "$TMDB_STILL_BASE${episode.still_path}",
                    contentDescription = episode.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${episode.episode_number}. ${episode.displayName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.displayRuntime?.let { runtime ->
                Spacer(Modifier.height(2.dp))
                Text(
                    "⏱ $runtime",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!episode.overview.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    episode.overview,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Fallback usado só quando a TMDB não retorna detalhe da temporada. */
@Composable
private fun SimpleEpisodeRow(
    episodeNumber: Int,
    isSelected: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Episódio $episodeNumber",
            fontSize = 13.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SourcesSection(
    sources: List<VipSource>,
    onPlaySource: (VipSource) -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text("Onde assistir", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (sources.isEmpty()) {
            Text(
                "Nenhuma fonte disponível ainda para este título.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // A lista já vem ordenada por prioridade (maior primeiro) da
            // API — o primeiro item é a fonte que a gente recomenda, então
            // ganha destaque visual pra a pessoa não precisar adivinhar
            // qual servidor escolher.
            sources.forEachIndexed { index, source ->
                SourceRow(source = source, isRecommended = index == 0, onClick = { onPlaySource(source) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Extrai um selo de qualidade (4K/HD/SD etc) do nome da fonte, se houver. */
private fun qualityBadge(label: String?): String? {
    if (label == null) return null
    val upper = label.uppercase()
    return listOf("4K", "2160P", "1080P", "720P", "HD", "SD").firstOrNull { upper.contains(it) }
        ?.let { if (it == "2160P") "4K" else it }
}

@Composable
private fun SourceRow(source: VipSource, isRecommended: Boolean, onClick: () -> Unit) {
    val badge = qualityBadge(source.source_label)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isRecommended) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (isRecommended) {
                    Text(
                        "Recomendado",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            badge?.let {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
