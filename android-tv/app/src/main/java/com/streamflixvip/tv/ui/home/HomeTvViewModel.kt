package com.streamflixvip.tv.ui.home

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
    val items: List<TmdbItem> = emptyList(),
)

/**
 * ViewModel da Home de TV — uma lista só (`items`), trocada conforme a
 * aba de categoria selecionada (ver TvCategory em HomeTvScreen.kt), no
 * lugar de 3 seções fixas em carrossel. Resultado de cada categoria fica
 * em cache em memória (categoryCache) pra trocar de aba não recarregar
 * do zero toda vez que a pessoa volta numa aba já visitada.
 */
class HomeTvViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeTvUiState())
    val uiState: StateFlow<HomeTvUiState> = _uiState.asStateFlow()

    private val categoryCache = mutableMapOf<TvCategory, List<TmdbItem>>()

    fun loadCategory(category: TvCategory) {
        categoryCache[category]?.let { cached ->
            _uiState.update { it.copy(isLoading = false, items = cached) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val path = when (category) {
                TvCategory.RECOMENDACOES -> "/trending/all/week"
                TvCategory.FILMES -> "/movie/popular"
                TvCategory.SERIES -> "/tv/popular"
                // TMDB não tem endpoint "infantil" dedicado — filtra filmes
                // pelo gênero Family (10751) como aproximação, mesmo padrão
                // de filtro por gênero já usado no app de celular (ver
                // GenreCategory/CatalogRepository.exploreCatalog lá).
                TvCategory.CRIANCAS -> "/discover/movie"
                // Aproximação de "animes": séries de idioma original
                // japonês — mesmo critério já usado em VipSource.isAnime
                // no app de celular, não um endpoint TMDB dedicado.
                TvCategory.ANIMES -> "/discover/tv"
            }

            val results = runCatching {
                when (category) {
                    TvCategory.CRIANCAS -> NetworkModule.tmdbApi.request(
                        path = path,
                        withGenres = "10751", // id fixo TMDB pra "Family"
                    ).results.orEmpty()
                    TvCategory.ANIMES -> NetworkModule.tmdbApi.request(
                        path = path,
                        withOriginalLanguage = "ja",
                    ).results.orEmpty()
                    else -> NetworkModule.tmdbApi.request(path = path).results.orEmpty()
                }
            }.getOrDefault(emptyList())

            categoryCache[category] = results
            _uiState.update { it.copy(isLoading = false, items = results) }
        }
    }
}
