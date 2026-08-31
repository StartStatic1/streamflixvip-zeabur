package com.streamflixvip.app.ui.reels

import com.streamflixvip.app.network.ReelEpisode
import com.streamflixvip.app.network.ReelStory

data class PendingReelSession(
    val story: ReelStory,
    val episodes: List<ReelEpisode>,
    val startEpisode: Int = 1,
)

object PendingReel {
    @Volatile
    private var pending: PendingReelSession? = null

    fun set(session: PendingReelSession) {
        pending = session
    }

    fun consume(): PendingReelSession? {
        val s = pending
        pending = null
        return s
    }
}
