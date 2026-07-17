package com.streamflixvip.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.data.ProgressRepository
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.WatchProgressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Filtro que o "Ver mais" desta fileira deve abrir na aba Explorar. Null
 * quando a fileira não corresponde a um filtro simples de gênero+ano
 * (ex: Trending e "Populares" usam endpoints próprios da TMDB sem
 * equivalente direto em discover) — nesses casos a UI deveria esconder
 * o "Ver mais" em vez de abrir um Explorar que não reproduziria a mesma
 * lista.
 */
data class HomeRowExploreLink(
    val category: GenreCategory,
    val genreId: Int?,
    val year: Int?,
)

data class HomeRow(
    val title: String,
    val items: List<TmdbItem>,
    val mediaType: String, // "movie" ou "tv" — usado depois pra navegar pra Detail certo
    // Numeração 1/2/3... no card — só a fileira de Top da Semana usa isso.
    val isRanked: Boolean = false,
    val exploreLink: HomeRowExploreLink? = null,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(
        val continueWatching: List<WatchProgressEntry> = emptyList(),
        val heroItems: List<TmdbItem> = emptyList(),
        val rows: List<HomeRow>,
    ) : HomeUiState
}

class HomeViewModel(
    private val userId: String? = null,
    private val accessToken: String? = null,
    private val repository: CatalogRepository = CatalogRepository(),
    private val progressRepository: ProgressRepository = ProgressRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                // Carrega as fileiras em paralelo seria mais rápido, mas
                // mantemos sequencial por simplicidade nesta primeira
                // versão — o proxy TMDB já cacheia por 1h no CDN, então o
                // custo de rede é baixo mesmo assim.
                val trending = repository.getTrendingWeek()
                val popularMovies = repository.getPopularMovies()
                val nowPlaying = repository.getNowPlayingMovies()
                val popularSeries = repository.getPopularSeries()
                val continueWatching = if (userId != null && accessToken != null) {
                    progressRepository.getContinueWatching(accessToken, userId)
                } else {
                    emptyList()
                }

                // Ano sorteado a cada carregamento — sem isso, "Clássicos"
                // e "Trash & Cult" mostrariam sempre o mesmo ano fixo toda
                // vez que a Home abre, o que fica repetitivo rápido.
                val classicsYear = (1970..1999).random()
                val trashYear = (1970..1995).random()

                // Clássicos: filmes de décadas passadas, pra dar uma opção
                // de nostalgia sem misturar com os lançamentos recentes
                // das fileiras "populares" de cima.
                val classics = runCatching {
                    repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = null, year = classicsYear)
                }.getOrElse { emptyList() }

                // Trash/Cult: terror categoria B — mesmo filtro de gênero
                // usado na aba Gêneros, mas com um ano mais antigo pra
                // puxar títulos de nicho em vez dos mesmos lançamentos de
                // terror recentes que já aparecem em outros lugares do app.
                val trash = runCatching {
                    repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = 27, year = trashYear)
                }.getOrElse { emptyList() }

                _uiState.value = HomeUiState.Success(
                    continueWatching = continueWatching,
                    // Os 5 primeiros "em cartaz" viram o banner rotativo do
                    // topo — mais impacto visual que uma fileira comum, e
                    // evita repetir os mesmos pôsteres duas vezes na tela.
                    heroItems = nowPlaying.take(5),
                    rows = listOfNotNull(
                        trending.takeIf { it.isNotEmpty() }?.let {
                            HomeRow("Top 10 da Semana", it.take(10), "movie", isRanked = true)
                        },
                        HomeRow("Filmes populares", popularMovies, "movie"),
                        HomeRow("Séries populares", popularSeries, "tv"),
                        classics.takeIf { it.isNotEmpty() }?.let {
                            HomeRow(
                                "Clássicos",
                                it,
                                "movie",
                                exploreLink = HomeRowExploreLink(GenreCategory.MOVIES, genreId = null, year = classicsYear),
                            )
                        },
                        trash.takeIf { it.isNotEmpty() }?.let {
                            HomeRow(
                                "Trash & Cult",
                                it,
                                "movie",
                                exploreLink = HomeRowExploreLink(GenreCategory.MOVIES, genreId = 27, year = trashYear),
                            )
                        },
                    )
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Erro ao carregar catálogo")
            }
        }
    }
}
