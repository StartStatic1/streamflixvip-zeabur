package com.streamflixvip.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.VipStatusHolder
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.TmdbResponse
import com.streamflixvip.app.network.VipSource
import com.streamflixvip.app.network.VipTitleConfig
import com.streamflixvip.app.network.requiresVip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Success(
        val details: TmdbResponse,
        val mediaType: String,
        val tmdbId: Int,
        val movieSources: List<VipSource> = emptyList(),
        val vipConfig: VipTitleConfig? = null,
        val expandedSeason: Int? = null,
        val episodesOfExpandedSeason: List<TmdbEpisode> = emptyList(),
        val isLoadingEpisodes: Boolean = false,
        val selectedSeason: Int? = null,
        val selectedEpisode: Int? = null,
        val episodeSources: List<VipSource> = emptyList(),
        val isLoadingEpisodeSources: Boolean = false,
        val showServerPickerForEpisode: Int? = null,
        val similarTitles: List<TmdbItem> = emptyList(),
        val showSeasonPicker: Boolean = false,
        val expandedEpisodeNumber: Int? = null,
        val showComments: Boolean = false,
        val comments: List<com.streamflixvip.app.network.TitleComment> = emptyList(),
        val isLoadingComments: Boolean = false,
        val isPostingComment: Boolean = false,
        val canPostComments: Boolean = false,
        val isFavorite: Boolean = false,
        val isTogglingFavorite: Boolean = false,
        val episodesWithSources: Set<Int>? = null,
    ) : DetailUiState {
        val trailerKey: String? get() = details.trailerKey
        fun movieIsLocked(isVip: Boolean): Boolean = !isVip && requiresVip(vipConfig, episodeNumber = null)
        fun episodeIsLocked(episodeNumber: Int, isVip: Boolean): Boolean = !isVip && requiresVip(vipConfig, episodeNumber)
    }
}

class DetailViewModel(
    private val tmdbId: Int,
    private val mediaType: String,
    private val initialSeason: Int = -1,
    private val initialEpisode: Int = -1,
    private val userId: String? = null,
    private val accessToken: String? = null,
    private val userDisplayName: String? = null,
    private val repository: CatalogRepository = CatalogRepository(),
    private val commentsRepository: com.streamflixvip.app.data.CommentsRepository = com.streamflixvip.app.data.CommentsRepository(),
    private val favoritesRepository: com.streamflixvip.app.data.FavoritesRepository = com.streamflixvip.app.data.FavoritesRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val details = if (mediaType == "tv") {
                    repository.getSeriesDetails(tmdbId)
                } else {
                    repository.getMovieDetails(tmdbId)
                }

                val movieSources = if (mediaType == "movie") {
                    repository.getSourcesForMovie(tmdbId)
                } else {
                    emptyList()
                }
                val vipConfig = repository.getVipTitleConfig(tmdbId, mediaType)

                _uiState.value = DetailUiState.Success(
                    details = details,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    movieSources = movieSources,
                    vipConfig = vipConfig,
                    canPostComments = userId != null && accessToken != null,
                )

                if (userId != null && accessToken != null) {
                    launch {
                        val favorited = favoritesRepository.isFavorite(accessToken, userId, tmdbId, mediaType)
                        val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
                        _uiState.value = stillCurrent.copy(isFavorite = favorited)
                    }
                }

                if (mediaType == "tv") {
                    val seasons = details.seasons.orEmpty().filter { it.season_number > 0 }
                    val seasonToOpen = if (initialSeason > 0) initialSeason else seasons.firstOrNull()?.season_number
                    if (seasonToOpen != null) {
                        expandSeason(seasonToOpen)
                    }
                    if (initialSeason > 0) {
                        preloadEpisodeSourcesSilently(initialSeason, initialEpisode.coerceAtLeast(1))
                    }
                }

                launch {
                    val similar = repository.getSimilarTitles(tmdbId, mediaType)
                    val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
                    _uiState.value = stillCurrent.copy(similarTitles = similar)
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Erro ao carregar detalhes")
            }
        }
    }

    fun expandSeason(season: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        if (current.expandedSeason == season) {
            _uiState.value = current.copy(expandedSeason = null, episodesOfExpandedSeason = emptyList())
            return
        }
        _uiState.value = current.copy(expandedSeason = season, episodesOfExpandedSeason = emptyList(), isLoadingEpisodes = true)
        viewModelScope.launch {
            val episodes = repository.getSeasonEpisodes(tmdbId, season)
            var withSources: Set<Int>? = null
            try {
                val resp = NetworkModule.mediaSourcesApi.getSeasonEpisodesWithSources(tmdbId = tmdbId, season = season)
                withSources = resp.episodesWithSources.toSet()
            } catch (_: Exception) {
                withSources = null
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (stillCurrent.expandedSeason == season) {
                _uiState.value = stillCurrent.copy(
                    episodesOfExpandedSeason = episodes,
                    isLoadingEpisodes = false,
                    episodesWithSources = withSources,
                )
            }
        }
    }

    private fun preloadEpisodeSourcesSilently(season: Int, episode: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        val isVipNow = VipStatusHolder.isVipNow()
        if (current.episodeIsLocked(episode, isVipNow)) {
            _uiState.value = current.copy(selectedSeason = season, selectedEpisode = episode, episodeSources = emptyList())
            return
        }
        viewModelScope.launch {
            val sources = try {
                repository.getSourcesForEpisode(tmdbId, season, episode)
            } catch (_: Exception) {
                emptyList()
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            _uiState.value = stillCurrent.copy(
                selectedSeason = season,
                selectedEpisode = episode,
                episodeSources = sources,
            )
        }
    }

    fun loadEpisodeSources(season: Int, episode: Int, forceAutoPlay: Boolean = false, onAutoPlay: (VipSource) -> Unit) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        val isVipNow = VipStatusHolder.isVipNow()

        if (current.episodeIsLocked(episode, isVipNow)) {
            _uiState.value = current.copy(
                selectedSeason = season,
                selectedEpisode = episode,
                episodeSources = emptyList(),
                isLoadingEpisodeSources = false,
                showServerPickerForEpisode = null,
            )
            return
        }

        _uiState.value = current.copy(
            selectedSeason = season,
            selectedEpisode = episode,
            isLoadingEpisodeSources = true,
        )
        viewModelScope.launch {
            val sources = try {
                repository.getSourcesForEpisode(tmdbId, season, episode)
            } catch (_: Exception) {
                emptyList()
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (stillCurrent.selectedSeason != season || stillCurrent.selectedEpisode != episode) return@launch

            when {
                sources.size == 1 -> {
                    _uiState.value = stillCurrent.copy(
                        episodeSources = sources,
                        isLoadingEpisodeSources = false,
                        showServerPickerForEpisode = null,
                    )
                    onAutoPlay(sources.first())
                }
                sources.size > 1 -> {
                    if (forceAutoPlay) {
                        _uiState.value = stillCurrent.copy(
                            episodeSources = sources,
                            isLoadingEpisodeSources = false,
                            showServerPickerForEpisode = null,
                        )
                        onAutoPlay(sources.first())
                    } else {
                        _uiState.value = stillCurrent.copy(
                            episodeSources = sources,
                            isLoadingEpisodeSources = false,
                            showServerPickerForEpisode = episode,
                        )
                    }
                }
                else -> {
                    _uiState.value = stillCurrent.copy(
                        episodeSources = sources,
                        isLoadingEpisodeSources = false,
                        showServerPickerForEpisode = null,
                    )
                }
            }
        }
    }

    fun closeServerPicker() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showServerPickerForEpisode = null)
    }

    fun toggleSeasonPicker() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showSeasonPicker = !current.showSeasonPicker)
    }

    fun selectSeasonFromPicker(season: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        if (current.expandedSeason == season) {
            _uiState.value = current.copy(showSeasonPicker = false)
            return
        }
        _uiState.value = current.copy(
            showSeasonPicker = false,
            expandedEpisodeNumber = null,
            expandedSeason = season,
            episodesOfExpandedSeason = emptyList(),
            isLoadingEpisodes = true,
        )
        viewModelScope.launch {
            val episodes = repository.getSeasonEpisodes(tmdbId, season)
            var withSources: Set<Int>? = null
            try {
                val resp = NetworkModule.mediaSourcesApi.getSeasonEpisodesWithSources(tmdbId = tmdbId, season = season)
                withSources = resp.episodesWithSources.toSet()
            } catch (_: Exception) {
                withSources = null
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (stillCurrent.expandedSeason == season) {
                _uiState.value = stillCurrent.copy(
                    episodesOfExpandedSeason = episodes,
                    isLoadingEpisodes = false,
                    episodesWithSources = withSources,
                )
            }
        }
    }

    fun toggleEpisodeExpanded(episodeNumber: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(
            expandedEpisodeNumber = if (current.expandedEpisodeNumber == episodeNumber) null else episodeNumber,
        )
    }

    fun openComments() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showComments = true)
        if (current.comments.isNotEmpty() || current.isLoadingComments) return
        _uiState.value = (_uiState.value as DetailUiState.Success).copy(isLoadingComments = true)
        viewModelScope.launch {
            val comments = commentsRepository.getComments(tmdbId, mediaType)
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            _uiState.value = stillCurrent.copy(comments = comments, isLoadingComments = false)
        }
    }

    fun closeComments() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(showComments = false)
    }

    fun postComment(text: String, isVip: Boolean, onResult: (Boolean) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || userId == null || accessToken == null) {
            onResult(false)
            return
        }
        val current = _uiState.value as? DetailUiState.Success ?: return
        _uiState.value = current.copy(isPostingComment = true)
        viewModelScope.launch {
            val success = commentsRepository.postComment(
                accessToken = accessToken,
                userId = userId,
                userDisplayName = userDisplayName,
                isVipAuthor = isVip,
                tmdbId = tmdbId,
                mediaType = mediaType,
                text = trimmed,
            )
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (success) {
                val comments = commentsRepository.getComments(tmdbId, mediaType)
                _uiState.value = stillCurrent.copy(comments = comments, isPostingComment = false)
            } else {
                _uiState.value = stillCurrent.copy(isPostingComment = false)
            }
            onResult(success)
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value as? DetailUiState.Success ?: return
        val uid = userId ?: return
        val token = accessToken ?: return
        if (current.isTogglingFavorite) return

        val wasFavorite = current.isFavorite
        val title = current.details.title ?: current.details.name
        val posterPath = current.details.poster_path
        val originalLanguage = current.details.original_language

        _uiState.value = current.copy(isFavorite = !wasFavorite, isTogglingFavorite = true)

        viewModelScope.launch {
            val success = if (wasFavorite) {
                favoritesRepository.removeFavorite(token, uid, tmdbId, mediaType)
            } else {
                favoritesRepository.addFavorite(
                    accessToken = token,
                    userId = uid,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    title = title,
                    posterPath = posterPath,
                    originalLanguage = originalLanguage,
                )
            }
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            _uiState.value = if (success) {
                stillCurrent.copy(isTogglingFavorite = false)
            } else {
                stillCurrent.copy(isFavorite = wasFavorite, isTogglingFavorite = false)
            }
        }
    }
}
