package com.streamflixvip.tv.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.tv.data.LocalFavorite
import com.streamflixvip.tv.data.LocalLibraryStore
import com.streamflixvip.tv.data.LocalWatchProgress
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BrowseUiState(
    val specs: List<RowSpec> = emptyList(),
    val rows: Map<String, List<TmdbItem>> = emptyMap(),
    val pending: Set<String> = emptySet(),
    val continueWatching: List<LocalWatchProgress> = emptyList(),
    val favorites: List<LocalFavorite> = emptyList(),
    val error: String? = null,
) {
    val isLoading: Boolean get() = rows.values.none { it.isNotEmpty() } && pending.isNotEmpty()
}

/**
 * Catalogo de uma secao (Inicio, Filmes, Series, Infantil). As fileiras chegam
 * aos poucos (cada uma aparece assim que carrega), em paralelo com limite de 4
 * pedidos ao mesmo tempo para nao engasgar TV box fraca.
 */
class HomeTvViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private val libraryStore = LocalLibraryStore(application)
    private var loadedSection: BrowseSection? = null
    private val gate = Semaphore(4)

    fun load(section: BrowseSection, force: Boolean = false) {
        refreshLibrary()
        if (!force && loadedSection == section && _uiState.value.rows.values.any { it.isNotEmpty() }) return
        loadedSection = section
        val specs = Catalog.rowsFor(section)
        _uiState.update { it.copy(specs = specs, rows = emptyMap(), pending = specs.map { s -> s.key }.toSet(), error = null) }
        val kids = section == BrowseSection.KIDS
        specs.forEach { spec ->
            viewModelScope.launch {
                val items = gate.withPermit { fetchRow(spec, page = 1) }
                    .let { if (kids) Catalog.kidsSafe(it) else it }
                    .let { list ->
                        if (spec.style == RowStyle.FEATURED) list.filter { !it.backdrop_path.isNullOrBlank() } else list
                    }
                _uiState.update { st ->
                    val rows = st.rows + (spec.key to items)
                    val pending = st.pending - spec.key
                    st.copy(
                        rows = rows,
                        pending = pending,
                        error = if (pending.isEmpty() && rows.values.all { it.isEmpty() }) {
                            "Não foi possível carregar o catálogo. Verifique a internet."
                        } else null,
                    )
                }
            }
        }
    }

    fun retry() {
        loadedSection?.let { load(it, force = true) }
    }

    fun refreshLibrary() {
        _uiState.update {
            it.copy(
                continueWatching = libraryStore.getContinueWatching(),
                favorites = libraryStore.getFavorites(),
            )
        }
    }

    fun toggleFavorite(item: TmdbItem): Boolean {
        val added = libraryStore.toggleFavorite(
            LocalFavorite(item.id, item.resolvedMediaType, item.displayTitle, item.poster_path),
        )
        refreshLibrary()
        return added
    }

    fun removeFromContinue(entry: LocalWatchProgress) {
        libraryStore.removeTitleProgress(entry.tmdbId, entry.mediaType)
        refreshLibrary()
    }

    companion object {
        suspend fun fetchRow(spec: RowSpec, page: Int): List<TmdbItem> = runCatching {
            val params = spec.params + (if (page > 1) mapOf("page" to page.toString()) else emptyMap())
            NetworkModule.tmdbApi.requestMap(spec.path, params).results.orEmpty()
                .filter { it.media_type == null || it.media_type == "movie" || it.media_type == "tv" }
                .map { if (it.media_type == null && spec.mediaType != null) it.copy(media_type = spec.mediaType) else it }
                .filter { !it.poster_path.isNullOrBlank() || !it.backdrop_path.isNullOrBlank() }
        }.getOrDefault(emptyList())
    }
}
