package com.streamflixvip.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbItem

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Pesquisa geral para localizar títulos específicos. Esta tela não reutiliza
 * a lógica de Explorar: ela parte da consulta textual e mantém filtros locais
 * de tipo de mídia, enquanto Explorar continua responsável pela descoberta
 * através de gênero, ano e categorias.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Pesquisa geral",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Encontre filmes, séries e animes pelo título.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = if (state.query.isNotBlank()) {
                    {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar pesquisa")
                        }
                    }
                } else {
                    null
                },
                placeholder = { Text("Busque um título, ator ou franquia") },
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black,
                        ),
                    )
                }
            }
        }

        when {
            state.query.isBlank() -> SearchInstruction()
            state.isLoading -> SearchLoadingState()
            state.errorMessage != null -> SearchErrorState(
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::retry,
            )
            state.hasSearched && state.results.isEmpty() -> SearchEmptyState(query = state.query)
            else -> SearchResultsGrid(
                query = state.query,
                results = state.results,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
private fun SearchInstruction() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "O que você quer assistir?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Digite o nome de um filme, série, anime, ator ou franquia para pesquisar em todo o catálogo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Pesquisando títulos...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) {
                Text("Tentar novamente")
            }
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Nenhum título encontrado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Não encontramos resultados para “${query.trim()}”. Tente outro nome ou remova o filtro selecionado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchResultsGrid(
    query: String,
    results: List<TmdbItem>,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${results.size} resultado${if (results.size == 1) "" else "s"} para “${query.trim()}”",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = results,
                key = { "${it.id}_${it.resolvedMediaType}" },
            ) { item ->
                SearchResultCard(
                    item = item,
                    onClick = { onItemClick(item.id, item.resolvedMediaType) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: TmdbItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.poster_path?.let { TMDB_POSTER_BASE + it },
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                )
                Text(
                    text = item.displayMediaLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Text(
                    text = item.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.displayYear?.let { year ->
                        Text(
                            text = year,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.displayYear != null && item.displayRating != null) {
                        Spacer(Modifier.width(8.dp))
                    }
                    item.displayRating?.let { rating ->
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = rating,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
