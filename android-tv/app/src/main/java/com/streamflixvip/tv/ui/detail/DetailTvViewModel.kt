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
    val expandedEpisode: Int? = null,
    val similar: List<TmdbItem> = emptyList(),
    val cast: List<com.streamflixvip.tv.network.TmdbCastMember> = emptyList(),
    val trailerKey: String? = null,
    val sources: List<VipSource> = emptyList(),
    val isVip: Boolean = true,
    val showError: String? = null,
    val showServerPicker: Boolean = false,
)

/**
 * ViewModel da tela de Detalhe de TV — carrega todos os dados do TMDB
 * (detalhe + elenco + similares) em uma única chamada com append_to_response,
 * carrega temporadas/episódios sob demanda, e busca fontes do Supabase.
 */
class DetailTvViewModel(
    private val tmdbId: Int,
    private val mediaType: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailTvUiState())
    val uiState: StateFlow<DetailTvUiState> = _uiState.asStateFlow()

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showError = null) }

            val appendToResponse = "credits,videos"
            val path = if (mediaType == "movie") "/movie/$tmdbId" else "/tv/$tmdbId"

            val result = runCatching {
                NetworkModule.tmdbApi.request(path = path, appendToResponse = appendToResponse)
            }

            result.fold(
                onSuccess = { response ->
                    val trailerKey = response.trailerKey
                    val cast = response.credits?.cast.orEmpty()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            details = response,
                            trailerKey = trailerKey,
                            cast = cast,
                        )
                    }

                    // Carrega similares
                    loadSimilar()
                    // Se é série, carrega temporadas e episódios
                    if (mediaType == "tv" && response.number_of_seasons != null) {
                        loadSeasons(response)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showError = e.message ?: "Erro ao carregar detalhes",
                        )
                    }
                },
            )
        }
    }

    private suspend fun loadSimilar() {
        val path = if (mediaType == "movie") "/movie/$tmdbId/similar" else "/tv/$tmdbId/similar"
        runCatching {
            NetworkModule.tmdbApi.request(path = path).results.orEmpty()
        }.onSuccess { items ->
            _uiState.update { it.copy(similar = items.take(20)) }
        }
    }

    private fun loadSeasons(details: TmdbResponse) {
        viewModelScope.launch {
            val totalSeasons = details.number_of_seasons ?: 1
            val episodesMap = mutableMapOf<Int, List<TmdbEpisode>>()

            // Carrega as primeiras 3 temporadas em paralelo (suficiente pro UI)
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

    fun expandEpisode(episode: Int) {
        _uiState.update {
            it.copy(expandedEpisode = if (it.expandedEpisode == episode) null else episode)
        }
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
                if (sources.size == 1) {
                    onSuccess(sources.first())
                } else {
                    _uiState.update { it.copy(sources = sources, showServerPicker = true) }
                }
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
                if (sources.size == 1) {
                    onSuccess(sources.first())
                } else {
                    _uiState.update { it.copy(sources = sources, showServerPicker = true) }
                }
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
}
