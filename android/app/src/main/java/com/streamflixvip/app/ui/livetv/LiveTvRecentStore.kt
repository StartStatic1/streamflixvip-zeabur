package com.streamflixvip.app.ui.livetv

import android.content.Context

/** Ultimos canais assistidos (mais recente primeiro) — so no aparelho. */
object LiveTvRecentStore {
    private const val PREF = "streamflix_live_tv_recent"
    private const val KEY = "ids"
    private const val MAX = 20

    fun getIds(context: Context): List<String> {
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = p.getString(KEY, null).orEmpty()
        return raw.split('\n').filter { it.isNotBlank() }.distinct().take(MAX)
    }

    /** @return nova lista (mais recente primeiro) */
    fun push(context: Context, channelId: String): List<String> {
        if (channelId.isBlank() || channelId.contains('\n')) return getIds(context)
        val next = (listOf(channelId) + getIds(context).filter { it != channelId }).take(MAX)
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, next.joinToString("\n")).apply()
        return next
    }
}
