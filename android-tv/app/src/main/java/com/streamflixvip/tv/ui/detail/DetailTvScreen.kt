package com.streamflixvip.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.data.LocalFavorite
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.VipSource

private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280"

private val Bg = Color(0xFF0B0B14)
private val Accent = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)
private val PlayBlue = Color(0xFF3B82F6)
private val PlayFocus = Color(0xFF7C3AED)
private val TextMuted = Color(0xFFA1A1B5)
private val Glass = Color.White.copy(alpha = 0.08f)
private val GlassBorder = Color.White.copy(alpha = 0.14f)
private val GlassStrong = Color.White.copy(alpha = 0.14f)

@Composable
fun DetailTvScreen(
    tmdbId: Int,
    mediaType: String,
    viewModel: DetailTvViewModel = viewModel(factory = DetailTvViewModelFactory(tmdbId, mediaType)),
    onPlayClick: (source: VipSource, sources: List<VipSource>, season: Int, episode: Int, title: String, posterPath: String?) -> Unit = { _, _, _, _, _, _ -> },
    onBack: () -> Unit = {},
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(tmdbId) { viewModel.loadDetails() }

    val context = LocalContext.current
    val libraryStore = remember(context) { LocalLibraryStore(context) }
    val playFocus = remember { FocusRequester() }

    // Episódio pendente quando o seletor de servidor abre (série)
    var pendingSeason by remember { mutableIntStateOf(1) }
    var pendingEpisode by remember { mutableIntStateOf(1) }
    var pendingEpisodeTitle by remember { mutableStateOf("") }
    var localSources by remember { mutableStateOf<List<VipSource>>(emptyList()) }
    var showServerPicker by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentSoft)
            }
            state.showError != null && state.details == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.showError!!, color = TextMuted)
                    Spacer(Modifier.height(16.dp))
                    GlassButton("Tentar novamente") { viewModel.retryLoad() }
                }
            }
            else -> {
                val details = state.details ?: return@Box
                val backdropUrl = details.backdrop_path?.let { "$TMDB_BACKDROP$it" }
                val displayTitle = details.title ?: details.name ?: "Sem título"
                val isSeries = mediaType == "tv"
                var isFavorite by remember(tmdbId, mediaType) {
                    mutableStateOf(libraryStore.isFavorite(tmdbId, mediaType))
                }

                fun startWithSources(sources: List<VipSource>, season: Int, episode: Int, title: String) {
                    if (sources.isEmpty()) return
                    if (sources.size == 1) {
                        onPlayClick(sources.first(), sources, season, episode, title, details.poster_path)
                    } else {
                        localSources = sources
                        pendingSeason = season
                        pendingEpisode = episode
                        pendingEpisodeTitle = title
                        showServerPicker = true
                    }
                }

                // Backdrop full
                if (backdropUrl != null) {
                    AsyncImage(backdropUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color(0x990B0B14),
                                0.35f to Color(0xCC0B0B14),
                                0.7f to Bg,
                                1f to Bg,
                            ),
                        ),
                    )
                }

                Column(Modifier.fillMaxSize()) {
                    // Top bar
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
                        }
                    }

                    // Hero info
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        Text(
                            displayTitle,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        details.overview?.let {
                            Text(
                                it,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = if (isSeries) 2 else 4,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (isSeries) {
                                        val season = state.selectedSeason
                                        viewModel.loadEpisodeSources(season, 1) { source ->
                                            // loadEpisodeSources chama onSuccess só com 1 fonte;
                                            // com várias, o estado sources é atualizado — tratamos abaixo
                                            val sources = state.sources.ifEmpty { listOf(source) }
                                            startWithSources(sources, season, 1, "$displayTitle · T${season}E1")
                                        }
                                        // Fallback: se múltiplas, o ViewModel seta sources + showServerPicker
                                    } else {
                                        viewModel.loadMovieSources { source ->
                                            val sources = state.sources.ifEmpty { listOf(source) }
                                            startWithSources(sources, 0, 0, displayTitle)
                                        }
                                    }
                                },
                                modifier = Modifier.focusRequester(playFocus),
                                colors = ButtonDefaults.colors(
                                    containerColor = PlayBlue,
                                    focusedContainerColor = PlayFocus,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Assistir Agora", fontWeight = FontWeight.Bold)
                            }
                            GlassButton(if (isFavorite) "Na Minha Lista" else "Minha Lista") {
                                isFavorite = libraryStore.toggleFavorite(
                                    LocalFavorite(tmdbId, mediaType, displayTitle, details.poster_path),
                                )
                            }
                        }
                    }

                    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

                    // Sincroniza picker do ViewModel (quando load*Sources acha >1 fonte)
                    LaunchedEffect(state.showServerPicker, state.sources) {
                        if (state.showServerPicker && state.sources.size > 1) {
                            localSources = state.sources
                            if (!isSeries) {
                                pendingSeason = 0
                                pendingEpisode = 0
                                pendingEpisodeTitle = displayTitle
                            }
                            showServerPicker = true
                            viewModel.dismissServerPicker()
                        }
                    }

                    if (isSeries) {
                        // Temporadas + episódios
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 48.dp),
                        ) {
                            Text("Temporadas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items((1..(state.details?.number_of_seasons ?: 1)).toList()) { season ->
                                    SeasonChip(
                                        label = "T$season",
                                        selected = season == state.selectedSeason,
                                        onClick = { viewModel.selectSeason(season) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))

                            if (state.loadingSeasons.contains(state.selectedSeason)) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = AccentSoft, modifier = Modifier.size(28.dp))
                                }
                            } else {
                                val episodes = state.seasonEpisodes[state.selectedSeason].orEmpty()
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                ) {
                                    items(episodes, key = { it.episode_number }) { ep ->
                                        EpisodeRow(
                                            episode = ep,
                                            onClick = {
                                                val title = "$displayTitle · T${state.selectedSeason}E${ep.episode_number}"
                                                pendingSeason = state.selectedSeason
                                                pendingEpisode = ep.episode_number
                                                pendingEpisodeTitle = title
                                                viewModel.loadEpisodeSources(state.selectedSeason, ep.episode_number) { source ->
                                                    val sources = state.sources.ifEmpty { listOf(source) }
                                                    startWithSources(
                                                        sources,
                                                        state.selectedSeason,
                                                        ep.episode_number,
                                                        title,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Overlay de servidores (não empurra o layout)
                if (showServerPicker && localSources.isNotEmpty()) {
                    ServerPickerOverlay(
                        sources = localSources,
                        onPick = { src ->
                            showServerPicker = false
                            onPlayClick(
                                src,
                                localSources,
                                pendingSeason,
                                pendingEpisode,
                                pendingEpisodeTitle.ifBlank { displayTitle },
                                details.poster_path,
                            )
                        },
                        onDismiss = { showServerPicker = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerPickerOverlay(
    sources: List<VipSource>,
    onPick: (VipSource) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.4f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF0121220))
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(22.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Escolha o servidor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Fechar", tint = TextMuted)
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources) { src ->
                    Button(
                        onClick = { onPick(src) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.colors(
                            containerColor = Glass,
                            focusedContainerColor = Accent.copy(alpha = 0.4f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                        ),
                    ) {
                        Text(src.displayName, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = when {
                selected -> Accent.copy(alpha = 0.45f)
                focused -> GlassStrong
                else -> Glass
            },
            focusedContainerColor = Accent.copy(alpha = 0.55f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                1.dp,
                when {
                    selected || focused -> AccentSoft
                    else -> GlassBorder
                },
                RoundedCornerShape(999.dp),
            ),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun EpisodeRow(episode: TmdbEpisode, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        colors = CardDefaults.colors(
            containerColor = Glass,
            focusedContainerColor = Accent.copy(alpha = 0.28f),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentSoft),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "E${episode.episode_number}",
                color = AccentSoft,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.width(48.dp),
            )
            Text(
                episode.name ?: "Episódio ${episode.episode_number}",
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun GlassButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = GlassStrong,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
    ) {
        Text(text, fontSize = 14.sp)
    }
}
