package com.streamflixvip.tv.network

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
            val lower = source_url.lowercase()
            return lower.endsWith(".m3u8") ||
                lower.endsWith(".mp4") ||
                lower.contains("/stream-proxy")
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

interface WatchProgressApi {

    @retrofit2.http.Headers("Content-Type: application/json", "Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/watch_progress")
    suspend fun upsertProgress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("on_conflict") onConflict: String = "user_id,tmdb_id,media_type,season,episode",
        @retrofit2.http.Body body: WatchProgressUpsert,
    )

    @GET("rest/v1/watch_progress")
    suspend fun getContinueWatching(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("order") order: String = "updated_at.desc",
        @Query("limit") limit: Int = 20,
    ): List<WatchProgressEntry>

    @retrofit2.http.DELETE("rest/v1/watch_progress")
    suspend fun deleteProgress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("season") seasonFilter: String,
        @Query("episode") episodeFilter: String,
    )
}

@JsonClass(generateAdapter = true)
data class WatchProgressUpsert(
    val user_id: String,
    val tmdb_id: Int,
    val media_type: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val poster_path: String?,
    val position_seconds: Int,
    val duration_seconds: Int,
)

@JsonClass(generateAdapter = true)
data class WatchProgressEntry(
    val tmdb_id: Int,
    val media_type: String,
    val season: Int,
    val episode: Int,
    val title: String?,
    val poster_path: String?,
    val position_seconds: Int,
    val duration_seconds: Int,
) {
    val progressFraction: Float
        get() = if (duration_seconds > 0) (position_seconds.toFloat() / duration_seconds).coerceIn(0f, 1f) else 0f

    val displayTitle: String get() = title ?: "Sem título"
}

interface FavoritesApi {

    @retrofit2.http.Headers("Content-Type: application/json", "Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/favorites")
    suspend fun addFavorite(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("on_conflict") onConflict: String = "user_id,tmdb_id,media_type",
        @retrofit2.http.Body body: FavoriteUpsert,
    )

    @GET("rest/v1/favorites")
    suspend fun getFavorites(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
    ): List<FavoriteEntry>

    @GET("rest/v1/favorites")
    suspend fun getFavoriteByTitle(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
    ): List<FavoriteEntry>

    @retrofit2.http.DELETE("rest/v1/favorites")
    suspend fun removeFavorite(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
    )
}

@JsonClass(generateAdapter = true)
data class FavoriteUpsert(
    val user_id: String,
    val tmdb_id: Int,
    val media_type: String,
    val title: String?,
    val poster_path: String?,
    val original_language: String? = null,
)

@JsonClass(generateAdapter = true)
data class FavoriteEntry(
    val tmdb_id: Int,
    val media_type: String,
    val title: String?,
    val poster_path: String?,
    val original_language: String? = null,
) {
    val displayTitle: String get() = title ?: "Sem título"
    val isAnime: Boolean get() = media_type == "tv" && original_language == "ja"
    val isDorama: Boolean get() = media_type == "tv" && original_language == "ko"
}

interface CommentsApi {

    @GET("rest/v1/title_comments")
    suspend fun getComments(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("select") select: String = "id,user_display_name,is_vip_author,comment_text,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
    ): List<TitleComment>

    @retrofit2.http.Headers("Content-Type: application/json", "Prefer: return=minimal")
    @POST("rest/v1/title_comments")
    suspend fun postComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @retrofit2.http.Body body: TitleCommentInsert,
    )
}

@JsonClass(generateAdapter = true)
data class TitleComment(
    val id: Long? = null,
    val user_display_name: String,
    val is_vip_author: Boolean = false,
    val comment_text: String,
    val created_at: String? = null,
)

@JsonClass(generateAdapter = true)
data class TitleCommentInsert(
    val user_id: String,
    val tmdb_id: Int,
    val media_type: String,
    val user_display_name: String,
    val comment_text: String,
)
