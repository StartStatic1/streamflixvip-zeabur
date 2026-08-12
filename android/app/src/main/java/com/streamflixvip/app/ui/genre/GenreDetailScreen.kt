package com.streamflixvip.app.ui.genre

import com.streamflixvip.app.network.TmdbImages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.network.TmdbItem
import kotlinx.coroutines.launch


/**
 * Grade de pôsteres de um gênero, com rolagem infinita — carrega a
 * próxima página automaticamente quando a pessoa se aproxima do final
 * da lista atual, em vez de exigir um botão "carregar mais" ou travar
 * tudo numa única página fixa.
 */
@Composable
fun GenreDetailScreen(
    genreId: Int,
    genreName: String,
    category: GenreCategory,
    onBack: () -> Unit,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val repository = remember { CatalogRepository() }
    val gridState = rememberLazyGridState()
    var items by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoadingFirstPage by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Carrega a primeira página sempre que gênero ou categoria mudam.
    LaunchedEffect(genreId, category) {
        isLoadingFirstPage = true
        currentPage = 1
        reachedEnd = false
        items = runCatching { repository.getTitlesByGenre(genreId, category, page = 1) }.getOrElse { emptyList() }
        isLoadingFirstPage = false
    }

    // Observa a posição de rolagem: quando faltam poucos itens pro fim
    // da lista já carregada, dispara a busca da próxima página.
    LaunchedEffect(gridState, items) {
        snapshotFlowLastVisibleIndex(gridState).collect { lastVisible ->
            val nearEnd = lastVisible != null && lastVisible >= items.size - 6
            if (nearEnd && !isLoadingMore && !isLoadingFirstPage && !reachedEnd && items.isNotEmpty()) {
                isLoadingMore = true
                val nextPage = currentPage + 1
                val more = runCatching { repository.getTitlesByGenre(genreId, category, page = nextPage) }.getOrElse { emptyList() }
                if (more.isEmpty()) {
                    reachedEnd = true
                } else {
                    items = items + more
                    currentPage = nextPage
                }
                isLoadingMore = false
            }
        }
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

        if (isLoadingFirstPage) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nenhum título encontrado nesse gênero.")
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
                items(items, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier.clickable { onItemClick(item.id, item.resolvedMediaType) },
                    ) {
                        AsyncImage(
                            model = TmdbImages.poster(it)em.poster_path,
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
                if (isLoadingMore) {
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

/** Índice do último item visível na grade — usado só pra decidir quando carregar mais páginas. */
private fun snapshotFlowLastVisibleIndex(state: LazyGridState) =
    snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
