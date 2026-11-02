package com.streamflixvip.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.data.ProgressRepository
import com.streamflixvip.app.data.ResumePlaybackCache
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.WatchProgressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeRowExploreLink(
    val category: GenreCategory,
    val genreId: Int?,
    val year: Int?,
)

data class HomeRow(
    val title: String,
    val items: List<TmdbItem>,
    val mediaType: String,
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

    fun refreshContinueWatching() {
        if (userId == null || accessToken == null) return
        viewModelScope.launch {
            val list = runCatching {
                progressRepository.getContinueWatching(accessToken, userId)
            }.getOrElse { emptyList() }
            val cur = _uiState.value
            if (cur is HomeUiState.Success) {
                _uiState.value = cur.copy(continueWatching = list)
            }
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                // Tudo em paralelo (antes era em fila: a Home esperava a soma de 7 chamadas).
                // Uma lista que falha vira vazia em vez de derrubar a Home inteira.
                val kids = com.streamflixvip.app.profiles.ProfileStore.isKids
                val classicsYear = (1970..1999).random()
                val trashYear = (1970..1995).random()
                suspend fun <T> safe(block: suspend () -> List<T>): List<T> = runCatching { block() }.getOrElse { emptyList() }
                val (trending, popularMovies, nowPlaying, popularSeries, continueWatching, classics, trash, freeArea) =
                    kotlinx.coroutines.coroutineScope {
                        val dTrending = async { if (kids) safe { repository.exploreCatalog(GenreCategory.MOVIES, 10751, null) } else safe { repository.getTrendingWeek() } }
                        val dPopMovies = async { if (kids) safe { repository.exploreCatalog(GenreCategory.MOVIES, 16, null) } else safe { repository.getPopularMovies() } }
                        val dNow = async { if (kids) emptyList() else safe { repository.getNowPlayingMovies() } }
                        val dPopSeries = async { if (kids) safe { repository.exploreCatalog(GenreCategory.SERIES, 10762, null) } else safe { repository.getPopularSeries() } }
                        val dCont = async {
                            if (userId != null && accessToken != null) safe { progressRepository.getContinueWatching(accessToken, userId) } else emptyList()
                        }
                        val dClassics = async { if (kids) emptyList() else safe { repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = null, year = classicsYear) } }
                        val dTrash = async { if (kids) emptyList() else safe { repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = 27, year = trashYear) } }
                        val dFree = async { if (kids) emptyList() else safe { repository.getFreeCatalog(50) } }
                        HomeLoad(dTrending.await(), dPopMovies.await(), dNow.await(), dPopSeries.await(), dCont.await(), dClassics.await(), dTrash.await(), dFree.await())
                    }
                if (trending.isEmpty() && popularMovies.isEmpty() && popularSeries.isEmpty()) {
                    throw IllegalStateException("Sem conexão com o catálogo")
                }

                prefetchContinueSources(continueWatching)
                _uiState.value = HomeUiState.Success(
                    continueWatching = continueWatching,
                    heroItems = (
                        trending.filter { !it.backdrop_path.isNullOrBlank() }.ifEmpty { trending }
                            .ifEmpty { nowPlaying }
                    ).take(6),
                    rows = listOfNotNull(
                        // sempre monta; a tela esconde para VIP (o status VIP chega depois da home carregar)
                        freeArea.takeIf { it.isNotEmpty() }?.let {
                            HomeRow("Area Free", it, "movie")
                        },
                        trending.takeIf { it.isNotEmpty() }?.let {
                            if (kids) HomeRow("Para toda a família", it, "movie")
                            else HomeRow("Top 10 da Semana", it.take(10), "movie", isRanked = true)
                        },
                        popularMovies.takeIf { it.isNotEmpty() }?.let { HomeRow(if (kids) "Animações" else "Filmes populares", it, "movie") },
                        popularSeries.takeIf { it.isNotEmpty() }?.let { HomeRow(if (kids) "Desenhos e séries infantis" else "Series populares", it, "tv") },
                        classics.takeIf { it.isNotEmpty() }?.let {
                            HomeRow(
                                "Classicos",
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
                _uiState.value = HomeUiState.Error(e.message ?: "Erro ao carregar catalogo")
            }
        }
    }

    private fun prefetchContinueSources(list: List<WatchProgressEntry>) {
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            list.take(8).forEach { entry ->
                runCatching {
                    val season = if (entry.media_type == "tv") entry.season.coerceAtLeast(1) else 0
                    val episode = if (entry.media_type == "tv") entry.episode.coerceAtLeast(1) else 0
                    if (ResumePlaybackCache.get(entry.tmdb_id, entry.media_type, season, episode) != null) return@forEach
                    val sources = if (entry.media_type == "tv" && entry.season > 0) {
                        repository.getSourcesForEpisode(entry.tmdb_id, season, episode)
                    } else {
                        repository.getSourcesForMovie(entry.tmdb_id)
                    }
                    val src = sources.firstOrNull { it.source_url.isNotBlank() } ?: return@forEach
                    ResumePlaybackCache.put(
                        entry.tmdb_id,
                        entry.media_type,
                        season,
                        episode,
                        src.resolvedPlaybackUrl(com.streamflixvip.app.BuildConfig.API_BASE_URL),
                        src.isDirectPlayable,
                        src.source_label,
                    )
                }
            }
        }
    }

    fun dismissContinueWatching(entry: WatchProgressEntry) {
        val uid = userId
        val token = accessToken
        if (uid == null || token == null) return
        viewModelScope.launch {
            progressRepository.removeFromContinueWatching(token, uid, entry.tmdb_id, entry.media_type)
            val cur = _uiState.value as? HomeUiState.Success ?: return@launch
            _uiState.value = cur.copy(
                continueWatching = cur.continueWatching.filterNot {
                    it.tmdb_id == entry.tmdb_id && it.media_type == entry.media_type
                },
            )
        }
    }
}

private data class HomeLoad(
    val trending: List<TmdbItem>,
    val popularMovies: List<TmdbItem>,
    val nowPlaying: List<TmdbItem>,
    val popularSeries: List<TmdbItem>,
    val continueWatching: List<WatchProgressEntry>,
    val classics: List<TmdbItem>,
    val trash: List<TmdbItem>,
    val freeArea: List<TmdbItem>,
)
