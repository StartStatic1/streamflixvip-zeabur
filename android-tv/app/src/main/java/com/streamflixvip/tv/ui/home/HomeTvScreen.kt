package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.TmdbItem

/**
 * Categorias exibidas na barra de abas superior. "Recomendações" carrega
 * a lista de "Em Alta" (trending); as demais mapeiam pra endpoints TMDB
 * distintos (ver HomeTvViewModel.loadCategory). Baseado no padrão visual
 * de players de IPTV/TV existentes que o usuário já usa como referência
 * (abas de categoria no topo + sidebar de navegação fixa à esquerda).
 */
enum class TvCategory(val label: String) {
    RECOMENDACOES("Recomendações"),
    FILMES("Filmes"),
    SERIES("Séries"),
    CRIANCAS("Crianças"),
    ANIMES("Animes"),
}

@Composable
fun HomeTvScreen(
    viewModel: HomeTvViewModel = HomeTvViewModel(),
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCategory by remember { mutableStateOf(TvCategory.RECOMENDACOES) }

    LaunchedEffect(selectedCategory) {
        viewModel.loadCategory(selectedCategory)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SidebarNav()

        Column(modifier = Modifier.fillMaxSize()) {
            CategoryTabs(
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Carregando...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                PosterGrid(items = state.items, onItemClick = onItemClick)
            }
        }
    }
}

/**
 * Sidebar fina fixa à esquerda — mesma ideia visual das referências
 * (ícones empilhados verticalmente, sem texto, sempre visível). Cada
 * ícone é focável por D-pad; navegação entre eles fica a cargo do
 * FocusManager padrão do Compose for TV (movimento vertical natural
 * dentro da Column).
 */
@Composable
private fun SidebarNav() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(modifier = Modifier.height(24.dp))
        SidebarIcon(Icons.Filled.Search, "Buscar")
        SidebarIcon(Icons.Filled.Home, "Início", selected = true)
        SidebarIcon(Icons.Filled.Person, "Perfil")
        SidebarIcon(Icons.Filled.Settings, "Configurações")
    }
}

@Composable
private fun SidebarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false) {
    Card(
        onClick = { /* TODO: navegação entre seções quando existirem */ },
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(44.dp),
        colors = CardDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Barra de abas de categoria no topo — mesma ideia das referências
 * (Recomendações / Filmes / Séries / Crianças / Animes lado a lado,
 * aba ativa com destaque de cor/sublinhado).
 */
@Composable
private fun CategoryTabs(selected: TvCategory, onSelect: (TvCategory) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvCategory.entries.forEach { category ->
            val isSelected = category == selected
            Card(
                onClick = { onSelect(category) },
                colors = CardDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        category.label,
                        fontSize = 18.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grid de pôsteres — GridCells.Adaptive deixa o número de colunas se
 * ajustar à largura da tela (importante em TV, já que resoluções variam
 * bastante entre 720p/1080p/4K), diferente de um número fixo de colunas.
 */
@Composable
private fun PosterGrid(items: List<TmdbItem>, onItemClick: (Int, String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(items) { item ->
            PosterCard(item, onClick = { onItemClick(item.id, item.resolvedMediaType) })
        }
    }
}

/**
 * Card de pôster com badge de nota (estrela) no canto superior — mesmo
 * padrão visual das referências. androidx.tv.material3.Card já cuida do
 * destaque de foco/D-pad.
 */
@Composable
private fun PosterCard(item: TmdbItem, onClick: () -> Unit) {
    Column {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster_path?.let { "https://image.tmdb.org/t/p/w342$it" },
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                item.displayRating?.let { rating ->
                    RatingBadge(rating, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
        }
        Spacer_(height = 6.dp)
        Text(
            item.displayTitle,
            fontSize = 13.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Badge amarelo com estrela + nota, replicando o selo visto nas referências (ex: "⭐ 6.0"). */
@Composable
private fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFFFC107), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(11.dp),
            )
            Text(
                " $rating",
                fontSize = 11.sp,
                color = Color.Black,
            )
        }
    }
}

/** Pequeno espaçador vertical — evitando importar Spacer do foundation por nome já usado acima como Composable local. */
@Composable
private fun Spacer_(height: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.height(height))
}
