package com.streamflixvip.tv.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.data.LocalFavorite
import com.streamflixvip.tv.data.LocalLibraryStore

private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"
private val Gold = Color(0xFFD4AF37)
private val Bg = Color(0xFF0B0B12)

/**
 * Tela dedicada de Minha lista (favoritos locais do aparelho).
 * Separada da Conta: coração na sidebar abre aqui; engrenagem abre Conta.
 */
@Composable
fun MyListTvScreen(
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val libraryStore = remember { LocalLibraryStore(context) }
    var favorites by remember { mutableStateOf(libraryStore.getFavorites()) }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        favorites = libraryStore.getFavorites()
        runCatching { backFocus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Card(
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocus).height(48.dp),
                colors = CardDefaults.colors(
                    containerColor = Color(0xFF1A1A24),
                    focusedContainerColor = Gold,
                ),
                shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Voltar", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(20.dp))
            Text("Minha lista", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                "${favorites.size} título${if (favorites.size == 1) "" else "s"}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.height(24.dp))

        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sua lista está vazia",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Adicione títulos nos detalhes do filme ou série.",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                contentPadding = PaddingValues(bottom = 48.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(favorites, key = { "${it.tmdbId}_${it.mediaType}" }) { fav ->
                    MyListPoster(fav) { onItemClick(fav.tmdbId, fav.mediaType) }
                }
            }
        }
    }
}

@Composable
private fun MyListPoster(fav: LocalFavorite, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1A1A24),
            focusedContainerColor = Color(0xFF1A1A24),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Gold),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        AsyncImage(
            model = fav.posterPath?.let { "$TMDB_POSTER$it" },
            contentDescription = fav.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
