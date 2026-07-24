package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

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

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(
        @retrofit2.http.Header("apikey") apiKey: String,
        @Body body: RefreshTokenRequest,
    ): AuthSession
}

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(val refresh_token: String)

@JsonClass(generateAdapter = true)
data class SendOtpRequest(
    val email: String,
    val create_user: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val email: String,
    val token: String,
    val type: String = "email",
)

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
