package com.streamflixvip.app.data

import android.content.Context

/**
 * Cache da ultima fonte tocada por titulo/EP.
 * Continuar assistindo usa isso para ir ao player sem abrir a ficha.
 */
object ResumePlaybackCache {
    data class Hit(
        val url: String,
        val isDirect: Boolean,
        val label: String? = null,
    )

    private const val PREFS = "resume_playback_cache"
    private val memory = LinkedHashMap<String, Hit>(24, 0.75f, true)
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun key(tmdbId: Int, mediaType: String, season: Int, episode: Int): String =
        "$tmdbId|$mediaType|$season|$episode"

    fun put(tmdbId: Int, mediaType: String, season: Int, episode: Int, url: String, isDirect: Boolean, label: String? = null) {
        if (url.isBlank()) return
        val hit = Hit(url = url, isDirect = isDirect, label = label)
        val k = key(tmdbId, mediaType, season, episode)
        synchronized(memory) {
            memory[k] = hit
            if (memory.size > 40) {
                val first = memory.keys.first()
                memory.remove(first)
            }
        }
        prefs?.edit()?.putString(k, "${if (isDirect) 1 else 0}|$url")?.apply()
    }

    fun get(tmdbId: Int, mediaType: String, season: Int, episode: Int): Hit? {
        val k = key(tmdbId, mediaType, season, episode)
        synchronized(memory) {
            memory[k]?.let { return it }
        }
        val raw = prefs?.getString(k, null) ?: return null
        val pipe = raw.indexOf('|')
        if (pipe <= 0) return null
        val direct = raw.substring(0, pipe) == "1"
        val url = raw.substring(pipe + 1)
        if (url.isBlank()) return null
        val hit = Hit(url = url, isDirect = direct)
        synchronized(memory) { memory[k] = hit }
        return hit
    }
}
