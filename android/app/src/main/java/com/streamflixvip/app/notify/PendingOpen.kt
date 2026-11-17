package com.streamflixvip.app.notify

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Título a abrir quando a pessoa toca numa notificação (lido pela navegação). */
object PendingOpen {
    const val EXTRA_TMDB_ID = "sfv_open_tmdb_id"
    const val EXTRA_MEDIA_TYPE = "sfv_open_media_type"
    const val EXTRA_SEASON = "sfv_open_season"
    const val EXTRA_EPISODE = "sfv_open_episode"
    const val EXTRA_OPEN_PROFILE = "sfv_open_profile"

    data class Target(val tmdbId: Int, val mediaType: String, val season: Int = -1, val episode: Int = -1, val profile: Boolean = false)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target

    fun handle(intent: Intent?) {
        intent ?: return
        val id = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        val media = intent.getStringExtra(EXTRA_MEDIA_TYPE)
        if (id > 0 && !media.isNullOrBlank()) {
            _target.value = Target(id, media, intent.getIntExtra(EXTRA_SEASON, -1), intent.getIntExtra(EXTRA_EPISODE, -1))
        } else if (intent.getBooleanExtra(EXTRA_OPEN_PROFILE, false)) {
            _target.value = Target(0, "", profile = true)
        } else return
        intent.removeExtra(EXTRA_TMDB_ID)
        intent.removeExtra(EXTRA_OPEN_PROFILE)
    }

    fun consume(): Target? = _target.value.also { _target.value = null }
}
