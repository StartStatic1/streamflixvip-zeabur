package com.streamflixvip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.app.data.AuthRepository
import com.streamflixvip.app.data.SessionStore
import com.streamflixvip.app.ui.auth.AuthScreen
import com.streamflixvip.app.ui.auth.AuthViewModel
import com.streamflixvip.app.ui.detail.DetailScreen
import com.streamflixvip.app.ui.detail.DetailViewModel
import com.streamflixvip.app.ui.home.HomeScreen
import com.streamflixvip.app.ui.home.HomeViewModel
import com.streamflixvip.app.ui.mylist.MyListScreen
import com.streamflixvip.app.ui.nav.StreamFlixBottomBar
import com.streamflixvip.app.ui.player.PlayerScreen
import com.streamflixvip.app.ui.profile.ProfileScreen
import com.streamflixvip.app.ui.search.SearchScreen
import com.streamflixvip.app.ui.theme.StreamFlixTheme
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Activity única hospedando toda a navegação via Compose Navigation.
 *
 * Estrutura em duas camadas:
 * 1. Gate de autenticação — se não estiver logado, mostra AuthScreen até
 *    o login completar. Sessão persiste via SessionStore (SharedPreferences),
 *    então isso só aparece na primeira vez ou após logout.
 * 2. Depois de logado: Scaffold com bottom bar fixa (Início/Buscar/Minha
 *    Lista/Perfil) + NavHost cobrindo essas 4 abas MAIS as rotas de
 *    Detail/Player (que abrem por cima, sem bottom bar — faz sentido
 *    principalmente pro Player, que quer maximizar área de vídeo).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val sessionStore = remember { SessionStore(context) }
    val authRepository = remember { AuthRepository(sessionStore) }
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory { AuthViewModel(authRepository) })

    var isLoggedIn by remember { mutableStateOf(sessionStore.isLoggedIn) }

    if (!isLoggedIn) {
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
    val showBottomBar = currentRoute in listOf("home", "search", "mylist", "profile")

    Scaffold(
        bottomBar = {
            if (showBottomBar) StreamFlixBottomBar(navController)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(if (showBottomBar) innerPadding else PaddingValues(0.dp)),
        ) {
            composable("home") {
                val viewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory { HomeViewModel(userId = userId, accessToken = accessToken) },
                )
                HomeScreen(
                    viewModel = viewModel,
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
                )
            }

            composable("search") {
                SearchScreen(onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") })
            }

            composable("mylist") {
                MyListScreen()
            }

            composable("profile") {
                ProfileScreen(authViewModel = authViewModel, userId = userId, userEmail = userEmail, onSignedOut = onSignedOut)
            }

            composable(
                route = "detail/{tmdbId}/{mediaType}",
                arguments = listOf(
                    navArgument("tmdbId") { type = NavType.IntType },
                    navArgument("mediaType") { type = NavType.StringType },
                ),
            ) { entry ->
                val tmdbId = entry.arguments?.getInt("tmdbId") ?: return@composable
                val mediaType = entry.arguments?.getString("mediaType") ?: "movie"
                val viewModel: DetailViewModel = viewModel(
                    factory = viewModelFactory { DetailViewModel(tmdbId, mediaType) },
                )
                DetailScreen(
                    viewModel = viewModel,
                    onPlaySource = { source, season, episode, title, posterPath ->
                        val encodedUrl = URLEncoder.encode(
                            source.resolvedPlaybackUrl(BuildConfig.API_BASE_URL),
                            "UTF-8",
                        )
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        val encodedPoster = URLEncoder.encode(posterPath ?: "none", "UTF-8")
                        navController.navigate(
                            "player/$encodedUrl/${source.isDirectPlayable}/$tmdbId/$mediaType/$season/$episode/$encodedTitle/$encodedPoster",
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "player/{encodedUrl}/{isDirect}/{tmdbId}/{mediaType}/{season}/{episode}/{encodedTitle}/{encodedPoster}",
                arguments = listOf(
                    navArgument("encodedUrl") { type = NavType.StringType },
                    navArgument("isDirect") { type = NavType.BoolType },
                    navArgument("tmdbId") { type = NavType.IntType },
                    navArgument("mediaType") { type = NavType.StringType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType },
                    navArgument("encodedTitle") { type = NavType.StringType },
                    navArgument("encodedPoster") { type = NavType.StringType },
                ),
            ) { entry ->
                val args = entry.arguments ?: return@composable
                val encodedUrl = args.getString("encodedUrl") ?: return@composable
                val isDirect = args.getBoolean("isDirect")
                val playerTmdbId = args.getInt("tmdbId")
                val playerMediaType = args.getString("mediaType") ?: "movie"
                val season = args.getInt("season")
                val episode = args.getInt("episode")
                val title = URLDecoder.decode(args.getString("encodedTitle") ?: "", "UTF-8")
                val posterPath = URLDecoder.decode(args.getString("encodedPoster") ?: "none", "UTF-8").let {
                    if (it == "none") null else it
                }
                val url = URLDecoder.decode(encodedUrl, "UTF-8")
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
                )
            }
        }
    }
}

/**
 * Helper genérico pra criar ViewModelProvider.Factory a partir de uma
 * lambda — evita repetir o boilerplate de `object : Factory { ... }`
 * pra cada ViewModel que recebe argumentos de construtor.
 */
private fun <T : androidx.lifecycle.ViewModel> viewModelFactory(create: () -> T) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM {
            return create() as VM
        }
    }
