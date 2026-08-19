package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET

@JsonClass(generateAdapter = true)
data class LiveStreamOption(
    val url: String,
    val label: String? = null,
    val priority: Int? = null,
    /** SD | HD | FHD | 4K — vindo da API live-tv */
    val quality: String? = null,
    /** true = versão legendada */
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

interface LiveTvApi {
    @GET("api/live-tv")
    suspend fun getLiveTv(): LiveTvResponse
}
