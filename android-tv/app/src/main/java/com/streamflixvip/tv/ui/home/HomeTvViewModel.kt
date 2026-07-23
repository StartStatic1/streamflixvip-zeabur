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

/** Estado da Home TV — hero, e múltiplas linhas de conteúdo. */
data class HomeTvUiState(
    val isLoading: Boolean = true,
    val heroItems: List<TmdbItem> = emptyList(),
    val trendingItems: List<TmdbItem> = emptyList(),
    val popularMovies: List<TmdbItem> = emptyList(),
    val popularSeries: List<TmdbItem> = emptyList(),
    val actionItems: List<TmdbItem> = emptyList(),
    val comedyItems: List<TmdbItem> = emptyList(),
    val dramaItems: List<TmdbItem> = emptyList(),
    val horrorItems: List<TmdbItem> = emptyList(),
    val scifiItems: List<TmdbItem> = emptyList(),
    val animeItems: List<TmdbItem> = emptyList(),
    val familyItems: List<TmdbItem> = emptyList(),
)

/**
 * ViewModel da Home de TV — carrega múltiplas seções em paralelo,
 * cada uma representando uma linha de carrossel horizontal no layout.
 * As seções seguem o padrão visual das referências (Streambox, serivia,
 * PlayBox): hero grande, trending, gêneros.
 */
class HomeTvViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeTvUiState())
    val uiState: StateFlow<HomeTvUiState> = _uiState.asStateFlow()

    private val cache = mutableMapOf<String, List<TmdbItem>>()

    fun loadAll() {
        if (cache.isNotEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val hero = safeFetch("/trending/all/day")
            val trending = safeFetch("/trending/all/week")
            val movies = safeFetch("/movie/popular")
            val series = safeFetch("/tv/popular")
            val action = safeFetch("/discover/movie", withGenres = "28")
            val comedy = safeFetch("/discover/movie", withGenres = "35")
            val drama = safeFetch("/discover/movie", withGenres = "18")
            val horror = safeFetch("/discover/movie", withGenres = "27")
            val scifi = safeFetch("/discover/movie", withGenres = "878")
            val anime = safeFetch("/discover/tv", withOriginalLanguage = "ja")
            val family = safeFetch("/discover/movie", withGenres = "10751")

            _uiState.update {
                it.copy(
                    isLoading = false,
                    heroItems = hero,
                    trendingItems = trending,
                    popularMovies = movies,
                    popularSeries = series,
                    actionItems = action,
                    comedyItems = comedy,
                    dramaItems = drama,
                    horrorItems = horror,
                    scifiItems = scifi,
                    animeItems = anime,
                    familyItems = family,
                )
            }
        }
    }

    private suspend fun safeFetch(
        path: String,
        withGenres: String? = null,
        withOriginalLanguage: String? = null,
    ): List<TmdbItem> {
        val cacheKey = "$path|$withGenres|$withOriginalLanguage"
        cache[cacheKey]?.let { return it }

        val results = runCatching {
            NetworkModule.tmdbApi.request(
                path = path,
                withGenres = withGenres,
                withOriginalLanguage = withOriginalLanguage,
            ).results.orEmpty()
        }.getOrDefault(emptyList())

        cache[cacheKey] = results
        return results
    }
}
