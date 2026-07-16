package com.streamflixvip.app.ui.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.GenreDefinition
import com.streamflixvip.app.data.TMDB_GENRES
import com.streamflixvip.app.network.TmdbItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GenreCard(
    val genre: GenreDefinition,
    // "movie" ou "tv" — cada card mostra só um lado por vez pra manter a
    // lista curta e a busca simples (mediaType fixo por card).
    val mediaType: String,
    val posters: List<String>, // até 3 poster_path pra empilhar no card
)

sealed interface GenreUiState {
    data object Loading : GenreUiState
    data class Success(val cards: List<GenreCard>) : GenreUiState
}

class GenreViewModel(
    private val repository: CatalogRepository = CatalogRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<GenreUiState>(GenreUiState.Loading)
    val uiState: StateFlow<GenreUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // Busca os gêneros em paralelo (movie), já que cada card só
            // precisa das 3 primeiras capas — sem contagem, é rápido.
            val cards = TMDB_GENRES.map { genre ->
                async {
                    val items = runCatching { repository.getTitlesByGenre(genre.id, "movie") }.getOrElse { emptyList() }
                    GenreCard(
                        genre = genre,
                        mediaType = "movie",
                        posters = items.mapNotNull { it.poster_path }.take(3),
                    )
                }
            }.awaitAll()
            _uiState.value = GenreUiState.Success(cards.filter { it.posters.isNotEmpty() })
        }
    }
}
