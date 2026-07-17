package com.streamflixvip.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.WatchProgressEntry
import kotlinx.coroutines.delay

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

/**
 * Tela inicial: banner rotativo em destaque + fileiras horizontais por
 * categoria. Tudo aqui é Compose nativo — sem WebView, sem HTML
 * renderizado. O scroll, os gestos de arrastar, tudo roda no motor de
 * renderização nativo do Android (Skia via Compose), não no motor de um
 * navegador embutido.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
    onContinueWatchingClick: (WatchProgressEntry) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        is HomeUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HomeUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Não foi possível carregar o catálogo.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadHome() }) { Text("Tentar de novo") }
                }
            }
        }
        is HomeUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (s.heroItems.isNotEmpty()) {
                    item {
                        HeroBanner(items = s.heroItems, onClick = { item -> onItemClick(item.id, "movie") })
                        Spacer(Modifier.height(24.dp))
                    }
                }
                if (s.continueWatching.isNotEmpty()) {
                    item {
                        ContinueWatchingRow(entries = s.continueWatching, onItemClick = onContinueWatchingClick)
                        Spacer(Modifier.height(24.dp))
                    }
                }
                items(s.rows) { row ->
                    ContentRow(row = row, onItemClick = onItemClick)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Banner rotativo em destaque, no topo da Home — o mesmo padrão visual
 * dos apps de referência (Netflix, Cinevia, Peacock): imagem grande,
 * gradiente escuro embaixo pra legibilidade, título + botão de assistir
 * por cima. Troca de slide sozinho a cada 6s, mas responde a swipe
 * manual do usuário a qualquer momento (HorizontalPager nativo).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroBanner(
    items: List<TmdbItem>,
    onClick: (TmdbItem) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    // Auto-avança o carrossel, mas não briga com o dedo do usuário: se a
    // pessoa está arrastando manualmente, pula a troca automática desse ciclo.
    LaunchedEffect(pagerState, items) {
        while (true) {
            delay(6_000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .padding(top = 20.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 60.dp),
            pageSpacing = (-20).dp, // Efeito de sobreposição leve
        ) { page ->
            val item = items[page]
            val posterUrl = item.poster_path?.let { TMDB_POSTER_BASE + it }
            
            // Cálculo para efeito de "carta" (escala e rotação leve baseada no scroll)
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val scale = 1f - (kotlin.math.abs(pageOffset) * 0.15f).coerceIn(0f, 0.25f)
            val rotation = pageOffset * 5f // Rotação leve lateral

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                        alpha = 1f - (kotlin.math.abs(pageOffset) * 0.3f).coerceIn(0f, 0.8f)
                    }
                    .clickable { onClick(item) },
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = posterUrl, // Usando Poster para estilo de carta
                        contentDescription = item.displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    startY = 400f,
                                ),
                            ),
                    )
                    Text(
                        text = item.displayTitle,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }

        // Indicadores de página (dots), mesmo padrão de carrossel dos apps
        // de referência — mostra qual slide está ativo sem precisar de texto.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.indices.forEach { index ->
                val isActive = pagerState.currentPage == index
                val width by animateFloatAsState(if (isActive) 18f else 6f, label = "dotWidth")
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isActive) Color.White else Color.White.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<WatchProgressEntry>,
    onItemClick: (WatchProgressEntry) -> Unit,
) {
    Column {
        Text(
            text = "Continuar assistindo",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onItemClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit,
) {
    val posterUrl = entry.poster_path?.let { TMDB_POSTER_BASE + it }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = entry.displayTitle,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            // Barra de progresso colada na base do poster — mesmo padrão
            // visual que Netflix/Prime/Disney+ usam pra "continuar assistindo".
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(entry.progressFraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.displayTitle,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(120.dp),
        )
        // Só faz sentido pra série (filme não tem season/episode reais —
        // chegam como 0 vindos do fluxo de filme). Ajuda a pessoa a saber
        // exatamente onde vai continuar, sem precisar abrir os detalhes.
        if (entry.media_type == "tv" && entry.season > 0) {
            Text(
                text = "T${entry.season}:E${entry.episode}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ContentRow(
    row: HomeRow,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    Column {
        Text(
            text = row.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(row.items) { item ->
                PosterCard(item = item, onClick = { onItemClick(item.id, row.mediaType) })
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: TmdbItem,
    onClick: () -> Unit,
) {
    val posterUrl = item.poster_path?.let { TMDB_POSTER_BASE + it }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = item.displayTitle,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            // Badge de nota no canto — mesmo padrão dos apps de referência
            // (estrela + número), só aparece se o TMDB já tem votos suficientes.
            item.displayRating?.let { rating ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(rating, fontSize = 10.sp, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.displayTitle,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(120.dp),
        )
        item.displayYear?.let { year ->
            Text(
                text = year,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
