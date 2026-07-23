package com.streamflixvip.tv.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Armazenamento local da sessão de autenticação — equivalente nativo do
 * localStorage com storageKey:'sfv-auth-token' que o site usa. Sem essa
 * persistência, o usuário precisaria fazer login toda vez que abrisse o
 * app, o que seria uma experiência ruim.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sfv_auth_session", Context.MODE_PRIVATE)

    fun saveSession(accessToken: String, refreshToken: String, userId: String, email: String?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val userEmail: String? get() = prefs.getString(KEY_EMAIL, null)
    val isLoggedIn: Boolean get() = accessToken != null

    /**
     * Atualiza só o access_token (e o refresh_token, que o Supabase
     * rotaciona a cada renovação) — chamado depois de um refresh bem
     * sucedido, sem precisar re-salvar userId/email que não mudam.
     */
    fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
    }
}
