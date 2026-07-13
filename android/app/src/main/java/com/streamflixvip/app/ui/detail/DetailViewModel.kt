package com.streamflixvip.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.network.TmdbResponse
import com.streamflixvip.app.network.VipSource
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
        // Só preenchido pra filme (série exige escolher season/episode
        // primeiro, então as fontes daquele episódio são buscadas sob
        // demanda quando o usuário seleciona um episódio na lista).
        val movieSources: List<VipSource> = emptyList(),
        val selectedSeason: Int? = null,
        val selectedEpisode: Int? = null,
        val episodeSources: List<VipSource> = emptyList(),
    ) : DetailUiState
}

class DetailViewModel(
    private val tmdbId: Int,
    private val mediaType: String,
    private val repository: CatalogRepository = CatalogRepository(),
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

                _uiState.value = DetailUiState.Success(
                    details = details,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    movieSources = movieSources,
                )
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Erro ao carregar detalhes")
            }
        }
    }

    /** Chamado quando o usuário escolhe um episódio numa série — busca as fontes daquele episódio específico. */
    fun loadEpisodeSources(season: Int, episode: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        viewModelScope.launch {
            try {
                val sources = repository.getSourcesForEpisode(tmdbId, season, episode)
                _uiState.value = current.copy(selectedSeason = season, selectedEpisode = episode, episodeSources = sources)
            } catch (_: Exception) {
                _uiState.value = current.copy(selectedSeason = season, selectedEpisode = episode, episodeSources = emptyList())
            }
        }
    }
}
