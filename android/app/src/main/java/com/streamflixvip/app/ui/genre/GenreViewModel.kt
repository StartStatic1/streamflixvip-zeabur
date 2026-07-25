package com.streamflixvip.app.ui.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.data.CatalogRepository
import com.streamflixvip.app.data.GenreCategory
import com.streamflixvip.app.data.GenreDefinition
import com.streamflixvip.app.data.TMDB_GENRES
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GenreCard(
    val genre: GenreDefinition,
    val posters: List<String>, // até 3 poster_path pra empilhar no card
)

data class FeaturedGenre(
    val genre: GenreDefinition,
    val backdropPath: String?,
)

sealed interface GenreUiState {
    data object Loading : GenreUiState
    data class Success(
        val category: GenreCategory,
        val featured: FeaturedGenre?,
        val cards: List<GenreCard>,
    ) : GenreUiState
}

class GenreViewModel(
    private val repository: CatalogRepository = CatalogRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<GenreUiState>(GenreUiState.Loading)
    val uiState: StateFlow<GenreUiState> = _uiState

    init {
        load(GenreCategory.ALL)
    }

    /** Troca o filtro (pill Tudo/Filmes/Séries/Animes/Doramas) e recarrega tudo pra essa categoria. */
    fun selectCategory(category: GenreCategory) {
        load(category)
    }

    private fun load(category: GenreCategory) {
        viewModelScope.launch {
            _uiState.value = GenreUiState.Loading

            // Gênero em destaque: escolhido pseudo-aleatoriamente a cada
            // troca de filtro (igual ao app de referência, onde o banner
            // muda de gênero ao trocar de aba) — só precisa de 1 imagem
            // de fundo, então usa o primeiro resultado como backdrop.
            val featuredDefinition = TMDB_GENRES.random()
            val featuredItemsDeferred = async {
                runCatching { repository.getTitlesByGenre(featuredDefinition.id, category) }.getOrElse { emptyList() }
            }

            // Cards da grade "Todos os gêneros" — busca em paralelo,
            // pegando só as 3 primeiras capas de cada um (sem contagem).
            val cardsDeferred = TMDB_GENRES.map { genre ->
                async {
                    val items = runCatching { repository.getTitlesByGenre(genre.id, category) }.getOrElse { emptyList() }
                    GenreCard(genre = genre, posters = items.mapNotNull { it.poster_path }.take(3))
                }
            }

            val featuredItems = featuredItemsDeferred.await()
            val cards = cardsDeferred.awaitAll().filter { it.posters.isNotEmpty() }

            _uiState.value = GenreUiState.Success(
                category = category,
                featured = FeaturedGenre(
                    genre = featuredDefinition,
                    backdropPath = featuredItems.firstOrNull()?.backdrop_path,
                ),
                cards = cards,
            )
        }
    }
}
