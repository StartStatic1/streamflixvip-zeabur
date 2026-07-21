package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
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

    /**
     * Troca um refresh_token por uma sessão nova (access_token renovado).
     * O JWT do Supabase expira (padrão: 1h) — sem chamar isso quando o
     * token expira, toda chamada autenticada (favoritar, checar
     * progresso, etc) passa a falhar silenciosamente pela RLS: o
     * Supabase não retorna erro óbvio pro app, só age como se a pessoa
     * não tivesse permissão — na prática parece "não salvou nada", que é
     * exatamente o bug do coração/favoritos não persistindo depois de um
     * tempo de uso.
     */
    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(
        @retrofit2.http.Header("apikey") apiKey: String,
        @Body body: RefreshTokenRequest,
    ): AuthSession
}

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    val refresh_token: String,
)

@JsonClass(generateAdapter = true)
data class SendOtpRequest(
    val email: String,
    // shouldCreateUser: true — cria a conta automaticamente no primeiro
    // login, mesmo comportamento do site (sem etapa separada de cadastro)
    val create_user: Boolean = true,
)

@JsonClass(generateAdapter = true)
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
@JsonClass(generateAdapter = true)
data class AuthSession(
    val access_token: String,
    val refresh_token: String,
    val user: AuthUser,
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String?,
)
