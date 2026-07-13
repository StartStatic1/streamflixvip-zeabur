package com.streamflixvip.app.data

import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.SendOtpRequest
import com.streamflixvip.app.network.VerifyOtpRequest

/**
 * Repositório de autenticação — envia/verifica OTP e persiste a sessão.
 * Mesmo fluxo de duas etapas do site: sendOtp() manda o código por
 * e-mail, verifyOtp() troca o código pela sessão autenticada.
 */
class AuthRepository(private val sessionStore: SessionStore) {

    private val authApi = NetworkModule.supabaseAuthApi
    private val anonKey = NetworkModule.supabaseAnonKey

    val isLoggedIn: Boolean get() = sessionStore.isLoggedIn
    val userEmail: String? get() = sessionStore.userEmail

    suspend fun sendOtp(email: String) {
        authApi.sendOtp(apiKey = anonKey, body = SendOtpRequest(email = email, create_user = true))
    }

    suspend fun verifyOtp(email: String, code: String) {
        val session = authApi.verifyOtp(
            apiKey = anonKey,
            body = VerifyOtpRequest(email = email, token = code, type = "email"),
        )
        sessionStore.saveSession(
            accessToken = session.access_token,
            refreshToken = session.refresh_token,
            userId = session.user.id,
            email = session.user.email,
        )
    }

    fun signOut() {
        sessionStore.clearSession()
    }
}
