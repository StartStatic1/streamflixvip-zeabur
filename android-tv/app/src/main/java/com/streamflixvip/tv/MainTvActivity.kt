package com.streamflixvip.tv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.ui.account.AccountTvScreen
import com.streamflixvip.tv.ui.account.MyListTvScreen
import com.streamflixvip.tv.ui.activation.ActivationTvScreen
import com.streamflixvip.tv.ui.detail.DetailTvScreen
import com.streamflixvip.tv.ui.home.CategoryTvScreen
import com.streamflixvip.tv.ui.home.HomeTvScreen
import com.streamflixvip.tv.ui.livetv.LivePlayerTvScreen
import com.streamflixvip.tv.ui.livetv.LiveTvScreen
import com.streamflixvip.tv.ui.player.PlayerTvScreen
import com.streamflixvip.tv.ui.search.SearchTvScreen
import com.streamflixvip.tv.ui.splash.SplashTvScreen
import com.streamflixvip.tv.ui.theme.StreamFlixTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import com.streamflixvip.tv.BuildConfig
import com.streamflixvip.tv.network.AppVersionResponse
import com.streamflixvip.tv.ui.update.UpdateRequiredTvScreen
import com.streamflixvip.tv.update.ApkInstaller

class MainTvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                val navController = rememberNavController()
                val activationManager = remember { TvActivationManager(applicationContext) }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                var updateInfo by remember { mutableStateOf<AppVersionResponse?>(null) }
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableIntStateOf(0) }
                var updateStatusMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    try {
                        val response = NetworkModule.appVersionApi.getVersion()
                        if (response.forceUpdate && response.versionCode > BuildConfig.VERSION_CODE) {
                            updateInfo = response
                        }
                    } catch (_: Exception) {
                    }
                }

                if (updateInfo != null) {
                    UpdateRequiredTvScreen(
                        versionName = updateInfo!!.versionName,
                        releaseNotes = updateInfo!!.releaseNotes ?: "",
                        isDownloading = isDownloadingUpdate,
                        downloadProgress = downloadProgress,
                        statusMessage = updateStatusMessage,
                        onDownloadClick = {
                            val url = updateInfo!!.apkUrl
                            if (url.isNullOrBlank()) {
                                updateStatusMessage = "URL do APK nao configurada no servidor."
                                return@UpdateRequiredTvScreen
                            }
                            isDownloadingUpdate = true
                            downloadProgress = 0
                            updateStatusMessage = null
                            scope.launch {
                                try {
                                    // Em Android 8+: se ainda nao pode instalar, abre a permissao
                                    if (!ApkInstaller.canInstallPackages(context)) {
                                        updateStatusMessage =
                                            "Permita instalar apps deste app nas configuracoes e toque de novo em Baixar."
                                        ApkInstaller.openInstallPermissionSettings(context)
                                        isDownloadingUpdate = false
                                        return@launch
                                    }
                                    val file = ApkInstaller.download(context, url) { pct ->
                                        downloadProgress = pct
                                    }
                                    downloadProgress = 100
                                    ApkInstaller.install(context, file)
                                } catch (e: Exception) {
                                    updateStatusMessage =
                                        "Falha no download: ${e.message ?: "erro desconhecido"}"
                                    Toast.makeText(
                                        this@MainTvActivity,
                                        "Erro ao baixar atualizacao",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } finally {
                                    isDownloadingUpdate = false
                                }
                            }
                        },
                    )
                    return@StreamFlixTvTheme
                }

                var pendingSource by remember { mutableStateOf<VipSource?>(null) }
                var pendingSources by remember { mutableStateOf<List<VipSource>>(emptyList()) }
                var pendingSeason by remember { mutableStateOf(0) }
                var pendingEpisode by remember { mutableStateOf(0) }
                var pendingTitle by remember { mutableStateOf("Sem título") }
                var pendingTmdbId by remember { mutableStateOf(0) }
                var pendingMediaType by remember { mutableStateOf("movie") }
                var pendingPosterPath by remember { mutableStateOf<String?>(null) }

                var pendingLiveChannel by remember { mutableStateOf<LiveChannel?>(null) }
                var pendingLiveList by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
                var pendingLiveIndex by remember { mutableIntStateOf(0) }

                fun openPlayer(
                    source: VipSource,
                    sources: List<VipSource>,
                    season: Int,
                    episode: Int,
                    title: String,
                    tmdbId: Int,
                    mediaType: String,
                    posterPath: String?,
                ) {
                    pendingSource = source
                    pendingSources = sources
                    pendingSeason = season
                    pendingEpisode = episode
                    pendingTitle = title
                    pendingTmdbId = tmdbId
                    pendingMediaType = mediaType
                    pendingPosterPath = posterPath
                    navController.navigate("player") { launchSingleTop = true }
                }

                fun goBackFromPlayer() {
                    val tmdbId = pendingTmdbId
                    val mediaType = pendingMediaType
                    pendingSource = null
                    pendingSources = emptyList()
                    val detailRoute = "detail/$mediaType/$tmdbId"
                    if (tmdbId > 0) {
                        val landed = navController.popBackStack(detailRoute, inclusive = false)
                        if (!landed) {
                            navController.navigate(detailRoute) {
                                popUpTo("player") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }

                fun resumeContinueWatching(entry: LocalWatchProgress) {
                    scope.launch {
                        val sources = withContext(Dispatchers.IO) {
                            runCatching {
                                if (entry.mediaType == "tv") {
                                    NetworkModule.vipApi.getMediaSources(
                                        tmdbId = entry.tmdbId,
                                        type = "tv",
                                        season = entry.season,
                                        episode = entry.episode,
                                    ).sources
                                } else {
                                    NetworkModule.vipApi.getMediaSources(
                                        tmdbId = entry.tmdbId,
                                        type = "movie",
                                    ).sources
                                }
                            }.getOrDefault(emptyList())
                        }
                        if (sources.isEmpty()) {
                            Toast.makeText(this@MainTvActivity, "Nenhuma fonte disponível — abrindo detalhes", Toast.LENGTH_SHORT).show()
                            navController.navigate("detail/${entry.mediaType}/${entry.tmdbId}")
                            return@launch
                        }
                        openPlayer(sources.first(), sources, entry.season, entry.episode, entry.title, entry.tmdbId, entry.mediaType, entry.posterPath)
                    }
                }

                fun playNextEpisode() {
                    if (pendingMediaType != "tv" || pendingTmdbId <= 0) return
                    val nextEp = pendingEpisode + 1
                    val season = pendingSeason
                    val tmdbId = pendingTmdbId
                    val titleBase = pendingTitle.substringBefore(" · ").ifBlank { pendingTitle }
                    scope.launch {
                        val sources = withContext(Dispatchers.IO) {
                            runCatching {
                                NetworkModule.vipApi.getMediaSources(
                                    tmdbId = tmdbId,
                                    type = "tv",
                                    season = season,
                                    episode = nextEp,
                                ).sources
                            }.getOrDefault(emptyList())
                        }
                        if (sources.isEmpty()) {
                            Toast.makeText(this@MainTvActivity, "Não há próximo episódio nesta temporada", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        openPlayer(sources.first(), sources, season, nextEp, "$titleBase · T${season}E$nextEp", tmdbId, "tv", pendingPosterPath)
                    }
                }

                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") {
                        SplashTvScreen(
                            activationManager = activationManager,
                            onFinished = { isActivated ->
                                val next = if (isActivated) "home" else "activation"
                                navController.navigate(next) { popUpTo("splash") { inclusive = true } }
                            },
                        )
                    }
                    composable("activation") {
                        ActivationTvScreen(
                            activationManager = activationManager,
                            onActivated = {
                                navController.navigate("home") { popUpTo("activation") { inclusive = true } }
                            },
                        )
                    }
                    composable("home") {
                        HomeTvScreen(
                            onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$mediaType/$tmdbId") },
                            onContinueClick = { entry -> resumeContinueWatching(entry) },
                            onNavigateToSearch = { navController.navigate("search") },
                            onNavigateToLiveTv = { navController.navigate("live") },
                            onNavigateToMyList = { navController.navigate("mylist") },
                            onNavigateToAccount = { navController.navigate("account") },
                            onExploreCategory = { category -> navController.navigate("category/$category") },
                        )
                    }
                    composable("live") {
                        LiveTvScreen(
                            onChannelClick = { channel, filteredList, indexInList ->
                                pendingLiveChannel = channel
                                pendingLiveList = filteredList
                                pendingLiveIndex = indexInList
                                navController.navigate("live-player") { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("live-player") {
                        val channel = pendingLiveChannel
                        if (channel != null) {
                            LivePlayerTvScreen(
                                channelName = channel.name,
                                streams = channel.streams,
                                channelList = pendingLiveList,
                                initialChannelIndex = pendingLiveIndex,
                                onChannelChanged = { ch ->
                                    pendingLiveChannel = ch
                                    val idx = pendingLiveList.indexOfFirst { it.id == ch.id }
                                    if (idx >= 0) pendingLiveIndex = idx
                                },
                                onBack = {
                                    val landed = navController.popBackStack("live", inclusive = false)
                                    if (!landed) {
                                        navController.navigate("live") {
                                            popUpTo("live-player") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    pendingLiveChannel = null
                                },
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                val landed = navController.popBackStack("live", inclusive = false)
                                if (!landed) {
                                    navController.navigate("live") {
                                        popUpTo("live-player") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                    composable(
                        route = "category/{key}",
                        arguments = listOf(navArgument("key") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val key = backStackEntry.arguments?.getString("key") ?: "trending"
                        CategoryTvScreen(
                            category = key,
                            onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$mediaType/$tmdbId") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("search") {
                        SearchTvScreen(
                            onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$mediaType/$tmdbId") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("mylist") {
                        MyListTvScreen(
                            onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$mediaType/$tmdbId") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("account") {
                        AccountTvScreen(
                            activationManager = activationManager,
                            onBack = { navController.popBackStack() },
                            onDeactivated = {
                                navController.navigate("activation") { popUpTo(0) { inclusive = true } }
                            },
                        )
                    }
                    composable(
                        route = "detail/{mediaType}/{tmdbId}",
                        arguments = listOf(
                            navArgument("mediaType") { type = NavType.StringType },
                            navArgument("tmdbId") { type = NavType.IntType },
                        ),
                    ) { backStackEntry ->
                        val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
                        val tmdbId = backStackEntry.arguments?.getInt("tmdbId") ?: 0
                        DetailTvScreen(
                            tmdbId = tmdbId,
                            mediaType = mediaType,
                            onPlayClick = { source, sources, season, episode, title, posterPath ->
                                openPlayer(source, sources, season, episode, title, tmdbId, mediaType, posterPath)
                            },
                            onBack = { navController.popBackStack() },
                            onOpenTitle = { newTmdbId, newMediaType ->
                                navController.navigate("detail/$newMediaType/$newTmdbId") {
                                    popUpTo("detail/${mediaType}/${tmdbId}") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("player") {
                        val source = pendingSource
                        if (source != null) {
                            PlayerTvScreen(
                                source = source,
                                sources = pendingSources,
                                season = pendingSeason,
                                episode = pendingEpisode,
                                title = pendingTitle,
                                tmdbId = pendingTmdbId,
                                mediaType = pendingMediaType,
                                posterPath = pendingPosterPath,
                                onBack = { goBackFromPlayer() },
                                onServerFailed = { goBackFromPlayer() },
                                onNextEpisode = if (pendingMediaType == "tv") ({ playNextEpisode() }) else null,
                            )
                        } else {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}
