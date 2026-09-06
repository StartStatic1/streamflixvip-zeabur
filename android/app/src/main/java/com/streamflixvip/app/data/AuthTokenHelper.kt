package com.streamflixvip.app.data

import android.util.Base64
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.RefreshTokenRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AuthTokenHelper {

    private val refreshMutex = Mutex()

    suspend fun validAccessToken(skewSeconds: Long = 180): String? {
        val store = NetworkModule.sessionStore ?: return null
        val access = store.accessToken
        val refresh = store.refreshToken
        if (!access.isNullOrBlank() && (refresh.isNullOrBlank() || !isExpiredOrNear(access, skewSeconds))) {
            return access
        }
        if (refresh.isNullOrBlank()) return access
        return tryRefresh(store, refresh, skewSeconds) ?: store.accessToken
    }

    suspend fun forceRefresh(): String? {
        val store = NetworkModule.sessionStore ?: return null
        val refresh = store.refreshToken ?: return store.accessToken
        return tryRefresh(store, refresh, skewSeconds = 0)
    }

    fun currentUserId(): String? = NetworkModule.sessionStore?.userId

    private suspend fun tryRefresh(
        store: SessionStore,
        refreshToken: String,
        skewSeconds: Long,
    ): String? = refreshMutex.withLock {
        val latest = store.accessToken
        if (latest != null && skewSeconds > 0 && !isExpiredOrNear(latest, skewSeconds)) {
            return@withLock latest
        }
        val rt = store.refreshToken ?: refreshToken
        try {
            val session = NetworkModule.supabaseAuthApi.refreshToken(
                apiKey = NetworkModule.supabaseAnonKey,
                body = RefreshTokenRequest(rt),
            )
            store.updateTokens(session.access_token, session.refresh_token)
            session.access_token
        } catch (_: Exception) {
            null
        }
    }

    private fun isExpiredOrNear(jwt: String, skewSeconds: Long): Boolean {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return true
            var payload = parts[1]
            val pad = (4 - payload.length % 4) % 4
            if (pad > 0) payload += "=".repeat(pad)
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
            val expMark = "\"exp\":"
            val idx = json.indexOf(expMark)
            if (idx < 0) return false
            var i = idx + expMark.length
            while (i < json.length && json[i].isWhitespace()) i++
            val start = i
            while (i < json.length && json[i].isDigit()) i++
            val exp = json.substring(start, i).toLongOrNull() ?: return false
            val now = System.currentTimeMillis() / 1000
            now >= (exp - skewSeconds)
        } catch (_: Exception) {
            true
        }
    }
}
