package com.streamflixvip.tv.ui.livetv

import android.content.Context

/** Favoritos de canais ao vivo no TV — SharedPreferences local. */
object LiveTvFavoritesStore {
    private const val PREF = "streamflix_tv_live_favs"
    private const val KEY = "ids"

    fun getIds(context: Context): Set<String> {
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return p.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
    }

    fun isFavorite(context: Context, channelId: String): Boolean =
        getIds(context).contains(channelId)

    /** @return true se ficou favorito */
    fun toggle(context: Context, channelId: String): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val cur = p.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val nowFav = if (cur.contains(channelId)) {
            cur.remove(channelId)
            false
        } else {
            cur.add(channelId)
            true
        }
        p.edit().putStringSet(KEY, HashSet(cur)).apply()
        return nowFav
    }
}
