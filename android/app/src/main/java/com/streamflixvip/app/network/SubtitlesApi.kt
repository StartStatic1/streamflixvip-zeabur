package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Legendas externas via /api/subtitles (OpenSubtitles no backend).
 * A API key fica só no servidor — o app nunca a vê.
 * Preferência: pt-BR / pob. Fallback Stremio traz url direta.
 */
interface SubtitlesApi {
    @GET("api/subtitles")
    suspend fun search(
        @Query("action") action: String = "search",
        @Query("tmdb_id") tmdbId: Int,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
        @Query("media_type") mediaType: String? = null,
        @Query("imdb_id") imdbId: String? = null,
    ): SubtitleSearchResponse

    @GET("api/subtitles")
    suspend fun download(
        @Query("action") action: String = "download",
        @Query("file_id") fileId: Long? = null,
        @Query("url") url: String? = null,
        @Query("tmdb_id") tmdbId: Int,
        @Query("media_type") mediaType: String,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): SubtitleDownloadResponse
}

@JsonClass(generateAdapter = true)
data class SubtitleSearchResponse(
    val results: List<SubtitleSearchItem> = emptyList(),
    val error: String? = null,
    val prefer: String? = null,
)

@JsonClass(generateAdapter = true)
data class SubtitleSearchItem(
    val id: String? = null,
    val release: String? = null,
    val downloads: Int = 0,
    val fps: Double? = null,
    val hd: Boolean = false,
    val file_id: Long? = null,
    /** URL direta (fallback Stremio) — download via action=download&url= */
    val url: String? = null,
    val lang: String? = null,
    val source: String? = null,
)

@JsonClass(generateAdapter = true)
data class SubtitleDownloadResponse(
    val content: String? = null,
    val remaining: Int? = null,
    val from_cache: Boolean = false,
    val error: String? = null,
)
