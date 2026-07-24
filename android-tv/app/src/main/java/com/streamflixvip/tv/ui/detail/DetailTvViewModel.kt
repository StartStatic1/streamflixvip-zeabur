package com.streamflixvip.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.PostgrestFilter
import com.streamflixvip.tv.network.TmdbEpisode
import com.streamflixvip.tv.network.TmdbItem
import com.streamflixvip.tv.network.TmdbResponse
import com.streamflixvip.tv.network.VipSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailTvUiState(
    val isLoading: Boolean = true,
    val details: TmdbResponse? = null,
    val seasonEpisodes: Map<Int, List<TmdbEpisode>> = emptyMap(),
    val selectedSeason: Int = 1,
    val similar: List<TmdbItem> = emptyList(),
    val trailerKey: String? = null,
    val sources: List<VipSource> = emptyList(),
    val showError: String? = null,
    val showServerPicker: Boolean = false,
    val retryCount: Int = 0,
)

class DetailTvViewModel(
    private val tmdbId: Int,
    private val mediaType: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailTvUiState())
    val uiState: StateFlow<DetailTvUiState> = _uiState.asStateFlow()

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showError = null) }

            val appendToResponse = "videos,similar"
            val path = if (mediaType == "movie") "/movie/$tmdbId" else "/tv/$tmdbId"

            val result = runCatching {
                NetworkModule.tmdbApi.request(path = path, appendToResponse = appendToResponse)
            }

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = response,
                            trailerKey = response.trailerKey,
                            similar = response.results?.take(20) ?: response.results.orEmpty(),
                        )
                    }

                    // Carrega temporadas se for série
                    if (mediaType == "tv" && response.number_of_seasons != null) {
                        loadSeasons(response)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showError = "Erro ao carregar detalhes. Tente novamente.",
                        )
                    }
                },
            )
        }
    }

    private fun loadSeasons(details: TmdbResponse) {
        viewModelScope.launch {
            val totalSeasons = details.number_of_seasons ?: 1
            val episodesMap = mutableMapOf<Int, List<TmdbEpisode>>()

            val seasonNumbers = (1..totalSeasons.coerceAtMost(8))
            for (seasonNum in seasonNumbers) {
                val seasonPath = "/tv/$tmdbId/season/$seasonNum"
                runCatching {
                    NetworkModule.tmdbApi.requestSeasonDetail(path = seasonPath)
                }.onSuccess { seasonDetail ->
                    episodesMap[seasonNum] = seasonDetail.episodes.orEmpty()
                    _uiState.update {
                        it.copy(seasonEpisodes = episodesMap.toMap())
                    }
                }
            }
        }
    }

    fun selectSeason(season: Int) {
        _uiState.update { it.copy(selectedSeason = season) }
    }

    fun loadEpisodeSources(season: Int, episode: Int, onSuccess: (VipSource) -> Unit) {
        viewModelScope.launch {
            runCatching {
                NetworkModule.supabaseApi.getSourcesForEpisode(
                    apiKey = NetworkModule.supabaseAnonKey,
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    seasonFilter = PostgrestFilter.eq(season),
                    episodeFilter = PostgrestFilter.eq(episode),
                )
            }.onSuccess { sources ->
                if (sources.isEmpty()) {
                    _uiState.update { it.copy(showError = "Nenhuma fonte disponível para este episódio") }
                } else if (sources.size == 1) {
                    onSuccess(sources.first())
                } else {
                    _uiState.update { it.copy(sources = sources, showServerPicker = true, showError = null) }
                }
            }.onFailure {
                _uiState.update { it.copy(showError = "Erro ao carregar fontes") }
            }
        }
    }

    fun loadMovieSources(onSuccess: (VipSource) -> Unit) {
        viewModelScope.launch {
            runCatching {
                NetworkModule.supabaseApi.getSourcesForMovie(
                    apiKey = NetworkModule.supabaseAnonKey,
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq(mediaType),
                )
            }.onSuccess { sources ->
                if (sources.isEmpty()) {
                    _uiState.update { it.copy(showError = "Nenhuma fonte disponível para este filme") }
                } else if (sources.size == 1) {
                    onSuccess(sources.first())
                } else {
                    _uiState.update { it.copy(sources = sources, showServerPicker = true, showError = null) }
                }
            }.onFailure {
                _uiState.update { it.copy(showError = "Erro ao carregar fontes") }
            }
        }
    }

    fun dismissServerPicker() {
        _uiState.update { it.copy(showServerPicker = false) }
    }

    fun pickServer(source: VipSource, onSuccess: () -> Unit) {
        dismissServerPicker()
        onSuccess()
    }

    fun retryLoad() {
        loadDetails()
    }
}
