package com.streamflixvip.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.ProgressRepository
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.WatchProgressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeRow(
    val title: String,
    val items: List<TmdbItem>,
    val mediaType: String, // "movie" ou "tv" — usado depois pra navegar pra Detail certo
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Success(
        val continueWatching: List<WatchProgressEntry> = emptyList(),
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
                val popularMovies = repository.getPopularMovies()
                val nowPlaying = repository.getNowPlayingMovies()
                val popularSeries = repository.getPopularSeries()
                val continueWatching = if (userId != null && accessToken != null) {
                    progressRepository.getContinueWatching(accessToken, userId)
                } else {
                    emptyList()
                }

                _uiState.value = HomeUiState.Success(
                    continueWatching = continueWatching,
                    rows = listOf(
                        HomeRow("Em cartaz", nowPlaying, "movie"),
                        HomeRow("Filmes populares", popularMovies, "movie"),
                        HomeRow("Séries populares", popularSeries, "tv"),
                    )
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Erro ao carregar catálogo")
            }
        }
    }
}
