package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class MediaSourcesResponse(
    val sources: List<VipSource> = emptyList(),
    val vipConfig: VipTitleConfig? = null,
    val requiresVip: Boolean = false,
    val isVip: Boolean = false,
    val error: String? = null,
    val code: String? = null,
)

interface MediaSourcesApi {
    @GET("api/media-sources")
    suspend fun getMovieSources(
        @Query("tmdb_id") tmdbId: Int,
        @Query("type") type: String = "movie",
    ): MediaSourcesResponse

    @GET("api/media-sources")
    suspend fun getEpisodeSources(
        @Query("tmdb_id") tmdbId: Int,
        @Query("type") type: String = "tv",
        @Query("season") season: Int,
        @Query("episode") episode: Int,
    ): MediaSourcesResponse
}
