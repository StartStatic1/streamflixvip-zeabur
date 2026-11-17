package com.streamflixvip.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Episódios vistos até o fim neste aparelho (o watch_progress apaga a linha ao
 * passar de 95%, então o "visto" fica guardado aqui para a lista de episódios).
 */
object WatchedEpisodes {
    private var prefs: SharedPreferences? = null
    private const val KEY = "watched"

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("sfv_watched_eps", Context.MODE_PRIVATE)
    }

    private fun scope(): String = com.streamflixvip.app.profiles.ProfileLocalData.localScope() ?: "main"

    @Synchronized
    fun mark(tmdbId: Int, season: Int, episode: Int) {
        val p = prefs ?: return
        val k = "${scope()}:$tmdbId:$season:$episode"
        val list = (p.getString(KEY, "") ?: "").split("\n").filter { it.isNotBlank() && it != k }.takeLast(3000) + k
        p.edit().putString(KEY, list.joinToString("\n")).apply()
    }

    /** "temporada:episódio" vistos desta série no perfil atual. */
    fun forTitle(tmdbId: Int): Set<String> {
        val pre = "${scope()}:$tmdbId:"
        return (prefs?.getString(KEY, "") ?: "").split("\n").filter { it.startsWith(pre) }.map { it.removePrefix(pre) }.toSet()
    }
}

/** Posição para retomar ao tocar "Continuar T1:E3" na tela da série (lida na navegação do player). */
object PendingResume {
    private data class R(val tmdbId: Int, val season: Int, val episode: Int, val seconds: Int, val at: Long)
    @Volatile private var r: R? = null

    fun set(tmdbId: Int, season: Int, episode: Int, seconds: Int) {
        r = R(tmdbId, season, episode, seconds, System.currentTimeMillis())
    }

    fun take(tmdbId: Int, season: Int, episode: Int): Int? {
        val x = r ?: return null
        r = null
        if (x.tmdbId != tmdbId || x.season != season || x.episode != episode) return null
        if (System.currentTimeMillis() - x.at > 3 * 60_000L) return null
        return x.seconds
    }
}
