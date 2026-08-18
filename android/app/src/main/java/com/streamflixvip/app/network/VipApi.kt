package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interface Retrofit pros endpoints VIP do backend Express — os MESMOS
 * que o site já usa (/api/vip-status e /api/redeem-vip). Nenhuma lógica
 * nova de negócio aqui: hoje, no site, VIP só remove anúncios — não
 * existe bloqueio de conteúdo por título. Replicamos fielmente esse
 * comportamento em vez de inventar uma feature de paywall que não existe
 * no produto real.
 */
interface VipApi {

    @GET("api/vip-status")
    suspend fun getVipStatus(@Query("userId") userId: String): VipStatusResponse

    @POST("api/redeem-vip")
    suspend fun redeemCode(@Body body: RedeemVipRequest): RedeemVipResponse

    @POST("api/mercadopago/create-pix")
    suspend fun createPix(@Body body: PixRequest): PixResponse

    @POST("api/infinitepay/create-link")
    suspend fun createInfinitePayLink(@Body body: PixRequest): InfinitePayResponse
}

@JsonClass(generateAdapter = true)
data class PixRequest(
    val userId: String,
    val amount: Double,
    val planLabel: String,
    val durationHours: Int
)

@JsonClass(generateAdapter = true)
data class PixResponse(
    val paymentId: String,
    val qrCode: String,
    val qrCodeBase64: String,
    val status: String
)

@JsonClass(generateAdapter = true)
data class InfinitePayResponse(
    val url: String,
    val order_nsu: String,
    val amount_cents: Int,
    val handle: String
)

@JsonClass(generateAdapter = true)
data class VipStatusResponse(
    val isVip: Boolean,
    val expiresAt: String?,
    val planLabel: String?,
)

@JsonClass(generateAdapter = true)
data class RedeemVipRequest(
    val code: String,
    val userId: String,
)

@JsonClass(generateAdapter = true)
data class RedeemVipResponse(
    val expiresAt: String? = null,
    val planLabel: String? = null,
    val error: String? = null,
)
