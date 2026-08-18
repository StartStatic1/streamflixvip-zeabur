package com.streamflixvip.app.ui.home

import com.streamflixvip.app.network.TmdbImages

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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.startapp.sdk.ads.banner.Banner
import com.streamflixvip.app.data.VipStatusHolder
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay


@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
    onContinueWatchingClick: (WatchProgressEntry) -> Unit,
    onContinueWatchingDismiss: (WatchProgressEntry) -> Unit = {},
    onSeeAllClick: (HomeRowExploreLink) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshContinueWatching()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    when (val s = state) {
        is HomeUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
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
                        Spacer(Modifier.height(28.dp))
                        ContinueWatchingRow(entries = s.continueWatching, onItemClick = onContinueWatchingClick, onItemDismiss = onContinueWatchingDismiss)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                itemsIndexed(s.rows) { index, row ->
                    Spacer(Modifier.height(20.dp))
                    ContentRow(row = row, onItemClick = onItemClick, onSeeAllClick = onSeeAllClick)

                    val isVip by VipStatusHolder.isVip.collectAsState()
                    if (!isVip && (index + 1) % 3 == 0) {
                        Spacer(Modifier.height(20.dp))
                        StartIoBanner()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroBanner(
    items: List<TmdbItem>,
    onClick: (TmdbItem) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

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
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(400.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.7f),
            )
            .clip(RoundedCornerShape(22.dp))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
            pageSpacing = 0.dp,
        ) { page ->
            val item = items[page]
            val heroImage = item.backdrop_path?.let { TmdbImages.backdrop(it) }
                ?: item.poster_path?.let { TmdbImages.poster(it) }
            val overview = item.overview?.takeIf { it.isNotBlank() }
                ?: "Confira detalhes, nota e opções para assistir."

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick(item) },
            ) {
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
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.92f),
                                ),
                                startY = 60f,
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = item.displayMediaLabel,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.displayTitle,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        item.displayYear?.let { year ->
                            Text(year, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
                        }
                        if (item.displayYear != null && item.displayRating != null) {
                            Text(" • ", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                        item.displayRating?.let { rating ->
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(rating, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = overview,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onClick(item) },
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Assistir", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center,
            ) {
                items.indices.forEach { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateFloatAsState(
                        targetValue = if (isActive) 20f else 6f,
                        label = "heroIndicatorWidth",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(5.dp)
                            .width(width.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StartIoBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Patrocinado",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                Banner(context).apply { }
            }
        )
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<WatchProgressEntry>,
    onItemClick: (WatchProgressEntry) -> Unit,
    onItemDismiss: (WatchProgressEntry) -> Unit = {},
) {
    Column {
        Text(
            text = "Continuar assistindo",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(entries) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = { onItemClick(entry) },
                    onDismiss = { onItemDismiss(entry) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    val posterUrl = entry.poster_path?.let { TmdbImages.poster(it) }

    Column(
        modifier = Modifier
            .width(124.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.55f),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = entry.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // X para tirar da lista Continuar assistindo
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(entry.progressFraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                text = entry.displayTitle,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
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
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(row.items) { index, item ->
                // Top 10 (trending) mistura filme e série — usa o tipo real do item.
                // Nas outras fileiras o mediaType da row é confiável, mas resolvedMediaType
                // também cobre o caso corretamente.
                val clickType = item.resolvedMediaType.ifBlank { row.mediaType }
                PosterCard(
                    item = item,
                    onClick = { onItemClick(item.id, clickType) },
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
    val posterUrl = item.poster_path?.let { TmdbImages.poster(it) }
    // Números do ranking com accent suave (não gritante)
    val rankColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    Row(verticalAlignment = Alignment.Bottom) {
        if (rank != null) {
            Text(
                text = "$rank",
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = rankColor,
                modifier = Modifier.offset(x = 10.dp),
            )
        }
        Column(
            modifier = Modifier
                .width(124.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                )
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                item.displayRating?.let { rating ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.68f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(rating, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = item.displayTitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
}
