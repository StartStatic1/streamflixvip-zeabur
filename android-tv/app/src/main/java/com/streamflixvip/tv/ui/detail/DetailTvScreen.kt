package com.streamflixvip.tv.ui.detail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.streamflixvip.tv.data.LocalFavorite
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.network.VipSource

private const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280"

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

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFD4AF37))
            }
            return@Box
        }
        if (state.showError != null && state.details == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.showError!!, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.retryLoad() }) { Text("Tentar novamente") }
                }
            }
            return@Box
        }
        val details = state.details ?: return@Box
        val backdropUrl = details.backdrop_path?.let { "$TMDB_BACKDROP$it" }
        val displayTitle = details.title ?: details.name ?: "Sem titulo"
        val isSeries = mediaType == "tv"
        val showPanel = state.showServerPicker && state.sources.size > 1
        val libraryStore = remember { LocalLibraryStore(LocalContext.current) }
        var isFavorite by remember(tmdbId, mediaType) {
            mutableStateOf(libraryStore.isFavorite(tmdbId, mediaType))
        }

        Column(Modifier.fillMaxSize().let { if (isSeries) it.verticalScroll(rememberScrollState()) else it }) {
            Row(Modifier.fillMaxWidth().let { if (isSeries) it.height(520.dp) else it.fillMaxHeight() }) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (backdropUrl != null) {
                        AsyncImage(backdropUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0A0A10), Color(0xFF0A0A10).copy(0.75f), Color.Transparent), endX = 900f)))
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
                        Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(48.dp).widthIn(max = 760.dp)) {
                        Text(displayTitle, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                        Spacer(Modifier.height(12.dp))
                        details.overview?.let {
                            Text(it, fontSize = 14.sp, color = Color.White.copy(0.8f), maxLines = 3)
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                if (isSeries) {
                                    val season = state.selectedSeason
                                    viewModel.loadEpisodeSources(season, 1) { source ->
                                        onPlayClick(source, state.sources, season, 1, displayTitle, details.poster_path)
                                    }
                                } else {
                                    viewModel.loadMovieSources { source ->
                                        onPlayClick(source, state.sources, 0, 0, displayTitle, details.poster_path)
                                    }
                                }
                            }) { Text("Assistir Agora") }
                            Button(onClick = {
                                isFavorite = libraryStore.toggleFavorite(
                                    LocalFavorite(tmdbId, mediaType, displayTitle, details.poster_path)
                                )
                            }) { Text(if (isFavorite) "Na Minha Lista" else "Minha Lista") }
                        }
                    }
                }
                AnimatedVisibility(visible = showPanel, enter = fadeIn() + slideInHorizontally { it }, exit = fadeOut() + slideOutHorizontally { it }) {
                    Column(Modifier.fillMaxHeight().width(420.dp).background(Color(0xFF12121A)).padding(28.dp)) {
                        Text("Escolha o Servidor", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.sources) { src ->
                                Button(onClick = {
                                    viewModel.pickServer(src) {
                                        if (isSeries) onPlayClick(src, state.sources, state.selectedSeason, 1, displayTitle, details.poster_path)
                                        else onPlayClick(src, state.sources, 0, 0, displayTitle, details.poster_path)
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text(src.displayName) }
                            }
                        }
                    }
                }
            }
            if (isSeries) {
                Column(Modifier.fillMaxWidth().padding(48.dp, 20.dp)) {
                    Text("Temporadas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items((1..(state.details?.number_of_seasons ?: 1)).toList()) { season ->
                            Button(onClick = { viewModel.selectSeason(season) }) { Text("T$season") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val episodes = state.seasonEpisodes[state.selectedSeason].orEmpty()
                    for (ep in episodes) {
                        Card(
                            onClick = {
                                viewModel.loadEpisodeSources(state.selectedSeason, ep.episode_number) { source ->
                                    onPlayClick(source, state.sources, state.selectedSeason, ep.episode_number, "$displayTitle - S${state.selectedSeason}E${ep.episode_number}", details.poster_path)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text("E${ep.episode_number} - ${ep.name}", modifier = Modifier.padding(16.dp), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
