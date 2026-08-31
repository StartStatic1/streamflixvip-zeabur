package com.streamflixvip.app.ui.reels

import android.content.Context
import android.content.SharedPreferences
import com.streamflixvip.app.network.NetworkModule

object ReelLocalStore {
    fun prefs(context: Context): SharedPreferences {
        val p = context.getSharedPreferences("sfv_reels", Context.MODE_PRIVATE)
        migrateLegacy(p)
        return p
    }

    private fun uid(): String =
        NetworkModule.sessionStore?.userId?.takeIf { it.isNotBlank() } ?: "anon"

    fun likeKey(storyId: String) = "like_${uid()}_$storyId"
    fun idxKey(storyId: String) = "idx_${uid()}_$storyId"
    fun posKey(storyId: String, index: Int) = "pos_${uid()}_${storyId}_$index"
    fun doneKey(storyId: String) = "done_${uid()}_$storyId"

    fun isLiked(p: SharedPreferences, storyId: String): Boolean =
        p.getBoolean(likeKey(storyId), p.getBoolean("like_$storyId", false))

    fun isDone(p: SharedPreferences, storyId: String): Boolean =
        p.getBoolean(doneKey(storyId), p.getBoolean("done_$storyId", false))

    fun isInProgress(p: SharedPreferences, storyId: String): Boolean {
        if (isDone(p, storyId)) return false
        val uid = uid()
        if (p.contains(idxKey(storyId)) || p.all.keys.any { it.startsWith("pos_${uid}_${storyId}_") }) return true
        if (p.contains("idx_$storyId") || p.all.keys.any { it.startsWith("pos_${storyId}_") && !it.startsWith("pos_${uid}_") }) return true
        return false
    }

    private fun migrateLegacy(p: SharedPreferences) {
        val uid = uid()
        val flag = "migrated_$uid"
        if (p.getBoolean(flag, false)) return
        val uuid = Regex("^[0-9a-fA-F-]{36}$")
        val editor = p.edit()
        for ((k, v) in p.all) {
            when {
                k.startsWith("like_") -> {
                    val id = k.removePrefix("like_")
                    if (uuid.matches(id) && !p.contains(likeKey(id)) && v is Boolean) {
                        editor.putBoolean(likeKey(id), v)
                    }
                }
                k.startsWith("idx_") -> {
                    val id = k.removePrefix("idx_")
                    if (uuid.matches(id) && !p.contains(idxKey(id)) && v is Int) {
                        editor.putInt(idxKey(id), v)
                    }
                }
                k.startsWith("done_") -> {
                    val id = k.removePrefix("done_")
                    if (uuid.matches(id) && !p.contains(doneKey(id)) && v is Boolean) {
                        editor.putBoolean(doneKey(id), v)
                    }
                }
                k.startsWith("pos_") -> {
                    val rest = k.removePrefix("pos_")
                    val cut = rest.lastIndexOf('_')
                    if (cut > 0) {
                        val id = rest.substring(0, cut)
                        val idx = rest.substring(cut + 1).toIntOrNull()
                        if (idx != null && uuid.matches(id) && !p.contains(posKey(id, idx)) && v is Long) {
                            editor.putLong(posKey(id, idx), v)
                        }
                    }
                }
            }
        }
        editor.putBoolean(flag, true).apply()
    }
}
