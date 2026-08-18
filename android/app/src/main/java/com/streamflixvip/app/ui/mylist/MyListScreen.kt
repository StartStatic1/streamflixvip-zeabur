package com.streamflixvip.app.ui.mylist

import com.streamflixvip.app.network.TmdbImages

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.streamflixvip.app.network.FavoriteEntry


/**
 * Tela Favoritos: pills de filtro por tipo (com contagem), e grade de
 * pôsteres favoritados. Sem itens, mostra um estado vazio convidativo
 * com atalho pra busca, em vez de uma tela em branco.
 */
@Composable
fun MyListScreen(
    viewModel: MyListViewModel,
    onItemClick: (tmdbId: Int, mediaType: String) -> Unit,
    onSearchClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    // Recarrega a lista ao voltar pra tela.
    // Usa Lifecycle do Activity direto — evita crash
    // "LocalLifecycleOwner not present" em release/minify.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val owner = context as ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    when (val s = state) {
        is MyListUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is MyListUiState.LoggedOut -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Entre na sua conta para ver seus favoritos.", textAlign = TextAlign.Center)
            }
        }
        is MyListUiState.Success -> {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Favoritos", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        val count = s.allFavorites.size
                        Text(
                            if (count == 1) "1 título salvo" else "$count títulos salvos",
                            fontSize = 12.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FavoritesFilter.entries.forEach { filter ->
                        FilterPill(
                            label = filter.label,
                            count = s.countFor(filter),
                            selected = filter == s.filter,
                            onClick = { viewModel.selectFilter(filter) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                val visible = s.visibleFavorites
                if (visible.isEmpty()) {
                    EmptyFavoritesState(onSearchClick)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(visible, key = { "${it.tmdb_id}_${it.media_type}" }) { favorite ->
                            FavoriteCard(
                                favorite = favorite,
                                onClick = { onItemClick(favorite.tmdb_id, favorite.media_type) },
                                onRemove = { viewModel.removeFavorite(favorite.tmdb_id, favorite.media_type) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count",
            color = if (selected) Color.Black.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

/** Estado vazio convidativo — "sua lista está esperando", com atalho pra busca em vez de tela em branco. */
@Composable
private fun EmptyFavoritesState(onSearchClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Sua lista está esperando", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Toque no coração dos filmes e séries para montar uma fila do seu jeito.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSearchClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buscar títulos", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FavoriteCard(
    favorite: FavoriteEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val posterUrl = favorite.poster_path?.let { TmdbImages.poster(it) }

    Column(modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = favorite.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Botão de remover — sempre visível (não depende de
            // long-press), já que remover da lista é ação frequente e de
            // baixo risco.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remover dos favoritos",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(favorite.displayTitle, fontSize = 12.sp, maxLines = 1)
    }
}
