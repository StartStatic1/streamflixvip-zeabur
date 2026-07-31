package com.streamflixvip.app.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.network.LiveCategory
import com.streamflixvip.app.network.LiveChannel
import com.streamflixvip.app.network.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val selectedCategoryId: String = "all",
    val searchQuery: String = "",
    val sourcesUsed: Int = 0,
    val error: String? = null,
) {
    val filteredChannels: List<LiveChannel>
        get() {
            var list = channels
            if (selectedCategoryId != "all") {
                list = list.filter { it.categoryId == selectedCategoryId }
            }
            val q = searchQuery.trim()
            if (q.isNotEmpty()) {
                list = list.filter { it.name.contains(q, ignoreCase = true) }
            }
            return list
        }
}

class LiveTvViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                NetworkModule.liveTvApi.getLiveTv()
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = response.categories.ifEmpty {
                            listOf(LiveCategory("all", "Todos"))
                        },
                        channels = response.channels,
                        sourcesUsed = response.sourcesUsed,
                        error = if (response.channels.isEmpty()) {
                            "Nenhum canal disponível no momento."
                        } else null,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Erro ao carregar canais",
                    )
                }
            }
        }
    }

    fun selectCategory(id: String) {
        _uiState.update { it.copy(selectedCategoryId = id) }
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
