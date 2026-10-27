package com.streamflixvip.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.data.RecentStore
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.network.AppVersionResponse
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.network.MediaSourcesResponse
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.network.sourcesMessage
import com.streamflixvip.tv.ui.activation.ActivationTvScreen
import com.streamflixvip.tv.ui.components.LocalToaster
import com.streamflixvip.tv.ui.components.ToastHost
import com.streamflixvip.tv.ui.components.Toaster
import com.streamflixvip.tv.ui.detail.DetailTvScreen
import com.streamflixvip.tv.ui.home.CategoryTvScreen
import com.streamflixvip.tv.ui.livetv.LivePlayerTvScreen
import com.streamflixvip.tv.ui.player.PlayerArgs
import com.streamflixvip.tv.ui.player.PlayerTvScreen
import com.streamflixvip.tv.ui.shell.MainShell
import com.streamflixvip.tv.ui.splash.SplashTvScreen
import com.streamflixvip.tv.ui.theme.LocalPalette
import com.streamflixvip.tv.ui.theme.StreamFlixTvTheme
import com.streamflixvip.tv.ui.update.UpdateRequiredTvScreen
import com.streamflixvip.tv.update.ApkInstaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class LiveSession(
    val channels: List<LiveChannel>,
    val index: Int,
    val epgNow: Map<String, String>,
    val epgNext: Map<String, String>,
)

class MainTvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                val toaster = remember { Toaster() }
                CompositionLocalProvider(LocalToaster provides toaster) {
                    // fundo opaco SEMPRE: sem ele algumas TVs/Fire TV nao limpam a tela
                    // e ficam "rastros" (cartoes e titulos repetidos, tela tremendo)
                    Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                        AppRoot(toaster)
                        ToastHost(toaster)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppRoot(toaster: Toaster) {
        val navController = rememberNavController()
        val activationManager = remember { TvActivationManager(applicationContext) }
        val recent = remember { RecentStore(applicationContext) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // ── atualizacao obrigatoria ─────────────────────────────────────
        var updateInfo by remember { mutableStateOf<AppVersionResponse?>(null) }
        var isDownloadingUpdate by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableIntStateOf(0) }
        var updateStatusMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            runCatching { NetworkModule.appVersionApi.getVersion() }.onSuccess { response ->
                if (response.forceUpdate && response.versionCode > BuildConfig.VERSION_CODE) updateInfo = response
            }
        }

        val info = updateInfo
        if (info != null) {
            UpdateRequiredTvScreen(
                versionName = info.versionName,
                releaseNotes = info.releaseNotes ?: "",
                isDownloading = isDownloadingUpdate,
                downloadProgress = downloadProgress,
                statusMessage = updateStatusMessage,
                onDownloadClick = {
                    val url = info.apkUrl
                    if (url.isNullOrBlank()) {
                        updateStatusMessage = "Link da atualização não configurado no servidor."
                        return@UpdateRequiredTvScreen
                    }
                    isDownloadingUpdate = true
                    downloadProgress = 0
                    updateStatusMessage = null
                    scope.launch {
                        try {
                            if (!ApkInstaller.canInstallPackages(context)) {
                                updateStatusMessage = "Permita instalar apps deste app nas configurações e aperte Baixar de novo."
                                ApkInstaller.openInstallPermissionSettings(context)
                                isDownloadingUpdate = false
                                return@launch
                            }
                            val file = ApkInstaller.download(context, url) { pct -> downloadProgress = pct }
                            downloadProgress = 100
                            ApkInstaller.install(context, file)
                        } catch (e: Exception) {
                            updateStatusMessage = "Falha no download: ${e.message ?: "erro desconhecido"}"
                        } finally {
                            isDownloadingUpdate = false
                        }
                    }
                },
            )
            return
        }

        // ── estado compartilhado entre telas ─────────────────────────────
        var playerArgs by remember { mutableStateOf<PlayerArgs?>(null) }
        var liveSession by remember { mutableStateOf<LiveSession?>(null) }
        var busy by remember { mutableStateOf(false) }

        fun openPlayer(args: PlayerArgs) {
            playerArgs = args
            if (navController.currentDestination?.route != "player") {
                navController.navigate("player") { launchSingleTop = true }
            }
        }

        /** Busca servidores (fast=1) e para cedo quando o servidor preferido aparece. */
        suspend fun resolveSources(tmdbId: Int, type: String, season: Int?, episode: Int?, preferred: String?): Pair<List<VipSource>, MediaSourcesResponse?> {
            var last: MediaSourcesResponse? = null
            var polls = 0
            while (true) {
                val res = runCatching {
                    NetworkModule.vipApi.getMediaSources(tmdbId, type, season, episode, fast = 1)
                }.getOrNull() ?: break
                val merged = if (last != null && res.sources.size < last.sources.size) res.copy(sources = last.sources) else res
                last = merged
                val hasPreferred = preferred != null && merged.sources.any { it.displayName == preferred }
                if (!merged.partial || merged.code != null || hasPreferred || polls >= 8) break
                delay((merged.pollAfterMs ?: 1500L).coerceIn(500L, 5000L))
                polls++
            }
            val list = last?.sources.orEmpty().distinctBy { it.source_url }
                .sortedByDescending { preferred != null && it.displayName == preferred }
            return list to last
        }

        fun resumeContinueWatching(entry: LocalWatchProgress) {
            if (busy) return
            busy = true
            toaster.show("Abrindo ${entry.title}…")
            scope.launch {
                try {
                    val isTv = entry.mediaType == "tv"
                    val (sources, res) = resolveSources(
                        entry.tmdbId,
                        if (isTv) "tv" else "movie",
                        if (isTv) entry.season else null,
                        if (isTv) entry.episode else null,
                        recent.lastServer(entry.tmdbId),
                    )
                    if (sources.isEmpty()) {
                        toaster.show(sourcesMessage(res))
                        navController.navigate("detail/${entry.mediaType}/${entry.tmdbId}")
                        return@launch
                    }
                    val base = entry.title.substringBefore(" · T")
                    openPlayer(
                        PlayerArgs(
                            tmdbId = entry.tmdbId,
                            mediaType = entry.mediaType,
                            season = entry.season,
                            episode = entry.episode,
                            title = if (isTv) "$base · T${entry.season}E${entry.episode}" else base,
                            posterPath = entry.posterPath,
                            backdropPath = entry.backdropPath,
                            sources = sources,
                        ),
                    )
                } finally {
                    busy = false
                }
            }
        }

        /** Proximo episodio: mesma temporada, senao 1o da proxima. Mesmo servidor de antes. */
        fun playNextEpisode() {
            val cur = playerArgs ?: return
            if (cur.mediaType != "tv" || busy) return
            busy = true
            toaster.show("Carregando o próximo episódio…")
            scope.launch {
                try {
                    val next = runCatching {
                        val sd = NetworkModule.tmdbApi.requestSeasonDetail("/tv/${cur.tmdbId}/season/${cur.season}")
                        if (sd.episodes.orEmpty().any { it.episode_number == cur.episode + 1 }) {
                            cur.season to cur.episode + 1
                        } else {
                            val nsd = runCatching {
                                NetworkModule.tmdbApi.requestSeasonDetail("/tv/${cur.tmdbId}/season/${cur.season + 1}")
                            }.getOrNull()
                            if (nsd?.episodes.orEmpty().isNotEmpty()) (cur.season + 1) to 1 else null
                        }
                    }.getOrElse { cur.season to cur.episode + 1 }
                    if (next == null) {
                        toaster.show("Você chegou ao último episódio")
                        return@launch
                    }
                    val preferred = cur.sources.firstOrNull()?.displayName ?: recent.lastServer(cur.tmdbId)
                    val (sources, res) = resolveSources(cur.tmdbId, "tv", next.first, next.second, preferred)
                    // saiu do player enquanto buscava: nao reabre o player sozinho
                    if (navController.currentDestination?.route != "player") return@launch
                    if (sources.isEmpty()) {
                        toaster.show(sourcesMessage(res))
                        return@launch
                    }
                    val base = cur.title.substringBefore(" · T")
                    openPlayer(
                        cur.copy(
                            season = next.first,
                            episode = next.second,
                            title = "$base · T${next.first}E${next.second}",
                            sources = sources,
                            fromStart = false,
                        ),
                    )
                } finally {
                    busy = false
                }
            }
        }

        // Tela atual vai junto no relatorio de erro (rota + id, nada pessoal)
        androidx.compose.runtime.DisposableEffect(navController) {
            val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, dest, args ->
                val route = dest.route ?: "?"
                val id = args?.let { a -> listOf("mediaType", "tmdbId", "key").mapNotNull { k -> a.get(k)?.toString() } }
                    ?.joinToString("/").orEmpty()
                com.streamflixvip.tv.util.CrashLog.screen(if (id.isEmpty()) route else "$route [$id]")
            }
            navController.addOnDestinationChangedListener(listener)
            onDispose { navController.removeOnDestinationChangedListener(listener) }
        }

        // Sem animacao entre telas: a tela antiga nao fica por baixo da nova e TV box fraca agradece
        NavHost(
            navController = navController,
            startDestination = "splash",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("splash") {
                SplashTvScreen(
                    activationManager = activationManager,
                    onFinished = { isActivated ->
                        navController.navigate(if (isActivated) "main" else "activation") {
                            popUpTo("splash") { inclusive = true }
                        }
                    },
                )
            }
            composable("activation") {
                ActivationTvScreen(
                    activationManager = activationManager,
                    onActivated = {
                        navController.navigate("main") { popUpTo("activation") { inclusive = true } }
                    },
                )
            }
            composable("main") {
                MainShell(
                    activationManager = activationManager,
                    onItemClick = { id, type -> navController.navigate("detail/$type/$id") },
                    onContinueClick = { resumeContinueWatching(it) },
                    onSeeAll = { key -> navController.navigate("category/$key") },
                    onChannelClick = { _, list, index, now, next ->
                        liveSession = LiveSession(list, index, now, next)
                        navController.navigate("live-player") { launchSingleTop = true }
                    },
                    onDeactivated = {
                        navController.navigate("activation") { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(
                route = "category/{key}",
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                CategoryTvScreen(
                    rowKey = entry.arguments?.getString("key") ?: "trending",
                    onItemClick = { id, type -> navController.navigate("detail/$type/$id") },
                )
            }
            composable(
                route = "detail/{mediaType}/{tmdbId}",
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("tmdbId") { type = NavType.IntType },
                ),
            ) { entry ->
                val mediaType = entry.arguments?.getString("mediaType") ?: "movie"
                val tmdbId = entry.arguments?.getInt("tmdbId") ?: 0
                DetailTvScreen(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    onPlay = { req ->
                        openPlayer(
                            PlayerArgs(
                                tmdbId = tmdbId,
                                mediaType = mediaType,
                                season = req.season,
                                episode = req.episode,
                                title = req.title,
                                posterPath = req.posterPath,
                                backdropPath = req.backdropPath,
                                sources = req.sources,
                                fromStart = req.fromStart,
                            ),
                        )
                    },
                    onOpenTitle = { id, type ->
                        navController.navigate("detail/$type/$id") {
                            popUpTo("detail/{mediaType}/{tmdbId}") { inclusive = true }
                        }
                    },
                )
            }
            composable("player") { entry ->
                val args = playerArgs
                if (args != null) {
                    PlayerTvScreen(
                        args = args,
                        // Nao zera playerArgs: durante a animacao de saida a rota "player"
                        // ainda esta na tela e, com args nulo, fazia um 2o popBackStack
                        // (voltava 2 telas, ex. pulava os detalhes).
                        onBack = {
                            if (navController.currentDestination?.route == "player") navController.popBackStack()
                        },
                        onNextEpisode = if (args.mediaType == "tv") ({ playNextEpisode() }) else null,
                    )
                } else {
                    LaunchedEffect(Unit) {
                        if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
                    }
                }
            }
            composable("live-player") { entry ->
                val session = liveSession
                if (session != null && session.channels.isNotEmpty()) {
                    LivePlayerTvScreen(
                        channels = session.channels,
                        initialIndex = session.index,
                        epgNow = session.epgNow,
                        epgNext = session.epgNext,
                        onChannelChanged = { i -> liveSession = session.copy(index = i) },
                        onBack = {
                            if (navController.currentDestination?.route == "live-player") navController.popBackStack()
                        },
                    )
                } else {
                    LaunchedEffect(Unit) {
                        if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
                    }
                }
            }
        }
    }
}
