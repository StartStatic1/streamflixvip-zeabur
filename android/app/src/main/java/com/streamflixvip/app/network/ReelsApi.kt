package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ReelStory(
    val id: String,
    val title: String? = null,
    val subtitle: String? = null,
    val poster_url: String? = null,
    val genre: String? = null,
    val language: String? = null,
    val vip_only: Boolean? = true,
    val use_addons: Boolean? = true,
    val sort_order: Int? = 0,
)

@JsonClass(generateAdapter = true)
data class ReelEpisode(
    val id: String? = null,
    val episode: Int? = 1,
    val title: String? = null,
    val video_url: String? = null,
    val duration_seconds: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ReelListResponse(
    val stories: List<ReelStory> = emptyList(),
    val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class ReelStoryResponse(
    val story: ReelStory? = null,
    val episodes: List<ReelEpisode> = emptyList(),
    val error: String? = null,
)

interface ReelsApi {
    @GET("api/reels")
    suspend fun listStories(
        @Query("action") action: String = "list",
    ): ReelListResponse

    @GET("api/reels")
    suspend fun getStory(
        @Query("action") action: String = "story",
        @Query("id") id: String,
    ): ReelStoryResponse
}
