package com.streamflixvip.app.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
    onUpgradeClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Fonte já decidida (única disponível, ou escolhida no sheet de
    // servidor) aguardando a pessoa decidir COMO assistir — player
    // interno (chama onPlaySource de verdade, que navega pro player) ou
    // externo (abre um app de vídeo instalado via Intent, sem navegar
    // pra lugar nenhum dentro do próprio app). Fica neste nível, e não
    // dentro de DetailContent, porque tanto o fluxo de fonte única
    // (abaixo, em onSelectEpisode) quanto o de múltiplas fontes (dentro
    // de DetailContent, no sheet de servidor) precisam preenchê-lo.
    var pendingWatch by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<PendingSource?>(null)
    }

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
                onRequestWatch = { source, season, episode -> pendingWatch = PendingSource(source, season, episode) },
                onSelectEpisode = { season, episode, _, _ ->
                    viewModel.loadEpisodeSources(season, episode) { source ->
                        // Fonte única: já sabemos qual fonte usar, então
                        // pula direto pra decisão de COMO assistir (interno
                        // vs externo), sem exigir escolher servidor — não
                        // há o que escolher quando só existe um.
                        pendingWatch = PendingSource(source, season, episode)
                    }
                },
                onToggleSeason = viewModel::expandSeason,
                onDismissServerPicker = viewModel::closeServerPicker,
                onUpgradeClick = onUpgradeClick,
            )
        }
    }

    // O modal em si mora aqui (fora do when de loading/error/success),
    // ligado ao mesmo pendingWatch preenchido pelos dois fluxos acima —
    // um único lugar decide a UI de "player interno vs externo",
    // independente de ter vindo de filme, fonte única de série, ou
    // seletor de servidor de série.
    pendingWatch?.let { pending ->
        val successState = state as? DetailUiState.Success
        val title = successState?.details?.title ?: successState?.details?.name ?: "Sem título"
        val posterPath = successState?.details?.poster_path
        WatchOptionsSheet(
            source = pending.source,
            onDismiss = { pendingWatch = null },
            onPlayInternal = { source ->
                onPlaySource(source, pending.season, pending.episode, title, posterPath)
            },
        )
    }
}

/** Fonte já resolvida (única, ou escolhida no seletor de servidor) aguardando decisão de player interno/externo. */
private data class PendingSource(val source: VipSource, val season: Int, val episode: Int)

// ModalBottomSheet e rememberModalBottomSheetState ainda são marcados como
// @ExperimentalMaterial3Api pela própria biblioteca do Compose (podem
// mudar de assinatura em versões futuras) — o OptIn abaixo é a forma
// padrão de reconhecer isso e permitir o uso mesmo assim, já que é a
// via oficial (não um workaround) para abrir bottom sheets no Material3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    state: DetailUiState.Success,
    onRequestWatch: (source: VipSource, season: Int, episode: Int) -> Unit,
    onSelectEpisode: (season: Int, episode: Int, title: String, posterPath: String?) -> Unit,
    onToggleSeason: (season: Int) -> Unit,
    onDismissServerPicker: () -> Unit,
    onUpgradeClick: () -> Unit,
) {
    val details = state.details
    val title = details.title ?: details.name ?: "Sem título"
    val posterPath = details.poster_path
    val backdropUrl = details.backdrop_path?.let { TMDB_BACKDROP_BASE + it }
    val posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val isVip by com.streamflixvip.app.data.VipStatusHolder.isVip.collectAsState()
    var isFavorite by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                title = title,
                tagline = details.tagline,
                backdropUrl = backdropUrl,
                posterUrl = posterUrl,
                rating = details.vote_average,
                year = (details.release_date ?: details.first_air_date)?.take(4),
                runtimeLabel = details.displayRuntime,
                isFavorite = isFavorite,
                onToggleFavorite = { isFavorite = !isFavorite },
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
                    Text(overview, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
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
                    isLocked = state.movieIsLocked(isVip),
                    onPlaySource = { source -> onRequestWatch(source, 0, 0) },
                    onUpgradeClick = onUpgradeClick,
                )
            }
        } else {
            val seasons = details.seasons.orEmpty().filter { it.season_number > 0 } // ignora "specials" (temporada 0)
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Temporadas", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    // Aviso visível de quantos episódios são grátis, quando a
                    // série tem limite parcial configurado — ajuda a pessoa a
                    // entender o cadeado antes mesmo de esbarrar nele.
                    val freeLimit = state.vipConfig?.vip_free_episode_limit
                    val seriesFullyLocked = state.vipConfig?.vip_lock == true
                    if (!seriesFullyLocked && freeLimit != null && !isVip) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Grátis até o episódio $freeLimit — demais exigem VIP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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
                    isEpisodeLocked = { epNum -> state.episodeIsLocked(epNum, isVip) },
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
                                onDismissServerPicker()
                                onRequestWatch(source, state.selectedSeason ?: 0, state.selectedEpisode ?: 0)
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
    isEpisodeLocked: (Int) -> Boolean,
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
                            isLocked = isEpisodeLocked(epNum),
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
                            isLocked = isEpisodeLocked(ep.episode_number),
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
    isLocked: Boolean,
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
            when {
                isLocked -> {
                    // Camada escura + cadeado por cima da thumbnail — sinaliza
                    // bloqueio já na miniatura, antes mesmo de tocar no episódio.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🔒", fontSize = 20.sp)
                    }
                }
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${episode.episode_number}. ${episode.displayName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isLocked) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ) {
                        Text(
                            "VIP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
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

/**
 * Cabeçalho da tela de detalhes, no padrão visual do CineVerse: backdrop
 * grande com gradiente escuro na base (garante contraste pro texto sem
 * precisar de um scrim fixo), poster com sombra flutuando por cima
 * (profundidade, não fica "colado" na imagem de fundo), título grande,
 * tagline em itálico, e uma fileira de chips com a meta info essencial
 * (ano, duração, nota) — tudo escaneável num único golpe de vista antes
 * de decidir assistir.
 */
@Composable
private fun DetailHeader(
    title: String,
    tagline: String?,
    backdropUrl: String?,
    posterUrl: String?,
    rating: Double?,
    year: String?,
    runtimeLabel: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
        ) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Gradiente duplo: escurece o topo o suficiente pra status bar
            // não brigar com a imagem, e escurece a base pra que o poster
            // e o texto por cima fiquem sempre legíveis, não importa o
            // quão clara seja a cena do backdrop escolhido.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                0.5f to androidx.compose.ui.graphics.Color.Transparent,
                                1.0f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        // Botões de ação flutuando sobre o backdrop, um em cada canto —
        // mesmo padrão do CineVerse (coração à esquerda, compartilhar à
        // direita), fora do fluxo de leitura principal do título.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 140.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIconButton(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White,
                contentDescription = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = onToggleFavorite,
            )
            CircleIconButton(
                icon = Icons.Filled.Share,
                tint = androidx.compose.ui.graphics.Color.White,
                contentDescription = "Compartilhar",
                onClick = { /* integração de share fica a cargo da Activity host */ },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(Modifier),
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            tagline?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                year?.let { MetaChip(it) }
                runtimeLabel?.let { MetaChip(it) }
                rating?.let { MetaChip("⭐ ${"%.1f".format(it)}") }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SimpleEpisodeRow(
    episodeNumber: Int,
    isSelected: Boolean,
    isLoading: Boolean,
    isLocked: Boolean,
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
        when {
            isLocked -> Text("🔒", fontSize = 14.sp, modifier = Modifier.size(18.dp))
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else -> Icon(
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
    isLocked: Boolean,
    onPlaySource: (VipSource) -> Unit,
    onUpgradeClick: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text("Assistir agora", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (isLocked) {
            VipLockCard(onUpgradeClick = onUpgradeClick)
        } else if (sources.isEmpty()) {
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

/**
 * Cartão de cadeado exibido no lugar da lista de fontes quando o
 * título/episódio exige VIP. Usa a cor dourada do tema (primary), nunca
 * vermelho — cadeado comunica "exclusivo", não "erro".
 */
@Composable
private fun VipLockCard(onUpgradeClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔒", fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Conteúdo exclusivo VIP",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Assine o VIP para desbloquear este título e assistir sem espera.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                Text("Seja VIP agora")
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
