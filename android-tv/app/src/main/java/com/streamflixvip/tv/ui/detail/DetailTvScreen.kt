package com.streamflixvip.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamflixvip.tv.network.TmdbCastMember

/**
 * Tela de Detalhe — repaginada a partir de referências reais de players
 * de TV bem avaliados (Streambox, PlayBox, serivia): metadados (duração,
 * gênero, ano, classificação) como PILLS sobrepostas na própria imagem
 * de fundo — não uma linha de texto solta abaixo do título — e os
 * botões de ação (Assistir/Trailer/+Lista) dentro da faixa escura do
 * hero, próximos ao título, ao invés de isolados no rodapé.
 */
@Composable
fun DetailTvScreen(
    tmdbId: Int,
    mediaType: String,
    onPlayClick: () -> Unit,
    viewModel: DetailTvViewModel = DetailTvViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(tmdbId, mediaType) {
        viewModel.load(tmdbId, mediaType)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.isLoading || state.detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Carregando...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Box
        }

        val detail = state.detail!!

        // Backdrop em tela cheia
        AsyncImage(
            model = detail.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Gradiente duplo: horizontal (texto sempre legível à esquerda)
        // + vertical (rodapé com elenco não compete com a imagem).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        endX = 1150f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        startY = 460f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, top = 56.dp, end = 56.dp, bottom = 32.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    detail.title ?: detail.name ?: "",
                    fontSize = 40.sp,
                    color = Color.White,
                    maxLines = 2,
                    modifier = Modifier.width(640.dp),
                )

                Spacer_(height = 14.dp)

                // ── Pills de metadados sobre a imagem ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val year = (detail.release_date ?: detail.first_air_date)?.take(4)
                    year?.let { MetaPill(it) }
                    detail.displayRuntime?.let { MetaPill(it) }
                    detail.number_of_seasons?.let { MetaPill("$it temporadas") }
                    detail.genres?.firstOrNull()?.let { MetaPill(it.name) }
                    detail.vote_average?.takeIf { it > 0 }?.let { rating ->
                        RatingPill(rating)
                    }
                }

                Spacer_(height = 20.dp)

                detail.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 3,
                        modifier = Modifier.width(600.dp),
                    )
                }

                Spacer_(height = 24.dp)

                // ── Botões de ação dentro do hero ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                        Text("  Assistir", fontSize = 16.sp, color = Color.Black)
                    }
                    Spacer_(width = 12.dp)
                    OutlinedButton(onClick = { /* TODO: trailer quando existir player de vídeo embutido */ }) {
                        Text("Trailer", fontSize = 15.sp)
                    }
                    Spacer_(width = 12.dp)
                    IconButton(
                        onClick = { /* TODO: minha lista */ },
                        colors = IconButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.12f)),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Minha lista", tint = Color.White)
                    }
                }
            }

            // ── Elenco ──
            val cast = detail.credits?.cast.orEmpty().take(12)
            if (cast.isNotEmpty()) {
                Text(
                    "Elenco",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(cast) { member -> CastMemberCard(member) }
                }
            }
        }
    }
}

/** Pill de metadado (ano, duração, gênero) — fundo translúcido claro sobre a imagem, texto branco. */
@Composable
private fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 13.sp, color = Color.White)
    }
}

/** Pill de nota — mesmo padrão visual do badge dourado usado nos pôsteres da Home, pra manter consistência. */
@Composable
private fun RatingPill(rating: Double) {
    Box(
        modifier = Modifier
            .background(Color(0xFFFFC107), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
            Text(" %.1f".format(rating), fontSize = 13.sp, color = Color.Black)
        }
    }
}

@Composable
private fun CastMemberCard(member: TmdbCastMember) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp),
    ) {
        AsyncImage(
            model = member.profile_path?.let { "https://image.tmdb.org/t/p/w185$it" },
            contentDescription = member.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
        )
        Spacer_(height = 6.dp)
        Text(member.name, fontSize = 11.sp, maxLines = 1, color = Color.White)
        member.character?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Spacer_(height: Dp = 0.dp, width: Dp = 0.dp) {
    Box(modifier = Modifier.height(height).width(width))
}
