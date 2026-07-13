package com.streamflixvip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.app.ui.detail.DetailScreen
import com.streamflixvip.app.ui.detail.DetailViewModel
import com.streamflixvip.app.ui.home.HomeScreen
import com.streamflixvip.app.ui.home.HomeViewModel
import com.streamflixvip.app.ui.player.PlayerScreen
import com.streamflixvip.app.ui.theme.StreamFlixTheme
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Activity única hospedando toda a navegação via Compose Navigation —
 * padrão moderno equivalente ao "Single Activity Architecture". Cada tela
 * (Home, Detail, Player) é uma composable própria, navegada por rota,
 * nunca uma WebView carregando uma URL.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = viewModel,
                onItemClick = { tmdbId, mediaType ->
                    navController.navigate("detail/$tmdbId/$mediaType")
                },
            )
        }

        composable(
            route = "detail/{tmdbId}/{mediaType}",
            arguments = listOf(
                navArgument("tmdbId") { type = NavType.IntType },
                navArgument("mediaType") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val tmdbId = backStackEntry.arguments?.getInt("tmdbId") ?: return@composable
            val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val viewModel: DetailViewModel = viewModel(
                factory = detailViewModelFactory(tmdbId, mediaType),
            )
            DetailScreen(
                viewModel = viewModel,
                onPlaySource = { source ->
                    // URL vai codificada na rota porque pode conter caracteres
                    // especiais (query string do stream original, "?", "&", etc.)
                    // que quebrariam o parsing de rota do Navigation Compose.
                    val encodedUrl = URLEncoder.encode(
                        source.resolvedPlaybackUrl(com.streamflixvip.app.BuildConfig.API_BASE_URL),
                        "UTF-8",
                    )
                    navController.navigate("player/$encodedUrl/${source.isDirectPlayable}")
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "player/{encodedUrl}/{isDirect}",
            arguments = listOf(
                navArgument("encodedUrl") { type = NavType.StringType },
                navArgument("isDirect") { type = NavType.BoolType },
            ),
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("encodedUrl") ?: return@composable
            val isDirect = backStackEntry.arguments?.getBoolean("isDirect") ?: true
            val url = URLDecoder.decode(encodedUrl, "UTF-8")
            PlayerScreen(sourceUrl = url, isDirectPlayable = isDirect)
        }
    }
}

/**
 * Factory manual pro DetailViewModel — necessária porque ele recebe
 * tmdbId/mediaType no construtor (vindos da rota de navegação), e o
 * `viewModel()` padrão do Compose só sabe instanciar ViewModels sem
 * argumentos de construtor.
 */
private fun detailViewModelFactory(tmdbId: Int, mediaType: String) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return DetailViewModel(tmdbId, mediaType) as T
        }
    }
