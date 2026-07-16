package com.streamflixvip.app.ui.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w185"

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
    onGenreClick: (genreId: Int, genreName: String, mediaType: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        is GenreUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is GenreUiState.Success -> {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Gêneros",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(s.cards, key = { it.genre.id }) { card ->
                        val index = s.cards.indexOf(card)
                        GenreCardView(
                            card = card,
                            gradient = GRADIENTS[index % GRADIENTS.size],
                            onClick = { onGenreClick(card.genre.id, card.genre.displayName, card.mediaType) },
                        )
                    }
                }
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
                    model = TMDB_POSTER_BASE + path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(start = if (i > 0) (-26).dp else 0.dp)
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
