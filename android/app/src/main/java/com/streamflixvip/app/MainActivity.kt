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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val showBottomBar = currentRoute in listOf("home", "search", "explore", "genres", "mylist", "profile")
    // A lupa aparece nas abas principais. Pesquisa Geral e Explorar têm
    // cabeçalhos próprios porque são fluxos diferentes: uma localiza títulos
    // por texto; a outra descobre conteúdo por filtros e categorias.
    val showTopBar = currentRoute in listOf("home", "genres", "mylist", "profile")

    // Popula o status VIP em memória assim que o app abre logado — assim,
    // MESMO que o usuário nunca visite a aba Perfil, a tela de Detalhes já
    // sabe se ele é VIP a tempo de decidir mostrar cadeado ou não. Sem
    // isso, requiresVip() ficaria preso em "não-VIP" até a primeira visita
    // ao Perfil (comportamento seguro, mas indesejado pra quem já pagou).
    LaunchedEffect(userId) {
        if (userId != null) {
            val status = VipRepository().getStatus(userId)
            VipStatusHolder.update(status.isVip)
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                com.streamflixvip.app.ui.nav.AppTopBar(
                    // userDisplayName = userEmail?.substringBefore("@"), // Removido a pedido do usuário
                    onSearchClick = {
                        navController.navigate("search") { launchSingleTop = true }
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
            // innerPadding já reflete corretamente topBar e bottomBar
            // juntos (o Scaffold calcula isso sozinho a partir do que
            // topBar/bottomBar acima realmente desenharam) — não precisa
            // de lógica condicional aqui, só zerar padding nas telas que
            // não têm NENHUMA das duas (Detail/Player, tela cheia).
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                val viewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory { HomeViewModel(userId = userId, accessToken = accessToken) },
                )
                HomeScreen(
                    viewModel = viewModel,
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
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
                        // Lê e imediatamente limpa o filtro pendente (se
                        // veio do "Ver mais" da Home) — assim, reabrir a
                        // aba Explorar pela bottom bar depois não fica
                        // "grudada" no último filtro usado.
                        val pending = PendingExploreFilter.consume()
                        ExploreViewModel(initialFilters = pending ?: com.streamflixvip.app.ui.explore.ExploreFilters())
                    },
                )
                ExploreScreen(
                    viewModel = viewModel,
                    onItemClick = { tmdbId, mediaType -> navController.navigate("detail/$tmdbId/$mediaType") },
                )
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
                ProfileScreen(authViewModel = authViewModel, userId = userId, userEmail = userEmail, onSignedOut = onSignedOut)
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
                        // Leva pra aba Perfil, onde a seção VIP (resgate de
                        // código + benefícios) já está — evita duplicar essa
                        // tela em dois lugares diferentes do app.
                        navController.navigate("profile") {
                            launchSingleTop = true
                        }
                    },
                    onOpenTitle = { openTmdbId, openMediaType ->
                        // Empilha uma nova tela de Detail por cima da atual —
                        // ao tocar em "Voltar" no título similar, volta pro
                        // título original, comportamento padrão de navegação
                        // encadeada (mesmo que abrir de qualquer outro lugar
                        // do app, tipo Home ou Buscar).
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

                // Fricção pra não-VIP: espera alguns segundos antes do player
                // abrir de verdade, com CTA pra pular virando VIP — mesma
                // ideia do Rewarded Interstitial que o site já usa no
                // primeiro play. VIP nunca vê essa tela (waitDone já nasce
                // true). Estado local (não navegação) porque é só uma
                // barreira temporal antes do MESMO destino, não uma tela
                // que a pessoa navega "de volta" pra ela.
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
