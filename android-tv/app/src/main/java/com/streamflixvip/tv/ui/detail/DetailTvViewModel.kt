package com.streamflixvip.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.tv.network.NetworkModule
import com.streamflixvip.tv.network.TmdbResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailTvUiState(
    val isLoading: Boolean = true,
    val detail: TmdbResponse? = null,
)

/**
 * ViewModel da tela de Detalhe — uma chamada só, trazendo sinopse,
 * gêneros, duração/temporadas e elenco (append_to_response=credits)
 * juntos, pra abrir a tela sem esperar múltiplas requisições em série.
 */
class DetailTvViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DetailTvUiState())
    val uiState: StateFlow<DetailTvUiState> = _uiState.asStateFlow()

    fun load(tmdbId: Int, mediaType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val detail = runCatching {
                NetworkModule.tmdbApi.request(
                    path = "/$mediaType/$tmdbId",
                    appendToResponse = "credits",
                )
            }.getOrNull()
            _uiState.update { it.copy(isLoading = false, detail = detail) }
        }
    }
}
