package com.streamflixvip.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.network.TmdbItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Tela de busca — consulta /api/tmdb com debounce (espera o usuário parar
 * de digitar por 500ms antes de buscar), evitando disparar uma chamada
 * de rede a cada tecla.
 */
@Composable
fun SearchScreen(onItemClick: (tmdbId: Int, mediaType: String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val repository = remember { CatalogRepository() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(500) // debounce
        isLoading = true
        try {
            val response = com.streamflixvip.app.network.NetworkModule.tmdbApi.request(
                path = "/search/multi",
                query = query,
                page = 1,
            )
            results = response.results.orEmpty().filter { it.poster_path != null }
        } catch (_: Exception) {
            results = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Buscar filmes e séries...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results) { item ->
                    AsyncImage(
                        model = item.poster_path?.let { TMDB_IMAGE_BASE + it },
                        contentDescription = item.displayTitle,
                        modifier = Modifier
                            .aspectRatio(0.68f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onItemClick(item.id, item.resolvedMediaType) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
