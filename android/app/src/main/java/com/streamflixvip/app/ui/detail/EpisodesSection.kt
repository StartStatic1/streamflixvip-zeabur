package com.streamflixvip.app.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbImages
import com.streamflixvip.app.network.TmdbSeason
import com.streamflixvip.app.network.WatchProgressEntry
import com.streamflixvip.app.ui.theme.StreamFlixColors

/** Progresso da série: em andamento (watch_progress) + vistos até o fim (local). */
internal data class SeriesProgress(
    val entries: List<WatchProgressEntry> = emptyList(),
    val watched: Set<String> = emptySet(),
) {
    /** Último episódio em andamento (lista já vem do mais recente para o mais antigo). */
    val latest: WatchProgressEntry? get() = entries.firstOrNull { it.season > 0 || it.episode > 0 }

    fun fraction(season: Int, episode: Int): Float {
        if (watched.contains("$season:$episode")) return 1f
        val e = entries.firstOrNull { it.season == season && it.episode == episode } ?: return 0f
        if (e.duration_seconds <= 0) return 0f
        return (e.position_seconds.toFloat() / e.duration_seconds).coerceIn(0f, 1f)
    }

    fun isWatched(season: Int, episode: Int) = watched.contains("$season:$episode")
}

@Composable
internal fun rememberSeriesProgress(tmdbId: Int, enabled: Boolean): SeriesProgress {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var progress by remember(tmdbId) { mutableStateOf(SeriesProgress()) }
    LaunchedEffect(tmdbId, enabled, tick) {
        if (!enabled) return@LaunchedEffect
        val entries = runCatching { com.streamflixvip.app.data.ProgressRepository().getTitleProgress(tmdbId, "tv") }.getOrDefault(emptyList())
        val watched = runCatching { com.streamflixvip.app.data.WatchedEpisodes.forTitle(tmdbId) }.getOrDefault(emptySet())
        progress = SeriesProgress(entries, watched)
    }
    return progress
}

internal fun seasonShortName(s: TmdbSeason): String =
    if (s.season_number == 0) "Especiais" else "Temporada ${s.season_number}"

/** Abas roláveis de temporada, cada uma com a contagem de episódios. */
@Composable
internal fun SeasonChipsRow(
    seasons: List<TmdbSeason>,
    currentSeason: Int?,
    onPick: (Int) -> Unit,
) {
    val accent = StreamFlixColors.Accent
    val rowState = rememberLazyListState()
    LaunchedEffect(currentSeason, seasons.size) {
        val idx = seasons.indexOfFirst { it.season_number == currentSeason }
        if (idx > 0) rowState.animateScrollToItem((idx - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(seasons, key = { it.season_number }) { s ->
            val selected = s.season_number == currentSeason
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, if (selected) accent else StreamFlixColors.GlassBorder, RoundedCornerShape(12.dp))
                    .clickable { if (!selected) onPick(s.season_number) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    seasonShortName(s),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) StreamFlixColors.OnAccent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    if (s.episode_count == 1) "1 episódio" else "${s.episode_count} episódios",
                    fontSize = 10.sp,
                    color = if (selected) StreamFlixColors.OnAccent.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Atalho "Continuar T1:E3" com barra de progresso. */
@Composable
internal fun ContinueEpisodeCard(
    entry: WatchProgressEntry,
    episodeName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = StreamFlixColors.Accent
    val frac = if (entry.duration_seconds > 0) (entry.position_seconds.toFloat() / entry.duration_seconds).coerceIn(0f, 1f) else 0f
    val leftMin = if (entry.duration_seconds > 0) ((entry.duration_seconds - entry.position_seconds) / 60).coerceAtLeast(1) else 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = StreamFlixColors.OnAccent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Continuar T${entry.season}:E${entry.episode}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val sub = listOfNotNull(episodeName, if (leftMin > 0) "faltam $leftMin min" else null).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (frac > 0f) {
                Spacer(Modifier.height(6.dp))
                ProgressLine(frac, accent, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProgressLine(frac: Float, color: Color, modifier: Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(color))
    }
}

/** Cabeçalho da lista: contagem + ordem (do primeiro / do último). */
@Composable
internal fun EpisodeListHeader(
    count: Int,
    watchedCount: Int,
    newestFirst: Boolean,
    onToggleSort: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                append(if (count == 1) "1 episódio" else "$count episódios")
                if (watchedCount > 0) append(" · $watchedCount vistos")
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (count > 1) {
            Text(
                if (newestFirst) "↓ Do último" else "↑ Do primeiro",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = StreamFlixColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggleSort).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private val ptMonths = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

private fun formatAirDate(raw: String?): String? {
    val p = raw?.split("-") ?: return null
    if (p.size < 3) return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].take(2).toIntOrNull() ?: return null
    if (m !in 1..12) return null
    return "$d ${ptMonths[m - 1]} ${p[0]}"
}

/** Card de episódio: foto do episódio, número + título, duração, progresso e sinopse expansível. */
@Composable
internal fun EpisodeCard(
    episode: TmdbEpisode,
    progress: Float,
    watched: Boolean,
    isExpanded: Boolean,
    isSelected: Boolean,
    isLoading: Boolean,
    isLocked: Boolean,
    isAvailable: Boolean,
    onToggleExpand: () -> Unit,
    onPlay: () -> Unit,
) {
    val accent = StreamFlixColors.Accent
    val dim = !isAvailable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .animateContentSize()
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = isLocked || isAvailable, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                if (episode.still_path != null) {
                    AsyncImage(
                        model = TmdbImages.still(episode.still_path),
                        contentDescription = episode.displayName,
                        contentScale = ContentScale.Crop,
                        alpha = if (dim) 0.45f else 1f,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("${episode.episode_number}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    when {
                        isLocked -> Text("🔒", fontSize = 13.sp)
                        !isAvailable -> Text("⏳", fontSize = 13.sp)
                        isLoading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Assistir episódio ${episode.episode_number}", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (watched) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Visto", tint = StreamFlixColors.OnAccent, modifier = Modifier.size(12.dp))
                    }
                }
                if (progress > 0.02f && !watched) {
                    ProgressLine(progress, accent, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onToggleExpand)) {
                Text(
                    "${episode.episode_number}. ${episode.displayName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        dim -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        isSelected -> accent
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = listOfNotNull(episode.displayRuntime, formatAirDate(episode.air_date)).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (isLocked) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "VIP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.18f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (dim) {
                    Text("Em breve", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        if (!episode.overview.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                episode.overview,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand),
            )
        }
    }
}
