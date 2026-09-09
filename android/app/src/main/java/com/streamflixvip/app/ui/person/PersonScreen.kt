package com.streamflixvip.app.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbImages
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.ui.theme.StreamFlixColors

private fun departmentPt(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return when (value.lowercase()) {
        "acting" -> "Atuação"
        "directing" -> "Direção"
        "writing" -> "Roteiro"
        "production" -> "Produção"
        "camera" -> "Fotografia"
        "editing" -> "Edição"
        "sound" -> "Som"
        "art" -> "Arte"
        "costume & make-up", "costume and make-up" -> "Figurino"
        "visual effects" -> "Efeitos"
        "creator" -> "Criação"
        "crew" -> "Equipe"
        else -> value
    }
}

@Composable
fun PersonScreen(
    viewModel: PersonViewModel,
    onBack: () -> Unit,
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val state by viewModel.ui.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StreamFlixColors.Background),
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator(
                    color = StreamFlixColors.Amber,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.error || state.person == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Não deu para abrir este perfil.", color = StreamFlixColors.TextMuted)
                    TextButton(onClick = viewModel::reload) {
                        Text("Tentar de novo", color = StreamFlixColors.Amber)
                    }
                }
            }
            else -> {
                val person = state.person!!
                var bioOpen by remember { mutableStateOf(false) }
                val bio = person.biography.orEmpty().trim()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(bottom = 28.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = StreamFlixColors.Text,
                            )
                        }
                        Text(
                            "Elenco",
                            color = StreamFlixColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        AsyncImage(
                            model = TmdbImages.poster(person.profile_path, "w185"),
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .background(StreamFlixColors.SurfaceHigh),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = person.name ?: "Sem nome",
                            color = StreamFlixColors.Text,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        val meta = listOfNotNull(
                            departmentPt(person.known_for_department),
                            person.birthday?.take(4),
                            person.place_of_birth?.substringAfterLast(",")?.trim()?.takeIf { it.isNotBlank() },
                        )
                        if (meta.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = meta.joinToString("  ·  "),
                                color = StreamFlixColors.Amber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (bio.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = if (bioOpen || bio.length < 280) bio else bio.take(260).trimEnd() + "…",
                            color = StreamFlixColors.TextMuted,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        if (bio.length >= 280) {
                            Text(
                                text = if (bioOpen) "mostrar menos" else "ler bio",
                                color = StreamFlixColors.Amber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                                    .clickable { bioOpen = !bioOpen },
                            )
                        }
                    }

                    if (state.movies.isNotEmpty()) {
                        CreditRow("Filmes", state.movies, onOpenTitle)
                    }
                    if (state.series.isNotEmpty()) {
                        CreditRow("Séries", state.series, onOpenTitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditRow(
    title: String,
    items: List<TmdbItem>,
    onOpenTitle: (Int, String) -> Unit,
) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = title,
        color = StreamFlixColors.Text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .clickable { onOpenTitle(item.id, item.resolvedMediaType) },
            ) {
                AsyncImage(
                    model = TmdbImages.poster(item.poster_path, "w185"),
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(160.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StreamFlixColors.SurfaceHigh),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.displayTitle,
                    color = StreamFlixColors.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val year = item.displayYear
                if (year != null) {
                    Text(year, color = StreamFlixColors.TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}
