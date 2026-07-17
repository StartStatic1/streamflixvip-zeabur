package com.streamflixvip.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.network.TmdbItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Filtros exclusivos da pesquisa geral. Diferente de Explorar, esta tela
 * parte do texto digitado pela pessoa para localizar um título específico.
 */
enum class SearchCategory(
    val label: String,
    val mediaType: String? = null,
    val originalLanguage: String? = null,
) {
    ALL(label = "Tudo"),
    MOVIES(label = "Filmes", mediaType = "movie"),
    SERIES(label = "Séries", mediaType = "tv"),
    ANIME(label = "Animes", mediaType = "tv", originalLanguage = "ja"),
}

data class SearchUiState(
    val query: String = "",
    val category: SearchCategory = SearchCategory.ALL,
    val results: List<TmdbItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

class SearchViewModel(
    private val repository: CatalogRepository = CatalogRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null
    private var requestVersion = 0

    fun onQueryChanged(value: String) {
        _uiState.value = _uiState.value.copy(query = value, errorMessage = null)
        submitSearch(withDebounce = true)
    }

    fun selectCategory(category: SearchCategory) {
        if (_uiState.value.category == category) return
        _uiState.value = _uiState.value.copy(category = category, errorMessage = null)
        submitSearch(withDebounce = false)
    }

    fun retry() = submitSearch(withDebounce = false)

    private fun submitSearch(withDebounce: Boolean) {
        searchJob?.cancel()
        requestVersion += 1
        val version = requestVersion
        val snapshot = _uiState.value
        val query = snapshot.query.trim()

        if (query.isBlank()) {
            _uiState.value = snapshot.copy(
                results = emptyList(),
                isLoading = false,
                hasSearched = false,
                errorMessage = null,
            )
            return
        }

        searchJob = viewModelScope.launch {
            if (withDebounce) delay(350)
            if (version != requestVersion) return@launch

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                hasSearched = true,
                errorMessage = null,
            )

            val result = runCatching {
                repository.searchCatalog(
                    query = query,
                    mediaType = snapshot.category.mediaType,
                    originalLanguage = snapshot.category.originalLanguage,
                )
            }

            if (version != requestVersion) return@launch

            result.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    results = items,
                    isLoading = false,
                    hasSearched = true,
                    errorMessage = null,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    isLoading = false,
                    hasSearched = true,
                    errorMessage = "Não foi possível pesquisar agora. Verifique sua conexão e tente novamente.",
                )
            }
        }
    }
}
