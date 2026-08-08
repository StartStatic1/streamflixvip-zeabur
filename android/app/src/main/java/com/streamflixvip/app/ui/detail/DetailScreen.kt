package com.streamflixvip.app.ui.detail

import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.TmdbSeason
import com.streamflixvip.app.network.VipSource
import com.streamflixvip.app.ads.AdsHelper

private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
private const val TMDB_STILL_BASE = "https://image.tmdb.org/t/p/w300"
private const val TMDB_POSTER_SMALL_BASE = "https://image.tmdb.org/t/p/w342"

/**
 * Tela de Detalhes: backdrop, sinopse, gêneros — e a lista de fontes
 * disponíveis pra assistir. Pra filme, a lista de fontes já vem pronta.
 * Pra série, primeiro escolhe a temporada/episódio, e só então busca as
 * fontes daquele episódio específico (mesma lógica do site: cada
 * episódio tem suas próprias fontes cadastradas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    resumeSeconds: Int = 0,
    initialSeason: Int = -1,
    initialEpisode: Int = -1,
    onPlaySource: (source: VipSource, season: Int, episode: Int, title: String, posterPath: String?) -> Unit,
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit,
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val isVip by com.streamflixvip.app.data.VipStatusHolder.isVip.collectAsState()

    // Fonte já decidida (única disponível, ou escolhida no sheet de
    // servidor) aguardando a pessoa decidir COMO assistir — player
    // interno (chama onPlaySource de verdade, que navega pro player) ou
    // externo (abre um app de vídeo instalado via Intent, sem navegar
    // pra lugar nenhum dentro do próprio app). Fica neste nível, e não
    // dentro de DetailContent, porque tanto o fluxo de fonte única
    // (abaixo, em onSelectEpisode) quanto o de múltiplas fontes (dentro
    // de DetailContent, no sheet de servidor) precisam preenchê-lo.
    var pendingWatch by remember {
        mutableStateOf<PendingSource?>(null)
    }

    // Filme com 2+ fontes: o botão "Assistir Agora" não pode simplesmente
    // tocar a primeira sem perguntar, porque aí a existência de um
    // servidor secundário vira invisível pra pessoa. Mesmo padrão que
    // série já usa (showServerPickerForEpisode), só que aqui é um state
    // simples porque filme não depende de buscar fontes sob demanda — a
    // lista já veio pronta no carregamento inicial da tela.
    var showMovieServerPicker by remember { mutableStateOf(false) }
    var autoResumedContinue by remember { mutableStateOf(false) }
    val successForResume = state as? DetailUiState.Success
    LaunchedEffect(successForResume, resumeSeconds) {
        if (autoResumedContinue || resumeSeconds <= 0) return@LaunchedEffect
        val s = successForResume ?: return@LaunchedEffect
        autoResumedContinue = true
        val title = s.details.title ?: s.details.name ?: "Sem titulo"
        val posterPath = s.details.poster_path
        if (s.movieSources.isNotEmpty() && initialSeason <= 0) {
            onPlaySource(s.movieSources.first(), 0, 0, title, posterPath)
            return@LaunchedEffect
        }
        if (initialSeason > 0) {
            val ep = initialEpisode.coerceAtLeast(1)
            viewModel.loadEpisodeSources(initialSeason, ep) { src ->
                onPlaySource(src, initialSeason, ep, title, posterPath)
            }
        }
    }

    when (val s = state) {
        is DetailUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DetailUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Não foi possível carregar este título.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadDetails() }) { Text("Tentar de novo") }
                }
            }
        }
        is DetailUiState.Success -> {
            DetailContent(
                state = s,
                onRequestWatch = { source, season, episode -> pendingWatch = PendingSource(source, season, episode) },
                onSelectEpisode = { season, episode, _, _ ->
                    viewModel.loadEpisodeSources(season, episode) { source ->
                        // Fonte única: já sabemos qual fonte usar, então
                        // pula direto pra decisão de COMO assistir (interno
                        // vs externo), sem exigir escolher servidor — não
                        // há o que escolher quando só existe um.
                        pendingWatch = PendingSource(source, season, episode)
                    }
                },
                onWatchMovieNow = {
                    when (s.movieSources.size) {
                        0 -> Unit
                        1 -> pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
                        else -> showMovieServerPicker = true
                    }
                },
                onDismissServerPicker = viewModel::closeServerPicker,
                onUpgradeClick = onUpgradeClick,
                onOpenTitle = onOpenTitle,
                onBack = onBack,
                onToggleSeasonPicker = viewModel::toggleSeasonPicker,
                onPickSeason = viewModel::selectSeasonFromPicker,
                onToggleEpisodeExpanded = viewModel::toggleEpisodeExpanded,
                onOpenComments = viewModel::openComments,
                onDismissComments = viewModel::closeComments,
                onPostComment = { text, onResult -> viewModel.postComment(text, isVip = com.streamflixvip.app.data.VipStatusHolder.isVip.value, onResult = onResult) },
                onToggleFavorite = viewModel::toggleFavorite,
            )

            if (showMovieServerPicker) {
                val sheetState = rememberModalBottomSheetState()
                var showPremiumSheet by remember { mutableStateOf(false) }
                ModalBottomSheet(onDismissRequest = { showMovieServerPicker = false }, sheetState = sheetState) {
                    Column(Modifier.padding(bottom = 24.dp)) {
                        Text(
                            "Escolha o servidor",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                        )
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            s.movieSources.forEachIndexed { index, source ->
                                val lockedForFree = !isVip && index >= FREE_SERVER_SLOTS
                                SourceRow(
                                    source = source,
                                    isRecommended = index == 0,
                                    isLockedForFree = lockedForFree,
                                    onClick = {
                                        showMovieServerPicker = false
                                        pendingWatch = PendingSource(source, 0, 0)
                                    },
                                    onLockedClick = { showPremiumSheet = true },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                if (showPremiumSheet) {
                    PremiumServerSheet(
                        onDismiss = { showPremiumSheet = false },
                        onUpgradeClick = onUpgradeClick,
                    )
                }
            }
        }
    }

    // O modal em si mora aqui (fora do when de loading/error/success),
    // ligado ao mesmo pendingWatch preenchido pelos dois fluxos acima —
    // um único lugar decide a UI de "player interno vs externo",
    // independente de ter vindo de filme, fonte única de série, ou
    // seletor de servidor de série.
    pendingWatch?.let { pending ->
        val successState = state as? DetailUiState.Success
        val title = successState?.details?.title ?: successState?.details?.name ?: "Sem título"
        val posterPath = successState?.details?.poster_path
        WatchOptionsSheet(
            source = pending.source,
            onDismiss = { pendingWatch = null },
            onPlayInternal = { source ->
                onPlaySource(source, pending.season, pending.episode, title, posterPath)
            },
        )
    }
}

/** Fonte já resolvida (única, ou escolhida no seletor de servidor) aguardando decisão de player interno/externo. */
private data class PendingSource(val source: VipSource, val season: Int, val episode: Int)

// ModalBottomSheet e rememberModalBottomSheetState ainda são marcados como
// @ExperimentalMaterial3Api pela própria biblioteca do Compose (podem
// mudar de assinatura em versões futuras) — o OptIn abaixo é a forma
// padrão de reconhecer isso e permitir o uso mesmo assim, já que é a
// via oficial (não um workaround) para abrir bottom sheets no Material3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    state: DetailUiState.Success,
    onRequestWatch: (source: VipSource, season: Int, episode: Int) -> Unit,
    onSelectEpisode: (season: Int, episode: Int, title: String, posterPath: String?) -> Unit,
    onWatchMovieNow: () -> Unit,
    onDismissServerPicker: () -> Unit,
    onUpgradeClick: () -> Unit,
    onOpenTitle: (tmdbId: Int, mediaType: String) -> Unit,
    onBack: () -> Unit,
    onToggleSeasonPicker: () -> Unit,
    onPickSeason: (season: Int) -> Unit,
    onToggleEpisodeExpanded: (episode: Int) -> Unit,
    onOpenComments: () -> Unit,
    onDismissComments: () -> Unit,
    onPostComment: (text: String, onResult: (Boolean) -> Unit) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val details = state.details
    val title = details.title ?: details.name ?: "Sem título"
    val posterPath = details.poster_path
    val backdropUrl = details.backdrop_path?.let { TMDB_BACKDROP_BASE + it }
    val posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val isVip by com.streamflixvip.app.data.VipStatusHolder.isVip.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Botão fixo "Assistir Agora" no header: só existe pra FILME. Série
    // não tem esse botão — o padrão de referência (CineVerse) não usa
    // botão fixo pra série, só a lista de temporadas/episódios logo
    // abaixo, onde cada episódio já é o próprio "botão de play" (1 toque
    // já busca a fonte e decide sozinho, ver loadEpisodeSources). Colocar
    // um botão fixo em cima seria redundante e ambíguo ("qual episódio
    // isso vai tocar?").
    val heroWatchEnabled = state.mediaType == "movie" &&
        state.movieSources.isNotEmpty() &&
        !state.movieIsLocked(isVip)

    // Controla se o modal de trailer inline está aberto.
    var showTrailerModal by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            DetailHeader(
                title = title,
                tagline = details.tagline,
                backdropUrl = backdropUrl,
                posterUrl = posterUrl,
                rating = details.vote_average,
                year = (details.release_date ?: details.first_air_date)?.take(4),
                runtimeLabel = details.displayRuntime,
                isFavorite = state.isFavorite,
                onToggleFavorite = onToggleFavorite,
                showWatchNowButton = heroWatchEnabled,
                onWatchNowClick = onWatchMovieNow,
                onBack = onBack,
                trailerKey = state.trailerKey,
                onTrailerClick = {
                    // Abre o trailer em modal fullscreen dentro do próprio
                    // app — sem sair pro YouTube ou navegador externo.
                    if (state.trailerKey != null) {
                        // Se não for VIP, mostra um anúncio antes do trailer
                        if (!isVip) {
                            AdsHelper.showInterstitial(context)
                        }
                        showTrailerModal = true
                    }
                },
                onShare = {
                    // Compartilha link direto do título no site via Intent
                    // nativo do Android — quem recebe pode abrir na hora,
                    // não é só o nome do filme solto sem destino nenhum.
                    val mediaTypeForUrl = if (state.mediaType == "tv") "serie" else "filme"
                    val shareUrl = "https://streamflixvip.online/titulo/$mediaTypeForUrl/${details.id}"
                    val shareText = "Assista \"$title\" no StreamFlixVIP! $shareUrl"
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Compartilhar via"))
                    } catch (_: Exception) { }
                },
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                details.overview?.let { overview ->
                    Text(overview, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                }
            }
        }

        if (state.mediaType == "movie") {
            // Filme: cadeado VIP continua aparecendo aqui quando bloqueado
            // (o botão "Assistir Agora" some nesse caso — ver
            // heroWatchEnabled — então o cadeado com CTA de upgrade é a
            // única forma de ação visível). Quando NÃO está bloqueado e já
            // existe fonte, o botão do header já resolve o play; não repete
            // a lista de servidores aqui embaixo pra não duplicar a mesma
            // ação em dois lugares da tela.
            if (state.movieIsLocked(isVip)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        VipLockCard(onUpgradeClick = onUpgradeClick)
                    }
                }
            } else if (state.movieSources.isEmpty()) {
                item {
                    Text(
                        "Nenhuma fonte disponível ainda para este título.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                CommentsEntryButton(onClick = onOpenComments, modifier = Modifier.padding(16.dp))
            }
        } else {
            val seasons = details.seasons.orEmpty().filter { it.season_number > 0 } // ignora "specials" (temporada 0)
            val currentSeason = seasons.firstOrNull { it.season_number == state.expandedSeason }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Temporadas", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    // Aviso visível de quantos episódios são grátis, quando a
                    // série tem limite parcial configurado — ajuda a pessoa a
                    // entender o cadeado antes mesmo de esbarrar nele.
                    val freeLimit = state.vipConfig?.vip_free_episode_limit
                    val seriesFullyLocked = state.vipConfig?.vip_lock == true
                    if (!seriesFullyLocked && freeLimit != null && !isVip) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Grátis até o episódio $freeLimit — demais exigem VIP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Dropdown "Temporada N ▾" no lugar da lista solta de
            // "Temporada 1 / Temporada 2 / Temporada 3..." empilhada —
            // igual à referência do CineVerse: 1 seletor compacto que abre
            // um menu flutuante com as opções, com a atual marcada. Só
            // aparece se a série realmente tem mais de uma temporada
            // (com 1 temporada só, o seletor seria clique morto).
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SeasonPickerHeader(
                        currentSeason = currentSeason,
                        allSeasons = seasons,
                        showPicker = state.showSeasonPicker,
                        onToggle = onToggleSeasonPicker,
                        onPickSeason = onPickSeason,
                    )
                }
            }

            if (state.isLoadingEpisodes) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            } else if (state.episodesOfExpandedSeason.isNotEmpty()) {
                items(state.episodesOfExpandedSeason) { ep ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        CineverseEpisodeRow(
                            episode = ep,
                            isExpanded = state.expandedEpisodeNumber == ep.episode_number,
                            isSelected = state.selectedSeason == state.expandedSeason && state.selectedEpisode == ep.episode_number,
                            isLoading = state.isLoadingEpisodeSources && state.selectedSeason == state.expandedSeason && state.selectedEpisode == ep.episode_number,
                            isLocked = state.episodeIsLocked(ep.episode_number, isVip),
                            onToggleExpand = { onToggleEpisodeExpanded(ep.episode_number) },
                            onPlay = { onSelectEpisode(state.expandedSeason ?: 1, ep.episode_number, title, posterPath) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            } else if (currentSeason != null) {
                // Fallback: TMDB não trouxe detalhe da temporada — ainda dá
                // pra escolher por número, melhor que travar a tela.
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        (1..currentSeason.episode_count).forEach { epNum ->
                            SimpleEpisodeRow(
                                episodeNumber = epNum,
                                isSelected = state.selectedSeason == state.expandedSeason && state.selectedEpisode == epNum,
                                isLoading = state.isLoadingEpisodeSources && state.selectedSeason == state.expandedSeason && state.selectedEpisode == epNum,
                                isLocked = state.episodeIsLocked(epNum, isVip),
                                onClick = { onSelectEpisode(state.expandedSeason ?: 1, epNum, title, posterPath) },
                            )
                        }
                    }
                }
            }

            // Comentários: mesma posição que o CineVerse usa (logo depois
            // da lista de episódios) — funciona igual pra filme e série,
            // então também aparece no bloco de filme, um pouco mais acima.
            item {
                CommentsEntryButton(onClick = onOpenComments, modifier = Modifier.padding(16.dp))
            }
        }

        // "Você também pode gostar" — preenche o espaço que sobrava vazio
        // embaixo da lista de fontes/episódios. Só aparece quando a busca
        // (disparada em paralelo no ViewModel) já trouxe algo; enquanto
        // isso a seção simplesmente não existe, sem placeholder de loading
        // pra não chamar atenção pra uma parte secundária da tela.
        if (state.similarTitles.isNotEmpty()) {
            item {
                Text(
                    "Você também pode gostar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 10.dp),
                )
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    items(state.similarTitles) { similar ->
                        // O endpoint /similar sempre retorna itens do MESMO
                        // tipo do título de origem (filme→filmes, série→
                        // séries) — não precisa resolver media_type aqui,
                        // já sabemos que é state.mediaType.
                        SimilarTitleCard(
                            item = similar,
                            onClick = { onOpenTitle(similar.id, state.mediaType) },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    // Só abre quando o episódio tocado tem 2+ servidores — ver a
    // decisão em DetailViewModel.loadEpisodeSources. Fonte única já
    // tocou direto e nunca chega a marcar showServerPickerForEpisode.
    if (state.mediaType == "tv" && state.showServerPickerForEpisode != null) {
        val sheetState = rememberModalBottomSheetState()
        var showPremiumSheet by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = onDismissServerPicker,
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Episódio ${state.showServerPickerForEpisode} · Escolha o servidor",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
                Column(Modifier.padding(horizontal = 20.dp)) {
                    state.episodeSources.forEachIndexed { index, source ->
                        val lockedForFree = !isVip && index >= FREE_SERVER_SLOTS
                        SourceRow(
                            source = source,
                            isRecommended = index == 0,
                            isLockedForFree = lockedForFree,
                            onClick = {
                                onDismissServerPicker()
                                onRequestWatch(source, state.selectedSeason ?: 0, state.selectedEpisode ?: 0)
                            },
                            onLockedClick = { showPremiumSheet = true },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        if (showPremiumSheet) {
            PremiumServerSheet(
                onDismiss = { showPremiumSheet = false },
                onUpgradeClick = onUpgradeClick,
            )
        }
    }

    if (state.showComments) {
        CommentsModal(
            comments = state.comments,
            isLoading = state.isLoadingComments,
            isPosting = state.isPostingComment,
            isVip = isVip,
            canPost = state.canPostComments,
            onDismiss = onDismissComments,
            onPost = onPostComment,
        )
    }

    // Modal de trailer inline — Dialog sobreposto à tela inteira, fora
    // do LazyColumn, para não ser tratado como item de lista.
    if (showTrailerModal && state.trailerKey != null) {
        TrailerModal(
            trailerKey = state.trailerKey!!,
            title = title,
            onDismiss = { showTrailerModal = false },
        )
    }
}

/**
 * Seletor de temporada compacto — "Temporada N ▾" que abre um menu
 * flutuante com todas as temporadas, a atual marcada com check. Substitui
 * a lista antiga de "Temporada 1 / Temporada 2 / Temporada 3..." solta na
 * tela (que ficava grande demais em séries com muitas temporadas) pelo
 * mesmo padrão compacto do CineVerse.
 */
@Composable
private fun SeasonPickerHeader(
    currentSeason: TmdbSeason?,
    allSeasons: List<TmdbSeason>,
    showPicker: Boolean,
    onToggle: () -> Unit,
    onPickSeason: (season: Int) -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    currentSeason?.name ?: "Temporada",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                imageVector = if (showPicker) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (showPicker) "Fechar seleção de temporada" else "Escolher temporada",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Menu flutuante ancorado embaixo do seletor — mesmo padrão visual
        // do print de referência (fundo escuro sólido, temporada atual
        // destacada em vermelho/laranja com check à direita).
        if (showPicker) {
            Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(0, 130),
                onDismissRequest = onToggle,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(220.dp),
                ) {
                    Column {
                        allSeasons.forEach { season ->
                            val isCurrent = season.season_number == currentSeason?.season_number
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickSeason(season.season_number) }
                                    .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        season.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "${season.episode_count} episódios",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isCurrent) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Linha de episódio estilo CineVerse: quando RECOLHIDA, é compacta (play
 * + nome + tag SxEy + seta) — quando o usuário toca na seta, expande pra
 * mostrar thumbnail 16:9 + sinopse completa por baixo, mantendo os outros
 * episódios da lista compactos. Diferente do EpisodeCard antigo (que
 * sempre mostrava thumbnail+sinopse de uma vez para TODOS os episódios da
 * temporada, ocupando a tela inteira de rolagem).
 */
@Composable
private fun CineverseEpisodeRow(
    episode: TmdbEpisode,
    isExpanded: Boolean,
    isSelected: Boolean,
    isLoading: Boolean,
    isLocked: Boolean,
    onToggleExpand: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .animateContentSize(),
    ) {
        // Linha compacta: sempre visível, é o que a lista mostra por padrão.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLocked -> Text("🔒", fontSize = 14.sp)
                    isLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else -> Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Assistir episódio ${episode.episode_number}",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${episode.episode_number}. ${episode.displayName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.displayRuntime?.let {
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isLocked) {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) {
                    Text(
                        "VIP",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Recolher detalhes do episódio" else "Ver detalhes do episódio",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onToggleExpand)
                    .padding(4.dp),
            )
        }

        // Área expandida: thumbnail + título + sinopse — só existe quando
        // isExpanded, e some de novo ao tocar a seta outra vez.
        if (isExpanded) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onPlay),
                ) {
                    if (episode.still_path != null) {
                        AsyncImage(
                            model = "$TMDB_STILL_BASE${episode.still_path}",
                            contentDescription = episode.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (!episode.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        episode.overview,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

/**
 * Botão de entrada pra Comentários — mesma posição/estilo que o print de
 * referência mostra (logo abaixo da lista de episódios/fontes), com ícone
 * de balão e seta indicando que abre algo. Reaproveitado tanto por filme
 * quanto série, já que comentário não depende de temporada/episódio.
 */
@Composable
private fun CommentsEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💬", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text("Comentários", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Modal fullscreen de comentários — lista + campo de digitar embaixo
 * (só aparece pra quem está logado; deslogado vê um convite pra entrar).
 * Autor VIP ganha um selinho ao lado do nome, igual o comportamento
 * descrito: "aparece do lado se usuário for VIP".
 */
@Composable
private fun CommentsModal(
    comments: List<com.streamflixvip.app.network.TitleComment>,
    isLoading: Boolean,
    isPosting: Boolean,
    isVip: Boolean,
    canPost: Boolean,
    onDismiss: () -> Unit,
    onPost: (text: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Comentários", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    // Seta pra fechar o modal — pedido explícito: "tem seta
                    // pra fecha modal tela inteira".
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Fechar comentários",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onDismiss)
                            .padding(2.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        comments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Nenhum comentário ainda. Seja o primeiro!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(comments) { comment ->
                                Column(Modifier.padding(vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(comment.displayAuthor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        if (comment.is_vip_author) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            ) {
                                                Text(
                                                    "VIP",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(comment.comment_text, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }

                // Campo de digitar: só aparece pra quem está logado — sem
                // login, mostra convite simples em vez de campo desabilitado
                // (mais claro do que um campo cinza sem explicação).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (canPost) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                placeholder = { Text("Escreva um comentário...") },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                shape = RoundedCornerShape(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            if (isPosting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Enviar comentário",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .graphicsLayer { rotationZ = 180f }
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable(enabled = draft.isNotBlank()) {
                                            onPost(draft) { success -> if (success) draft = "" }
                                        }
                                        .padding(4.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            "Entre na sua conta para comentar.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cabeçalho da tela de detalhes, no padrão visual do CineVerse: backdrop
 * grande com gradiente escuro na base (garante contraste pro texto sem
 * precisar de um scrim fixo), poster com sombra flutuando por cima
 * (profundidade, não fica "colado" na imagem de fundo), título grande,
 * tagline em itálico, e uma fileira de chips com a meta info essencial
 * (ano, duração, nota) — tudo escaneável num único golpe de vista antes
 * de decidir assistir.
 */
@Composable
private fun DetailHeader(
    title: String,
    tagline: String?,
    backdropUrl: String?,
    posterUrl: String?,
    rating: Double?,
    year: String?,
    runtimeLabel: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    showWatchNowButton: Boolean,
    onWatchNowClick: () -> Unit,
    onBack: () -> Unit,
    trailerKey: String?,
    onTrailerClick: () -> Unit,
    onShare: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
        ) {
            LivingBackdrop(backdropUrl = backdropUrl)
            // Gradiente duplo: escurece o topo o suficiente pra status bar
            // não brigar com a imagem, e escurece a base pra que o poster
            // e o texto por cima fiquem sempre legíveis, não importa o
            // quão clara seja a cena do backdrop escolhido.
            // Gradiente triplo mais refinado:
            // 1. Escurece o topo para legibilidade da status bar/botão voltar.
            // 2. Gradiente radial/lateral sutil para focar no centro.
            // 3. Gradiente vertical longo na base para fusão suave com o conteúdo.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                0.3f to androidx.compose.ui.graphics.Color.Transparent,
                                0.7f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                1.0f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                                0.5f to androidx.compose.ui.graphics.Color.Transparent,
                                1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                            ),
                        ),
                    ),
            )
        }

        // Barra de topo: apenas a seta de voltar à esquerda.
        // O botão de trailer foi movido para a barra de ações abaixo do
        // CTA principal, junto com favorito e compartilhar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                tint = androidx.compose.ui.graphics.Color.White,
                contentDescription = "Voltar",
                onClick = onBack,
                size = 38.dp,
            )
        }

        // Os botões de ação (favorito, compartilhar, trailer) foram
        // movidos para baixo do botão "Assistir Agora", em barra horizontal
        // coesa — mais fácil de alcançar com o polegar e sem poluir o backdrop.

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 130.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Poster reduzido (110dp, era 150dp) — no tamanho anterior ele
            // ocupava quase 2/3 da altura do backdrop, competindo com a
            // própria cena de fundo em vez de complementá-la. Agora fica
            // mais discreto/decorativo, deixando o backdrop ser o elemento
            // visual principal do header, como na referência do CineVerse.
            // Poster com sombra colorida e profundidade (efeito card flutuante)
            Surface(
                modifier = Modifier
                    .width(115.dp)
                    .aspectRatio(2f / 3f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 12.dp,
                tonalElevation = 4.dp,
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            tagline?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    // Subtítulo em cor âmbar/dourada suave para destacar sem berrar
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                year?.let { MetaChip(it) }
                runtimeLabel?.let { MetaChip(it) }
                rating?.let { MetaChip("⭐ ${"%.1f".format(it)}") }
            }

            // Botão grande e fixo logo abaixo do poster, no padrão do
            // CineVerse (referência que o Teddy mandou print): em vez de
            // precisar rolar até a seção de servidores lá embaixo, 1 toque
            // aqui já dispara a fonte recomendada e abre o mesmo modal de
            // "player interno vs externo" que a lista de servidores usa.
            // Some (em vez de desabilitar) quando não há fonte disponível
            // ainda, pra não prometer um play que vai falhar.
            if (showWatchNowButton) {
                Spacer(Modifier.height(16.dp))
                // Efeito Shimmer/Brilho sutil no botão CTA
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
                val shimmerX by infiniteTransition.animateFloat(
                    initialValue = -300f,
                    targetValue = 600f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(durationMillis = 3000, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
                    ),
                    label = "shimmerX",
                )

                Button(
                    onClick = onWatchNowClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // O brilho (shimmer) que passa pelo botão
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(60.dp)
                                .graphicsLayer { translationX = shimmerX }
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                                            androidx.compose.ui.graphics.Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Assistir Agora", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Barra de ações secundárias: favorito, trailer (se existir) e
            // compartilhar — alinhados horizontalmente abaixo do CTA principal,
            // fáceis de alcançar com o polegar e sem poluir o backdrop.
            Spacer(Modifier.height(if (showWatchNowButton) 12.dp else 20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                // Botão Favorito
                ActionButton(
                    icon = if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    label = if (isFavorite) "Salvo" else "Salvar",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f),
                )
                // Botão Trailer — só aparece quando a TMDB retornou uma key
                if (trailerKey != null) {
                    ActionButton(
                        icon = Icons.Outlined.Videocam,
                        label = "Trailer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onTrailerClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Botão Compartilhar
                ActionButton(
                    icon = Icons.Outlined.IosShare,
                    label = "Compartilhar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Backdrop "vivo": em vez da imagem estática parada, aplica um zoom+pan
 * lento e contínuo (efeito Ken Burns) puxando o próprio AsyncImage, e um
 * véu escuro por cima que pulsa bem sutilmente — dá a sensação de "cena em
 * movimento ofuscada por trás" que a referência do CineVerse tem com vídeo
 * de fundo de verdade, sem o custo de banda/decodificação de rodar um
 * vídeo por card de detalhe (inviável com 188K+ títulos no catálogo).
 * Puramente decorativo: nunca temos vídeo de preview real por título.
 */
@Composable
private fun LivingBackdrop(backdropUrl: String?) {
    // Zoom bem sutil e só nessa direção (sem pan lateral) — a versão
    // anterior movia a imagem pros lados (translationX) por cima de um
    // Box sem clip, e como o AsyncImage já preenche 100% do Box com
    // Crop, qualquer translação empurra a borda da imagem pra dentro da
    // área visível, aparecendo como uma "quina"/quadrado se destacando.
    // Só escala (sempre a partir do centro, sem nunca sair da área
    // clipada) evita esse artefato por completo.
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "backdrop")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 12000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "backdropScale",
    )
    val veilAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 5000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "backdropVeil",
    )

    // clipToBounds garante que nada da imagem escalada escape da área
    // reservada pro backdrop, não importa o valor de scale.
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = backdropUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentScale = ContentScale.Crop,
        )
        // Véu escuro pulsante bem discreto — a "ofuscação" que faz a
        // cena por trás parecer estar respirando, mesmo sendo 1 imagem.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = veilAlpha)),
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 42.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/**
 * Botão de ação secundária do hero: ícone outlined + label embaixo.
 * Substitui os botões genéricos flutuantes do backdrop — agora ficam
 * alinhados em barra horizontal abaixo do CTA principal, com ícones
 * mais expressivos e label de texto para clareza imediata.
 */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = tint.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Modal fullscreen de trailer: abre um WebView com o embed do YouTube
 * dentro do próprio app, sem sair para o YouTube ou navegador externo.
 * Mesmo padrão de WebView que o PlayerScreen já usa para fontes iframe.
 */
/**
 * Trailer inline — bottom sheet consistente com o resto do app (mesmo
 * padrão do WatchOptionsSheet/seletor de servidor), com o player do
 * YouTube em 16:9 dentro dele. Substituiu o Dialog fullscreen anterior,
 * que abria de um jeito abrupto por cima da tela inteira.
 *
 * SOBRE O ERRO 153 ("Video player configuration error"): o YouTube
 * exige verificar a origem de quem está pedindo pra tocar o embed. Um
 * WebView Android que só chama loadUrl(url, headers) com um Referer
 * manual NÃO resolve isso de forma confiável — o header extra não é
 * propagado pros recursos internos que o player carrega depois, que é
 * onde a verificação de origem realmente acontece (documentado em
 * relatos de devs que bateram nesse mesmo erro em WebViews).
 *
 * A correção real: hospedar um HTML mínimo, local, com o <iframe> do
 * YouTube usando referrerpolicy="strict-origin-when-cross-origin", e
 * carregar ESSE HTML via loadDataWithBaseURL com uma baseUrl HTTPS fixa
 * — isso estabelece uma origem consistente e verificável pelo player.
 */
private const val TRAILER_BASE_URL = "https://streamflixvip.app"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrailerModal(
    trailerKey: String,
    title: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Black,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Barra de título com botão de fechar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Trailer — $title",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                CircleIconButton(
                    icon = Icons.Filled.KeyboardArrowDown,
                    tint = androidx.compose.ui.graphics.Color.White,
                    contentDescription = "Fechar trailer",
                    onClick = onDismiss,
                    size = 32.dp,
                )
            }

            // Player em 16:9 — fica contido dentro do sheet, sem ocupar a
            // tela inteira de forma abrupta.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(androidx.compose.ui.graphics.Color.Black),
            ) {
                var isLoading by remember { mutableStateOf(true) }

                // HTML mínimo local com o iframe do YouTube — carregado
                // via loadDataWithBaseURL (não loadUrl direto na URL do
                // YouTube), pra estabelecer a origem real que resolve o
                // Erro 153. autoplay=1 já dispara o vídeo assim que o
                // player carrega, sem precisar de toque extra.
                val embedHtml = remember(trailerKey) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                            html, body { margin:0; padding:0; background:#000; height:100%; overflow:hidden; }
                            iframe { position:absolute; top:0; left:0; width:100%; height:100%; border:0; }
                        </style>
                    </head>
                    <body>
                        <iframe
                            src="https://www.youtube.com/embed/$trailerKey?autoplay=1&playsinline=1&rel=0&modestbranding=1&origin=$TRAILER_BASE_URL"
                            referrerpolicy="strict-origin-when-cross-origin"
                            allow="autoplay; encrypted-media; picture-in-picture"
                            allowfullscreen>
                        </iframe>
                    </body>
                    </html>
                    """.trimIndent()
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            setBackgroundColor(android.graphics.Color.BLACK)

                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                            }

                            loadDataWithBaseURL(
                                TRAILER_BASE_URL,
                                embedHtml,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SimilarTitleCard(item: TmdbItem, onClick: () -> Unit) {
    val posterUrl = item.poster_path?.let { TMDB_POSTER_SMALL_BASE + it }

    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = item.displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            item.displayRating?.let { rating ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⭐", fontSize = 9.sp)
                    Spacer(Modifier.width(2.dp))
                    Text(rating, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.displayTitle,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SimpleEpisodeRow(
    episodeNumber: Int,
    isSelected: Boolean,
    isLoading: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isLocked -> Text("🔒", fontSize = 14.sp, modifier = Modifier.size(18.dp))
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Episódio $episodeNumber",
            fontSize = 13.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * Cartão de cadeado exibido no lugar da lista de fontes quando o
 * título/episódio exige VIP. Usa a cor dourada do tema (primary), nunca
 * vermelho — cadeado comunica "exclusivo", não "erro".
 */
@Composable
private fun VipLockCard(onUpgradeClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔒", fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Conteúdo exclusivo VIP",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Assine o VIP para desbloquear este título e assistir sem espera.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                Text("Seja VIP agora")
            }
        }
    }
}

/** Extrai um selo de qualidade (4K/HD/SD etc) do nome da fonte, se houver. */
private fun qualityBadge(label: String?): String? {
    if (label == null) return null
    val upper = label.uppercase()
    return listOf("4K", "2160P", "1080P", "720P", "HD", "SD").firstOrNull { upper.contains(it) }
        ?.let { if (it == "2160P") "4K" else it }
}

/**
 * Quantos servidores ficam liberados de graça pra quem não é VIP —
 * sempre os N primeiros por ordem de `priority` (o mesmo campo que já
 * define a ordem de exibição/recomendação, ver VipSource). Reaproveitar
 * esse campo em vez de criar uma flag "is_free" separada evita trabalho
 * extra de cadastro: quem já é o servidor melhor/recomendado também é o
 * que aparece de graça, e mudar quem é grátis é só reordenar prioridade
 * no painel — não editar cada fonte uma a uma.
 */
private const val FREE_SERVER_SLOTS = 0

@Composable
private fun SourceRow(
    source: VipSource,
    isRecommended: Boolean,
    isLockedForFree: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
) {
    val badge = qualityBadge(source.source_label)
    val gold = MaterialTheme.colorScheme.primary

    Surface(
        onClick = if (isLockedForFree) onLockedClick else onClick,
        shape = RoundedCornerShape(12.dp),
        color = when {
            isLockedForFree -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            isRecommended -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isLockedForFree) {
            androidx.compose.foundation.BorderStroke(1.dp, gold.copy(alpha = 0.35f))
        } else null,
        tonalElevation = if (isRecommended) 3.dp else 0.dp,
        shadowElevation = if (isRecommended) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (isLockedForFree) gold.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isLockedForFree) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = gold,
                        modifier = Modifier.size(17.dp),
                    )
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source.displayName,
                    fontSize = 14.sp,
                    fontWeight = if (isLockedForFree) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isLockedForFree) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                when {
                    isLockedForFree -> PremiumTag(gold)
                    isRecommended -> Text(
                        "RECOMENDADO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            badge?.let {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLockedForFree) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Selo "PREMIUM" — mesma cor dourada (primary) usada em VipLockCard pra
 * comunicar "exclusivo, não erro". Reaproveita a linguagem visual que já
 * existe no app em vez de inventar um vermelho/cinza de "bloqueado" novo.
 */
@Composable
private fun PremiumTag(gold: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = gold.copy(alpha = 0.16f),
    ) {
        Text(
            "PREMIUM",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = gold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Bottom sheet leve mostrado ao tocar num servidor travado — explica o
 * porquê sem tirar a pessoa da lista de servidores (ela decide se quer
 * seguir pro upgrade ou continuar vendo as opções liberadas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumServerSheet(onDismiss: () -> Unit, onUpgradeClick: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val gold = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(gold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = gold,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            PremiumTag(gold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Este servidor é exclusivo VIP",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Assine o VIP para desbloquear todos os servidores, com mais velocidade e estabilidade.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onDismiss(); onUpgradeClick() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Seja VIP agora", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Continuar com os servidores gratuitos",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
