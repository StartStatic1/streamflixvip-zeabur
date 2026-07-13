package com.streamflixvip.app.network

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
}

data class VipStatusResponse(
    val isVip: Boolean,
    val expiresAt: String?,
    val planLabel: String?,
)

data class RedeemVipRequest(
    val code: String,
    val userId: String,
)

data class RedeemVipResponse(
    val expiresAt: String? = null,
    val planLabel: String? = null,
    val error: String? = null,
)
