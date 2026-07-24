package com.streamflixvip.tv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.TmdbItem

private const val TMDB_BACKDROP_W1280 = "https://image.tmdb.org/t/p/w1280"
private const val TMDB_POSTER_W342 = "https://image.tmdb.org/t/p/w342"

@Composable
fun HomeTvScreen(
    viewModel: HomeTvViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onNavigateToSearch: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAll()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        Row(modifier = Modifier.fillMaxSize()) {
            SidebarNav(onNavigateToSearch = onNavigateToSearch)

            Column(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error, color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                onClick = { viewModel.loadAll() },
                                colors = CardDefaults.colors(containerColor = Color(0xFFD4AF37)),
                                shape = CardDefaults.shape(RoundedCornerShape(12.dp))
                            ) {
                                Text("Tentar novamente", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // CORREÇÃO BUG #2: focusRequester para primeira row
                    // (precisa ser criado aqui fora, em contexto @Composable —
                    // dentro do LazyColumn { } o escopo é LazyListScope, não Composable)
                    val firstRow = remember { FocusRequester() }
                    val secondRow = remember { FocusRequester() }
                    val thirdRow = remember { FocusRequester() }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 60.dp),
                    ) {
                        val heroItem = state.heroItems.firstOrNull()
                        if (heroItem != null) {
                            item(key = "hero") {
                                HeroSection(
                                    backdropUrl = heroItem.backdrop_path?.let { "$TMDB_BACKDROP_W1280$it" },
                                    title = heroItem.displayTitle,
                                    overview = heroItem.overview,
                                    year = heroItem.displayYear,
                                    rating = heroItem.displayRating,
                                    genre = heroItem.displayMediaLabel,
                                    onPlayClick = { onItemClick(heroItem.id, heroItem.resolvedMediaType) },
                                    firstRowFocusRequester = remember { FocusRequester() },
                                )
                            }
                        }

                        item(key = "trending") {
                            ContentRow("Em Alta", state.trendingItems, onItemClick, firstRow)
                        }
                        item(key = "movies") {
                            ContentRow("Filmes Populares", state.popularMovies, onItemClick, secondRow)
                        }
                        item(key = "series") {
                            ContentRow("Séries Populares", state.popularSeries, onItemClick, thirdRow)
                        }
                        item(key = "action") {
                            ContentRow("Ação", state.actionItems, onItemClick)
                        }
                        item(key = "comedy") {
                            ContentRow("Comédia", state.comedyItems, onItemClick)
                        }
                        item(key = "drama") {
                            ContentRow("Drama", state.dramaItems, onItemClick)
                        }
                        item(key = "horror") {
                            ContentRow("Terror", state.horrorItems, onItemClick)
                        }
                        item(key = "scifi") {
                            ContentRow("Ficção Científica", state.scifiItems, onItemClick)
                        }
                        item(key = "anime") {
                            ContentRow("Animes", state.animeItems, onItemClick)
                        }
                        item(key = "family") {
                            ContentRow("Família", state.familyItems, onItemClick)
                        }
                    }
                }
            }
        }
    }
}

// ─── SIDEBAR FUNCIONAL ──────────────────────────────────────────────────────────

@Composable
private fun SidebarNav(onNavigateToSearch: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxHeight().width(80.dp).background(Color(0xFF12121A)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Logo
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFD4AF37)),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(40.dp))

        SidebarIcon(
            icon = Icons.Filled.Home,
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 }
        )
        SidebarIcon(
            icon = Icons.Filled.Search,
            selected = selectedTab == 1,
            onClick = {
                selectedTab = 1
                onNavigateToSearch()
            }
        )
        SidebarIcon(
            icon = Icons.Filled.Person,
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 }
        )
        SidebarIcon(
            icon = Icons.Filled.Settings,
            selected = selectedTab == 3,
            onClick = { selectedTab = 3 }
        )
    }
}

@Composable
private fun SidebarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.2f else 1f, label = "sidebar_scale")

    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(48.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (selected) Color(0xFFD4AF37).copy(alpha = 0.2f) else Color.Transparent,
            focusedContainerColor = Color(0xFFD4AF37).copy(alpha = 0.3f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected || isFocused) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ─── HERO SECTION ───────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(
    backdropUrl: String?,
    title: String,
    overview: String?,
    year: String?,
    rating: String?,
    genre: String?,
    onPlayClick: () -> Unit,
    firstRowFocusRequester: FocusRequester,
) {
    Box(modifier = Modifier.fillMaxWidth().height(440.dp)) {
        // Backdrop
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Gradiente horizontal (esquerda escura → direita transparente)
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0A0A10), Color(0xFF0A0A10).copy(alpha = 0.95f), Color(0xFF0A0A10).copy(alpha = 0.6f), Color.Transparent),
                    endX = 900f,
                ),
            ),
        )
        // Gradiente vertical (baixo escura)
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Transparent, Color(0xFF0A0A10)),
                    startY = 250f,
                ),
            ),
        )

        // Conteúdo do Hero
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 48.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Título
            Text(title, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(modifier = Modifier.height(12.dp))

            // Pills de metadado
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                year?.let { MetaPill(it) }
                rating?.let { RatingPill(it) }
                genre?.let { MetaPill(it) }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Sinopse truncada
            overview?.let {
                Text(it, fontSize = 16.sp, lineHeight = 22.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 3, modifier = Modifier.width(600.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Botão Assistir
            var isPlayFocused by remember { mutableStateOf(false) }
            val playScale by animateFloatAsState(if (isPlayFocused) 1.05f else 1f, label = "hero_play_scale")

            Card(
                onClick = onPlayClick,
                modifier = Modifier
                    .height(48.dp)
                    .focusRequester(firstRowFocusRequester)
                    .scale(playScale)
                    .onFocusChanged { isPlayFocused = it.isFocused },
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

// ─── CONTENT ROW (CARROSSEL) ───────────────────────────────────────────────────

@Composable
private fun ContentRow(
    title: String,
    items: List<TmdbItem>,
    onItemClick: (Int, String) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
        )

        LazyRow(
            modifier = Modifier.then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            ),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items) { item ->
                PosterCard(item) { onItemClick(item.id, item.resolvedMediaType) }
            }
        }
    }
}

// ─── POSTER CARD (CORREÇÃO BUG #4: CARDS MENORES) ──────────────────────────────

@Composable
private fun PosterCard(item: TmdbItem, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "poster_scale")

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(130.dp)  // REDUZIDO de 160dp para 130dp
            .aspectRatio(2f / 3f)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1E1E2E),
            focusedContainerColor = Color(0xFF2E2E3E)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.poster_path?.let { "$TMDB_POSTER_W342$it" },
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isFocused) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)))
            }
            // Badge de nota no canto superior
            item.displayRating?.let { rating ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color(0xFFFFC107), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(rating, fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
