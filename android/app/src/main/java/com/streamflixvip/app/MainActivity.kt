package com.streamflixvip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.app.data.AuthRepository
import com.streamflixvip.app.data.SessionStore
import com.streamflixvip.app.data.VipRepository
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.ui.auth.AuthScreen
import com.streamflixvip.app.ui.auth.AuthViewModel
import com.streamflixvip.app.ui.detail.DetailScreen
import com.streamflixvip.app.ui.detail.DetailViewModel
import com.streamflixvip.app.ui.explore.ExploreScreen
import com.streamflixvip.app.ui.explore.ExploreViewModel
import com.streamflixvip.app.ui.explore.PendingExploreFilter
import com.streamflixvip.app.ui.home.HomeScreen
import com.streamflixvip.app.ui.home.HomeViewModel
import com.streamflixvip.app.ui.livetv.LivePlayerScreen
import com.streamflixvip.app.ui.livetv.LiveTvScreen
import com.streamflixvip.app.ui.livetv.PendingLiveChannel
import com.streamflixvip.app.ui.mylist.MyListScreen
import com.streamflixvip.app.ui.mylist.MyListViewModel
import com.streamflixvip.app.ui.nav.StreamFlixBottomBar
import com.streamflixvip.app.ui.player.PlayerScreen
import com.streamflixvip.app.ui.player.VipWaitScreen
import com.streamflixvip.app.ui.profile.ProfileScreen
import com.streamflixvip.app.ui.search.SearchScreen
import com.streamflixvip.app.ui.search.SearchViewModel
import com.streamflixvip.app.ui.genre.GenreDetailScreen
import com.streamflixvip.app.ui.genre.GenreScreen
import com.streamflixvip.app.ui.genre.GenreViewModel
import com.streamflixvip.app.ui.theme.StreamFlixTheme
import java.net.URLDecoder
import java.net.URLEncoder
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        StartAppSDK.init(this, "206908168", true)
        StartAppAd.disableSplash()

        enableEdgeToEdge()
        setContent {
            // Garante LocalLifecycleOwner p/ lifecycle-compose (release/minify)
            CompositionLocalProvider(LocalLifecycleOwner provides this) {
                StreamFlixTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val sessionStore = remember {
        SessionStore(context).also {
            com.streamflixvip.app.network.NetworkModule.sessionStore = it
        }
    }
    val authRepository = remember { AuthRepository(sessionStore) }
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory { AuthViewModel(authRepository) })

    var isLoggedIn by remember { mutableStateOf(sessionStore.isLoggedIn) }

    var updateInfo by remember { mutableStateOf<com.streamflixvip.app.network.AppVersionResponse?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(-1) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val updateScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val response = com.streamflixvip.app.network.NetworkModule.appVersionApi.getLatestVersion()
            if (response.forceUpdate && response.versionCode > com.streamflixvip.app.BuildConfig.VERSION_CODE) {
                updateInfo = response
            }
        } catch (_: Exception) {
        }
    }

    if (updateInfo != null) {
        com.streamflixvip.app.ui.update.UpdateRequiredScreen(
            versionName = updateInfo!!.versionName,
            releaseNotes = updateInfo!!.releaseNotes ?: "",
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadProgress,
            errorMessage = updateError,
            onDownloadClick = {
                val url = updateInfo!!.apkUrl
                if (url.isBlank()) {
                    updateError = "URL vazia"
                    return@UpdateRequiredScreen
                }
                updateError = null
                isDownloadingUpdate = true
                downloadProgress = 0
                updateScope.launch {
                    try {
                        if (!com.streamflixvip.app.update.ApkInstaller.canInstallPackages(context)) {
                            com.streamflixvip.app.update.ApkInstaller.openInstallPermissionSettings(context)
                            updateError = "Ative permitir desta fonte e toque Baixar de novo"
                            android.widget.Toast.makeText(context, updateError, android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val file = com.streamflixvip.app.update.ApkInstaller.download(context, url) { pct ->
                            downloadProgress = pct
                        }
                        downloadProgress = 100
                        com.streamflixvip.app.update.ApkInstaller.install(context, file)
                        android.widget.Toast.makeText(context, "Abrindo instalador...", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        updateError = (e.message ?: "Falha") + " — abrindo navegador"
                        android.widget.Toast.makeText(context, updateError, android.widget.Toast.LENGTH_LONG).show()
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url),
                                )
                            )
                        } catch (_: Exception) {
                        }
                    } finally {
                        isDownloadingUpdate = false
                        downloadProgress = -1
                    }
                }
            },
        )
        return
    }

    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        com.streamflixvip.app.ui.splash.SplashScreen(onSplashFinished = { showSplash = false })
    } else if (!isLoggedIn) {
        AuthScreen(viewModel = authViewModel, onLoggedIn = { isLoggedIn = true })
    } else {
        MainAppScaffold(
            authViewModel = authViewModel,
            userId = sessionStore.userId,
            userEmail = sessionStore.userEmail,
            accessToken = sessionStore.accessToken,
            onSignedOut = { isLoggedIn = false },
        )
    }
}

@Composable
private fun MainAppScaffold(
    authViewModel: AuthViewModel,
    userId: String?,
    userEmail: String?,
    accessToken: String?,
    onSignedOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "explore", "livetv", "profile")
    val showTopBar = currentRoute in listOf("home", "explore", "livetv", "profile", "mylist", "genres")

    LaunchedEffect(userId) {
        if (userId != null) {
            val status = VipRepository().getStatus(userId)
            VipStatusHolder.update(status.isVip, status.expiresAt)
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                com.streamflixvip.app.ui.nav.AppTopBar(
                    onSearchClick = {
                        navController.navigate("search") { launchSingleTop = true }
                    },
                    onFavoritesClick = {
                        navController.navigate("mylist") { launchSingleTop = true }
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) StreamFlixBottomBar(navController)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                val viewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory { HomeViewModel(userId = userId, accessToken = accessToken) },
                )
                HomeScreen(
                    viewModel = viewModel,
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
                    onContinueWatchingDismiss = { entry -> viewModel.dismissContinueWatching(entry) },
                    onContinueWatchingClick = { entry ->
                        navController.navigate(
                            "detail/${entry.tmdb_id}/${entry.media_type}?season=${entry.season}&episode=${entry.episode}&resume=${entry.position_seconds}",
                        )
                    },
                    onSeeAllClick = { link ->
                        PendingExploreFilter.set(
                            com.streamflixvip.app.ui.explore.ExploreFilters(
                                category = link.category,
                                genre = link.genreId?.let { id -> com.streamflixvip.app.data.TMDB_GENRES.find { it.id == id } },
                                year = link.year,
                            ),
                        )
                        navController.navigate("explore") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable("search") {
                val viewModel: SearchViewModel = viewModel()
                SearchScreen(
                    viewModel = viewModel,
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
                )
            }

            composable("explore") {
                val viewModel: ExploreViewModel = viewModel(
                    factory = viewModelFactory {
                        val pending = PendingExploreFilter.consume()
                        ExploreViewModel(initialFilters = pending ?: com.streamflixvip.app.ui.explore.ExploreFilters())
                    },
                )
                ExploreScreen(
                    viewModel = viewModel,
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
                )
            }

            composable("livetv") {
                LiveTvScreen(
                    onChannelClick = { channel ->
                        if (!VipStatusHolder.isVipNow()) {
                            navController.navigate("profile") { launchSingleTop = true }
                            return@LiveTvScreen
                        }
                        PendingLiveChannel.set(channel)
                        navController.navigate("liveplayer")
                    },
                    onUpgradeClick = {
                        navController.navigate("profile") { launchSingleTop = true }
                    },
                )
            }

            composable("liveplayer") {
                val isVip by VipStatusHolder.isVip.collectAsState()
                if (!isVip) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
                val channel = remember { PendingLiveChannel.consume() }
                if (channel == null || channel.streams.isEmpty()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    LivePlayerScreen(
                        channelName = channel.name,
                        streams = channel.streams,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable("genres") {
                val viewModel: GenreViewModel = viewModel()
                GenreScreen(
                    viewModel = viewModel,
                    onGenreClick = { genreId, genreName, category ->
                        val encodedName = java.net.URLEncoder.encode(genreName, "UTF-8")
                        navController.navigate("genre_detail/$genreId/${category.name}?name=$encodedName")
                    },
                )
            }

            composable(
                route = "genre_detail/{genreId}/{category}?name={name}",
                arguments = listOf(
                    navArgument("genreId") { type = NavType.IntType },
                    navArgument("category") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                val args = backStackEntry.arguments!!
                GenreDetailScreen(
                    genreId = args.getInt("genreId"),
                    genreName = java.net.URLDecoder.decode(args.getString("name") ?: "", "UTF-8"),
                    category = runCatching {
                        com.streamflixvip.app.data.GenreCategory.valueOf(args.getString("category") ?: "ALL")
                    }.getOrDefault(com.streamflixvip.app.data.GenreCategory.ALL),
                    onBack = { navController.popBackStack() },
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
                )
            }

            composable("mylist") {
                val viewModel: MyListViewModel = viewModel(
                    factory = viewModelFactory { MyListViewModel(userId = userId, accessToken = accessToken) },
                )
                MyListScreen(
                    viewModel = viewModel,
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
                    onSearchClick = {
                        navController.navigate("search") { launchSingleTop = true }
                    },
                )
            }

            composable("profile") {
                ProfileScreen(
                    authViewModel = authViewModel,
                    userId = userId,
                    userEmail = userEmail,
                    onSignedOut = onSignedOut,
                    onMyListClick = {
                        navController.navigate("mylist") { launchSingleTop = true }
                    },
                    onAnnouncementClick = { tmdbId, mediaType ->
                        navController.navigate("detail/$tmdbId/$mediaType")
                    },
                )
            }

            composable(
                route = "detail/{tmdbId}/{mediaType}?season={season}&episode={episode}&resume={resume}",
                arguments = listOf(
                    navArgument("tmdbId") { type = NavType.IntType },
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("season") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("episode") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("resume") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                val tmdbId = entry.arguments?.getInt("tmdbId") ?: return@composable
                val mediaType = entry.arguments?.getString("mediaType") ?: "movie"
                val initialSeason = entry.arguments?.getInt("season") ?: -1
                val initialEpisode = entry.arguments?.getInt("episode") ?: -1
                val resumeSeconds = entry.arguments?.getInt("resume") ?: 0
                val viewModel: DetailViewModel = viewModel(
                    factory = viewModelFactory {
                        DetailViewModel(
                            tmdbId,
                            mediaType,
                            initialSeason = initialSeason,
                            initialEpisode = initialEpisode,
                            userId = userId,
                            accessToken = accessToken,
                            userDisplayName = userEmail?.substringBefore("@"),
                        )
                    },
                )
                DetailScreen(
                    viewModel = viewModel,
                    resumeSeconds = resumeSeconds,
                    initialSeason = initialSeason,
                    initialEpisode = initialEpisode,
                    onPlaySource = { source, season, episode, title, posterPath ->
                        val encodedUrl = URLEncoder.encode(
                            source.resolvedPlaybackUrl(BuildConfig.API_BASE_URL),
                            "UTF-8",
                        )
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        val encodedPoster = URLEncoder.encode(posterPath ?: "none", "UTF-8")
                        navController.navigate(
                            "player/$encodedUrl/${source.isDirectPlayable}/$tmdbId/$mediaType/$season/$episode/$encodedTitle/$encodedPoster/$resumeSeconds",
                        )
                    },
                    onBack = { navController.popBackStack() },
                    onUpgradeClick = {
                        navController.navigate("profile") { launchSingleTop = true }
                    },
                    onOpenTitle = { openTmdbId, openMediaType ->
                        navController.navigate("detail/$openTmdbId/$openMediaType")
                    },
                )
            }

            composable(
                route = "player/{encodedUrl}/{isDirect}/{tmdbId}/{mediaType}/{season}/{episode}/{encodedTitle}/{encodedPoster}/{resume}",
                arguments = listOf(
                    navArgument("encodedUrl") { type = NavType.StringType },
                    navArgument("isDirect") { type = NavType.BoolType },
                    navArgument("tmdbId") { type = NavType.IntType },
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType },
                    navArgument("encodedTitle") { type = NavType.StringType },
                    navArgument("encodedPoster") { type = NavType.StringType },
                    navArgument("resume") { type = NavType.IntType },
                ),
            ) { entry ->
                val args = entry.arguments ?: return@composable
                val encodedUrl = args.getString("encodedUrl") ?: return@composable
                val isDirect = args.getBoolean("isDirect")
                val playerTmdbId = args.getInt("tmdbId")
                val playerMediaType = args.getString("mediaType") ?: "movie"
                val season = args.getInt("season")
                val episode = args.getInt("episode")
                val resumeSeconds = args.getInt("resume")
                val title = URLDecoder.decode(args.getString("encodedTitle") ?: "", "UTF-8")
                val posterPath = URLDecoder.decode(args.getString("encodedPoster") ?: "none", "UTF-8").let {
                    if (it == "none") null else it
                }
                val url = URLDecoder.decode(encodedUrl, "UTF-8")

                val isVip by VipStatusHolder.isVip.collectAsState()
                var waitDone by remember(entry.id) { mutableStateOf(isVip) }

                if (!waitDone) {
                    VipWaitScreen(
                        title = title,
                        posterPath = posterPath,
                        onWaitFinished = { waitDone = true },
                        onUpgradeClick = {
                            navController.navigate("profile") { launchSingleTop = true }
                        },
                    )
                } else {
                    PlayerScreen(
                        sourceUrl = url,
                        isDirectPlayable = isDirect,
                        userId = userId,
                        accessToken = accessToken,
                        tmdbId = playerTmdbId,
                        mediaType = playerMediaType,
                        season = season,
                        episode = episode,
                        title = title,
                        posterPath = posterPath,
                        resumeSeconds = resumeSeconds,
                    )
                }
            }
        }
    }
}

private fun <T : androidx.lifecycle.ViewModel> viewModelFactory(create: () -> T) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM {
            return create() as VM
        }
    }
