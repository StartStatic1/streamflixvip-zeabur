package com.streamflixvip.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Gerencia as credenciais do leitor IPTV nativo (Xtream Codes).
 * Guarda o Host (que pode vir do painel), Usuário e Senha fornecidos.
 */
class IptvStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sfv_iptv_store", Context.MODE_PRIVATE)

    var xtreamHost: String?
        get() = prefs.getString(KEY_HOST, null)
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var xtreamUser: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var xtreamPass: String?
        get() = prefs.getString(KEY_PASS, null)
        set(value) = prefs.edit().putString(KEY_PASS, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    val hasCredentials: Boolean
        get() = !xtreamUser.isNullOrBlank() && !xtreamPass.isNullOrBlank()

    companion object {
        private const val KEY_HOST = "xtream_host"
        private const val KEY_USER = "xtream_user"
        private const val KEY_PASS = "xtream_pass"
    }
}
