package com.streamflixvip.app.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.TmdbResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PersonUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val person: TmdbResponse? = null,
    val movies: List<TmdbItem> = emptyList(),
    val series: List<TmdbItem> = emptyList(),
)

class PersonViewModel(
    private val personId: Int,
) : ViewModel() {

    private val _ui = MutableStateFlow(PersonUiState())
    val ui: StateFlow<PersonUiState> = _ui

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _ui.value = PersonUiState(loading = true)
            runCatching {
                NetworkModule.tmdbApi.request(
                    path = "/person/$personId",
                    appendToResponse = "combined_credits",
                )
            }.onSuccess { person ->
                val credits = person.combined_credits?.cast.orEmpty()
                    .filter { !it.poster_path.isNullOrBlank() }
                    .distinctBy { "${it.id}_${it.resolvedMediaType}" }
                    .sortedByDescending { it.popularity ?: 0.0 }
                _ui.value = PersonUiState(
                    loading = false,
                    person = person,
                    movies = credits.filter { it.resolvedMediaType == "movie" }.take(24),
                    series = credits.filter { it.resolvedMediaType == "tv" }.take(24),
                )
            }.onFailure {
                _ui.value = PersonUiState(loading = false, error = true)
            }
        }
    }
}
