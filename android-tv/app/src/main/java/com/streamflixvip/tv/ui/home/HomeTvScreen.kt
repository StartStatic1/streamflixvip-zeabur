package com.streamflixvip.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.TmdbItem

/**
 * Home do app de TV — carrosséis horizontais focáveis por D-pad, cada
 * item usando androidx.tv.material3.Card (não o Card comum do Compose):
 * essa versão já implementa o destaque visual de foco (escala + borda)
 * e o comportamento de "entrar no item" com o botão OK do controle sem
 * nenhum código extra — é exatamente o que faltava no app de celular
 * rodando no Fire Stick (ver conversa sobre isDirectPlayable/D-pad).
 *
 * Layout deliberadamente simples nesta primeira versão: uma seção
 * "Em Alta" carregada da TMDB via o mesmo proxy que o app de celular já
 * usa (TmdbApi.request). Novas seções (Continuar Assistindo, Séries,
 * Animes) seguem o mesmo padrão de MovieRow, só trocando o `path` da
 * chamada.
 */
@Composable
fun HomeTvScreen(viewModel: HomeTvViewModel = HomeTvViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item {
                Text(
                    "StreamFlixVIP",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(start = 48.dp),
                )
            }

            if (state.trending.isNotEmpty()) {
                item {
                    MovieRow(title = "Em Alta", items = state.trending)
                }
            }

            if (state.popularMovies.isNotEmpty()) {
                item {
                    MovieRow(title = "Filmes Populares", items = state.popularMovies)
                }
            }

            if (state.popularSeries.isNotEmpty()) {
                item {
                    MovieRow(title = "Séries Populares", items = state.popularSeries)
                }
            }

            if (state.isLoading) {
                item {
                    Text(
                        "Carregando...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieRow(title: String, items: List<TmdbItem>) {
    Column {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items) { item ->
                PosterCard(item)
            }
        }
    }
}

/**
 * Card focável de pôster. androidx.tv.material3.Card já cuida de:
 * - Escalar/realçar quando recebe foco do D-pad (sem precisar de
 *   onFocusChanged manual)
 * - Disparar onClick ao apertar OK/Enter no controle remoto
 * - Ordem de navegação entre itens da mesma TvLazyRow (esquerda/direita)
 */
@Composable
private fun PosterCard(item: TmdbItem) {
    Card(
        onClick = { /* TODO: navegar pra tela de detalhe quando ela existir */ },
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(2f / 3f),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.poster_path?.let { "https://image.tmdb.org/t/p/w342$it" },
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.displayTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}
