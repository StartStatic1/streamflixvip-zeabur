package com.streamflixvip.app.ui.reels

import android.content.Context
import android.content.SharedPreferences
import com.streamflixvip.app.network.NetworkModule

object ReelLocalStore {
    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("sfv_reels", Context.MODE_PRIVATE)

    private fun uid(): String =
        NetworkModule.sessionStore?.userId?.takeIf { it.isNotBlank() } ?: "anon"

    fun likeKey(storyId: String) = "like_${uid()}_$storyId"
    fun idxKey(storyId: String) = "idx_${uid()}_$storyId"
    fun posKey(storyId: String, index: Int) = "pos_${uid()}_${storyId}_$index"
    fun doneKey(storyId: String) = "done_${uid()}_$storyId"

    fun isLiked(p: SharedPreferences, storyId: String) = p.getBoolean(likeKey(storyId), false)
    fun isDone(p: SharedPreferences, storyId: String) = p.getBoolean(doneKey(storyId), false)
    fun isInProgress(p: SharedPreferences, storyId: String): Boolean {
        if (isDone(p, storyId)) return false
        val uid = uid()
        return p.contains(idxKey(storyId)) || p.all.keys.any { it.startsWith("pos_${uid}_${storyId}_") }
    }
}
