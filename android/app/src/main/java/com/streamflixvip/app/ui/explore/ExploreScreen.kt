package com.streamflixvip.app.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
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
import com.streamflixvip.app.data.GenreDefinition
import com.streamflixvip.app.data.TMDB_GENRES
import com.streamflixvip.app.network.TmdbItem

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Aba Explorar: pills de categoria sempre visíveis + botão de filtro que
 * abre um bottom sheet com Gênero e Ano (chips horizontais roláveis,
 * seleção única cada). Um resumo textual mostra os filtros ativos acima
 * da grade, que rola infinitamente conforme a pessoa desce.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    val filters = when (val s = state) {
        is ExploreUiState.Success -> s.filters
        ExploreUiState.Loading -> ExploreFilters()
    }

    // Dispara a próxima página quando a rolagem se aproxima do final da lista já carregada.
    LaunchedEffect(gridState, state) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val s = state as? ExploreUiState.Success ?: return@collect
                val nearEnd = lastVisible != null && lastVisible >= s.items.size - 6
                if (nearEnd) viewModel.loadNextPage()
            }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Explorar", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { showFilterSheet = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filtrar catálogo")
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GenreCategory.entries.forEach { category ->
                CategoryPill(
                    label = category.label,
                    selected = category == filters.category,
                    onClick = { viewModel.applyFilters(filters.copy(category = category)) },
                )
            }
        }

        // Resumo dos filtros ativos — só aparece quando gênero ou ano
        // estão de fato selecionados, pra não poluir a tela no estado padrão.
        if (filters.genre != null || filters.year != null) {
            val parts = buildList {
                add("Explorar")
                filters.genre?.let { add("Gênero: ${it.displayName}") }
                filters.year?.let { add("Ano: $it") }
            }
            Text(
                parts.joinToString("  •  "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        when (val s = state) {
            is ExploreUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ExploreUiState.Success -> {
                if (s.items.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum título encontrado com esses filtros.")
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
                            ExploreCard(item = item, onClick = { onItemClick(item.id, item.resolvedMediaType) })
                        }
                        if (s.isLoadingMore) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            currentFilters = filters,
            onApply = { newFilters ->
                viewModel.applyFilters(newFilters)
                showFilterSheet = false
            },
            onClear = {
                viewModel.applyFilters(ExploreFilters(category = filters.category))
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ExploreCard(item: TmdbItem, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = item.poster_path?.let { TMDB_POSTER_BASE + it },
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

/**
 * Bottom sheet de filtro — Gênero e Ano em chips horizontais roláveis,
 * seleção única em cada seção. "Aplicar" confirma, "Limpar" reseta
 * gênero/ano (mantendo a categoria atual), fechar sem tocar em nada
 * descarta a alteração.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    currentFilters: ExploreFilters,
    onApply: (ExploreFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedGenre by remember { mutableStateOf(currentFilters.genre) }
    var selectedYear by remember { mutableStateOf(currentFilters.year) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Filtrar catálogo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Ajuste a lista sem sair da página.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Text("Gênero", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    label = "Todos",
                    selected = selectedGenre == null,
                    onClick = { selectedGenre = null },
                )
                TMDB_GENRES.forEach { genre ->
                    FilterChip(
                        label = genre.displayName,
                        selected = selectedGenre == genre,
                        onClick = { selectedGenre = genre },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Ano", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    label = "Todos",
                    selected = selectedYear == null,
                    onClick = { selectedYear = null },
                )
                EXPLORE_YEARS.forEach { year ->
                    FilterChip(
                        label = "$year",
                        selected = selectedYear == year,
                        onClick = { selectedYear = year },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClear) {
                    Text("Limpar")
                }
                Button(
                    onClick = { onApply(currentFilters.copy(genre = selectedGenre, year = selectedYear)) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Aplicar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
