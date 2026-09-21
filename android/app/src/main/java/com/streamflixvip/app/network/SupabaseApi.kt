package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApi {

    @GET("rest/v1/vip_sources")
    suspend fun getSourcesForMovie(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("is_active") isActiveFilter: String = "eq.true",
        @Query("season") seasonFilter: String = "is.null",
        @Query("select") select: String = "source_url,source_label,priority",
        @Query("order") order: String = "priority.desc",
    ): List<VipSource>

    @GET("rest/v1/vip_sources")
    suspend fun getSourcesForEpisode(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String = "eq.tv",
        @Query("season") seasonFilter: String,
        @Query("episode") episodeFilter: String,
        @Query("is_active") isActiveFilter: String = "eq.true",
        @Query("select") select: String = "source_url,source_label,priority",
        @Query("order") order: String = "priority.desc",
    ): List<VipSource>

    @GET("rest/v1/vip_titles")
    suspend fun getVipTitleConfig(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("select") select: String = "vip_lock,vip_free_episode_limit",
    ): List<VipTitleConfig>
}

@JsonClass(generateAdapter = true)
data class VipTitleConfig(
    val vip_lock: Boolean? = null,
    val vip_free_episode_limit: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VipSource(
    val source_url: String,
    val source_label: String?,
    val priority: Int?,
) {
    val displayName: String get() = source_label ?: "Servidor"

    val isDirectPlayable: Boolean
        get() {
            val path = source_url.lowercase().substringBefore("?").substringBefore("#")
            val lower = source_url.lowercase()
            return path.endsWith(".m3u8") ||
                path.endsWith(".mp4") ||
                path.endsWith(".mkv") ||
                path.endsWith(".webm") ||
                path.endsWith(".m4v") ||
                path.endsWith(".mov") ||
                path.endsWith(".ts") ||
                path.endsWith(".m2ts") ||
                lower.contains("/stream-proxy") ||
                lower.contains("tb-cdn") ||
                lower.contains("torbox.app") ||
                lower.contains("/dld/")
        }

    fun resolvedPlaybackUrl(apiBaseUrl: String): String {
        if (source_url.contains("/stream-proxy")) {
            return source_url
        }
        val isIptv = source_url.contains("/movie/") ||
                     source_url.contains("/series/") ||
                     source_url.contains("/live/")

        if (isIptv || source_url.startsWith("https://", ignoreCase = true)) {
            return source_url
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return "${apiBaseUrl}api/stream-proxy?url=$encoded"
    }

    fun candidatePlaybackUrls(koyebBaseUrl: String, zeaburBaseUrl: String): List<String> {
        if (source_url.contains("/stream-proxy")) {
            return listOf(source_url)
        }
        val isIptv = source_url.contains("/movie/") ||
                     source_url.contains("/series/") ||
                     source_url.contains("/live/")

        if (isIptv || source_url.startsWith("https://", ignoreCase = true)) {
            return listOf(source_url)
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return listOf(
            "${koyebBaseUrl}api/stream-proxy?url=$encoded",
            "${zeaburBaseUrl}api/stream-proxy?url=$encoded",
        )
    }
}

object PostgrestFilter {
    fun eq(value: Any) = "eq.$value"
}

fun requiresVip(config: VipTitleConfig?, episodeNumber: Int?): Boolean {
    if (config == null) return false
    if (config.vip_lock == true) return true
    val limit = config.vip_free_episode_limit
    if (limit != null && episodeNumber != null) {
        return episodeNumber > limit
    }
    return false
}
