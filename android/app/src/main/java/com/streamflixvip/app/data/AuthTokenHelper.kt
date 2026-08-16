package com.streamflixvip.app.data

import android.util.Base64
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.RefreshTokenRequest

/**
 * Garante access_token válido antes de chamadas autenticadas (favoritos, etc).
 * Se o JWT estiver perto de expirar (ou já expirou), renova com refresh_token.
 */
object AuthTokenHelper {

    suspend fun validAccessToken(skewSeconds: Long = 120): String? {
        val store = NetworkModule.sessionStore ?: return null
        val access = store.accessToken ?: return null
        val refresh = store.refreshToken ?: return access

        if (!isExpiredOrNear(access, skewSeconds)) return access

        return tryRefresh(store, refresh) ?: access
    }

    /** Força refresh (usado no retry após falha). */
    suspend fun forceRefresh(): String? {
        val store = NetworkModule.sessionStore ?: return null
        val refresh = store.refreshToken ?: return store.accessToken
        return tryRefresh(store, refresh)
    }

    private suspend fun tryRefresh(store: SessionStore, refreshToken: String): String? =
        try {
            val session = NetworkModule.supabaseAuthApi.refreshToken(
                apiKey = NetworkModule.supabaseAnonKey,
                body = RefreshTokenRequest(refreshToken),
            )
            store.updateTokens(session.access_token, session.refresh_token)
            session.access_token
        } catch (_: Exception) {
            null
        }

    private fun isExpiredOrNear(jwt: String, skewSeconds: Long): Boolean {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return true
            var payload = parts[1]
            val pad = (4 - payload.length % 4) % 4
            if (pad > 0) payload += "=".repeat(pad)
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
            val exp = Regex("\"exp\"\\s*:\\s*(\\d+)")
                .find(json)?.groupValues?.get(1)?.toLongOrNull() ?: return false
            val now = System.currentTimeMillis() / 1000
            now >= (exp - skewSeconds)
        } catch (_: Exception) {
            true
        }
    }
}
