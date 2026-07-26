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

                    // Busca episódios SÓ da temporada 1 de cara (mesmo padrão
                    // do app de celular, ver CatalogRepository.getSeasonEpisodes
                    // — busca sob demanda, uma temporada por vez, em vez de
                    // disparar N chamadas simultâneas pra todas as temporadas
                    // ao abrir a tela). Isso evita o bug em que temporadas
                    // além da 3ª pareciam "sumir": eram chamadas que falhavam
                    // silenciosamente numa rajada de requisições paralelas,
                    // sem nenhum aviso de erro na tela.
                    if (mediaType == "tv" && response.number_of_seasons != null) {
                        loadSeasonIfNeeded(1)
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

    /**
     * Busca episódios de UMA temporada por vez, só quando ainda não foram
     * carregados — chamada tanto ao abrir a tela (temporada 1) quanto ao
     * trocar de temporada (ver selectSeason). Erro de rede agora aparece
     * de verdade em showError, em vez de ser engolido e deixar a seção
     * simplesmente vazia sem explicação.
     */
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
                            showError = "Não foi possível carregar a Temporada $season. Toque para tentar de novo.",
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

    /** Permite tentar de novo a temporada atual após uma falha, sem sair da tela. */
    fun retryCurrentSeason() {
        loadSeasonIfNeeded(_uiState.value.selectedSeason)
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
