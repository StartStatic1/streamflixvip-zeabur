package com.streamflixvip.app.ui.detail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.streamflixvip.app.network.TmdbImages
import com.streamflixvip.app.ui.vip.PixPaymentSheet

import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.TmdbSeason
import com.streamflixvip.app.network.VipSource
import com.streamflixvip.app.ads.AdsHelper


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
    userId: String? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val isVip by com.streamflixvip.app.data.VipStatusHolder.isVip.collectAsState()
    var showTicketPay by remember { mutableStateOf(false) }
    if (showTicketPay && !userId.isNullOrBlank()) {
        PixPaymentSheet(
            userId = userId,
            amount = 2.50,
            planLabel = "Ingresso 24h",
            durationHours = 24,
            onDismiss = { showTicketPay = false },
            type = "ticket",
            tmdbId = viewModel.tmdbIdForTicket(),
            mediaType = viewModel.mediaTypeForTicket(),
        )
    }

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
    var autoResumedContinue by rememberSaveable { mutableStateOf(false) }
    val successForResume = state as? DetailUiState.Success
    LaunchedEffect(
        successForResume?.details?.id,
        resumeSeconds,
        successForResume?.movieSources?.size,
        successForResume?.isLoadingMovieSources,
        initialSeason,
        initialEpisode,
    ) {
        if (autoResumedContinue || resumeSeconds <= 0) return@LaunchedEffect
        val s = successForResume ?: return@LaunchedEffect
        val title = s.details.title ?: s.details.name ?: "Sem titulo"
        val posterPath = s.details.poster_path
        if (initialSeason > 0) {
            autoResumedContinue = true
            val ep = initialEpisode.coerceAtLeast(1)
            viewModel.loadEpisodeSources(initialSeason, ep, forceAutoPlay = true) { src ->
                onPlaySource(src, initialSeason, ep, title, posterPath)
            }
            return@LaunchedEffect
        }
        if (s.mediaType == "movie") {
            if (s.isLoadingMovieSources && s.movieSources.isEmpty()) return@LaunchedEffect
            val src = s.movieSources.firstOrNull() ?: return@LaunchedEffect
            autoResumedContinue = true
            onPlaySource(src, 0, 0, title, posterPath)
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
                skipHeroLoading = resumeSeconds > 0,
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
                    when {
                        s.isLoadingMovieSources || s.movieSources.size > 1 -> showMovieServerPicker = true
                        s.movieSources.size == 1 -> pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
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
                onDeleteComment = { id, done -> viewModel.deleteComment(id, done) },
                onVoteComment = { id, v -> viewModel.voteComment(id, v) },
                onReplyComment = { parentId -> /* set reply target */ },
                onToggleFavorite = viewModel::toggleFavorite,
                onTicketClick = {
                    if (!userId.isNullOrBlank()) showTicketPay = true
                },
                userId = userId,
            )

            if (showMovieServerPicker) {
                val sheetState = rememberModalBottomSheetState()
                var showPremiumSheet by remember { mutableStateOf(false) }
                // Auto: 1 fonte pronta enquanto sheet aberto → segue pro play
                LaunchedEffect(s.movieSources, s.isLoadingMovieSources, showMovieServerPicker) {
                    if (!showMovieServerPicker) return@LaunchedEffect
                    if (s.isLoadingMovieSources) return@LaunchedEffect
                    if (s.movieSources.size == 1) {
                        showMovieServerPicker = false
                        pendingWatch = PendingSource(s.movieSources.first(), 0, 0)
                    }
                }
                ServersBrowser(
                    title = s.details.title ?: s.details.name ?: "Servidores",
                    sources = s.movieSources,
                    loading = s.isLoadingMovieSources,
                    isVip = isVip,
                    onDismiss = { showMovieServerPicker = false },
                    onPick = { source ->
                        showMovieServerPicker = false
                        pendingWatch = PendingSource(source, 0, 0)
                    },
                    onLocked = { showPremiumSheet = true },
                )
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
    onDeleteComment: (Long, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    onVoteComment: (Long, Int) -> Unit = { _, _ -> },
    onReplyComment: (Long) -> Unit = {},
    onToggleFavorite: () -> Unit,
    onTicketClick: () -> Unit = {},
    skipHeroLoading: Boolean = false,
    userId: String? = null,
) {
    val details = state.details
    val title = details.title ?: details.name ?: "Sem título"
    val posterPath = details.poster_path
    val backdropUrl = details.backdrop_path?.let { TmdbImages.backdrop(it, "w1280") }
    val posterUrl = posterPath?.let { TmdbImages.poster(it, "w500") }
    val isVip by com.streamflixvip.app.data.VipStatusHolder.isVip.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Botão fixo "Assistir Agora" no header: só existe pra FILME. Série
    // não tem esse botão — o padrão de referência (CineVerse) não usa
    // botão fixo pra série, só a lista de temporadas/episódios logo
    // abaixo, onde cada episódio já é o próprio "botão de play" (1 toque
    // já busca a fonte e decide sozinho, ver loadEpisodeSources). Colocar
    // um botão fixo em cima seria redundante e ambíguo ("qual episódio
    // isso vai tocar?").
    // Assistir so quando existe fonte — evita clique em titulo fora da grade
    // (loading embaixo; se vazio, card Pedir filme)
    val heroWatchEnabled = !skipHeroLoading && state.mediaType == "movie" &&
        !state.movieIsLocked(isVip)
    val heroServersLoading = !skipHeroLoading && state.mediaType == "movie" &&
        !state.movieIsLocked(isVip) &&
        state.isLoadingMovieSources &&
        state.movieSources.isEmpty()

    // Controla se o modal de trailer inline está aberto.
    var showTrailerModal by remember { mutableStateOf(false) }
    var personId by remember { mutableStateOf<Int?>(null) }
    var trailerPick by remember { mutableStateOf<String?>(null) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val showBar = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
    Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                showServersLoading = heroServersLoading,
                onWatchNowClick = onWatchMovieNow,
                onBack = onBack,
                trailerKey = state.trailerKey,
                logoUrl = details.bestLogoUrl(),
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
            ExpandableSynopsis(details.overview)
        }
        item {
            DetailGenreAndCast(
                genres = details.genres,
                cast = details.credits?.cast,
                crew = details.credits?.crew,
                onPersonClick = { personId = it },
            )
        }
        val clips = details.videos?.results.orEmpty().filter {
            it.site == "YouTube" && it.type in setOf("Trailer", "Teaser")
        }
        if (clips.isNotEmpty()) {
            item {
                TrailerWindow(clips = clips, onClick = { key ->
                    if (!isVip) AdsHelper.showInterstitial(context)
                    trailerPick = key
                    showTrailerModal = true
                })
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
            val waitingSources = state.isLoadingMovieSources && state.movieSources.isEmpty()
            val freeTitle = state.vipConfig?.is_free == true
            if (!waitingSources && !freeTitle && (state.movieIsLocked(isVip) || (!isVip && state.movieSources.isEmpty() && state.vipConfig?.vip_lock == true))) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = onTicketClick)
                    }
                }
            } else if (state.isLoadingMovieSources) {
                // Loading cinema fica no hero (no lugar do Assistir)
            } else if (state.movieSources.isEmpty()) {
                item {
                    MovieRequestCard()
                }
            }
            item {
                CommentsEntryButton(onClick = onOpenComments, modifier = Modifier.padding(16.dp))
            }
        } else {
            // T1–Tn primeiro; season 0 = Especiais no fim
            val seasons = details.seasons.orEmpty()
                .filter { it.season_number >= 0 }
                .sortedBy { if (it.season_number == 0) Int.MAX_VALUE else it.season_number }
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
                            isAvailable = state.episodesWithSources?.contains(ep.episode_number) != false,
                            onToggleExpand = { onToggleEpisodeExpanded(ep.episode_number) },
                            onPlay = { val hasSource = state.episodesWithSources?.contains(ep.episode_number) != false; when { state.episodeIsLocked(ep.episode_number, isVip) -> onUpgradeClick(); !hasSource -> onSelectEpisode(state.expandedSeason ?: 1, ep.episode_number, title, posterPath); else -> onSelectEpisode(state.expandedSeason ?: 1, ep.episode_number, title, posterPath) } },
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
                                onClick = { if (state.episodeIsLocked(epNum, isVip)) onUpgradeClick() else onSelectEpisode(state.expandedSeason ?: 1, epNum, title, posterPath) },
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
        if (state.collectionParts.isNotEmpty()) {
            item {
                val raw = state.collectionName.orEmpty()
                    .replace(" Coleção", "")
                    .replace(" Collection", "")
                Text(
                    if (raw.isBlank()) "Do universo" else "Do universo · $raw",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                )
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                ) {
                    items(state.collectionParts) { part ->
                        SimilarTitleCard(item = part, onClick = { onOpenTitle(part.id, part.resolvedMediaType) })
                    }
                }
            }
        }

        if (state.similarTitles.isNotEmpty()) {
            val genre = details.genres?.firstOrNull()?.name
            item {
                Text(
                    if (genre != null) "Mais de $genre" else "Você também pode gostar",
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
    androidx.compose.animation.AnimatedVisibility(
        visible = showBar,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010))
                .zIndex(4f)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                tint = Color.White,
                contentDescription = "Voltar",
                onClick = onBack,
                size = 36.dp,
            )
            val logo = details.bestLogoUrl()
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp),
                )
            } else {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.width(36.dp))
        }
    }
    }

    personId?.let { id ->
        PersonSheet(personId = id, onDismiss = { personId = null }, onOpenTitle = onOpenTitle)
    }

    // Só abre quando o episódio tocado tem 2+ servidores — ver a
    // decisão em DetailViewModel.loadEpisodeSources. Fonte única já
    // tocou direto e nunca chega a marcar showServerPickerForEpisode.
    if (state.mediaType == "tv" && state.showServerPickerForEpisode != null) {
        val sheetState = rememberModalBottomSheetState()
        var showPremiumSheet by remember { mutableStateOf(false) }
        ServersBrowser(
            title = state.details.name ?: state.details.title ?: "Episódio",
            sources = state.episodeSources,
            loading = state.isLoadingEpisodeSources,
            isVip = isVip,
            onDismiss = onDismissServerPicker,
            onPick = { source ->
                onDismissServerPicker()
                onRequestWatch(source, state.selectedSeason ?: 0, state.selectedEpisode ?: 0)
            },
            onLocked = { showPremiumSheet = true },
        )
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
            currentUserId = userId,
            onDismiss = onDismissComments,
            onPost = onPostComment,
            onDelete = onDeleteComment,
            onVote = onVoteComment,
            onReply = { id, _ -> onReplyComment(id) },
        )
    }

    // Modal de trailer inline — Dialog sobreposto à tela inteira, fora
    // do LazyColumn, para não ser tratado como item de lista.
    if (showTrailerModal && (trailerPick ?: state.trailerKey) != null) {
        TrailerModal(
            trailerKey = (trailerPick ?: state.trailerKey)!!,
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

/** Nome amigavel: season 0 = Especiais (OVAs); demais = nome TMDB ou Temporada N */
private fun seasonDisplayName(season: TmdbSeason?): String {
    if (season == null) return "Temporada"
    if (season.season_number == 0) return "Especiais"
    val n = season.name?.takeIf { it.isNotBlank() }
    if (n != null && !n.equals("Specials", ignoreCase = true) && !n.equals("Especiais", ignoreCase = true)) return n
    return "Temporada ${season.season_number}"
}

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
                    seasonDisplayName(currentSeason),
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
                                        seasonDisplayName(season),
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
    isAvailable: Boolean = true,
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
                    .clickable(enabled = isLocked || isAvailable, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLocked -> Text("🔒", fontSize = 14.sp)
                    !isAvailable -> Text("⏳", fontSize = 14.sp)
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
                    color = when { !isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f); isSelected -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isAvailable) { Text("Em breve", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary) }
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
                            model = TmdbImages.still(episode.still_path),
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
    currentUserId: String? = null,
    onDismiss: () -> Unit,
    onPost: (text: String, onResult: (Boolean) -> Unit) -> Unit,
    onDelete: (Long, (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    onVote: (Long, Int) -> Unit = { _, _ -> },
    onReply: (Long, String) -> Unit = { _, _ -> },
) {
    var draft by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(0) }

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
                                val raw = comment.comment_text
                                val rate = when {
                                    raw.startsWith("👍 ") -> 1
                                    raw.startsWith("👎 ") -> -1
                                    else -> 0
                                }
                                val body = if (rate == 0) raw else raw.drop(2)
                                val me = com.streamflixvip.app.network.NetworkModule.sessionStore?.userEmail
                                val isAdmin = me.equals("xfdapx@gmail.com", ignoreCase = true)
                                val canDelete = isAdmin || (!currentUserId.isNullOrBlank() && currentUserId == comment.user_id)
                                val ctx = androidx.compose.ui.platform.LocalContext.current
                                val prefs = ctx.getSharedPreferences("comment_rate", android.content.Context.MODE_PRIVATE)
                                var myRate by remember(comment.id) { mutableStateOf(prefs.getInt("c_${comment.id}", 0)) }
                                var up by remember(comment.id) { mutableStateOf(prefs.getInt("up_${comment.id}", 0)) }
                                var down by remember(comment.id) { mutableStateOf(prefs.getInt("down_${comment.id}", 0)) }
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
                                        if (rate != 0) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(
                                                if (rate > 0) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown,
                                                contentDescription = null,
                                                tint = if (rate > 0) Color(0xFF4CAF50) else Color(0xFFE53935),
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            comment.created_at.take(10).let { d ->
                                                if (d.length == 10) "${d.substring(8,10)}/${d.substring(5,7)}" else ""
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(body, fontSize = 13.sp, lineHeight = 18.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                        Icon(
                                            Icons.Filled.ThumbUp,
                                            contentDescription = null,
                                            tint = if (myRate == 1) Color(0xFF4CAF50) else Color(0xFF8A8A8A),
                                            modifier = Modifier.size(16.dp).clickable { if (myRate != 1) { onVote(comment.id, 1); myRate = 1 } },
                                        )
                                        Text(" ${comment.up_count}", fontSize = 12.sp, color = Color(0xFFB5B5B5))
                                        Spacer(Modifier.width(12.dp))
                                        Icon(
                                            Icons.Filled.ThumbDown,
                                            contentDescription = null,
                                            tint = if (myRate == -1) Color(0xFFE53935) else Color(0xFF8A8A8A),
                                            modifier = Modifier.size(16.dp).clickable { if (myRate != -1) { onVote(comment.id, -1); myRate = -1 } },
                                        )
                                        Text(" ${comment.down_count}", fontSize = 12.sp, color = Color(0xFFB5B5B5))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Responder", fontSize = 12.sp, color = Color(0xFFB5B5B5), modifier = Modifier.clickable { onReply(comment.id, "") })
                                        if (canDelete) {
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                "Excluir",
                                                fontSize = 12.sp,
                                                color = Color(0xFFE53935),
                                                modifier = Modifier.clickable {
                                                onDelete(comment.id) { ok ->
                                                    if (!ok) android.widget.Toast.makeText(ctx, "Não foi possível excluir", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            )
                                        }
                                    }
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (rate == -1) Color(0xFFE53935) else Color(0xFF2A2A2A))
                                    .clickable { rate = if (rate == -1) 0 else -1 },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.ThumbDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (rate == 1) Color(0xFF4CAF50) else Color(0xFF2A2A2A))
                                    .clickable { rate = if (rate == 1) 0 else 1 },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
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
                                            val marked = when (rate) {
                                                1 -> "👍 $draft"
                                                -1 -> "👎 $draft"
                                                else -> draft
                                            }
                                            onPost(marked) { success -> if (success) { draft = ""; rate = 0 } }
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
private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl(): String? {
    val logos = images?.logos.orEmpty().filter { !it.file_path.isNullOrBlank() }
    val pick = logos.firstOrNull { it.iso_639_1 == "pt" }
        ?: logos.firstOrNull { it.iso_639_1 == "en" }
        ?: logos.firstOrNull { it.iso_639_1 == null }
        ?: logos.maxByOrNull { it.vote_average ?: 0.0 }
    return com.streamflixvip.app.network.TmdbImages.url(pick?.file_path, "w500")
}

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
    showServersLoading: Boolean = false,
    onWatchNowClick: () -> Unit,
    onBack: () -> Unit,
    trailerKey: String?,
    logoUrl: String? = null,
    onTrailerClick: () -> Unit,
    onShare: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp),
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
                .statusBarsPadding()
                .padding(top = 8.dp)
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
                .padding(top = 310.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 28.dp),
                )
            } else {
            Text(
                title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp,
                lineHeight = 32.sp,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 3f),
                        blurRadius = 16f,
                    ),
                ),
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            }
            tagline?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    // Subtítulo em cor âmbar/dourada suave para destacar sem berrar
                    color = Color(0xFFB5B5B5),
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
            var sessionReady by rememberSaveable(title) { mutableStateOf(false) }
            LaunchedEffect(title, showServersLoading) {
                if (showServersLoading) sessionReady = false
            }
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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                Button(
                    onClick = onWatchNowClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(50)),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White,
                        disabledContentColor = Color.Black,
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
                            Icon(Icons.Outlined.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Black)
                            Spacer(Modifier.width(10.dp))
                            Text("Assistir", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
                SideActions(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}


@Composable
private fun SideActions(isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0xFF2A2A2A))
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Salvo" else "Salvar",
                    tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF2A2A2A))
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Mais", tint = Color.White)
        }
    }
}

@Composable
private fun ExpandableSynopsis(overview: String?) {
    if (overview.isNullOrBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            overview,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (overview.length > 140) {
            Text(
                if (expanded) "Mostrar menos" else "Mostrar mais",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB5B5B5),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}


@Composable
private fun PersonSheet(
    personId: Int,
    onDismiss: () -> Unit,
    onOpenTitle: (Int, String) -> Unit,
) {
    var person by remember { mutableStateOf<com.streamflixvip.app.network.TmdbResponse?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(personId) {
        failed = false
        person = null
        try {
            var data = com.streamflixvip.app.network.NetworkModule.tmdbApi.request(
                path = "/person/$personId",
                appendToResponse = "combined_credits,images",
            )
            if (data.biography.isNullOrBlank()) {
                val en = com.streamflixvip.app.network.NetworkModule.tmdbApi.request(
                    path = "/person/$personId?language=en-US",
                    appendToResponse = "combined_credits,images",
                )
                if (!en.biography.isNullOrBlank()) data = en
            }
            person = data
        } catch (_: Exception) {
            failed = true
        }
    }
    val photos = person?.images?.profiles.orEmpty().mapNotNull { it.file_path }.ifEmpty {
        listOfNotNull(person?.profile_path)
    }
    var photoIndex by remember { mutableStateOf(0) }
    var photoOpen by remember { mutableStateOf(false) }
    val works = person?.combined_credits?.cast.orEmpty()
        .filter { !it.poster_path.isNullOrBlank() }
        .distinctBy { it.id }
    val movies = works.filter { it.resolvedMediaType == "movie" }
        .sortedByDescending { it.displayYear ?: "0" }
    val series = works.filter { it.resolvedMediaType != "movie" }
        .sortedByDescending { it.displayYear ?: "0" }
    val hero = photos.getOrNull(photoIndex)?.let { com.streamflixvip.app.network.TmdbImages.poster(it, "w500") }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                AsyncImage(
                    model = hero,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                Box(Modifier.statusBarsPadding().padding(8.dp)) {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = Color.White,
                        contentDescription = "Voltar",
                        onClick = onDismiss,
                        size = 38.dp,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = com.streamflixvip.app.network.TmdbImages.poster(person?.profile_path, "w185"),
                        contentDescription = person?.name,
                        modifier = Modifier.size(84.dp).clip(androidx.compose.foundation.shape.CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(person?.name ?: "Carregando…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (photos.size > 1) {
                            Text("${photoIndex + 1}/${photos.size}", color = Color(0xFFB5B5B5), fontSize = 12.sp)
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text("Biografia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        failed -> "Não foi possível carregar."
                        person == null -> "Buscando…"
                        person?.biography.isNullOrBlank() -> "Sem biografia cadastrada."
                        else -> person?.biography.orEmpty()
                    },
                    color = Color(0xFFD0D0D0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                if (photos.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Text("Fotos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        photos.forEachIndexed { index, path ->
                            AsyncImage(
                                model = com.streamflixvip.app.network.TmdbImages.poster(path, "w185"),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable { photoIndex = index; photoOpen = true },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                FilmographyRow("Filmes", movies, onDismiss, onOpenTitle)
                FilmographyRow("Séries", series, onDismiss, onOpenTitle)
            }
        }
        if (photoOpen && photos.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = com.streamflixvip.app.network.TmdbImages.poster(photos[photoIndex], "w780"),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                Box(Modifier.statusBarsPadding().padding(8.dp)) {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = Color.White,
                        contentDescription = "Fechar",
                        onClick = { photoOpen = false },
                        size = 38.dp,
                    )
                }
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircleIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            tint = Color.White,
                            contentDescription = "Anterior",
                            onClick = { photoIndex = (photoIndex - 1 + photos.size) % photos.size },
                            size = 42.dp,
                        )
                        Box(Modifier.graphicsLayer { rotationZ = 180f }) {
                            CircleIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = Color.White,
                                contentDescription = "Próxima",
                                onClick = { photoIndex = (photoIndex + 1) % photos.size },
                                size = 42.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmographyRow(
    label: String,
    items: List<com.streamflixvip.app.network.TmdbItem>,
    onDismiss: () -> Unit,
    onOpenTitle: (Int, String) -> Unit,
) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Text("$label · ${items.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Spacer(Modifier.height(10.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        items.take(24).forEach { item ->
            Column(
                Modifier.width(96.dp).clickable {
                    onDismiss()
                    onOpenTitle(item.id, item.resolvedMediaType)
                },
            ) {
                AsyncImage(
                    model = com.streamflixvip.app.network.TmdbImages.poster(item.poster_path, "w185"),
                    contentDescription = item.displayTitle,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    item.displayTitle,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                item.displayYear?.let {
                    Text(it, color = Color(0xFF9A9A9A), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PersonalRateRow(tmdbId: Int, mediaType: String) {
    if (tmdbId == 0) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("personal_rate", android.content.Context.MODE_PRIVATE) }
    val key = "rate_${tmdbId}_$mediaType"
    var value by remember { mutableStateOf(prefs.getInt(key, 0)) }
    fun set(v: Int) {
        value = if (value == v) 0 else v
        prefs.edit().putInt(key, value).apply()
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RateIcon(
            icon = Icons.Filled.ThumbDown,
            selected = value == -1,
            onClick = { set(-1) },
        )
        Spacer(Modifier.width(10.dp))
        RateIcon(
            icon = Icons.Filled.ThumbUp,
            selected = value == 2,
            onClick = { set(2) },
        )
    }
}

@Composable
private fun RateIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (selected) Color.White else Color(0xFF2A2A2A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Color.Black else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TrailerWindow(
    clips: List<com.streamflixvip.app.network.TmdbVideo>,
    onClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.Start) {
        Text(
            "Trailers",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            clips.forEach { clip ->
                Column(Modifier.width(168.dp).clickable { onClick(clip.key) }) {
                    AsyncImage(
                        model = "https://img.youtube.com/vi/${clip.key}/hqdefault.jpg",
                        contentDescription = clip.type,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        if (clip.type == "Teaser") "Teaser" else "Trailer",
                        fontSize = 12.sp,
                        color = Color(0xFFB5B5B5),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
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
        initialValue = 0.04f,
        targetValue = 0.10f,
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
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.35f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Trailer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(title, color = Color(0xFFB5B5B5), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Fechar", tint = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
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
                            iframe { position:absolute; top:-34%; left:0; width:100%; height:172%; border:0; }
                        </style>
                    </head>
                    <body>
                        <iframe
                            src="https://www.youtube-nocookie.com/embed/$trailerKey?autoplay=1&playsinline=1&rel=0&modestbranding=1&controls=0&fs=0&iv_load_policy=3&disablekb=1&origin=$TRAILER_BASE_URL"
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
    val posterUrl = item.poster_path?.let { TmdbImages.poster(it) }

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
private fun VipLockCard(onUpgradeClick: () -> Unit, onTicketClick: () -> Unit = {}) {
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onTicketClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ingresso R$ 2,50 · 24h só este título")
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
private const val FREE_SERVER_SLOTS = 1

@Composable
private fun SourceRow(
    source: VipSource,
    isRecommended: Boolean,
    isLockedForFree: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
) {
    ServerSourceCard(
        source = source,
        isRecommended = isRecommended,
        isLockedForFree = isLockedForFree,
        onClick = onClick,
        onLockedClick = onLockedClick,
    )
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

private fun addonGroupOf(label: String?): String {
    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return "Outros"
    val known = listOf("FlixHub", "Goldvip", "FrostStream", "BeTor", "GuIndex", "TorrentsDB", "Magneto", "BestCine", "Hyper", "AIOStreams", "MegaEmbed", "Torrentio", "Jackettio", "DatCent", "Eliteapy")
    known.firstOrNull { raw.contains(it, ignoreCase = true) }?.let { return it }
    val first = raw.split("·", "•", "|").first().trim()
    return first.split(" ").firstOrNull()?.take(16) ?: "Outros"
}

@Composable
private fun ServersBrowser(
    title: String,
    sources: List<VipSource>,
    loading: Boolean,
    isVip: Boolean,
    onDismiss: () -> Unit,
    onPick: (VipSource) -> Unit,
    onLocked: () -> Unit,
) {
    val groups = sources.groupBy { addonGroupOf(it.source_label) }.toSortedMap()
    val chips = listOf("Todos") + groups.keys
    var selected by remember { mutableStateOf("Todos") }
    val shown = if (selected == "Todos") sources else groups[selected].orEmpty()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = Color.White,
                        contentDescription = "Voltar",
                        onClick = onDismiss,
                        size = 38.dp,
                    )
                    Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 2)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { chip ->
                        val on = chip == selected
                        Text(
                            chip,
                            color = if (on) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (on) Color.White else Color(0xFF2A2A2A))
                                .clickable { selected = chip }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                if (loading && sources.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Buscando servidores...", color = Color(0xFFB5B5B5), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                        repeat(4) {
                            Box(
                                Modifier.fillMaxWidth().height(72.dp).padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1A1A))
                            )
                        }
                    }
                } else if (loading && shown.isNotEmpty()) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text("Buscando mais servidores...", color = Color(0xFFB5B5B5), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        itemsIndexed(shown) { index, source ->
                            val locked = !isVip && isAddonSourceLabel(source.source_label) && index >= FREE_SERVER_SLOTS
                            ServerInfoCard(source, locked, onClick = { if (locked) onLocked() else onPick(source) })
                        }
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(72.dp)
                                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1A1A))
                            )
                        }
                    }
                } else if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum servidor agora.", color = Color(0xFFB5B5B5))
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(shown) { index, source ->
                            val locked = !isVip && isAddonSourceLabel(source.source_label) && index >= FREE_SERVER_SLOTS
                            ServerInfoCard(source, locked, onClick = { if (locked) onLocked() else onPick(source) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerInfoCard(source: VipSource, locked: Boolean, onClick: () -> Unit) {
    val quality = qualityFromSource(source)
    val audio = audioFromSource(source)
    val origin = originFromSource(source)
    val size = sizeFromSource(source)
    val label = source.source_label.orEmpty()
    val isFlix = label.contains("FlixHub", ignoreCase = true) || label.contains("Server", ignoreCase = true)
    val serverName = if (isFlix) {
        Regex("(?:FlixHub\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE).find(label)?.value
            ?: Regex("(?:Goldvip|Srcine|Kraps|Stank|Gooddb)\\s+Server\\s*\\d+", RegexOption.IGNORE_CASE).find(label)?.value
    } else null
    val head = if (isFlix && serverName != null) "FlixHub" else label.split("·", "•", "|").first().trim().ifBlank { source.displayName }
    val desc = if (isFlix) null else source.meta?.description?.takeIf { it.isNotBlank() } ?: label.takeIf { it.isNotBlank() && !it.equals(head, ignoreCase = true) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161616))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(head, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (!desc.isNullOrBlank() && !desc.equals(head, ignoreCase = true)) {
            Text(desc, color = Color(0xFFCCCCCC), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 2)
        }
        if (serverName != null) {
            Text("⚡ $serverName", color = Color(0xFFEEEEEE), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (!quality.isNullOrBlank()) {
            Text("🎯 $quality", color = Color(0xFFDDDDDD), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (!origin.isNullOrBlank() || !size.isNullOrBlank() || !audio.isNullOrBlank()) {
            val bits = listOfNotNull(audio, size, origin).joinToString("  ")
            if (bits.isNotBlank()) {
                Text(bits, color = Color(0xFFAAAAAA), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (locked) {
            Text("VIP", color = Color(0xFFFFD54F), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}


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


@Composable
private fun HeroCinemaLoading(
    finishing: Boolean = false,
    onReady: () -> Unit = {},
) {
    val cyan = Color(0xFF00E5FF)
    val purple = Color(0xFF8B5CFF)
    val gold = Color(0xFFFFD54F)
    var progress by remember { mutableStateOf(0.10f) }
    var phraseIdx by remember { mutableStateOf(0) }
    val phrases = listOf(
        "Abrindo a sala…",
        "Ajustando o projetor…",
        "Buscando as melhores fontes…",
        "Sessão quase pronta…",
    )
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "heroPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1100,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "glow",
    )
    LaunchedEffect(finishing) {
        if (finishing) {
            phraseIdx = phrases.lastIndex
            val from = progress.coerceAtLeast(0.35f)
            val steps = 10
            repeat(steps) { i ->
                progress = from + (1f - from) * ((i + 1) / steps.toFloat())
                kotlinx.coroutines.delay(28)
            }
            progress = 1f
            kotlinx.coroutines.delay(90)
            onReady()
            return@LaunchedEffect
        }
        while (true) {
            if (progress < 0.78f) {
                progress = (progress + 0.018f).coerceAtMost(0.78f)
            }
            phraseIdx = (phraseIdx + 1) % (phrases.size - 1)
            kotlinx.coroutines.delay(520)
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF070B12),
        shadowElevation = 6.dp,
        border = BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(
                    cyan.copy(alpha = 0.35f + glow * 0.4f),
                    purple.copy(alpha = 0.35f + glow * 0.3f),
                    gold.copy(alpha = 0.22f + glow * 0.22f),
                ),
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF101826), Color(0xFF070B12)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(cyan.copy(alpha = 0.38f), purple.copy(alpha = 0.24f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", fontSize = 15.sp, color = cyan, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (finishing) "SESSÃO PRONTA" else "PREPARANDO A SESSÃO",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                color = Color(0xFFF5F7FB),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                phrases[phraseIdx.coerceIn(0, phrases.lastIndex)],
                fontSize = 11.sp,
                color = Color(0xFF9AA3B5),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A2230)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(cyan, purple, gold.copy(alpha = 0.9f)),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "LUZ · CÂMERA · AÇÃO",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = cyan.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun CinemaServersLoading() {
    val cyan = Color(0xFF00E5FF)
    val purple = Color(0xFF7C5CFF)
    var progress by remember { mutableStateOf(0.12f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress = 0.12f
            val steps = 28
            repeat(steps) {
                progress = 0.12f + (it + 1) / steps.toFloat() * 0.88f
                kotlinx.coroutines.delay(85)
            }
            kotlinx.coroutines.delay(180)
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0A0E16),
        border = BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(cyan.copy(alpha = 0.55f), purple.copy(alpha = 0.55f))),
        ),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icone estilo play / sessao (sem emoji basico)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(cyan.copy(alpha = 0.35f), purple.copy(alpha = 0.22f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF121826)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "▶",
                        fontSize = 26.sp,
                        color = cyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "PREPARANDO A SESSÃO",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                color = Color(0xFFF2F5FA),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Localizando os melhores servidores para este título",
                fontSize = 12.sp,
                color = Color(0xFF9AA3B5),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A2230)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.1f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(cyan, purple, cyan),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "LUZ  ·  CÂMERA  ·  SERVIDORES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = cyan.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun MovieRequestCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Comunidade WhatsApp — pedidos de catálogo e novidades
    val requestUrl = "https://chat.whatsapp.com/FAxyer3o2pe8x3JXZJBDDV"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ainda não está na grade", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Quer esse título no catálogo? Manda o pedido na comunidade — a equipe analisa e coloca na programação o mais rápido possível.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(requestUrl),
                            ),
                        )
                    } catch (_: Exception) { }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
            ) {
                Text("Pedir este filme no WhatsApp", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}
