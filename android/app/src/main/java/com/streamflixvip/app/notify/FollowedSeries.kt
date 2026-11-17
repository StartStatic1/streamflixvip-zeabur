package com.streamflixvip.app.notify

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Séries que este aparelho "segue": as que estão na Minha Lista (qualquer perfil)
 * e as que alguém assistiu nos últimos 60 dias. É daqui que o aviso de
 * episódio novo sai — sem conta extra nem servidor guardando o que cada um vê.
 */
object FollowedSeries {
    private const val FILE = "sfv_followed_series"
    private const val KEY = "series"
    private const val WATCH_TTL_MS = 60L * 24 * 3600 * 1000
    private const val MAX = 60

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    data class Entry(val tmdbId: Int, val name: String, val poster: String?, val favorite: Boolean, val touchedAt: Long)

    @Synchronized
    private fun readAll(): MutableMap<Int, Entry> {
        val raw = prefs?.getString(KEY, null) ?: return mutableMapOf()
        return runCatching {
            val o = JSONObject(raw)
            val out = mutableMapOf<Int, Entry>()
            o.keys().forEach { k ->
                val e = o.optJSONObject(k) ?: return@forEach
                val id = k.toIntOrNull() ?: return@forEach
                out[id] = Entry(id, e.optString("n"), e.optString("p").ifBlank { null }, e.optBoolean("f"), e.optLong("t"))
            }
            out
        }.getOrDefault(mutableMapOf())
    }

    @Synchronized
    private fun writeAll(map: Map<Int, Entry>) {
        val o = JSONObject()
        map.values.sortedByDescending { it.touchedAt }.take(MAX).forEach { e ->
            o.put(e.tmdbId.toString(), JSONObject().put("n", e.name).put("p", e.poster ?: "").put("f", e.favorite).put("t", e.touchedAt))
        }
        prefs?.edit()?.putString(KEY, o.toString())?.apply()
    }

    /** Assistiu um episódio (ou abriu série no Continuar assistindo). */
    fun watched(tmdbId: Int, name: String?, poster: String?) {
        if (tmdbId <= 0 || prefs == null) return
        val all = readAll()
        val old = all[tmdbId]
        all[tmdbId] = Entry(tmdbId, cleanName(name).ifBlank { old?.name.orEmpty() }, poster ?: old?.poster, old?.favorite ?: false, System.currentTimeMillis())
        writeAll(all)
    }

    /** Lista do perfil carregada: marca as séries favoritas (não desmarca as de outros perfis). */
    fun favorites(items: List<Triple<Int, String?, String?>>) {
        if (prefs == null || items.isEmpty()) return
        val all = readAll()
        val now = System.currentTimeMillis()
        items.forEach { (id, name, poster) ->
            if (id <= 0) return@forEach
            val old = all[id]
            all[id] = Entry(id, cleanName(name).ifBlank { old?.name.orEmpty() }, poster ?: old?.poster, true, maxOf(old?.touchedAt ?: 0L, now - WATCH_TTL_MS / 2))
        }
        writeAll(all)
    }

    fun setFavorite(tmdbId: Int, name: String?, poster: String?, favorite: Boolean) {
        if (tmdbId <= 0 || prefs == null) return
        val all = readAll()
        val old = all[tmdbId]
        if (!favorite && old == null) return
        all[tmdbId] = Entry(tmdbId, cleanName(name).ifBlank { old?.name.orEmpty() }, poster ?: old?.poster, favorite, System.currentTimeMillis())
        writeAll(all)
    }

    /** Ids que valem a pena consultar agora. */
    fun active(): List<Entry> {
        val now = System.currentTimeMillis()
        return readAll().values.filter { it.favorite || now - it.touchedAt < WATCH_TTL_MS }.sortedByDescending { it.touchedAt }
    }

    fun nameOf(tmdbId: Int): String? = readAll()[tmdbId]?.name?.takeIf { it.isNotBlank() }

    private fun cleanName(n: String?): String =
        (n ?: "").replace(Regex("\\s+[—-]\\s+S\\d+E\\d+.*$"), "").trim()
}
