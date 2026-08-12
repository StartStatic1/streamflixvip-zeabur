package com.streamflixvip.app.network

import com.streamflixvip.app.BuildConfig
import java.net.URLEncoder

/**
 * Capas/backdrops via proxy do nosso servidor.
 * Evita falha quando image.tmdb.org esta bloqueado/instavel na rede do usuario.
 */
object TmdbImages {
    private val base: String
        get() {
            val b = BuildConfig.API_BASE_URL
            return if (b.endsWith("/")) b else "$b/"
        }

    /**
     * @param path poster_path ou backdrop_path da TMDB (ex: "/abc.jpg")
     * @param size w185, w342, w500, w780, original...
     */
    fun url(path: String?, size: String = "w342"): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val p = if (path.startsWith("/")) path else "/$path"
        val encoded = URLEncoder.encode(p, Charsets.UTF_8.name())
        return "${base}api/tmdb-image?size=$size&path=$encoded"
    }

    fun poster(path: String?, size: String = "w342"): String? = url(path, size)
    fun backdrop(path: String?, size: String = "w780"): String? = url(path, size)
    fun still(path: String?, size: String = "w300"): String? = url(path, size)
}
