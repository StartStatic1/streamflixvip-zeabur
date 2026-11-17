package com.streamflixvip.app.offline

import android.content.Context
import android.content.SharedPreferences

/**
 * Último VIP confirmado pelo servidor, guardado no aparelho, para poder assistir
 * os downloads sem internet. Só é gravado quando o servidor diz que é VIP
 * (falha de rede não apaga). Vale até a data de vencimento do plano.
 */
object OfflineVip {
    private var prefs: SharedPreferences? = null
    private const val KEY = "vip_until_ms"
    private const val NO_EXPIRY_GRACE_MS = 3L * 24 * 3600 * 1000

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("sfv_offline", Context.MODE_PRIVATE)
    }

    fun remember(isVip: Boolean, expiresAtMs: Long) {
        if (!isVip) return
        val until = if (expiresAtMs > 0) expiresAtMs else System.currentTimeMillis() + NO_EXPIRY_GRACE_MS
        prefs?.edit()?.putLong(KEY, until)?.apply()
    }

    fun clear() { prefs?.edit()?.remove(KEY)?.apply() }

    fun validUntil(): Long = prefs?.getLong(KEY, 0L) ?: 0L

    fun isValid(): Boolean = validUntil() > System.currentTimeMillis()
}
