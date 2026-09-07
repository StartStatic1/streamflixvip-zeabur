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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.data.TMDB_GENRES
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.ui.theme.StreamFlixColors

/**
 * Explorar — categoria em destaque, gênero e ano em chips menores.
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

    Column(
        Modifier
            .fillMaxSize()
            .background(StreamFlixColors.Background),
    ) {
        ChipRow {
            GenreCategory.entries.forEach { category ->
                FilterChipPill(
                    label = category.label,
                    selected = category == filters.category,
                    emphasis = true,
                    onClick = { viewModel.applyFilters(filters.copy(category = category)) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

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

        ChipRow {
            FilterChipPill(
                label = "Anos",
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

        Spacer(Modifier.height(10.dp))

        when (val s = state) {
            is ExploreUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StreamFlixColors.Amber)
                }
            }
            is ExploreUiState.Success -> {
                if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nenhum título com esses filtros.",
                            color = StreamFlixColors.TextMuted,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = StreamFlixColors.Amber,
                                    )
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    emphasis: Boolean = false,
) {
    val bg = when {
        selected -> StreamFlixColors.Amber
        emphasis -> StreamFlixColors.SurfaceHigh
        else -> StreamFlixColors.SurfaceRaised
    }
    val fg = if (selected) StreamFlixColors.Background else StreamFlixColors.Text
    Text(
        label,
        color = if (selected) StreamFlixColors.Background else if (emphasis) StreamFlixColors.Text else StreamFlixColors.TextMuted,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = if (emphasis) 13.sp else 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (emphasis) 14.dp else 11.dp,
                vertical = if (emphasis) 8.dp else 6.dp,
            ),
    )
}

@Composable
private fun ExploreCard(item: TmdbItem, onClick: () -> Unit) {
    val year = (item.release_date ?: item.first_air_date)?.take(4)
    val kind = if (item.resolvedMediaType == "movie") "Filme" else "Série"
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = item.poster_path?.let { TmdbImages.poster(it) },
            contentDescription = item.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(StreamFlixColors.SurfaceRaised),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            item.displayTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = StreamFlixColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
        )
        Text(
            listOfNotNull(kind, year).joinToString(" · "),
            fontSize = 10.sp,
            color = StreamFlixColors.TextDim,
            maxLines = 1,
        )
    }
}
