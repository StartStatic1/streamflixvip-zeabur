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
 *   "splash" → SplashTvScreen (nova!)
 *   "activation" → ActivationTvScreen (nova! — gate obrigatório de VIP)
 *   "home" → HomeTvScreen
 *   "search" → SearchTvScreen (nova!)
 *   "profile" → tela placeholder
 *   "settings" → tela placeholder
 *   "detail/{mediaType}/{tmdbId}" → DetailTvScreen
 *   "player" → PlayerTvScreen
 *
 * Fluxo:
 *   Splash (carrossel + som) → já ativado? Home : Ativação (código VIP)
 *   Ativação → código validado → Home
 *   Home/Search → click → Detail → Assistir → resolve fontes → Player
 *   Player → volta → Detail → volta → Home
 */
class MainTvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixTvTheme {
                val navController = rememberNavController()
                val activationManager = remember { TvActivationManager(applicationContext) }

                // Revalida contra o servidor em paralelo com a splash — pega
                // revogação feita manualmente no painel/Supabase sem atrasar
                // a abertura do app (splash já segura ~2.2s por conta própria).
                LaunchedEffect(Unit) {
                    activationManager.revalidate()
                }

                // State compartilhado para passar source do Detail pro Player
                var pendingSource by remember { mutableStateOf<VipSource?>(null) }
                var pendingSources by remember { mutableStateOf<List<VipSource>>(emptyList()) }
                var pendingSeason by remember { mutableStateOf(0) }
                var pendingEpisode by remember { mutableStateOf(0) }
                var pendingTitle by remember { mutableStateOf("Sem título") }

                NavHost(navController = navController, startDestination = "splash") {

                    // ── SPLASH ──
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

                    // ── ATIVAÇÃO (trava tudo até validar um código) ──
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

                    // ── HOME ──
                    composable("home") {
                        HomeTvScreen(
                            onItemClick = { tmdbId, mediaType ->
                                navController.navigate("detail/$mediaType/$tmdbId")
                            },
                            onNavigateToSearch = {
                                navController.navigate("search")
                            },
                        )
                    }

                    // ── BUSCA ──
                    composable("search") {
                        SearchTvScreen(
                            onItemClick = { tmdbId, mediaType ->
                                navController.navigate("detail/$mediaType/$tmdbId")
                            },
                            onBack = { navController.popBackStack() },
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
                            onPlayClick = { source, sources, season, episode, title ->
                                pendingSource = source
                                pendingSources = sources
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
                        )
                    }

                    // ── PLAYER ──
                    composable("player") {
                        val source = pendingSource
                        if (source != null) {
                            PlayerTvScreen(
                                source = source,
                                sources = pendingSources,
                                season = pendingSeason,
                                episode = pendingEpisode,
                                title = pendingTitle,
                                onBack = {
                                    pendingSource = null
                                    pendingSources = emptyList()
                                    navController.popBackStack()
                                },
                                onServerFailed = {
                                    // Volta pro detail para re-selecionar servidor
                                    pendingSource = null
                                    pendingSources = emptyList()
                                    navController.popBackStack()
                                },
                            )
                        } else {
                            // Fallback se chegou aqui sem source
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
