package com.streamflixvip.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onSeeAllClick: (HomeRowExploreLink) -> Unit,
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
            val scrollState = rememberLazyListState()

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Banner como item de verdade da lista — sobe e some com o
                // scroll naturalmente, sem precisar simular posição manual
                // nem deixar espaço vazio reservado quando ele desaparece.
                if (s.heroItems.isNotEmpty()) {
                    item {
                        HeroBanner(
                            items = s.heroItems,
                            onClick = { item -> onItemClick(item.id, item.resolvedMediaType) },
                        )
                    }
                }

                if (s.continueWatching.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        ContinueWatchingRow(entries = s.continueWatching, onItemClick = onContinueWatchingClick)
                        Spacer(Modifier.height(24.dp))
                    }
                }
                items(s.rows) { row ->
                    ContentRow(row = row, onItemClick = onItemClick, onSeeAllClick = onSeeAllClick)
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

    // Autoavança apenas quando há mais de um destaque e nunca interrompe o
    // arraste manual. O pager ocupa a largura inteira; não há cartas laterais
    // recortadas nem sobrepostas ao conteúdo principal.
    LaunchedEffect(pagerState, items) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6_000)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp) // Imersivo mas sem dominar a tela inteira no celular
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize(), // Ocupa todo o Box pai

            contentPadding = PaddingValues(0.dp),
            pageSpacing = 0.dp,
        ) { page ->
            val item = items[page]
            val heroImage = item.backdrop_path?.let { TMDB_BACKDROP_BASE + it }
                ?: item.poster_path?.let { TMDB_POSTER_BASE + it }
            val overview = item.overview?.takeIf { it.isNotBlank() }
                ?: "Confira detalhes, nota e opções para assistir."

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick(item) },
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = heroImage,
                        contentDescription = item.displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Black.copy(alpha = 0.34f),
                                        Color.Black.copy(alpha = 0.94f),
                                    ),
                                    startY = 80f,
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp), // Aumenta o padding interno
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = item.displayMediaLabel,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.displayTitle,
                            color = Color.White,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            item.displayYear?.let { year ->
                                Text(year, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                            }
                            if (item.displayYear != null && item.displayRating != null) {
                                Text(" • ", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                            item.displayRating?.let { rating ->
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(rating, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = overview,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onClick(item) },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Assistir", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp) // Ajusta o padding para os indicadores
                    .align(Alignment.BottomCenter), // Alinha os indicadores na parte inferior do Box
                horizontalArrangement = Arrangement.Center,
            ) {
                items.indices.forEach { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateFloatAsState(
                        targetValue = if (isActive) 22f else 7f,
                        label = "heroIndicatorWidth",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(width.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            ),
                    )
                }
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
    onSeeAllClick: (HomeRowExploreLink) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // "Ver mais" só aparece quando a fileira tem um filtro
            // equivalente na aba Explorar (gênero+ano) — fileiras como
            // Trending/Populares usam endpoints próprios da TMDB sem
            // correspondência direta em discover, então não expõem isso.
            row.exploreLink?.let { link ->
                Text(
                    text = "Ver mais",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSeeAllClick(link) },
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(row.items) { index, item ->
                PosterCard(
                    item = item,
                    onClick = { onItemClick(item.id, row.mediaType) },
                    rank = if (row.isRanked) index + 1 else null,
                )
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: TmdbItem,
    onClick: () -> Unit,
    rank: Int? = null,
) {
    val posterUrl = item.poster_path?.let { TMDB_POSTER_BASE + it }

    // Cards ranqueados (Top 10) ganham um espaço extra à esquerda pro
    // número gigante — sem isso, o número ficaria cortado ou sobreposto
    // de um jeito ruim em cima do próprio pôster.
    Row(verticalAlignment = Alignment.Bottom) {
        if (rank != null) {
            Text(
                text = "$rank",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.offset(x = 12.dp),
            )
        }
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
}
