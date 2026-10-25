package com.streamflixvip.tv.ui.livetv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixvip.tv.data.RecentStore
import com.streamflixvip.tv.network.LiveCategory
import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.network.LiveTvResponse
import com.streamflixvip.tv.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

const val CAT_ALL = "all"
const val CAT_FAV = "__fav"
const val CAT_RECENT = "__recent"

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val selectedCategoryId: String = CAT_ALL,
    val query: String = "",
    val favoriteIds: Set<String> = emptySet(),
    val recentIds: List<String> = emptyList(),
    val epgNow: Map<String, String> = emptyMap(),
    val epgNext: Map<String, String> = emptyMap(),
    val kidsMode: Boolean = false,
) {
    /** Canais do filtro atual (categoria + busca). */
    val visibleChannels: List<LiveChannel>
        get() {
            var list = when (selectedCategoryId) {
                CAT_ALL -> channels
                CAT_FAV -> channels.filter { it.id in favoriteIds }
                CAT_RECENT -> recentIds.mapNotNull { id -> channels.firstOrNull { it.id == id } }
                else -> channels.filter { it.categoryId == selectedCategoryId }
            }
            val q = normalize(query)
            if (q.isNotEmpty()) list = list.filter { normalize(it.name).contains(q) }
            return list
        }

    fun countFor(catId: String): Int = when (catId) {
        CAT_ALL -> channels.size
        CAT_FAV -> channels.count { it.id in favoriteIds }
        CAT_RECENT -> recentIds.count { id -> channels.any { it.id == id } }
        else -> channels.count { it.categoryId == catId }
    }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
    }
}

class LiveTvViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val recent = RecentStore(app)
    private val cacheFile = File(app.cacheDir, "live_tv_v2.json") // v2: categorias novas do servidor
    private val adapter = NetworkModule.moshi.adapter(LiveTvResponse::class.java)
    private var raw: LiveTvResponse? = null
    private var loaded = false

    fun start(kidsMode: Boolean) {
        val ctx = getApplication<Application>()
        _uiState.update {
            it.copy(
                favoriteIds = LiveTvFavoritesStore.getIds(ctx),
                recentIds = recent.liveChannels(),
            )
        }
        if (loaded && _uiState.value.kidsMode == kidsMode) return
        _uiState.update { it.copy(kidsMode = kidsMode) }
        if (loaded) {
            raw?.let { apply(it) }
            return
        }
        loaded = true
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.channels.isEmpty(), error = null) }
            val cached = withContext(Dispatchers.IO) {
                runCatching { if (cacheFile.exists()) adapter.fromJson(cacheFile.readText()) else null }.getOrNull()
            }
            if (cached != null && _uiState.value.channels.isEmpty()) apply(cached)
            runCatching { NetworkModule.liveTvApi.getLiveTv() }
                .onSuccess { res ->
                    // lista mudou no servidor (fonte apagada no admin etc.): troca tudo e regrava o cache
                    val same = res.version != null && res.version == raw?.version && _uiState.value.channels.isNotEmpty()
                    if (!same) {
                        apply(res)
                        withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(adapter.toJson(res)) } }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    loadEpg()
                }
                .onFailure {
                    _uiState.update { st ->
                        st.copy(
                            isLoading = false,
                            error = if (st.channels.isEmpty()) "Não foi possível carregar os canais. Verifique sua internet." else null,
                        )
                    }
                    if (_uiState.value.channels.isNotEmpty()) loadEpg()
                }
        }
    }

    private fun apply(res: LiveTvResponse) {
        raw = res
        val kids = _uiState.value.kidsMode
        val cats = res.categories.filter { !isAdult(it.id, it.name) && it.id != CAT_ALL }
        val adultCatIds = res.categories.filter { isAdult(it.id, it.name) }.map { it.id }.toSet()
        var chans = res.channels.filter { ch ->
            ch.streams.isNotEmpty() && ch.categoryId !in adultCatIds && !isAdult(ch.categoryId, ch.name)
        }
        val catsUsed = if (kids) {
            val kidsCatIds = cats.filter { isKidsName(it.name) }.map { it.id }.toSet()
            chans = chans.filter { it.categoryId in kidsCatIds || isKidsName(it.name) }
            cats.filter { c -> chans.any { it.categoryId == c.id } }
        } else cats
        // ordem vem pronta do servidor (Canais, Regionais, Capitais, HBO, Telecine...)
        val sortedCats = catsUsed
        _uiState.update {
            it.copy(
                isLoading = false,
                categories = sortedCats,
                channels = chans,
                error = if (chans.isEmpty()) (if (kids) "Nenhum canal infantil encontrado." else "Nenhum canal disponível agora.") else null,
            )
        }
    }

    private fun loadEpg() {
        viewModelScope.launch {
            runCatching { NetworkModule.liveTvApi.getLiveEpg() }.onSuccess { res ->
                val byKey = HashMap<String, Pair<String?, String?>>()
                for (p in res.programmes) {
                    val key = epgKey(p.name)
                    if (key.length >= 2) byKey[key] = p.now to p.next
                }
                val chans = _uiState.value.channels
                val (now, next) = withContext(Dispatchers.Default) {
                    val n = HashMap<String, String>()
                    val x = HashMap<String, String>()
                    for (ch in chans) {
                        val hit = matchEpg(ch.name, byKey) ?: continue
                        hit.first?.takeIf { it.isNotBlank() }?.let { n[ch.id] = it }
                        hit.second?.takeIf { it.isNotBlank() }?.let { x[ch.id] = it }
                    }
                    n to x
                }
                _uiState.update { it.copy(epgNow = now, epgNext = next) }
            }
        }
    }

    fun selectCategory(id: String) = _uiState.update { it.copy(selectedCategoryId = id) }

    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }

    fun toggleFavorite(channel: LiveChannel): Boolean {
        val ctx = getApplication<Application>()
        val fav = LiveTvFavoritesStore.toggle(ctx, channel.id)
        _uiState.update { it.copy(favoriteIds = LiveTvFavoritesStore.getIds(ctx)) }
        return fav
    }

    fun refreshRecents() = _uiState.update { it.copy(recentIds = recent.liveChannels()) }

    companion object {
        fun isAdult(id: String?, name: String?): Boolean {
            val n = (name ?: "").lowercase()
            if (id == "000" || id == "00") return true
            return listOf("adult", "xxx", "porn", "erotic", "onlyfans", "+18", "18+", "adulto", "sexy").any { n.contains(it) }
        }

        fun isKidsName(name: String): Boolean {
            val n = LiveTvUiState.normalize(name)
            return listOf(
                "infantil", "kids", "desenho", "cartoon", "gloob", "nick", "disney", "baby", "tooncast",
                "cartoonito", "zoomoo", "boomerang", "discovery kids", "mundo bita", "kids", "animacao",
            ).any { n.contains(it) }
        }

        private fun epgKey(name: String): String =
            LiveTvUiState.normalize(name)
                .replace(Regex("""\b(hd|fhd|sd|4k|uhd|h264|h265|hevc|hdr)\b"""), " ")
                .replace(Regex("""[^a-z0-9+ ]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        private fun matchEpg(channelName: String, byKey: Map<String, Pair<String?, String?>>): Pair<String?, String?>? {
            val key = epgKey(channelName)
            if (key.isBlank()) return null
            byKey[key]?.let { return it }
            val compact = key.replace(" ", "")
            byKey.entries.firstOrNull { it.key.replace(" ", "") == compact }?.value?.let { return it }
            val parts = key.split(" ").filter { it.length > 2 }
            if (parts.isEmpty()) return null
            return byKey.entries.mapNotNull { e ->
                val k = e.key
                val score = when {
                    k.startsWith(key) || key.startsWith(k) -> 80
                    k.contains(key) || key.contains(k) -> 60
                    parts.count { k.contains(it) } >= 2 -> 40
                    else -> 0
                }
                if (score > 0) score to e.value else null
            }.maxByOrNull { it.first }?.second
        }
    }
}
