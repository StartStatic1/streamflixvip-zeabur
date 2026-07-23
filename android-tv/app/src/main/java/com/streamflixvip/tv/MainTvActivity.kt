package com.streamflixvip.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.tv.network.VipSource
import com.streamflixvip.tv.ui.detail.DetailTvScreen
import com.streamflixvip.tv.ui.home.HomeTvScreen
import com.streamflixvip.tv.ui.player.PlayerTvScreen
import com.streamflixvip.tv.ui.theme.StreamFlixTvTheme

/**
 * Activity única do app de TV — Navigation Compose trocando entre
 * Home, Detail e Player. O fluxo completo:
 *
 *   Home → click poster → Detail (tmdbId + mediaType)
 *   Detail → "Assistir" → resolve fontes → Player (source + season + episode)
 *   Player → volta → Detail → volta → Home
 */
class MainTvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                val navController = rememberNavController()

                // Estado compartilhado pra passar source do Detail pro Player
                var pendingSource: VipSource? = null
                var pendingSeason: Int = 0
                var pendingEpisode: Int = 0
                var pendingTitle: String = "Sem título"

                NavHost(navController = navController, startDestination = "home") {

                    // ── HOME ──
                    composable("home") {
                        HomeTvScreen(
                            onItemClick = { tmdbId, mediaType ->
                                navController.navigate("detail/$mediaType/$tmdbId")
                            },
                        )
                    }

                    // ── DETAIL ──
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
                            onPlayClick = { source, season, episode, title ->
                                pendingSource = source
                                pendingSeason = season
                                pendingEpisode = episode
                                pendingTitle = title
                                navController.navigate("player")
                            },
                            onBack = { navController.popBackStack() },
                            onOpenTitle = { newTmdbId, newMediaType ->
                                navController.navigate("detail/$newMediaType/$newTmdbId") {
                                    popUpTo("detail/${mediaType}/${tmdbId}") { inclusive = true }
                                }
                            },
                            onPlayTrailer = { /* TODO: abrir trailer */ },
                        )
                    }

                    // ── PLAYER ──
                    composable("player") {
                        val source = pendingSource
                        if (source != null) {
                            PlayerTvScreen(
                                source = source,
                                season = pendingSeason,
                                episode = pendingEpisode,
                                title = pendingTitle,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
