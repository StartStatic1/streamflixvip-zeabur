package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/** Legendas externas via /api/subtitles (mesmo endpoint do app de celular, pt-BR primeiro). */
interface SubtitlesApi {
    @GET("api/subtitles")
    suspend fun search(
        @Query("action") action: String = "search",
        @Query("tmdb_id") tmdbId: Int,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
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
)

@JsonClass(generateAdapter = true)
data class SubtitleSearchItem(
    val id: String? = null,
    val release: String? = null,
    val downloads: Int = 0,
    val hd: Boolean = false,
    val file_id: Long? = null,
    val url: String? = null,
    val lang: String? = null,
    val source: String? = null,
)

@JsonClass(generateAdapter = true)
data class SubtitleDownloadResponse(
    val content: String? = null,
    val error: String? = null,
)
