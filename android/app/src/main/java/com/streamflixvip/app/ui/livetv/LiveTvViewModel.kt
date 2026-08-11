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
    val selectedCategoryId: String = "abertos",
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
            } else if (brandFilter == null) {
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
                val defaultCat = cleaned.categories
                    .firstOrNull { it.id == "abertos" }?.id
                    ?: cleaned.categories.firstOrNull { it.id != "all" }?.id
                    ?: "all"
                val autoSelect = _uiState.value.selectedChannelId
                    ?: cleaned.channels.firstOrNull { favs.contains(it.id) }?.id
                    ?: cleaned.channels.firstOrNull { it.categoryId == defaultCat }?.id
                    ?: cleaned.channels.firstOrNull()?.id

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = cleaned.categories,
                        channels = cleaned.channels,
                        sourcesUsed = response.sourcesUsed,
                        favoriteIds = favs,
                        selectedCategoryId = defaultCat,
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
        _uiState.update {
            it.copy(
                selectedCategoryId = id,
                searchQuery = "",
                tab = LiveTvTab.CHANNELS,
                brandFilter = null,
            )
        }
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
        _uiState.update { it.copy(tab = tab, searchQuery = "", brandFilter = null) }
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

        fun isJunkChannel(name: String): Boolean {
            val n = LiveTvUiState.normalize(name)
            if (n.contains("24h") || n.contains("24 h") || n.contains("24hrs") ||
                n.contains("24 horas") || n.contains("lendas do cinema") ||
                n.contains("lendas cinema")
            ) {
                return true
            }
            if (Regex("""\\s+-\\s*s\\d+\\s*$""").containsMatchIn(n)) return true
            if (Regex("""\\bs\\d+\\s*$""").containsMatchIn(n)) {
                val actors = listOf(
                    "smith", "snipes", "damme", "chan", "statham", "cruise", "dicaprio",
                    "schwarzenegger", "stallone", "willis", "cage", "diesel",
                    "vin diesel", "keanu", "reeves", "pitt", "depp", "affleck", "reynolds",
                    "hardy", "jackie", "jet li", "seagal", "lundgren",
                    "van damme", "wesley", "jason", "bruce lee", "chuck norris",
                )
                if (actors.any { n.contains(it) }) return true
            }
            return false
        }

        fun bucket(rawName: String, rawId: String, channelName: String): String? {
            val n = LiveTvUiState.normalize("$rawName $rawId $channelName")
            if (n.contains("adult") || n.contains("xxx") || n.contains("porn") ||
                n == "000" || n == "00" || n.contains("+18") || n.contains("18+")
            ) {
                return "000"
            }
            if (n.contains("anime") || n.contains("otaku") || n.contains("naruto") ||
                n.contains("dragon ball") || n.contains("one piece")
            ) {
                return "anime"
            }
            if (n.contains("discovery") || n.contains("animal planet") ||
                n.contains("nat geo") || n.contains("national geographic") ||
                n.contains("history channel") || n.contains("history 2") ||
                n.contains("h&h") || n.contains("h e h") || n.contains("home health") ||
                n.contains("investigation discovery")
            ) {
                return "discovery"
            }
            if (n.contains("cartoon") || n.contains("desenh") || n.contains("infantil") ||
                n.contains("kids") || n.contains("gloob") || n.contains("nick") ||
                n.contains("disney channel") || n.contains("disney jr") ||
                n.contains("baby tv") || n.contains("tooncast") || n.contains("cartoonito") ||
                n.contains("zoomoo") || n.contains("tv ra tim bum") || n.contains("boomerang")
            ) {
                return "desenhos"
            }
            if (n.contains("esporte") || n.contains("sport") || n.contains("futebol") ||
                n.contains("premiere") || n.contains("sportv") || n.contains("combate") ||
                n.contains("band sports") || n.contains("espn")
            ) {
                return "esportes"
            }
            if (n.contains("telecine") || n.contains("megapix") || n.contains("cinemax") ||
                n.contains("tcm") || n.contains("space ") || n.contains("filme") ||
                n.contains("cinema") || (n.contains("hbo") && !n.contains("series"))
            ) {
                return "filmes"
            }
            if (n.contains("serie") || n.contains("series") || n.contains("novela") ||
                n.contains("fx ") || n.contains("axn") || n.contains("sony") ||
                n.contains("warner") || n.contains("universal tv") || n.contains("star channel")
            ) {
                return "series"
            }
            if (n.contains("noticia") || n.contains("news") || n.contains("jornal") ||
                n.contains("cnn") || n.contains("band news") || n.contains("globo news") ||
                n.contains("record news") || n.contains("jovem pan")
            ) {
                return "noticias"
            }
            if (n.contains("document") || n.contains("doc ") || n.contains("docs")) {
                return "docs"
            }
            if (n.contains("aberto") || n.contains("abertos") || n.contains("globo") ||
                n.contains("sbt") || n.contains("record") || n.contains("band") ||
                n.contains("cultura") || n.contains("tv aberta") || n.contains("rede tv") ||
                n.contains("gazeta") || n.contains("cnt ") || n.contains("tv brasil")
            ) {
                return "abertos"
            }
            if (n.contains("musica") || n.contains("music") || n.contains("mtv") ||
                n.contains("music box")
            ) {
                return "musica"
            }
            return null
        }

        val label = mapOf(
            "abertos" to "Abertos",
            "esportes" to "Esportes",
            "filmes" to "Filmes",
            "series" to "Séries",
            "desenhos" to "Desenhos",
            "anime" to "Anime",
            "discovery" to "Discovery",
            "docs" to "Docs",
            "noticias" to "Notícias",
            "musica" to "Música",
            "outros" to "Outros",
        )

        val channelBuckets = linkedMapOf<String, MutableList<LiveChannel>>()
        for (ch in rawChannels) {
            if (isJunkChannel(ch.name)) continue
            val rawCatName = idToRawName[ch.categoryId ?: ""] ?: (ch.categoryId ?: "")
            val b = bucket(rawCatName, ch.categoryId ?: "", ch.name) ?: "outros"
            if (b == "000") continue
            channelBuckets.getOrPut(b) { mutableListOf() }.add(ch.copy(categoryId = b))
        }

        val order = listOf(
            "abertos", "esportes", "filmes", "series", "desenhos", "anime",
            "discovery", "docs", "noticias", "musica", "outros",
        )
        val categories = mutableListOf<LiveCategory>()
        for (key in order) {
            if (channelBuckets[key].orEmpty().isNotEmpty()) {
                categories.add(LiveCategory(key, label[key] ?: key))
            }
        }

        val channels = order.flatMap { channelBuckets[it].orEmpty() }
        return Cleaned(categories, channels)
    }
}
