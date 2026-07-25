package com.streamflixvip.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferências simples do app que não precisam de conta/rede — hoje só
 * guarda se a pessoa quer receber notificações. Mesmo padrão de
 * SharedPreferences do SessionStore, só que numa área separada (chave
 * de arquivo diferente) porque isso não é dado de sessão.
 */
class PreferencesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sfv_preferences", Context.MODE_PRIVATE)

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    companion object {
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
