package com.streamflixvip.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Cores de destaque que o usuario escolhe em Ajustes > Aparencia. */
enum class AccentTheme(val label: String) {
    AURORA("Aurora"),
    RUBI("Rubi"),
    ESMERALDA("Esmeralda"),
    OURO("Ouro"),
    VIOLETA("Violeta"),
}

/** Tela em que o app abre depois do splash. */
enum class StartScreen(val label: String) {
    HOME("Início"),
    LIVE("TV ao vivo"),
    LAST("Última aba usada"),
}

enum class SubSize(val label: String, val sp: Float) {
    SMALL("Pequena", 20f), MEDIUM("Média", 26f), LARGE("Grande", 32f), XL("Enorme", 40f)
}

enum class SubColor(val label: String, val argb: Int) {
    WHITE("Branca", 0xFFFFFFFF.toInt()),
    YELLOW("Amarela", 0xFFFFE14D.toInt()),
    GREEN("Verde", 0xFF7CFC9A.toInt()),
}

enum class SubBackground(val label: String) {
    OUTLINE("Contorno"), SHADOW("Sombra"), BOX("Caixa escura"), NONE("Nenhum")
}

data class TvSettings(
    val accent: AccentTheme = AccentTheme.AURORA,
    val kidsMode: Boolean = false,
    val startScreen: StartScreen = StartScreen.HOME,
    val lastSection: String = "home",
    val autoplayNext: Boolean = true,
    val autoSubtitles: Boolean = true,
    val livePreview: Boolean = true,
    val subSize: SubSize = SubSize.MEDIUM,
    val subColor: SubColor = SubColor.WHITE,
    val subBackground: SubBackground = SubBackground.OUTLINE,
)

/**
 * Preferencias do app de TV (SharedPreferences) expostas como StateFlow
 * para a UI reagir na hora (ex.: trocar a cor do tema repinta tudo).
 */
object TvSettingsStore {
    private const val PREFS = "sfv_tv_settings"
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(TvSettings())
    val state: StateFlow<TvSettings> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _state.value = TvSettings(
            accent = enumOr(p.getString("accent", null), AccentTheme.AURORA),
            kidsMode = p.getBoolean("kids", false),
            startScreen = enumOr(p.getString("start", null), StartScreen.HOME),
            lastSection = p.getString("last_section", "home") ?: "home",
            autoplayNext = p.getBoolean("autoplay_next", true),
            autoSubtitles = p.getBoolean("auto_subs", true),
            livePreview = p.getBoolean("live_preview", true),
            subSize = enumOr(p.getString("sub_size", null), SubSize.MEDIUM),
            subColor = enumOr(p.getString("sub_color", null), SubColor.WHITE),
            subBackground = enumOr(p.getString("sub_bg", null), SubBackground.OUTLINE),
        )
    }

    val current: TvSettings get() = _state.value

    fun update(transform: (TvSettings) -> TvSettings) {
        val next = transform(_state.value)
        _state.value = next
        prefs?.edit()
            ?.putString("accent", next.accent.name)
            ?.putBoolean("kids", next.kidsMode)
            ?.putString("start", next.startScreen.name)
            ?.putString("last_section", next.lastSection)
            ?.putBoolean("autoplay_next", next.autoplayNext)
            ?.putBoolean("auto_subs", next.autoSubtitles)
            ?.putBoolean("live_preview", next.livePreview)
            ?.putString("sub_size", next.subSize.name)
            ?.putString("sub_color", next.subColor.name)
            ?.putString("sub_bg", next.subBackground.name)
            ?.apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}

fun <T : Enum<T>> nextOf(value: T, all: List<T>): T = all[(all.indexOf(value) + 1) % all.size]

/** Listas curtas de "recentes" (buscas, canais, ultimo servidor por titulo). */
class RecentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sfv_tv_recent", Context.MODE_PRIVATE)

    fun searches(): List<String> = readList("searches")
    fun addSearch(q: String) {
        val clean = q.trim()
        if (clean.length < 2) return
        writeList("searches", (listOf(clean) + searches().filterNot { it.equals(clean, true) }).take(8))
    }
    fun clearSearches() = prefs.edit().remove("searches").apply()

    fun liveChannels(): List<String> = readList("live")
    fun addLiveChannel(id: String) {
        writeList("live", (listOf(id) + liveChannels().filterNot { it == id }).take(20))
    }

    fun lastServer(tmdbId: Int): String? = prefs.getString("srv_$tmdbId", null)
    fun setLastServer(tmdbId: Int, label: String) {
        if (tmdbId > 0 && label.isNotBlank()) prefs.edit().putString("srv_$tmdbId", label).apply()
    }

    private fun readList(key: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
