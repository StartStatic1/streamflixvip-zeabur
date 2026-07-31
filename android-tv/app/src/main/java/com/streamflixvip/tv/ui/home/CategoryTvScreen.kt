package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"
private val Bg = Color(0xFF0B0B14)
private val Accent = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)
private val TextMuted = Color(0xFFA1A1B5)

fun categoryTitle(key: String): String = when (key) {
    "trending" -> "Em Alta"
    "cinema" -> "No Cinema"
    "upcoming" -> "Em breve"
    "series" -> "Séries"
    "continue" -> "Continuar assistindo"
    else -> "Explorar"
}

private fun categoryPath(key: String): String = when (key) {
    "trending" -> "/trending/all/week"
    "cinema" -> "/movie/now_playing"
    "upcoming" -> "/movie/upcoming"
    "series" -> "/tv/popular"
    else -> "/trending/all/week"
}

@Composable
fun CategoryTvScreen(
    category: String,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    var items by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(category) {
        loading = true
        error = null
        val path = categoryPath(category)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                NetworkModule.tmdbApi.request(path = path).results.orEmpty()
            }
        }
        result.fold(
            onSuccess = { items = it; loading = false },
            onFailure = { error = "Não foi possível carregar"; loading = false },
        )
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                categoryTitle(category),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentSoft)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = TextMuted)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum título encontrado", color = TextMuted)
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        CategoryPosterCard(
                            title = item.displayTitle,
                            posterPath = item.poster_path,
                            onClick = { onItemClick(item.id, item.resolvedMediaType) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPosterCard(
    title: String,
    posterPath: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF151522),
            focusedContainerColor = Color(0xFF151522),
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentSoft),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        Box {
            AsyncImage(
                model = posterPath?.let { "$TMDB_POSTER$it" },
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Text(
                title,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(6.dp),
            )
        }
    }
}
