package com.streamflixvip.tv.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeTvUiState(
    val isLoading: Boolean = true,
    val trending: List<TmdbItem> = emptyList(),
    val popularMovies: List<TmdbItem> = emptyList(),
    val popularSeries: List<TmdbItem> = emptyList(),
)

/**
 * ViewModel da Home de TV — mesma fonte de dados do app de celular
 * (NetworkModule.tmdbApi, que fala com o proxy /api/tmdb já existente),
 * só que carregando as 3 seções em paralelo desde já, já que a tela de
 * TV precisa preencher a largura toda da tela com várias fileiras logo
 * de cara (diferente do celular, que pode ir carregando por scroll).
 */
class HomeTvViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeTvUiState())
    val uiState: StateFlow<HomeTvUiState> = _uiState.asStateFlow()

    private var loaded = false

    fun loadHome() {
        if (loaded) return
        loaded = true

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val trending = runCatching {
                NetworkModule.tmdbApi.request(path = "/trending/all/week").results.orEmpty()
            }.getOrDefault(emptyList())

            val popularMovies = runCatching {
                NetworkModule.tmdbApi.request(path = "/movie/popular").results.orEmpty()
            }.getOrDefault(emptyList())

            val popularSeries = runCatching {
                NetworkModule.tmdbApi.request(path = "/tv/popular").results.orEmpty()
            }.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    isLoading = false,
                    trending = trending,
                    popularMovies = popularMovies,
                    popularSeries = popularSeries,
                )
            }
        }
    }
}
