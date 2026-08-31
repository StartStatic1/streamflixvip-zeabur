package com.streamflixvip.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.data.ProgressRepository
import com.streamflixvip.app.data.ResumePlaybackCache
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.WatchProgressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
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
                val trending = repository.getTrendingWeek()
                val popularMovies = repository.getPopularMovies()
                val nowPlaying = repository.getNowPlayingMovies()
                val popularSeries = repository.getPopularSeries()
                val continueWatching = if (userId != null && accessToken != null) {
                    progressRepository.getContinueWatching(accessToken, userId)
                } else {
                    emptyList()
                }

                val classicsYear = (1970..1999).random()
                val trashYear = (1970..1995).random()

                val classics = runCatching {
                    repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = null, year = classicsYear)
                }.getOrElse { emptyList() }

                val trash = runCatching {
                    repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = 27, year = trashYear)
                }.getOrElse { emptyList() }

                prefetchContinueSources(continueWatching)
                _uiState.value = HomeUiState.Success(
                    continueWatching = continueWatching,
                    heroItems = (
                        trending.filter { !it.backdrop_path.isNullOrBlank() }.ifEmpty { trending }
                            .ifEmpty { nowPlaying }
                    ).take(6),
                    rows = listOfNotNull(
                        trending.takeIf { it.isNotEmpty() }?.let {
                            HomeRow("Top 10 da Semana", it.take(10), "movie", isRanked = true)
                        },
                        HomeRow("Filmes populares", popularMovies, "movie"),
                        HomeRow("Series populares", popularSeries, "tv"),
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
