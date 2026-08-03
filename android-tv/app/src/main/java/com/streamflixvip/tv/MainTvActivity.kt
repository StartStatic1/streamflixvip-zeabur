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
import com.streamflixvip.tv.network.PostgrestFilter
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

class MainTvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                val navController = rememberNavController()
                val activationManager = remember { TvActivationManager(applicationContext) }
                val scope = rememberCoroutineScope()

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
                                    NetworkModule.supabaseApi.getSourcesForEpisode(
                                        apiKey = NetworkModule.supabaseAnonKey,
                                        tmdbIdFilter = PostgrestFilter.eq(entry.tmdbId),
                                        seasonFilter = PostgrestFilter.eq(entry.season),
                                        episodeFilter = PostgrestFilter.eq(entry.episode),
                                    )
                                } else {
                                    NetworkModule.supabaseApi.getSourcesForMovie(
                                        apiKey = NetworkModule.supabaseAnonKey,
                                        tmdbIdFilter = PostgrestFilter.eq(entry.tmdbId),
                                        mediaTypeFilter = PostgrestFilter.eq(entry.mediaType),
                                    )
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
                                NetworkModule.supabaseApi.getSourcesForEpisode(
                                    apiKey = NetworkModule.supabaseAnonKey,
                                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                                    seasonFilter = PostgrestFilter.eq(season),
                                    episodeFilter = PostgrestFilter.eq(nextEp),
                                )
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
                                    // Nao zerar pendingLiveChannel antes do pop: se zerar, o
                                    // composable cai no else e o LaunchedEffect faz segundo pop
                                    // (pula live e vai pra Home no Firestick).
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
