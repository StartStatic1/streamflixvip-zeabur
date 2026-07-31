package com.streamflixvip.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.tv.data.TvActivationManager
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.ui.account.AccountTvScreen
import com.streamflixvip.tv.ui.activation.ActivationTvScreen
import com.streamflixvip.tv.ui.detail.DetailTvScreen
import com.streamflixvip.tv.ui.home.HomeTvScreen
import com.streamflixvip.tv.ui.player.PlayerTvScreen
import com.streamflixvip.tv.ui.search.SearchTvScreen
import com.streamflixvip.tv.ui.splash.SplashTvScreen
import com.streamflixvip.tv.ui.theme.StreamFlixTvTheme

/**
 * Activity única do app de TV — Navigation Compose com rotas:
 *
 *   "splash" → SplashTvScreen
 *   "activation" → ActivationTvScreen (gate VIP)
 *   "home" → HomeTvScreen
 *   "search" → SearchTvScreen
 *   "account" → AccountTvScreen (perfil + engrenagem)
 *   "detail/{mediaType}/{tmdbId}" → DetailTvScreen
 *   "player" → PlayerTvScreen
 */
class MainTvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                val navController = rememberNavController()
                val activationManager = remember { TvActivationManager(applicationContext) }

                LaunchedEffect(Unit) {
                    activationManager.revalidate()
                }

                var pendingSource by remember { mutableStateOf<VipSource?>(null) }
                var pendingSources by remember { mutableStateOf<List<VipSource>>(emptyList()) }
                var pendingSeason by remember { mutableStateOf(0) }
                var pendingEpisode by remember { mutableStateOf(0) }
                var pendingTitle by remember { mutableStateOf("Sem título") }
                var pendingTmdbId by remember { mutableStateOf(0) }
                var pendingMediaType by remember { mutableStateOf("movie") }
                var pendingPosterPath by remember { mutableStateOf<String?>(null) }

                NavHost(navController = navController, startDestination = "splash") {

                    composable("splash") {
                        SplashTvScreen(
                            onFinished = {
                                val next = if (activationManager.isActivatedLocally) "home" else "activation"
                                navController.navigate(next) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            },
                        )
                    }

                    composable("activation") {
                        ActivationTvScreen(
                            activationManager = activationManager,
                            onActivated = {
                                navController.navigate("home") {
                                    popUpTo("activation") { inclusive = true }
                                }
                            },
                        )
                    }

                    composable("home") {
                        HomeTvScreen(
                            onItemClick = { tmdbId, mediaType ->
                                navController.navigate("detail/$mediaType/$tmdbId")
                            },
                            onNavigateToSearch = {
                                navController.navigate("search")
                            },
                            onNavigateToAccount = {
                                navController.navigate("account")
                            },
                        )
                    }

                    composable("search") {
                        SearchTvScreen(
                            onItemClick = { tmdbId, mediaType ->
                                navController.navigate("detail/$mediaType/$tmdbId")
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable("account") {
                        AccountTvScreen(
                            activationManager = activationManager,
                            onBack = { navController.popBackStack() },
                            onDeactivated = {
                                navController.navigate("activation") {
                                    popUpTo(0) { inclusive = true }
                                }
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
                                pendingSource = source
                                pendingSources = sources
                                pendingSeason = season
                                pendingEpisode = episode
                                pendingTitle = title
                                pendingTmdbId = tmdbId
                                pendingMediaType = mediaType
                                pendingPosterPath = posterPath
                                navController.navigate("player")
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
                                onBack = {
                                    pendingSource = null
                                    pendingSources = emptyList()
                                    navController.popBackStack()
                                },
                                onServerFailed = {
                                    pendingSource = null
                                    pendingSources = emptyList()
                                    navController.popBackStack()
                                },
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }
}
