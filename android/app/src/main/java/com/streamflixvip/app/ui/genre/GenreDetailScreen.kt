package com.streamflixvip.app.ui.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.network.TmdbItem
import kotlinx.coroutines.launch

private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w342"

/** Grade simples de pôsteres de um gênero — aberta ao tocar num card na aba Gêneros. */
@Composable
fun GenreDetailScreen(
    genreId: Int,
    genreName: String,
    mediaType: String,
    onBack: () -> Unit,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val repository = remember { CatalogRepository() }
    var items by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(genreId, mediaType) {
        isLoading = true
        items = runCatching { repository.getTitlesByGenre(genreId, mediaType) }.getOrElse { emptyList() }
        isLoading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Spacer(Modifier.width(4.dp))
            Text(genreName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nenhum título encontrado nesse gênero.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier.clickable { onItemClick(item.id, item.resolvedMediaType) },
                    ) {
                        AsyncImage(
                            model = TMDB_POSTER_BASE + item.poster_path,
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
                    }
                }
            }
        }
    }
}
