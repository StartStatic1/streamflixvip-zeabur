package com.streamflixvip.app.ui.explore

import com.streamflixvip.app.network.TmdbImages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.data.TMDB_GENRES
import com.streamflixvip.app.network.TmdbItem


/**
 * Explorar com filtros sempre visíveis:
 * Categoria · Gênero · Ano — toque aplica na hora (sem bottom sheet).
 */
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    val filters = when (val s = state) {
        is ExploreUiState.Success -> s.filters
        ExploreUiState.Loading -> ExploreFilters()
    }

    LaunchedEffect(gridState, state) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val s = state as? ExploreUiState.Success ?: return@collect
                val nearEnd = lastVisible != null && lastVisible >= s.items.size - 6
                if (nearEnd) viewModel.loadNextPage()
            }
    }

    Column(Modifier.fillMaxSize()) {
        // Categoria
        ChipRow {
            GenreCategory.entries.forEach { category ->
                FilterChipPill(
                    label = category.label,
                    selected = category == filters.category,
                    onClick = { viewModel.applyFilters(filters.copy(category = category)) },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Gênero
        ChipRow {
            FilterChipPill(
                label = "Todos",
                selected = filters.genre == null,
                onClick = { viewModel.applyFilters(filters.copy(genre = null)) },
            )
            TMDB_GENRES.forEach { genre ->
                FilterChipPill(
                    label = genre.displayName,
                    selected = filters.genre == genre,
                    onClick = { viewModel.applyFilters(filters.copy(genre = genre)) },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Ano
        ChipRow {
            FilterChipPill(
                label = "Todos",
                selected = filters.year == null,
                onClick = { viewModel.applyFilters(filters.copy(year = null)) },
            )
            EXPLORE_YEARS.forEach { year ->
                FilterChipPill(
                    label = "$year",
                    selected = filters.year == year,
                    onClick = { viewModel.applyFilters(filters.copy(year = year)) },
                )
            }
        }

        // Resumo compacto quando há filtro ativo
        if (filters.genre != null || filters.year != null) {
            val parts = buildList {
                filters.genre?.let { add(it.displayName) }
                filters.year?.let { add("$it") }
            }
            Text(
                parts.joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }

        when (val s = state) {
            is ExploreUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is ExploreUiState.Success -> {
                if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nenhum título encontrado com esses filtros.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(s.items, key = { "${it.id}_${it.resolvedMediaType}" }) { item ->
                            ExploreCard(
                                item = item,
                                onClick = { onItemClick(item.id, item.resolvedMediaType) },
                            )
                        }
                        if (s.isLoadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ExploreCard(item: TmdbItem, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = item.poster_path?.let { TmdbImages.poster(it) },
            contentDescription = item.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(4.dp))
        Text(item.displayTitle, fontSize = 12.sp, maxLines = 1)
        Row {
            Text(
                if (item.resolvedMediaType == "movie") "FILME" else "SÉRIE",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            val year = (item.release_date ?: item.first_air_date)?.take(4)
            if (year != null) {
                Text(year, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
