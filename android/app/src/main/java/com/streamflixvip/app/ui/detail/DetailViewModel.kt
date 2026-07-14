package com.streamflixvip.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.network.TmdbEpisode
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
        // Temporada atualmente "aberta" na UI (mostrando a lista de
        // episódios com thumbnail/sinopse). Diferente de selectedSeason,
        // que é qual episódio está escolhido pra assistir agora.
        val expandedSeason: Int? = null,
        val episodesOfExpandedSeason: List<TmdbEpisode> = emptyList(),
        val isLoadingEpisodes: Boolean = false,
        val selectedSeason: Int? = null,
        val selectedEpisode: Int? = null,
        val episodeSources: List<VipSource> = emptyList(),
    ) : DetailUiState
}

class DetailViewModel(
    private val tmdbId: Int,
    private val mediaType: String,
    // Preenchidos quando a tela abre a partir de "Continuar assistindo" —
    // já sabemos qual episódio a pessoa estava vendo, então adiantamos a
    // busca das fontes daquele episódio em vez de forçar escolher de novo.
    private val initialSeason: Int = -1,
    private val initialEpisode: Int = -1,
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

                if (mediaType == "tv") {
                    // Abre direto a temporada que a pessoa estava vendo (vindo de
                    // "Continuar assistindo"), ou a primeira temporada da série —
                    // assim a lista de episódios já aparece pronta ao entrar na
                    // tela, sem exigir um toque extra pra "abrir a temporada 1".
                    val seasons = details.seasons.orEmpty().filter { it.season_number > 0 }
                    val seasonToOpen = if (initialSeason > 0) initialSeason else seasons.firstOrNull()?.season_number
                    if (seasonToOpen != null) {
                        expandSeason(seasonToOpen)
                    }
                    if (initialSeason > 0) {
                        loadEpisodeSources(initialSeason, initialEpisode.coerceAtLeast(1))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Erro ao carregar detalhes")
            }
        }
    }

    /**
     * Abre uma temporada na UI, buscando a lista real de episódios
     * (título, sinopse, imagem, duração) via TMDB. Repetir o toque na
     * mesma temporada já aberta a fecha de novo (comportamento de
     * accordion, como o site já faz).
     */
    fun expandSeason(season: Int) {
        val current = _uiState.value as? DetailUiState.Success ?: return
        if (current.expandedSeason == season) {
            _uiState.value = current.copy(expandedSeason = null, episodesOfExpandedSeason = emptyList())
            return
        }
        _uiState.value = current.copy(expandedSeason = season, episodesOfExpandedSeason = emptyList(), isLoadingEpisodes = true)
        viewModelScope.launch {
            val episodes = repository.getSeasonEpisodes(tmdbId, season)
            val stillCurrent = _uiState.value as? DetailUiState.Success ?: return@launch
            if (stillCurrent.expandedSeason == season) {
                _uiState.value = stillCurrent.copy(episodesOfExpandedSeason = episodes, isLoadingEpisodes = false)
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
