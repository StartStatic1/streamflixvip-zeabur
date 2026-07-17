package com.streamflixvip.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.data.GenreDefinition
import com.streamflixvip.app.network.TmdbItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Year

/** Anos disponíveis no filtro — do ano atual até 1940, mais que suficiente pro catálogo. */
val EXPLORE_YEARS: List<Int> = (Year.now().value downTo 1940).toList()

data class ExploreFilters(
    val category: GenreCategory = GenreCategory.ALL,
    val genre: GenreDefinition? = null,
    val year: Int? = null,
)

sealed interface ExploreUiState {
    data object Loading : ExploreUiState
    data class Success(
        val filters: ExploreFilters,
        val items: List<TmdbItem>,
        val isLoadingMore: Boolean = false,
        val reachedEnd: Boolean = false,
    ) : ExploreUiState
}

class ExploreViewModel(
    private val repository: CatalogRepository = CatalogRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val uiState: StateFlow<ExploreUiState> = _uiState

    private var currentPage = 1
    private var currentFilters = ExploreFilters()

    init {
        load(currentFilters)
    }

    /** Aplica novos filtros (categoria/gênero/ano) e recarrega do zero. */
    fun applyFilters(filters: ExploreFilters) {
        load(filters)
    }

    private fun load(filters: ExploreFilters) {
        currentFilters = filters
        currentPage = 1
        viewModelScope.launch {
            _uiState.value = ExploreUiState.Loading
            val items = runCatching {
                repository.exploreCatalog(filters.category, filters.genre?.id, filters.year, page = 1)
            }.getOrElse { emptyList() }
            _uiState.value = ExploreUiState.Success(filters = filters, items = items)
        }
    }

    /** Carrega a próxima página — chamado pela UI quando a rolagem se aproxima do fim da lista. */
    fun loadNextPage() {
        val current = _uiState.value as? ExploreUiState.Success ?: return
        if (current.isLoadingMore || current.reachedEnd) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            val nextPage = currentPage + 1
            val more = runCatching {
                repository.exploreCatalog(currentFilters.category, currentFilters.genre?.id, currentFilters.year, page = nextPage)
            }.getOrElse { emptyList() }

            val stillCurrent = _uiState.value as? ExploreUiState.Success ?: return@launch
            _uiState.value = if (more.isEmpty()) {
                stillCurrent.copy(isLoadingMore = false, reachedEnd = true)
            } else {
                currentPage = nextPage
                stillCurrent.copy(items = stillCurrent.items + more, isLoadingMore = false)
            }
        }
    }
}
