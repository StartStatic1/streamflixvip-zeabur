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
    val loadingSeasons: Set<Int> = emptySet(),
    val similar: List<TmdbItem> = emptyList(),
    val trailerKey: String? = null,
    val sources: List<VipSource> = emptyList(),
    val showError: String? = null,
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

            val appendToResponse = "videos,credits"
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
                        )
                    }
                    if (mediaType == "tv" && response.number_of_seasons != null) {
                        loadSeasonIfNeeded(1)
                    }
                },
                onFailure = {
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

    private fun loadSeasonIfNeeded(season: Int) {
        if (_uiState.value.seasonEpisodes.containsKey(season)) return
        if (_uiState.value.loadingSeasons.contains(season)) return

        viewModelScope.launch {
            _uiState.update { it.copy(loadingSeasons = it.loadingSeasons + season) }

            val seasonPath = "/tv/$tmdbId/season/$season"
            runCatching {
                NetworkModule.tmdbApi.requestSeasonDetail(path = seasonPath)
            }.fold(
                onSuccess = { seasonDetail ->
                    _uiState.update {
                        it.copy(
                            seasonEpisodes = it.seasonEpisodes + (season to seasonDetail.episodes.orEmpty()),
                            loadingSeasons = it.loadingSeasons - season,
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            loadingSeasons = it.loadingSeasons - season,
                            showError = "Não foi possível carregar a Temporada $season.",
                        )
                    }
                },
            )
        }
    }

    fun selectSeason(season: Int) {
        _uiState.update { it.copy(selectedSeason = season, showError = null) }
        loadSeasonIfNeeded(season)
    }

    fun retryCurrentSeason() {
        loadSeasonIfNeeded(_uiState.value.selectedSeason)
    }

    /** Sempre devolve a lista completa de fontes (0, 1 ou N). */
    fun loadEpisodeSources(season: Int, episode: Int, onResult: (List<VipSource>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                NetworkModule.supabaseApi.getSourcesForEpisode(
                    apiKey = NetworkModule.supabaseAnonKey,
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    seasonFilter = PostgrestFilter.eq(season),
                    episodeFilter = PostgrestFilter.eq(episode),
                )
            }.onSuccess { sources ->
                _uiState.update { it.copy(sources = sources, showError = if (sources.isEmpty()) "Nenhuma fonte disponível" else null) }
                onResult(sources)
            }.onFailure {
                _uiState.update { it.copy(showError = "Erro ao carregar fontes") }
                onResult(emptyList())
            }
        }
    }

    fun loadMovieSources(onResult: (List<VipSource>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                NetworkModule.supabaseApi.getSourcesForMovie(
                    apiKey = NetworkModule.supabaseAnonKey,
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq(mediaType),
                )
            }.onSuccess { sources ->
                _uiState.update { it.copy(sources = sources, showError = if (sources.isEmpty()) "Nenhuma fonte disponível" else null) }
                onResult(sources)
            }.onFailure {
                _uiState.update { it.copy(showError = "Erro ao carregar fontes") }
                onResult(emptyList())
            }
        }
    }

    fun retryLoad() {
        loadDetails()
    }
}
