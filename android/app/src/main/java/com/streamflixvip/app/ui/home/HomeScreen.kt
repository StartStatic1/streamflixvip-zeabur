package com.streamflixvip.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.WatchProgressEntry

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Tela inicial: lista vertical de fileiras horizontais, o mesmo padrão
 * visual que o site já usa (carrosséis por categoria). Tudo aqui é
 * Compose nativo — sem WebView, sem HTML renderizado. O scroll, os
 * gestos de arrastar, tudo roda no motor de renderização nativo do
 * Android (Skia via Compose), não no motor de um navegador embutido.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
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
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                if (s.continueWatching.isNotEmpty()) {
                    item {
                        ContinueWatchingRow(entries = s.continueWatching, onItemClick = onItemClick)
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

@Composable
private fun ContinueWatchingRow(
    entries: List<WatchProgressEntry>,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
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
                    onClick = { onItemClick(entry.tmdb_id, entry.media_type) },
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
    val posterUrl = entry.poster_path?.let { TMDB_IMAGE_BASE + it }

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
    val posterUrl = item.poster_path?.let { TMDB_IMAGE_BASE + it }

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
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.displayTitle,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.width(120.dp),
        )
    }
}
