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
import com.streamflixvip.tv.ui.detail.DetailTvScreen
import com.streamflixvip.tv.ui.home.HomeTvScreen
import com.streamflixvip.tv.ui.theme.StreamFlixTvTheme

/**
 * Activity única do app de TV — assim como o app de celular usa uma
 * MainActivity + Navigation Compose (ver android/.../MainActivity.kt),
 * este app também roda tudo dentro de uma Activity só, com Navigation
 * Compose trocando entre Home e Detail (Player entra depois, seguindo o
 * mesmo padrão).
 */
class MainTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeTvScreen(
                            onItemClick = { tmdbId, mediaType ->
                                navController.navigate("detail/$mediaType/$tmdbId")
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
                            onPlayClick = { /* TODO: navegar pro Player quando ele existir */ },
                        )
                    }
                }
            }
        }
    }
}
