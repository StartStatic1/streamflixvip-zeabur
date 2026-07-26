package com.streamflixvip.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val error: String? = null,
)

class HomeTvViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeTvUiState())
    val uiState: StateFlow<HomeTvUiState> = _uiState.asStateFlow()

    private val cache = mutableMapOf<String, List<TmdbItem>>()

    fun loadAll() {
        // Antes: bastava UMA chamada ter sucesso pro cache "existir" e
        // loadAll() pular tudo nas próximas vezes (ex: voltar pra Home
        // depois de sair do app) — mesmo que outras categorias tivessem
        // vindo vazias por falha de rede temporária, elas ficavam vazias
        // pra sempre até reiniciar o app. Agora só considera "já
        // carregado" se pelo menos uma categoria central (trending) tem
        // itens de verdade.
        if (cache["/trending/all/week|null|null"]?.isNotEmpty() == true) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // CORREÇÃO BUG #10: Carregamento PARALELO com awaitAll
                val results = coroutineScope {
                    val hero = async { safeFetch("/trending/all/day") }
                    val trending = async { safeFetch("/trending/all/week") }
                    val movies = async { safeFetch("/movie/popular") }
                    val series = async { safeFetch("/tv/popular") }
                    val action = async { safeFetch("/discover/movie", withGenres = "28") }
                    val comedy = async { safeFetch("/discover/movie", withGenres = "35") }
                    val drama = async { safeFetch("/discover/movie", withGenres = "18") }
                    val horror = async { safeFetch("/discover/movie", withGenres = "27") }
                    val scifi = async { safeFetch("/discover/movie", withGenres = "878") }
                    val anime = async { safeFetch("/discover/tv", withOriginalLanguage = "ja") }
                    val family = async { safeFetch("/discover/movie", withGenres = "10751") }
                    awaitAll(hero, trending, movies, series, action, comedy, drama, horror, scifi, anime, family)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        heroItems = results[0],
                        trendingItems = results[1],
                        popularMovies = results[2],
                        popularSeries = results[3],
                        actionItems = results[4],
                        comedyItems = results[5],
                        dramaItems = results[6],
                        horrorItems = results[7],
                        scifiItems = results[8],
                        animeItems = results[9],
                        familyItems = results[10],
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Erro ao carregar conteúdo")
                }
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
