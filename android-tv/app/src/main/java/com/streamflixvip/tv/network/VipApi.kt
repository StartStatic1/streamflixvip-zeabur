package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Headers
import retrofit2.http.POST

interface VipApi {

    @Headers("Content-Type: application/json")
    @POST("api/vip-status")
    suspend fun getVipStatus(
        @Body body: VipStatusRequest,
    ): VipStatusResponse

    @Headers("Content-Type: application/json")
    @POST("api/redeem-vip")
    suspend fun redeemCode(
        @Body body: RedeemVipRequest,
    ): RedeemVipResponse

    @Headers("Content-Type: application/json")
    @POST("api/create-pix")
    suspend fun createPix(
        @Body body: PixRequest,
    ): PixResponse

    @Headers("Content-Type: application/json")
    @POST("api/activate-tv")
    suspend fun activateTv(
        @Body body: ActivateTvRequest,
    ): ActivateTvResponse

    @Headers("Content-Type: application/json")
    @POST("api/tv-status")
    suspend fun getTvStatus(
        @Body body: TvStatusRequest,
    ): TvStatusResponse

    @GET("api/media-sources")
    suspend fun getMediaSources(
        @Query("tmdb_id") tmdbId: Int,
        @Query("type") type: String,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): MediaSourcesResponse
}

@JsonClass(generateAdapter = true)
data class MediaSourcesResponse(
    val sources: List<VipSource> = emptyList(),
    val error: String? = null,
    val code: String? = null,
    val requiresVip: Boolean? = null,
    val isVip: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class VipStatusRequest(val userId: String)

@JsonClass(generateAdapter = true)
data class VipStatusResponse(
    val isVip: Boolean = false,
    val expiresAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class RedeemVipRequest(val userId: String, val code: String)

@JsonClass(generateAdapter = true)
data class RedeemVipResponse(val success: Boolean = false, val message: String = "")

@JsonClass(generateAdapter = true)
data class PixRequest(val userId: String, val value: Double)

@JsonClass(generateAdapter = true)
data class PixResponse(val success: Boolean = false, val qrCode: String? = null, val pixCode: String? = null)

@JsonClass(generateAdapter = true)
data class ActivateTvRequest(val code: String, val deviceId: String, val deviceLabel: String? = null)

@JsonClass(generateAdapter = true)
data class ActivateTvResponse(
    val success: Boolean = false,
    val expiresAt: String? = null,
    val planLabel: String? = null,
    val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class TvStatusRequest(val deviceId: String)

@JsonClass(generateAdapter = true)
data class TvStatusResponse(
    val active: Boolean = false,
    val expiresAt: String? = null,
    val planLabel: String? = null,
)
