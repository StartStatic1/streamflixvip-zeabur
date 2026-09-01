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

    fun setLiked(p: SharedPreferences, storyId: String, liked: Boolean) {
        p.edit()
            .putBoolean(likeKey(storyId), liked)
            .putBoolean("like_$storyId", liked)
            .apply()
    }

    fun isDone(p: SharedPreferences, storyId: String): Boolean =
        p.getBoolean(doneKey(storyId), p.getBoolean("done_$storyId", false))

    fun savedIndex(p: SharedPreferences, storyId: String): Int {
        val n = p.getInt(idxKey(storyId), -1)
        if (n >= 0) return n
        return p.getInt("idx_$storyId", -1)
    }

    fun savedPos(p: SharedPreferences, storyId: String, index: Int): Long {
        val n = p.getLong(posKey(storyId, index), 0L)
        if (n > 0L) return n
        val old = p.getLong("pos_${storyId}_$index", 0L)
        if (old > 0L) return old
        return 0L
    }

    fun isInProgress(p: SharedPreferences, storyId: String): Boolean {
        if (isDone(p, storyId)) return false
        if (savedIndex(p, storyId) >= 0) return true
        val uid = uid()
        return p.all.keys.any {
            it.startsWith("pos_${uid}_${storyId}_") || it.startsWith("pos_${storyId}_")
        }
    }

    fun clearProgress(p: SharedPreferences, storyId: String) {
        val uid = uid()
        val editor = p.edit()
            .remove(idxKey(storyId))
            .remove("idx_$storyId")
            .putBoolean(doneKey(storyId), true)
            .putBoolean("done_$storyId", true)
        val keys = p.all.keys.filter {
            it.startsWith("pos_${uid}_${storyId}_") || it.startsWith("pos_${storyId}_")
        }
        for (k in keys) editor.remove(k)
        editor.apply()
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
                        val ms = when (v) {
                            is Long -> v
                            is Int -> v.toLong()
                            else -> 0L
                        }
                        if (idx != null && uuid.matches(id) && ms > 0L && !p.contains(posKey(id, idx))) {
                            editor.putLong(posKey(id, idx), ms)
                        }
                    }
                }
            }
        }
        editor.putBoolean(flag, true).apply()
    }
}
