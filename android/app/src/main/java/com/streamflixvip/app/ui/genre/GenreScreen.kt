package com.streamflixvip.app.ui.genre

import com.streamflixvip.app.network.TmdbImages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.GenreCategory


// Paleta de gradientes rotativa — cada card pega uma cor por posição,
// só pra dar variedade visual (igual aos prints de referência), sem
// precisar de nenhuma lógica de "cor por gênero" vinda do backend.
private val GRADIENTS = listOf(
    listOf(Color(0xFF6A1B9A), Color(0xFF283593)),
    listOf(Color(0xFFB71C1C), Color(0xFF4A148C)),
    listOf(Color(0xFF00695C), Color(0xFF01579B)),
    listOf(Color(0xFFE65100), Color(0xFFBF360C)),
    listOf(Color(0xFF880E4F), Color(0xFF311B92)),
    listOf(Color(0xFF1565C0), Color(0xFF004D40)),
)

@Composable
fun GenreScreen(
    viewModel: GenreViewModel,
    onGenreClick: (genreId: Int, genreName: String, category: GenreCategory) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Gêneros",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
        )

        // Pills de filtro — sempre visíveis mesmo durante o carregamento,
        // pra pessoa poder trocar de categoria sem esperar a primeira
        // busca terminar.
        val currentCategory = (state as? GenreUiState.Success)?.category ?: GenreCategory.ALL
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GenreCategory.entries.forEach { category ->
                CategoryPill(
                    label = category.label,
                    selected = category == currentCategory,
                    onClick = { viewModel.selectCategory(category) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        when (val s = state) {
            is GenreUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is GenreUiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // Banner de destaque ocupa a largura inteira (2 colunas).
                    if (s.featured != null) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            FeaturedGenreBanner(
                                featured = s.featured,
                                onExploreClick = {
                                    onGenreClick(s.featured.genre.id, s.featured.genre.displayName, s.category)
                                },
                            )
                        }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Text(
                                "Todos os gêneros",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(s.cards, key = { it.genre.id }) { card ->
                        val index = s.cards.indexOf(card)
                        GenreCardView(
                            card = card,
                            gradient = GRADIENTS[index % GRADIENTS.size],
                            onClick = { onGenreClick(card.genre.id, card.genre.displayName, s.category) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Banner grande de "destaque do dia" — troca de gênero a cada mudança de filtro, igual ao app de referência. */
@Composable
private fun FeaturedGenreBanner(featured: FeaturedGenre, onExploreClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (featured.backdropPath != null) {
            AsyncImage(
                model = TmdbImages.backdrop(featured.backdropPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Escurece a base da imagem pra legenda ficar legível por cima,
        // sem depender de a imagem em si já ter contraste suficiente.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                "DESTAQUE DE HOJE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                featured.genre.displayName,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onExploreClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Explorar ${featured.genre.displayName}", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GenreCardView(
    card: GenreCard,
    gradient: List<Color>,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradient, start = Offset(0f, 0f), end = Offset(400f, 400f)))
            .clickable(onClick = onClick),
    ) {
        // Capas empilhadas no canto inferior direito, levemente sobrepostas.
        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp)) {
            card.posters.forEachIndexed { i, path ->
                AsyncImage(
                    model = TmdbImages.poster(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = if (i > 0) (-26).dp else 0.dp)
                        .width(58.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(topStart = 8.dp))
                        .align(Alignment.Bottom),
                )
            }
        }
        Column(modifier = Modifier.align(Alignment.TopStart).padding(14.dp)) {
            Text(
                card.genre.displayName,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
