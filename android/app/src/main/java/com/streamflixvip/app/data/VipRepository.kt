package com.streamflixvip.app.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.RedeemVipRequest
import com.streamflixvip.app.network.RedeemVipResponse
import com.streamflixvip.app.network.VipStatusResponse
import retrofit2.HttpException

sealed class RedeemResult {
    data class Success(val expiresAt: String?, val planLabel: String?) : RedeemResult()
    data class Failure(val message: String) : RedeemResult()
}

/**
 * Repositório VIP — consulta status e resgata códigos usando os MESMOS
 * endpoints Express que o site já usa. Nenhuma lógica de negócio nova:
 * hoje VIP só remove anúncios no site (não bloqueia conteúdo), então o
 * app replica exatamente esse comportamento.
 */
class VipRepository {

    private val api = NetworkModule.vipApi
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val errorAdapter = moshi.adapter(RedeemVipResponse::class.java)

    suspend fun getStatus(userId: String): VipStatusResponse =
        try {
            api.getVipStatus(userId)
        } catch (e: Exception) {
            // Mesmo comportamento de fallback do site: se a consulta falhar,
            // assume não-VIP em vez de travar a experiência do usuário.
            VipStatusResponse(isVip = false, expiresAt = null, planLabel = null)
        }

    suspend fun redeemCode(code: String, userId: String): RedeemResult {
        return try {
            val response = api.redeemCode(RedeemVipRequest(code = code, userId = userId))
            RedeemResult.Success(expiresAt = response.expiresAt, planLabel = response.planLabel)
        } catch (e: HttpException) {
            // redeem-vip.js devolve {error: "mensagem"} no corpo mesmo em
            // status 4xx (código inválido, já usado, expirado, etc) — o
            // Retrofit não parseia corpo de erro automaticamente, então
            // extraímos manualmente pra mostrar a mensagem real ao usuário
            // em vez de um "erro genérico" sem contexto.
            val errorBody = e.response()?.errorBody()?.string()
            val message = try {
                errorBody?.let { errorAdapter.fromJson(it)?.error } ?: "Código inválido."
            } catch (_: Exception) {
                "Código inválido."
            }
            RedeemResult.Failure(message)
        } catch (e: Exception) {
            RedeemResult.Failure("Não foi possível ativar o código. Tente novamente.")
        }
    }
}
