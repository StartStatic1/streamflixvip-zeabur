package com.streamflixvip.app.network

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Interface Retrofit pra Supabase Auth REST API, replicando EXATAMENTE o
 * mesmo fluxo OTP por e-mail que index.html já usa via supabase-js:
 *
 *   db.auth.signInWithOtp({ email, options: { shouldCreateUser: true } })
 *   db.auth.verifyOtp({ email, token, type: 'email' })
 *
 * O supabase-js por baixo dos panos só chama esses dois endpoints REST —
 * não existe mágica adicional, então replicar em Kotlin puro com Retrofit
 * é fiel ao comportamento real sem precisar da SDK Kotlin do Supabase
 * (que adicionaria uma dependência grande só pra isso).
 */
interface SupabaseAuthApi {

    @Headers("Content-Type: application/json")
    @POST("auth/v1/otp")
    suspend fun sendOtp(
        @retrofit2.http.Header("apikey") apiKey: String,
        @Body body: SendOtpRequest,
    )

    @Headers("Content-Type: application/json")
    @POST("auth/v1/verify")
    suspend fun verifyOtp(
        @retrofit2.http.Header("apikey") apiKey: String,
        @Body body: VerifyOtpRequest,
    ): AuthSession
}

data class SendOtpRequest(
    val email: String,
    // shouldCreateUser: true — cria a conta automaticamente no primeiro
    // login, mesmo comportamento do site (sem etapa separada de cadastro)
    val create_user: Boolean = true,
)

data class VerifyOtpRequest(
    val email: String,
    val token: String,
    val type: String = "email",
)

/**
 * Resposta de sessão autenticada — os campos que a UI precisa pra saber
 * "quem está logado" e poder anexar o token nas próximas chamadas que
 * exigirem usuário autenticado (ex: /api/vip-status, /api/redeem-vip).
 */
data class AuthSession(
    val access_token: String,
    val refresh_token: String,
    val user: AuthUser,
)

data class AuthUser(
    val id: String,
    val email: String?,
)
