package com.streamflixvip.tv.ui.detail

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.ui.components.FadeImage
import com.streamflixvip.tv.ui.components.LandscapeCard
import com.streamflixvip.tv.ui.components.LoadingBox
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.MessageBox
import com.streamflixvip.tv.ui.components.MetaTag
import com.streamflixvip.tv.ui.components.PanelItem
import com.streamflixvip.tv.ui.components.PosterCard
import com.streamflixvip.tv.ui.components.SectionTitle
import com.streamflixvip.tv.ui.components.SidePanel
import com.streamflixvip.tv.ui.components.TmdbImg
import com.streamflixvip.tv.ui.components.TvButton
import com.streamflixvip.tv.ui.components.TvChip
import com.streamflixvip.tv.ui.home.FocusTargetBox
import com.streamflixvip.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dados para abrir o player. */
data class PlayRequest(
    val source: VipSource,
    val sources: List<VipSource>,
    val season: Int,
    val episode: Int,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val fromStart: Boolean,
)

private const val KEY_PLAY = "btn:play"

private val DetailH = 72.dp

@Composable
fun DetailTvScreen(
    tmdbId: Int,
    mediaType: String,
    onPlay: (PlayRequest) -> Unit,
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val p = LocalPalette.current
    val viewModel: DetailTvViewModel = viewModel(
        key = "detail_${mediaType}_$tmdbId",
        factory = DetailTvViewModelFactory(context.applicationContext as Application, tmdbId, mediaType),
    )
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val isSeries = mediaType == "tv"

    LaunchedEffect(Unit) { viewModel.loadDetails() }

    var restoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusTarget by remember { mutableStateOf<String?>(null) }
    var focusNonce by remember { mutableIntStateOf(0) }
    val listState = rememberTvLazyListState()
    val episodesState = rememberTvLazyListState()
    val retryFocus = remember { FocusRequester() }
    val panelFirst = remember { FocusRequester() }

    fun focus(key: String) {
        focusTarget = key
        focusNonce++
    }

    // primeiro foco (ou restaura ao voltar do player)
    val details = state.details
    LaunchedEffect(details != null, state.error) {
        if (state.error != null) {
            delay(60)
            runCatching { retryFocus.requestFocus() }
            return@LaunchedEffect
        }
        if (details == null) return@LaunchedEffect
        val k = restoreKey
        focus(k ?: KEY_PLAY)
        if (k != null && k != KEY_PLAY) {
            delay(500)
            if (focusTarget != null) focus(KEY_PLAY)
        }
    }

    val panel = state.panel
    // foco entra no painel quando abre / quando chegam os servidores
    val panelPhase = when {
        panel == null -> -1
        panel.sources.isNotEmpty() -> 2
        panel.loading -> 0
        else -> 1
    }
    LaunchedEffect(panelPhase) {
        if (panel != null) {
            delay(80)
            runCatching { panelFirst.requestFocus() }
        }
    }

    fun play(source: VipSource, sp: SourcePanel) {
        val d = state.details ?: return
        viewModel.rememberServer(source)
        val ordered = listOf(source) + sp.sources.filter { it.source_url != source.source_url }
        viewModel.closeSources()
        onPlay(
            PlayRequest(
                source = source,
                sources = ordered,
                season = sp.season,
                episode = sp.episode,
                title = sp.title,
                posterPath = d.poster_path,
                backdropPath = d.backdrop_path,
                fromStart = sp.fromStart,
            ),
        )
    }

    // so 1 servidor: abre direto
    LaunchedEffect(panel?.loading, panel?.sources?.size) {
        val sp = panel ?: return@LaunchedEffect
        if (!sp.loading && sp.sources.size == 1) {
            delay(250)
            play(sp.sources.first(), sp)
        }
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        when {
            state.isLoading && details == null -> LoadingBox(Modifier.fillMaxSize(), "Carregando…")
            details == null -> MessageBox(
                title = "Não foi possível abrir",
                message = state.error,
                actionLabel = "Tentar de novo",
                actionFocus = retryFocus,
                onAction = { viewModel.retry() },
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                // Arte de fundo
                FadeImage(
                    TmdbImg.backdropLg(details.backdrop_path),
                    Modifier.align(Alignment.TopEnd).fillMaxWidth(0.82f).aspectRatio(16f / 9f),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to p.bg, 0.38f to p.bg.copy(alpha = 0.97f), 0.66f to p.bg.copy(alpha = 0.45f), 1f to Color.Transparent,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 0.5f to p.bg.copy(alpha = 0.4f), 0.85f to p.bg, 1f to p.bg),
                    ),
                )

                val target = remember(state.progress, state.watched, state.seasonEpisodes, state.seasons) { viewModel.resumeTarget() }
                val recs = (details.recommendations?.results.orEmpty().ifEmpty { details.similar?.results.orEmpty() })
                    .filter { it.poster_path != null }.take(20)
                val episodes = state.seasonEpisodes[state.selectedSeason].orEmpty()
                TvLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    pivotOffsets = PivotOffsets(parentFraction = 0.0f),
                ) {
                    item(key = "header") {
                        Column(Modifier.wholeItemIntoView()) {
                        Column(
                            // Cabeçalho inteiro (título, infos, sinopse, botões) cabe na tela de uma vez
                            // em 1080p e 720p (~540dp de altura); a fileira de baixo aparece por baixo.
                            Modifier.fillMaxWidth()
                                .padding(start = DetailH, end = DetailH, top = 56.dp),
                        ) {
                            Header(details = details, isSeries = isSeries)
                            Spacer(Modifier.height(22.dp))
                            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                                item {
                                    FocusTargetBox(KEY_PLAY, focusTarget, focusNonce, { focusTarget = null }) { m ->
                                        TvButton(
                                            target.label,
                                            onClick = { viewModel.openSources(target.season, target.episode) },
                                            icon = Icons.Filled.PlayArrow,
                                            primary = true,
                                            modifier = m.onFocusChanged { if (it.isFocused) restoreKey = KEY_PLAY },
                                        )
                                    }
                                }
                                if (target.hasProgress) {
                                    item {
                                        TvButton(
                                            "Do início",
                                            onClick = { viewModel.openSources(target.season, target.episode, fromStart = true) },
                                            icon = Icons.Filled.Replay,
                                            modifier = Modifier.onFocusChanged { if (it.isFocused) restoreKey = KEY_PLAY },
                                        )
                                    }
                                }
                                if (isSeries) {
                                    item {
                                        TvButton(
                                            "Episódios",
                                            onClick = {
                                                scope.launch {
                                                    listState.animateScrollToItem(1)
                                                    val idx = episodes.indexOfFirst { it.episode_number == target.episode }
                                                        .takeIf { target.season == state.selectedSeason && it >= 0 } ?: 0
                                                    runCatching { episodesState.scrollToItem(idx) }
                                                    episodes.getOrNull(idx)?.let { focus("ep:${state.selectedSeason}:${it.episode_number}") }
                                                }
                                            },
                                            icon = Icons.Filled.FormatListNumbered,
                                        )
                                    }
                                }
                                details.trailerKey?.let { key ->
                                    item {
                                        TvButton("Trailer", onClick = {
                                            val ok = openTrailer(context, key)
                                            if (!ok) toaster.show("Instale o YouTube para ver o trailer")
                                        }, icon = Icons.Filled.Videocam)
                                    }
                                }
                                item {
                                    TvButton(
                                        if (state.isFavorite) "Na minha lista" else "Minha lista",
                                        onClick = {
                                            val added = viewModel.toggleFavorite()
                                            toaster.show(if (added) "Adicionado à Minha lista" else "Removido da Minha lista")
                                        },
                                        icon = if (state.isFavorite) Icons.Filled.Check else Icons.Filled.Add,
                                    )
                                }
                            }
                        }
                        }
                    }

                    if (isSeries) {
                        item(key = "episodes") {
                          Box(Modifier.wholeItemIntoView()) {
                            EpisodesBlock(
                                seasons = state.seasons,
                                selectedSeason = state.selectedSeason,
                                episodes = episodes,
                                loading = state.selectedSeason in state.loadingSeasons,
                                failed = state.selectedSeason in state.failedSeasons,
                                rowState = episodesState,
                                target = target,
                                watched = state.watched,
                                progressOf = { s, e -> viewModel.progressFor(s, e)?.progressFraction },
                                focusTarget = focusTarget,
                                focusNonce = focusNonce,
                                onFocusConsumed = { focusTarget = null },
                                onSeason = { viewModel.selectSeason(it) },
                                onRetrySeason = { viewModel.selectSeason(state.selectedSeason) },
                                onEpisodeFocused = { key -> restoreKey = key },
                                onEpisode = { ep -> viewModel.openSources(state.selectedSeason, ep.episode_number) },
                                onUpToActions = {
                                    scope.launch {
                                        runCatching { listState.animateScrollToItem(0) }
                                        focus(KEY_PLAY)
                                        delay(300)
                                        if (focusTarget == KEY_PLAY) focus(KEY_PLAY)
                                    }
                                },
                            )
                          }
                        }
                    }

                    if (recs.isNotEmpty()) {
                        item(key = "recs") {
                            Column(Modifier.wholeItemIntoView()) {
                                SectionTitle("Você também pode gostar", Modifier.padding(start = DetailH, bottom = 14.dp))
                                TvLazyRow(
                                    modifier = if (isSeries) Modifier else Modifier.onPreviewKeyEvent { ev ->
                                        if (ev.key == Key.DirectionUp) {
                                            if (ev.type == KeyEventType.KeyDown) scope.launch {
                                                runCatching { listState.animateScrollToItem(0) }
                                                focus(KEY_PLAY)
                                                delay(300)
                                                if (focusTarget == KEY_PLAY) focus(KEY_PLAY)
                                            }
                                            true
                                        } else false
                                    },
                                    contentPadding = PaddingValues(horizontal = DetailH, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                ) {
                                    items(recs.distinctBy { it.id }, key = { it.id }) { item ->
                                        PosterCard(
                                            title = item.displayTitle,
                                            posterPath = item.poster_path,
                                            width = 140.dp,
                                            onClick = { onOpenTitle(item.id, item.media_type ?: mediaType) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SidePanel(
            visible = panel != null,
            title = "Escolha o servidor",
            subtitle = panel?.heading,
            onDismiss = {
                viewModel.closeSources()
                restoreKey?.let { focus(it) } ?: focus(KEY_PLAY)
            },
        ) {
            val sp = panel
            if (sp != null) {
                if (sp.sources.isEmpty() && sp.loading) {
                    LoadingBox(Modifier.fillMaxWidth().height(160.dp), "Procurando servidores…")
                    TvButton(
                        "Cancelar",
                        onClick = {
                            viewModel.closeSources()
                            restoreKey?.let { focus(it) } ?: focus(KEY_PLAY)
                        },
                        modifier = Modifier.focusRequester(panelFirst),
                    )
                } else if (sp.sources.isEmpty()) {
                    Text(sp.message ?: "Nenhum servidor disponível agora.", color = p.textMuted, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    TvButton(
                        "Tentar de novo",
                        onClick = { viewModel.openSources(sp.season, sp.episode, sp.fromStart) },
                        icon = Icons.Filled.Refresh,
                        primary = true,
                        modifier = Modifier.focusRequester(panelFirst),
                    )
                } else {
                    TvLazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                        // mesma URL vinda de 2 fontes repetia a chave (crash): indice entra na chave
                        itemsIndexed(sp.sources, key = { i, s -> "$i:${s.source_url}" }) { index, s ->
                            val tags = sourceTags(s.displayName)
                            PanelItem(
                                title = s.displayName,
                                subtitle = listOfNotNull(
                                    if (s.displayName == sp.preferredLabel) "Último usado" else null,
                                    tags.takeIf { it.isNotBlank() },
                                    sourceHost(s.source_url).takeIf { it.isNotBlank() },
                                ).joinToString(" · ").ifBlank { null },
                                leading = Icons.Filled.Tv,
                                trailingText = "${index + 1}",
                                modifier = if (index == 0) Modifier.focusRequester(panelFirst) else Modifier,
                                onClick = { play(s, sp) },
                            )
                        }
                    }
                    if (sp.loading) {
                        Spacer(Modifier.height(12.dp))
                        Text("Buscando mais servidores…", color = p.textDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(details: com.streamflixvip.tv.network.TmdbResponse, isSeries: Boolean) {
    val p = LocalPalette.current
    val title = details.title ?: details.name ?: "Sem título"
    val logo = details.logoPath
    if (logo != null) {
        FadeImage(
            TmdbImg.LOGO + logo,
            Modifier.widthIn(max = 380.dp).heightIn(max = 96.dp),
            contentScale = ContentScale.Fit,
            contentDescription = title,
        )
    } else {
        Text(
            title,
            color = p.text,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.6f),
            lineHeight = 40.sp,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        details.vote_average?.takeIf { it > 0 }?.let {
            Icon(Icons.Filled.Star, null, tint = p.star, modifier = Modifier.size(16.dp))
            Text("%.1f".format(it), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        (details.release_date ?: details.first_air_date)?.take(4)?.let { Text(it, color = p.textMuted, fontSize = 14.sp) }
        details.ageRating?.let { MetaTag(if (it == "L") "L" else "$it+", highlight = false) }
        if (isSeries) {
            details.number_of_seasons?.let { n -> Text(if (n == 1) "1 temporada" else "$n temporadas", color = p.textMuted, fontSize = 14.sp) }
        } else {
            details.displayRuntime?.let { Text(it, color = p.textMuted, fontSize = 14.sp) }
        }
        MetaTag(if (isSeries) (if (details.original_language == "ja") "ANIME" else "SÉRIE") else "FILME", highlight = true)
    }
    details.genres?.takeIf { it.isNotEmpty() }?.let { g ->
        Spacer(Modifier.height(6.dp))
        Text(g.take(4).joinToString("  •  ") { it.name }, color = p.textMuted, fontSize = 13.sp)
    }
    details.overview?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(10.dp))
        Text(
            it,
            color = p.text.copy(alpha = 0.88f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(0.55f),
        )
    }
    details.credits?.cast?.take(5)?.takeIf { it.isNotEmpty() }?.let { cast ->
        Spacer(Modifier.height(10.dp))
        Text(
            "Elenco: " + cast.joinToString(", ") { it.name },
            color = p.textDim,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.5f),
        )
    }
}

@Composable
private fun EpisodesBlock(
    seasons: List<Int>,
    selectedSeason: Int,
    episodes: List<TmdbEpisode>,
    loading: Boolean,
    failed: Boolean,
    rowState: androidx.tv.foundation.lazy.list.TvLazyListState,
    target: ResumeTarget,
    watched: Set<Pair<Int, Int>>,
    progressOf: (Int, Int) -> Float?,
    focusTarget: String?,
    focusNonce: Int,
    onFocusConsumed: () -> Unit,
    onSeason: (Int) -> Unit,
    onRetrySeason: () -> Unit,
    onEpisodeFocused: (String) -> Unit,
    onEpisode: (TmdbEpisode) -> Unit,
    onUpToActions: () -> Unit,
) {
    val p = LocalPalette.current
    var focusedEp by remember(selectedSeason) { mutableStateOf<TmdbEpisode?>(null) }

    // abre a fileira no episodio da vez
    LaunchedEffect(selectedSeason, episodes.isNotEmpty()) {
        if (episodes.isNotEmpty() && target.season == selectedSeason) {
            val idx = episodes.indexOfFirst { it.episode_number == target.episode }
            if (idx > 0) runCatching { rowState.scrollToItem(idx) }
        }
    }

    val upToActions = Modifier.onPreviewKeyEvent { ev ->
        if (ev.key == Key.DirectionUp) {
            if (ev.type == KeyEventType.KeyDown) onUpToActions()
            true
        } else false
    }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Episódios", Modifier.padding(start = DetailH, bottom = 10.dp), trailing = "${episodes.size.takeIf { it > 0 } ?: ""}".ifBlank { null }?.let { "$it episódios" })
        if (seasons.size > 1) {
            TvLazyRow(
                modifier = upToActions,
                contentPadding = PaddingValues(horizontal = DetailH, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(seasons) { s ->
                    TvChip(
                        text = "Temporada $s",
                        selected = s == selectedSeason,
                        onClick = { onSeason(s) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        when {
            loading && episodes.isEmpty() -> LoadingBox(Modifier.fillMaxWidth().height(150.dp))
            failed && episodes.isEmpty() -> Box(Modifier.padding(start = DetailH)) {
                TvButton("Erro ao carregar episódios — tentar de novo", onClick = onRetrySeason, icon = Icons.Filled.Refresh)
            }
            episodes.isEmpty() -> Text("Nenhum episódio nesta temporada.", color = p.textMuted, modifier = Modifier.padding(start = DetailH))
            else -> {
                TvLazyRow(
                    state = rowState,
                    modifier = if (seasons.size > 1) Modifier else upToActions,
                    contentPadding = PaddingValues(horizontal = DetailH, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(episodes.distinctBy { it.episode_number }, key = { "$selectedSeason:${it.episode_number}" }) { ep ->
                        val key = "ep:$selectedSeason:${ep.episode_number}"
                        val isWatched = (selectedSeason to ep.episode_number) in watched
                        val isNext = target.season == selectedSeason && target.episode == ep.episode_number
                        FocusTargetBox(key, focusTarget, focusNonce, onFocusConsumed) { m ->
                            LandscapeCard(
                                title = "${ep.episode_number}. ${ep.displayName}",
                                imageUrl = ep.still_path?.let { TmdbImg.STILL + it },
                                width = 264.dp,
                                subtitle = listOfNotNull(ep.displayRuntime, if (isWatched) "✓ Assistido" else null).joinToString(" · ").ifBlank { null },
                                progress = progressOf(selectedSeason, ep.episode_number),
                                showPlayIcon = isNext,
                                topBadge = when {
                                    isNext && target.hasProgress -> "CONTINUAR"
                                    isNext -> "PRÓXIMO"
                                    else -> null
                                },
                                onFocus = {
                                    focusedEp = ep
                                    onEpisodeFocused(key)
                                },
                                onClick = { onEpisode(ep) },
                                modifier = m,
                            )
                        }
                    }
                }
                val fe = focusedEp
                Box(Modifier.padding(start = DetailH, end = DetailH, top = 6.dp).height(44.dp).fillMaxWidth(0.7f)) {
                    if (fe != null && !fe.overview.isNullOrBlank()) {
                        Text(fe.overview, color = p.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

private fun sourceTags(label: String): String {
    val l = label.lowercase()
    return buildList {
        val dub = Regex("\\bdub|dublad|dual").containsMatchIn(l)
        val leg = Regex("\\bleg|legend|\\bsub").containsMatchIn(l)
        if (dub) add("DUB")
        if (leg && !dub) add("LEG")
        when {
            l.contains("4k") || l.contains("2160") -> add("4K")
            l.contains("1080") || l.contains("fhd") -> add("Full HD")
            l.contains("720") -> add("HD")
        }
    }.joinToString(" · ")
}

private fun openTrailer(context: android.content.Context, key: String): Boolean {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")),
    )
    for (i in intents) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    return false
}

/**
 * Quando algo dentro do bloco ganha foco, a lista rola para mostrar o BLOCO inteiro (alinhado no
 * topo, pivot 0) em vez de só o botão focado — assim o título nunca some acima da tela e não há
 * duas rolagens brigando (era isso que fazia a tela pular/piscar).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.wholeItemIntoView(): Modifier = composed {
    val size = remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val responder = remember {
        object : androidx.compose.foundation.relocation.BringIntoViewResponder {
            override fun calculateRectForParent(localRect: androidx.compose.ui.geometry.Rect) =
                androidx.compose.ui.geometry.Rect(0f, 0f, size.value.width.toFloat(), size.value.height.toFloat())
            override suspend fun bringChildIntoView(localRect: () -> androidx.compose.ui.geometry.Rect?) {}
        }
    }
    this.onSizeChanged { size.value = it }
        .bringIntoViewResponder(responder)
}
