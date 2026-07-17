package com.streamflixvip.app.ui.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.FavoritesRepository
import com.streamflixvip.app.network.FavoriteEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Filtros da aba Favoritos — mesma aproximação por idioma original usada na aba Gêneros. */
enum class FavoritesFilter(val label: String) {
    ALL("Todos"),
    MOVIES("Filmes"),
    SERIES("Séries"),
    ANIME("Animes"),
    DORAMA("Doramas"),
}

sealed interface MyListUiState {
    data object Loading : MyListUiState
    data object LoggedOut : MyListUiState
    data class Success(
        val allFavorites: List<FavoriteEntry>,
        val filter: FavoritesFilter,
    ) : MyListUiState {
        /** Lista já filtrada pelo filtro atual — a UI só precisa exibir isso, sem repetir a lógica de filtro. */
        val visibleFavorites: List<FavoriteEntry>
            get() = when (filter) {
                FavoritesFilter.ALL -> allFavorites
                FavoritesFilter.MOVIES -> allFavorites.filter { it.media_type == "movie" }
                FavoritesFilter.SERIES -> allFavorites.filter { it.media_type == "tv" && !it.isAnime && !it.isDorama }
                FavoritesFilter.ANIME -> allFavorites.filter { it.isAnime }
                FavoritesFilter.DORAMA -> allFavorites.filter { it.isDorama }
            }

        fun countFor(f: FavoritesFilter): Int = when (f) {
            FavoritesFilter.ALL -> allFavorites.size
            FavoritesFilter.MOVIES -> allFavorites.count { it.media_type == "movie" }
            FavoritesFilter.SERIES -> allFavorites.count { it.media_type == "tv" && !it.isAnime && !it.isDorama }
            FavoritesFilter.ANIME -> allFavorites.count { it.isAnime }
            FavoritesFilter.DORAMA -> allFavorites.count { it.isDorama }
        }
    }
}

class MyListViewModel(
    private val userId: String?,
    private val accessToken: String?,
    private val repository: FavoritesRepository = FavoritesRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<MyListUiState>(MyListUiState.Loading)
    val uiState: StateFlow<MyListUiState> = _uiState

    init {
        load()
    }

    fun load() {
        val token = accessToken
        val uid = userId
        if (token == null || uid == null) {
            _uiState.value = MyListUiState.LoggedOut
            return
        }
        viewModelScope.launch {
            _uiState.value = MyListUiState.Loading
            val favorites = repository.getFavorites(token, uid)
            val currentFilter = (_uiState.value as? MyListUiState.Success)?.filter ?: FavoritesFilter.ALL
            _uiState.value = MyListUiState.Success(allFavorites = favorites, filter = currentFilter)
        }
    }

    fun selectFilter(filter: FavoritesFilter) {
        val current = _uiState.value as? MyListUiState.Success ?: return
        _uiState.value = current.copy(filter = filter)
    }

    /** Remove um item da lista — usado pelo "x"/coração no card. */
    fun removeFavorite(tmdbId: Int, mediaType: String) {
        val token = accessToken ?: return
        val uid = userId ?: return
        val current = _uiState.value as? MyListUiState.Success ?: return

        // Otimista: some da tela na hora, sem esperar a resposta da rede.
        val updated = current.allFavorites.filterNot { it.tmdb_id == tmdbId && it.media_type == mediaType }
        _uiState.value = current.copy(allFavorites = updated)

        viewModelScope.launch {
            val removed = repository.removeFavorite(token, uid, tmdbId, mediaType)
            if (!removed) {
                // Falhou de verdade (não só ausência de rede momentânea) — recarrega pra refletir o estado real.
                load()
            }
        }
    }
}
