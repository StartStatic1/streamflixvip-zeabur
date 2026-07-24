package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
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
}

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
