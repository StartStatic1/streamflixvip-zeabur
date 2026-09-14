package com.streamflixvip.app.ui.livetv

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.app.network.LiveCategory
import com.streamflixvip.app.network.LiveChannel
import com.streamflixvip.app.network.LiveStreamOption
import com.streamflixvip.app.network.NetworkModule
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException

enum class LiveTvTab { CHANNELS, FAVORITES }

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val epgLoading: Boolean = false,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val tagsByChannel: Map<String, Set<String>> = emptyMap(),
    val selectedCategoryId: String = "abertos",
    val searchQuery: String = "",
    val sourcesUsed: Int = 0,
    val error: String? = null,
    val vipRequiredByServer: Boolean = false,
    val tab: LiveTvTab = LiveTvTab.CHANNELS,
    val favoriteIds: Set<String> = emptySet(),
    val selectedChannelId: String? = null,
    val epgNowById: Map<String, String> = emptyMap(),
    val epgNextById: Map<String, String> = emptyMap(),
) {
    val selectedChannel: LiveChannel?
        get() = selectedChannelId?.let { id -> channels.find { it.id == id } }

    val filteredChannels: List<LiveChannel>
        get() {
            val q = normalize(searchQuery)
            var list = channels

            if (tab == LiveTvTab.FAVORITES) {
                list = list.filter { favoriteIds.contains(it.id) }
            } else if (selectedCategoryId != "all") {
                list = list.filter { ch ->
                    tagsByChannel[ch.id]?.contains(selectedCategoryId) == true ||
                        ch.categoryId == selectedCategoryId
                }
            }

            if (q.isNotEmpty()) {
                list = list.filter { normalize(it.name).contains(q) }
            }
            return list
        }

    fun categoryLabel(channel: LiveChannel): String {
        val id = channel.categoryId ?: tagsByChannel[channel.id]?.firstOrNull()
        return categories.firstOrNull { it.id == id }?.name
            ?: labels[id]
            ?: "Ao vivo"
    }

    fun nowTitle(channel: LiveChannel): String =
        epgNowById[channel.id]?.takeIf { it.isNotBlank() } ?: categoryLabel(channel)

    fun nextTitle(channel: LiveChannel): String? =
        epgNextById[channel.id]?.takeIf { it.isNotBlank() }

    companion object {
        val labels = mapOf(
            "all" to "Todos",
            "abertos" to "Abertos",
            "portugal" to "Portugal",
            "esportes" to "Esportes",
            "noticias" to "Noticias",
            "filmes" to "Filmes",
            "telecine" to "Telecine",
            "hbo" to "HBO",
            "premiere" to "Premiere",
            "series" to "Series",
            "desenhos" to "Desenhos",
            "anime" to "Anime",
            "discovery" to "Discovery",
            "docs" to "Docs",
            "musica" to "Musica",
            "outros" to "Outros",
        )

        fun normalize(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")
    }
}

class LiveTvViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("live_tv_cache_v2", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        LiveTvUiState(favoriteIds = LiveTvFavoritesStore.getIds(app)),
    )
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        val cached = readCache()
        if (cached != null && cached.channels.isNotEmpty()) {
            val favs = LiveTvFavoritesStore.getIds(app)
            val defaultCat = pickDefaultCategory(cached.categories)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    epgLoading = true,
                    categories = cached.categories,
                    channels = cached.channels,
                    tagsByChannel = cached.tags,
                    selectedCategoryId = defaultCat,
                    favoriteIds = favs,
                    selectedChannelId = it.selectedChannelId
                        ?: cached.channels.firstOrNull { ch -> favs.contains(ch.id) }?.id
                        ?: cached.channels.firstOrNull { ch ->
                            cached.tags[ch.id]?.contains(defaultCat) == true
                        }?.id
                        ?: cached.channels.firstOrNull()?.id,
                    error = null,
                )
            }
            loadEpg()
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val hasList = _uiState.value.channels.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !hasList,
                    error = if (hasList) null else it.error,
                    vipRequiredByServer = false,
                )
            }
            runCatching {
                NetworkModule.liveTvApi.getLiveTv()
            }.onSuccess { response ->
                val cleaned = cleanCategoriesAndChannels(response.categories, response.channels)
                persistCache(cleaned)
                val favs = LiveTvFavoritesStore.getIds(getApplication())
                val defaultCat = pickDefaultCategory(cleaned.categories)
                val currentId = _uiState.value.selectedChannelId
                val autoSelect = currentId?.takeIf { id -> cleaned.channels.any { it.id == id } }
                    ?: cleaned.channels.firstOrNull { favs.contains(it.id) }?.id
                    ?: cleaned.channels.firstOrNull {
                        cleaned.tags[it.id]?.contains(defaultCat) == true
                    }?.id
                    ?: cleaned.channels.firstOrNull()?.id

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = cleaned.categories,
                        channels = cleaned.channels,
                        tagsByChannel = cleaned.tags,
                        sourcesUsed = response.sourcesUsed,
                        favoriteIds = favs,
                        selectedCategoryId = if (
                            cleaned.categories.any { c -> c.id == it.selectedCategoryId }
                        ) it.selectedCategoryId else defaultCat,
                        selectedChannelId = autoSelect,
                        vipRequiredByServer = false,
                        error = if (cleaned.channels.isEmpty()) {
                            "Nenhum canal disponivel no momento."
                        } else null,
                    )
                }
                loadEpg()
            }.onFailure { e ->
                val isVipBlocked = (e as? HttpException)?.code() == 403
                if (_uiState.value.channels.isNotEmpty() && !isVipBlocked) {
                    _uiState.update { it.copy(isLoading = false, epgLoading = false) }
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        epgLoading = false,
                        channels = if (isVipBlocked) emptyList() else it.channels,
                        categories = if (isVipBlocked) emptyList() else it.categories,
                        sourcesUsed = it.sourcesUsed,
                        vipRequiredByServer = isVipBlocked,
                        error = if (isVipBlocked) {
                            "VIP necessario para assistir TV ao vivo."
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
            it.copy(selectedCategoryId = id, searchQuery = "", tab = LiveTvTab.CHANNELS)
        }
    }

    fun setSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
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

    private data class Cleaned(
        val categories: List<LiveCategory>,
        val channels: List<LiveChannel>,
        val tags: Map<String, Set<String>>,
    )

    private fun pickDefaultCategory(categories: List<LiveCategory>): String {
        return categories.firstOrNull { it.id == "abertos" }?.id
            ?: categories.firstOrNull { it.id == "portugal" }?.id
            ?: categories.firstOrNull { it.id != "all" }?.id
            ?: "abertos"
    }

    private fun isJunkChannel(name: String): Boolean {
        val n = LiveTvUiState.normalize(name)
        if (n.contains("24h") || n.contains("24 h") || n.contains("24hrs") ||
            n.contains("24 horas") || n.contains("lendas do cinema") ||
            n.contains("lendas cinema")
        ) return true
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

    private fun isMaxNotHbo(channelName: String, rawName: String = ""): Boolean {
        val ch = LiveTvUiState.normalize(channelName)
        if (ch.contains("cinemax")) return false
        if (Regex("""\\bhbo\\s*max\\b""").containsMatchIn(ch)) return true
        if (Regex("""\\bmax\\b""").containsMatchIn(ch) && !Regex("""\\bhbo\\b""").containsMatchIn(ch)) return true
        val cat = LiveTvUiState.normalize(rawName)
        if (Regex("""\\bmax\\b""").containsMatchIn(ch) && cat.contains("hbo")) return true
        return false
    }

    private fun isClassicHbo(channelName: String, rawName: String): Boolean {
        if (isMaxNotHbo(channelName, rawName)) return false
        val ch = LiveTvUiState.normalize(channelName)
        if (ch.contains("hbo")) return true
        val cat = LiveTvUiState.normalize(rawName)
        if (cat.contains("hbo") && !Regex("""\\bmax\\b""").containsMatchIn(ch)) return true
        return false
    }

    private fun isPortugalChannel(rawName: String, rawId: String, channelName: String): Boolean {
        val n = LiveTvUiState.normalize("$rawName $rawId $channelName")
        if (n.contains("portugal") || n.contains("porto canal")) return true
        if (n.contains("benfica") || n.contains("cmtv") || n.contains("canal 11") || n.contains("canal11")) return true
        if (Regex("""\\brtc\\b""").containsMatchIn(n) || n.contains("rtp1") || n.contains("rtp 1") ||
            n.contains("rtp2") || n.contains("rtp 2") || n.contains("rtp3") || n.contains("rtp 3")
        ) return true
        if (Regex("""\\bsic\\b""").containsMatchIn(n) || n.contains("sic noticias") || n.contains("sic radical")) return true
        if (Regex("""\\btvi\\b""").containsMatchIn(n) || n.contains("tvi 24") || n.contains("tvi24")) return true
        if (n.contains("sport tv") || (n.contains("sporttv") && n.contains("port"))) return true
        return false
    }

    private fun isSportChannel(rawName: String, rawId: String, channelName: String): Boolean {
        val n = LiveTvUiState.normalize("$rawName $rawId $channelName")
        return n.contains("esporte") || n.contains("sport") || n.contains("futebol") ||
            n.contains("sportv") || n.contains("combate") ||
            n.contains("band sports") || n.contains("espn") ||
            n.contains("benfica") || n.contains("porto canal")
    }

    private fun bucket(rawName: String, rawId: String, channelName: String): String? {
        val n = LiveTvUiState.normalize("$rawName $rawId $channelName")
        if (n.contains("adult") || n.contains("xxx") || n.contains("porn") ||
            n == "000" || n == "00" || n.contains("+18") || n.contains("18+")
        ) return "000"

        if (isPortugalChannel(rawName, rawId, channelName)) return "portugal"

        if (n.contains("telecine")) return "telecine"
        if (isMaxNotHbo(channelName, rawName)) return "filmes"
        if (isClassicHbo(channelName, rawName)) return "hbo"
        if (n.contains("premiere")) return "premiere"

        if (n.contains("anime") || n.contains("otaku")) return "anime"
        if (n.contains("discovery") || n.contains("animal planet") ||
            n.contains("nat geo") || n.contains("national geographic") ||
            n.contains("history channel") || n.contains("history 2") ||
            n.contains("h&h") || n.contains("home health") ||
            n.contains("investigation discovery")
        ) return "discovery"
        if (n.contains("cartoon") || n.contains("desenh") || n.contains("infantil") ||
            n.contains("kids") || n.contains("gloob") || n.contains("nick") ||
            n.contains("disney channel") || n.contains("disney jr") ||
            n.contains("baby tv") || n.contains("tooncast") || n.contains("cartoonito") ||
            n.contains("zoomoo") || n.contains("boomerang")
        ) return "desenhos"
        if (n.contains("esporte") || n.contains("sport") || n.contains("futebol") ||
            n.contains("sportv") || n.contains("combate") ||
            n.contains("band sports") || n.contains("espn")
        ) return "esportes"
        if (n.contains("megapix") || n.contains("cinemax") || n.contains("tcm") ||
            n.contains("space ") || n.contains("filme") || n.contains("cinema")
        ) return "filmes"
        if (n.contains("serie") || n.contains("series") || n.contains("novela") ||
            n.contains("fx ") || n.contains("axn") || n.contains("sony") ||
            n.contains("warner") || n.contains("universal tv") || n.contains("star channel")
        ) return "series"
        if (n.contains("noticia") || n.contains("news") || n.contains("jornal") ||
            n.contains("cnn") || n.contains("band news") || n.contains("globo news") ||
            n.contains("record news") || n.contains("jovem pan")
        ) return "noticias"
        if (n.contains("document") || n.contains("doc ") || n.contains("docs")) return "docs"
        if (n.contains("aberto") || n.contains("abertos") || n.contains("globo") ||
            n.contains("sbt") || n.contains("record") || n.contains("band") ||
            n.contains("cultura") || n.contains("tv aberta") || n.contains("rede tv") ||
            n.contains("gazeta") || n.contains("tv brasil")
        ) return "abertos"
        if (n.contains("musica") || n.contains("music") || n.contains("mtv") ||
            n.contains("music box")
        ) return "musica"
        return null
    }

    private fun cleanCategoriesAndChannels(
        rawCats: List<LiveCategory>,
        rawChannels: List<LiveChannel>,
    ): Cleaned {
        val idToRawName = rawCats.associate { it.id to (it.name ?: it.id) }
        val seen = linkedMapOf<String, LiveChannel>()
        val tags = linkedMapOf<String, MutableSet<String>>()

        for (ch in rawChannels) {
            if (isJunkChannel(ch.name)) continue
            val rawCatName = idToRawName[ch.categoryId ?: ""] ?: (ch.categoryId ?: "")
            val b = bucket(rawCatName, ch.categoryId ?: "", ch.name) ?: "outros"
            if (b == "000") continue
            if (seen[ch.id] == null) {
                seen[ch.id] = ch.copy(categoryId = b)
            }
            val set = tags.getOrPut(ch.id) { mutableSetOf() }
            if (b == "hbo" && isMaxNotHbo(ch.name, rawCatName)) {
                set.remove("hbo")
                set.add("filmes")
                if (seen[ch.id]?.categoryId == "hbo") {
                    seen[ch.id] = seen[ch.id]!!.copy(categoryId = "filmes")
                }
            } else {
                set.add(b)
            }
            if (isPortugalChannel(rawCatName, ch.categoryId ?: "", ch.name)) set.add("portugal")
            if (isSportChannel(rawCatName, ch.categoryId ?: "", ch.name)) set.add("esportes")
        }

        val order = listOf(
            "all", "abertos", "portugal", "esportes", "noticias", "filmes",
            "telecine", "hbo", "premiere", "series", "desenhos", "anime",
            "discovery", "docs", "musica", "outros",
        )
        val channels = seen.values.toList()
        val categories = mutableListOf<LiveCategory>()
        for (key in order) {
            val has = if (key == "all") channels.isNotEmpty() else tags.values.any { it.contains(key) }
            if (has) categories.add(LiveCategory(key, LiveTvUiState.labels[key] ?: key))
        }
        return Cleaned(categories, channels, tags)
    }

    private fun epgKey(name: String): String =
        LiveTvUiState.normalize(name)
            .replace(Regex("""\\b(hd|fhd|sd|4k|uhd|h264|h265|hevc|hdr)\\b"""), " ")
            .replace(Regex("""\\s+"""), " ")
            .trim()

    private fun loadEpg() {
        viewModelScope.launch {
            val cachedEpg = readEpgCache()
            if (cachedEpg != null) {
                applyEpg(cachedEpg.first, cachedEpg.second, loading = true)
            } else {
                _uiState.update { it.copy(epgLoading = true) }
            }
            runCatching {
                NetworkModule.liveTvApi.getLiveEpg()
            }.onSuccess { res ->
                val now = mutableMapOf<String, String>()
                val next = mutableMapOf<String, String>()
                val byKey = LinkedHashMap<String, Pair<String?, String?>>()
                for (p in res.programmes) {
                    val key = epgKey(p.name)
                    if (key.length < 2) continue
                    byKey[key] = p.now to p.next
                }
                for (ch in _uiState.value.channels) {
                    val hit = matchEpg(ch.name, byKey) ?: continue
                    hit.first?.takeIf { it.isNotBlank() }?.let { now[ch.id] = it }
                    hit.second?.takeIf { it.isNotBlank() }?.let { next[ch.id] = it }
                }
                persistEpgCache(now, next)
                applyEpg(now, next, loading = false)
            }.onFailure {
                _uiState.update { st -> st.copy(epgLoading = false) }
            }
        }
    }

    private fun matchEpg(
        channelName: String,
        byKey: Map<String, Pair<String?, String?>>,
    ): Pair<String?, String?>? {
        val key = epgKey(channelName)
        byKey[key]?.let { return it }
        val compact = key.replace(" ", "")
        byKey.entries.firstOrNull { it.key.replace(" ", "") == compact }?.value?.let { return it }
        val parts = key.split(" ").filter { it.length > 2 }
        if (parts.isEmpty()) return null
        val scored = byKey.entries.mapNotNull { e ->
            val k = e.key
            val score = when {
                k == key -> 100
                k.startsWith(key) || key.startsWith(k) -> 80
                k.contains(key) || key.contains(k) -> 60
                parts.count { k.contains(it) } >= 2 -> 40
                else -> 0
            }
            if (score > 0) score to e.value else null
        }
        return scored.maxByOrNull { it.first }?.second
    }

    private fun applyEpg(now: Map<String, String>, next: Map<String, String>, loading: Boolean) {
        _uiState.update { it.copy(epgNowById = now, epgNextById = next, epgLoading = loading) }
    }

    private fun persistEpgCache(now: Map<String, String>, next: Map<String, String>) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fun toObj(src: Map<String, String>): JSONObject {
                    val o = JSONObject()
                    src.forEach { (k, v) -> o.put(k, v) }
                    return o
                }
                val obj = JSONObject()
                obj.put("now", toObj(now))
                obj.put("next", toObj(next))
                prefs.edit().putString("epg", obj.toString()).apply()
            }
        }
    }

    private fun readEpgCache(): Pair<Map<String, String>, Map<String, String>>? {
        val raw = prefs.getString("epg", null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            fun mapOf(key: String): Map<String, String> {
                val o = obj.optJSONObject(key) ?: return emptyMap()
                val out = mutableMapOf<String, String>()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = o.optString(k)
                    if (v.isNotBlank()) out[k] = v
                }
                return out
            }
            mapOf("now") to mapOf("next")
        }.getOrNull()
    }

    private fun persistCache(cleaned: Cleaned) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray()
                cleaned.channels.take(500).forEach { ch ->
                    val obj = JSONObject()
                    obj.put("id", ch.id)
                    obj.put("name", ch.name)
                    obj.put("logo", ch.logo ?: "")
                    obj.put("categoryId", ch.categoryId ?: "")
                    val streams = JSONArray()
                    ch.streams.take(3).forEach { s ->
                        streams.put(
                            JSONObject()
                                .put("url", s.url)
                                .put("label", s.label ?: "")
                                .put("priority", s.priority ?: 0)
                                .put("quality", s.quality ?: ""),
                        )
                    }
                    obj.put("streams", streams)
                    obj.put("tags", JSONArray(cleaned.tags[ch.id]?.toList().orEmpty()))
                    arr.put(obj)
                }
                prefs.edit().putString("channels", arr.toString()).apply()
            }
        }
    }

    private fun readCache(): Cleaned? {
        val raw = prefs.getString("channels", null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            val channels = mutableListOf<LiveChannel>()
            val tags = linkedMapOf<String, Set<String>>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val streamsArr = o.optJSONArray("streams") ?: JSONArray()
                val streams = buildList {
                    for (s in 0 until streamsArr.length()) {
                        val so = streamsArr.getJSONObject(s)
                        add(
                            LiveStreamOption(
                                url = so.optString("url"),
                                label = so.optString("label").ifBlank { null },
                                priority = so.optInt("priority"),
                                quality = so.optString("quality").ifBlank { null },
                            ),
                        )
                    }
                }
                val id = o.getString("id")
                channels.add(
                    LiveChannel(
                        id = id,
                        name = o.optString("name"),
                        logo = o.optString("logo").ifBlank { null },
                        categoryId = o.optString("categoryId").ifBlank { null },
                        streams = streams,
                    ),
                )
                val tArr = o.optJSONArray("tags")
                val set = mutableSetOf<String>()
                if (tArr != null) {
                    for (t in 0 until tArr.length()) set.add(tArr.getString(t))
                }
                o.optString("categoryId").takeIf { it.isNotBlank() }?.let { set.add(it) }
                tags[id] = set
            }
            val order = listOf(
                "all", "abertos", "portugal", "esportes", "noticias", "filmes",
                "telecine", "hbo", "premiere", "series", "desenhos", "anime",
                "discovery", "docs", "musica", "outros",
            )
            val categories = order.filter { key ->
                key == "all" && channels.isNotEmpty() || tags.values.any { it.contains(key) }
            }.map { LiveCategory(it, LiveTvUiState.labels[it] ?: it) }
            Cleaned(categories, channels, tags)
        }.getOrNull()
    }
}
