package com.streamflixvip.app.ui.livetv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.network.LiveCategory
import com.streamflixvip.app.network.LiveChannel
import com.streamflixvip.app.network.NetworkModule
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class LiveTvTab { CHANNELS, FAVORITES }

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val selectedCategoryId: String = "all",
    val searchQuery: String = "",
    val sourcesUsed: Int = 0,
    val error: String? = null,
    val vipRequiredByServer: Boolean = false,
    val tab: LiveTvTab = LiveTvTab.CHANNELS,
    val favoriteIds: Set<String> = emptySet(),
    val selectedChannelId: String? = null,
    val brandFilter: String? = null,
) {
    val selectedChannel: LiveChannel?
        get() = selectedChannelId?.let { id -> channels.find { it.id == id } }

    val filteredChannels: List<LiveChannel>
        get() {
            val q = normalize(searchQuery)
            var list = channels

            if (tab == LiveTvTab.FAVORITES) {
                list = list.filter { favoriteIds.contains(it.id) }
            } else {
                if (selectedCategoryId == "all") {
                    list = list.filter { it.categoryId != "000" }
                } else {
                    list = list.filter { it.categoryId == selectedCategoryId }
                }
            }

            brandFilter?.let { brand ->
                list = list.filter { normalize(it.name).contains(brand) }
            }

            if (q.isNotEmpty()) {
                list = list.filter { normalize(it.name).contains(q) }
            }
            return list
        }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")
    }
}

class LiveTvViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(
        LiveTvUiState(favoriteIds = LiveTvFavoritesStore.getIds(app)),
    )
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, vipRequiredByServer = false)
            }
            runCatching {
                NetworkModule.liveTvApi.getLiveTv()
            }.onSuccess { response ->
                val cleaned = cleanCategoriesAndChannels(response.categories, response.channels)
                val favs = LiveTvFavoritesStore.getIds(getApplication())
                val autoSelect = _uiState.value.selectedChannelId
                    ?: cleaned.channels.firstOrNull { favs.contains(it.id) }?.id
                    ?: cleaned.channels.firstOrNull { it.categoryId != "000" }?.id

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = cleaned.categories,
                        channels = cleaned.channels,
                        sourcesUsed = response.sourcesUsed,
                        favoriteIds = favs,
                        selectedChannelId = autoSelect,
                        vipRequiredByServer = false,
                        error = if (cleaned.channels.isEmpty()) {
                            "Nenhum canal disponível no momento."
                        } else null,
                    )
                }
            }.onFailure { e ->
                val isVipBlocked = (e as? HttpException)?.code() == 403
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        channels = emptyList(),
                        categories = emptyList(),
                        sourcesUsed = 0,
                        vipRequiredByServer = isVipBlocked,
                        error = if (isVipBlocked) {
                            "VIP necessário para assistir TV ao vivo."
                        } else {
                            e.message ?: "Erro ao carregar canais"
                        },
                    )
                }
            }
        }
    }

    fun selectCategory(id: String) {
        _uiState.update { it.copy(selectedCategoryId = id, searchQuery = "", tab = LiveTvTab.CHANNELS) }
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setBrandFilter(brand: String?) {
        _uiState.update {
            it.copy(brandFilter = brand, tab = LiveTvTab.CHANNELS, searchQuery = "")
        }
    }

    fun setTab(tab: LiveTvTab) {
        _uiState.update { it.copy(tab = tab, searchQuery = "") }
    }

    fun selectChannel(channel: LiveChannel) {
        _uiState.update { it.copy(selectedChannelId = channel.id) }
    }

    fun toggleFavorite(channelId: String) {
        val nowFav = LiveTvFavoritesStore.toggle(getApplication(), channelId)
        _uiState.update { st ->
            val next = st.favoriteIds.toMutableSet()
            if (nowFav) next.add(channelId) else next.remove(channelId)
            st.copy(favoriteIds = next)
        }
    }

    private data class Cleaned(val categories: List<LiveCategory>, val channels: List<LiveChannel>)

    private fun cleanCategoriesAndChannels(
        rawCats: List<LiveCategory>,
        rawChannels: List<LiveChannel>,
    ): Cleaned {
        val idToRawName = rawCats.associate { it.id to (it.name ?: it.id) }

        fun bucket(rawName: String, rawId: String): String? {
            val n = LiveTvUiState.normalize("$rawName $rawId")
            if (n.contains("adult") || n.contains("xxx") || n.contains("porn") ||
                n == "000" || n == "00" || n.contains("+18") || n.contains("18+")
            ) {
                return "000"
            }
            return when {
                n.contains("esporte") || n.contains("sport") || n.contains("futebol") -> "esportes"
                n.contains("filme") || n.contains("cinema") || n.contains("telecine") ||
                    n.contains("hbo") || n.contains("premiere") || n.contains("paramount") ||
                    n.contains("universal") || n.contains("megapix") -> "filmes"
                n.contains("serie") || n.contains("series") || n.contains("novela") -> "series"
                n.contains("noticia") || n.contains("news") || n.contains("jornal") -> "noticias"
                n.contains("infantil") || n.contains("kids") || n.contains("cartoon") ||
                    n.contains("disney") || n.contains("nick") -> "infantil"
                n.contains("aberto") || n.contains("abertos") || n.contains("globo") ||
                    n.contains("sbt") || n.contains("record") || n.contains("band") ||
                    n.contains("cultura") || n.contains("tv aberta") -> "abertos"
                n.contains("musica") || n.contains("music") || n.contains("mtv") -> "musica"
                n.contains("document") || n.contains("doc ") -> "docs"
                else -> null
            }
        }

        val label = mapOf(
            "all" to "Todos",
            "abertos" to "Abertos",
            "esportes" to "Esportes",
            "filmes" to "Filmes",
            "series" to "Séries",
            "noticias" to "Notícias",
            "infantil" to "Infantil",
            "musica" to "Música",
            "docs" to "Docs",
            "outros" to "Outros",
        )

        val channelBuckets = linkedMapOf<String, MutableList<LiveChannel>>()
        for (ch in rawChannels) {
            val rawCatName = idToRawName[ch.categoryId ?: ""] ?: (ch.categoryId ?: "")
            val b = bucket(rawCatName, ch.categoryId ?: "") ?: "outros"
            if (b == "000") continue
            channelBuckets.getOrPut(b) { mutableListOf() }.add(ch.copy(categoryId = b))
        }

        val order = listOf("abertos", "esportes", "filmes", "series", "noticias", "infantil", "musica", "docs", "outros")
        val categories = mutableListOf(LiveCategory("all", "Todos"))
        for (key in order) {
            if (channelBuckets[key].orEmpty().isNotEmpty()) {
                categories.add(LiveCategory(key, label[key] ?: key))
            }
        }

        val channels = order.flatMap { channelBuckets[it].orEmpty() }
        return Cleaned(categories, channels)
    }
}
