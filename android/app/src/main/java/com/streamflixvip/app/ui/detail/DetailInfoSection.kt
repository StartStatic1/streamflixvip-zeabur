package com.streamflixvip.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbCastMember
import com.streamflixvip.app.network.TmdbCrewMember
import com.streamflixvip.app.network.TmdbGenre
import com.streamflixvip.app.network.TmdbImages
import com.streamflixvip.app.ui.theme.StreamFlixColors

@Composable
fun DetailGenreAndCast(
    genres: List<TmdbGenre>? = null,
    cast: List<TmdbCastMember>? = null,
    crew: List<TmdbCrewMember>? = null,
    onPersonClick: (Int) -> Unit = {},
) {
    val people = cast.orEmpty().sortedBy { it.order ?: 99 }.take(12)
    val directors = crew.orEmpty()
        .filter { it.job.equals("Director", ignoreCase = true) }
        .distinctBy { it.id }
        .take(3)
    if (people.isEmpty() && directors.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (directors.isNotEmpty()) {
            Text(
                text = "Dir. " + directors.joinToString(" · ") { it.name },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = StreamFlixColors.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (people.isNotEmpty()) {
            Text(
                text = "Elenco",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = StreamFlixColors.Text,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                people.forEach { person ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clickable { onPersonClick(person.id) },
                    ) {
                        AsyncImage(
                            model = TmdbImages.poster(person.profile_path, "w185"),
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(StreamFlixColors.SurfaceHigh),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = person.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StreamFlixColors.Text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        person.character?.takeIf { it.isNotBlank() }?.let { role ->
                            Text(
                                text = role,
                                fontSize = 10.sp,
                                color = StreamFlixColors.TextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
