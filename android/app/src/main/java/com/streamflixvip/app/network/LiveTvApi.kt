package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET

@JsonClass(generateAdapter = true)
data class LiveStreamOption(
    val url: String,
    val label: String? = null,
    val priority: Int? = null,
    val quality: String? = null,
    val leg: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class LiveChannel(
    val id: String,
    val name: String,
    val logo: String? = null,
    val categoryId: String? = null,
    val streams: List<LiveStreamOption> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LiveCategory(
    val id: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class LiveTvResponse(
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val sourcesUsed: Int = 0,
)

@JsonClass(generateAdapter = true)
data class LiveEpgProgramme(
    val name: String = "",
    val now: String? = null,
    val next: String? = null,
)

@JsonClass(generateAdapter = true)
data class LiveEpgResponse(
    val programmes: List<LiveEpgProgramme> = emptyList(),
)

interface LiveTvApi {
    @GET("api/live-tv")
    suspend fun getLiveTv(): LiveTvResponse

    @GET("api/live-epg")
    suspend fun getLiveEpg(): LiveEpgResponse
}
