package com.streamflixvip.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.streamflixvip.tv.network.TmdbCastMember
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.VipSource

private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_PROFILE = "https://image.tmdb.org/t/p/w185"

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
    val serverFocus = remember { FocusRequester() }

    var serverChoices by remember { mutableStateOf<List<VipSource>>(emptyList()) }
    var pendingSeason by remember { mutableIntStateOf(0) }
    var pendingEpisode by remember { mutableIntStateOf(0) }
    var pendingTitle by remember { mutableStateOf("") }
    var loadingSources by remember { mutableStateOf(false) }

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

                fun handleSources(sources: List<VipSource>, season: Int, episode: Int, title: String) {
                    loadingSources = false
                    if (sources.isEmpty()) return
                    if (sources.size == 1) {
                        serverChoices = emptyList()
                        onPlayClick(sources.first(), sources, season, episode, title, details.poster_path)
                    } else {
                        pendingSeason = season
                        pendingEpisode = episode
                        pendingTitle = title
                        serverChoices = sources
                    }
                }

                if (backdropUrl != null) {
                    AsyncImage(backdropUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color(0x880B0B14),
                                0.4f to Color(0xCC0B0B14),
                                0.75f to Bg,
                                1f to Bg,
                            ),
                        ),
                    )
                }

                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            displayTitle,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val year = (details.release_date ?: details.first_air_date)?.take(4)
                            year?.let { MetaPill(it) }
                            details.vote_average?.takeIf { it > 0 }?.let {
                                MetaPill("★ ${"%.1f".format(it)}")
                            }
                            details.displayRuntime?.let { MetaPill(it) }
                            details.genres.orEmpty().take(3).forEach { MetaPill(it.name) }
                        }

                        Spacer(Modifier.height(10.dp))
                        details.overview?.let {
                            Text(
                                it,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = if (isSeries) 2 else 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    loadingSources = true
                                    serverChoices = emptyList()
                                    if (isSeries) {
                                        val season = state.selectedSeason
                                        val title = "$displayTitle · T${season}E1"
                                        viewModel.loadEpisodeSources(season, 1) { sources ->
                                            handleSources(sources, season, 1, title)
                                        }
                                    } else {
                                        viewModel.loadMovieSources { sources ->
                                            handleSources(sources, 0, 0, displayTitle)
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
                                if (loadingSources) {
                                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text("Assistir Agora", fontWeight = FontWeight.Bold)
                            }
                            GlassButton(if (isFavorite) "Na Minha Lista" else "Minha Lista") {
                                isFavorite = libraryStore.toggleFavorite(
                                    LocalFavorite(tmdbId, mediaType, displayTitle, details.poster_path),
                                )
                            }
                        }

                        if (serverChoices.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Escolha o servidor", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(end = 8.dp),
                            ) {
                                items(serverChoices.size) { index ->
                                    val src = serverChoices[index]
                                    ServerChip(
                                        label = src.displayName,
                                        modifier = if (index == 0) Modifier.focusRequester(serverFocus) else Modifier,
                                        onClick = {
                                            onPlayClick(
                                                src,
                                                serverChoices,
                                                pendingSeason,
                                                pendingEpisode,
                                                pendingTitle.ifBlank { displayTitle },
                                                details.poster_path,
                                            )
                                            serverChoices = emptyList()
                                        },
                                    )
                                }
                            }
                            LaunchedEffect(serverChoices) {
                                runCatching { serverFocus.requestFocus() }
                            }
                        }
                    }

                    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

                    if (isSeries) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Text(
                                "Temporadas",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 48.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            // Padding no conteúdo da lista: primeiro e último chip cabem inteiros
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 48.dp),
                            ) {
                                items((1..(state.details?.number_of_seasons ?: 1)).toList()) { season ->
                                    SeasonChip(
                                        label = "T$season",
                                        selected = season == state.selectedSeason,
                                        onClick = {
                                            serverChoices = emptyList()
                                            viewModel.selectSeason(season)
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            if (state.loadingSeasons.contains(state.selectedSeason)) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = AccentSoft, modifier = Modifier.size(28.dp))
                                }
                            } else {
                                val episodes = state.seasonEpisodes[state.selectedSeason].orEmpty()
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 28.dp),
                                ) {
                                    items(episodes, key = { it.episode_number }) { ep ->
                                        EpisodeRow(
                                            episode = ep,
                                            onClick = {
                                                loadingSources = true
                                                serverChoices = emptyList()
                                                val title = "$displayTitle · T${state.selectedSeason}E${ep.episode_number}"
                                                viewModel.loadEpisodeSources(state.selectedSeason, ep.episode_number) { sources ->
                                                    handleSources(sources, state.selectedSeason, ep.episode_number, title)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val cast = details.credits?.cast.orEmpty().take(14)
                        if (cast.isNotEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                Text(
                                    "Elenco",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                // start/end no contentPadding: o último ator entra inteiro na tela ao focar/rolar
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 28.dp),
                                ) {
                                    items(cast) { member ->
                                        CastCard(member)
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Glass)
            .border(1.dp, GlassBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
    }
}

@Composable
private fun CastCard(member: TmdbCastMember) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(104.dp),
    ) {
        Box(
            Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A28))
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (member.profile_path != null) {
                AsyncImage(
                    model = "$TMDB_PROFILE${member.profile_path}",
                    contentDescription = member.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    member.name.take(1),
                    color = TextMuted,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            member.name,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        member.character?.let {
            Text(
                it,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServerChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                1.5.dp,
                if (focused) AccentSoft else GlassBorder,
                RoundedCornerShape(999.dp),
            ),
        colors = ButtonDefaults.colors(
            containerColor = if (focused) Accent.copy(alpha = 0.4f) else Glass,
            focusedContainerColor = Accent.copy(alpha = 0.5f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1)
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
