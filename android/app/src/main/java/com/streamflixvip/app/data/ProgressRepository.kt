package com.streamflixvip.app.data

import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PostgrestFilter
import com.streamflixvip.app.network.WatchProgressEntry
import com.streamflixvip.app.network.WatchProgressUpsert

/** Progresso / Continuar assistindo — JWT sempre fresco via AuthTokenHelper. */
class ProgressRepository {

    private val api = NetworkModule.watchProgressApi
    private val anonKey = NetworkModule.supabaseAnonKey

    private suspend fun tokenOrNull(preferred: String? = null): String? {
        val fresh = AuthTokenHelper.validAccessToken()
        if (!fresh.isNullOrBlank()) return fresh
        if (!preferred.isNullOrBlank()) return preferred
        return AuthTokenHelper.forceRefresh()
    }

    private fun uidOrNull(preferred: String? = null): String? =
        preferred?.takeIf { it.isNotBlank() } ?: AuthTokenHelper.currentUserId()

    suspend fun saveProgress(
        accessToken: String? = null,
        userId: String? = null,
        tmdbId: Int,
        mediaType: String,
        season: Int,
        episode: Int,
        title: String,
        posterPath: String?,
        positionSeconds: Int,
        durationSeconds: Int,
    ) {
        if (positionSeconds < 10) return
        if (mediaType == "tv") runCatching { com.streamflixvip.app.notify.FollowedSeries.watched(tmdbId, title, posterPath) }
        if (mediaType == "tv" && durationSeconds > 0 && positionSeconds.toFloat() / durationSeconds >= 0.9f) {
            runCatching { WatchedEpisodes.mark(tmdbId, season, episode) }
        }
        com.streamflixvip.app.profiles.ProfileLocalData.localScope()?.let { scope ->
            if (durationSeconds > 0 && positionSeconds.toFloat() / durationSeconds >= 0.95f) {
                com.streamflixvip.app.profiles.ProfileLocalData.removeProgress(scope, tmdbId, mediaType)
            } else {
                com.streamflixvip.app.profiles.ProfileLocalData.saveProgress(
                    scope,
                    com.streamflixvip.app.network.WatchProgressEntry(
                        tmdbId, mediaType, season, episode, title, posterPath, positionSeconds,
                        if (durationSeconds > 0) durationSeconds else maxOf(positionSeconds + 120, positionSeconds * 2),
                    ),
                )
            }
            return
        }
        val uid = uidOrNull(userId) ?: return
        val token = tokenOrNull(accessToken) ?: return
        val effectiveDuration = if (durationSeconds > 0) durationSeconds else maxOf(positionSeconds + 120, positionSeconds * 2)
        if (durationSeconds > 0) {
            val fraction = positionSeconds.toFloat() / durationSeconds
            if (fraction >= 0.95f) {
                removeProgress(token, uid, tmdbId, mediaType, season, episode)
                return
            }
        }
        suspend fun once(t: String): Boolean = try {
            api.upsertProgress(
                apiKey = anonKey,
                bearerToken = "Bearer $t",
                body = WatchProgressUpsert(
                    user_id = uid,
                    tmdb_id = tmdbId,
                    media_type = mediaType,
                    season = season,
                    episode = episode,
                    title = title,
                    poster_path = posterPath,
                    position_seconds = positionSeconds,
                    duration_seconds = effectiveDuration,
                ),
            )
            true
        } catch (e: Exception) {
            android.util.Log.w("ProgressRepo", "Falha ao salvar progresso tmdb=$tmdbId: ${e.message}")
            false
        }
        if (once(token)) return
        val refreshed = AuthTokenHelper.forceRefresh() ?: return
        once(refreshed)
    }

    /** Progresso de todos os episódios desta série (mais recente primeiro). */
    suspend fun getTitleProgress(tmdbId: Int, mediaType: String = "tv"): List<WatchProgressEntry> {
        com.streamflixvip.app.profiles.ProfileLocalData.localScope()?.let { scope ->
            return com.streamflixvip.app.profiles.ProfileLocalData.progress(scope).filter { it.tmdb_id == tmdbId && it.media_type == mediaType }
        }
        val uid = uidOrNull(null) ?: return emptyList()
        suspend fun once(t: String): List<WatchProgressEntry>? = try {
            api.getProgressForTitle(anonKey, "Bearer $t", PostgrestFilter.eq(uid), PostgrestFilter.eq(tmdbId))
                .filter { it.media_type == mediaType }
        } catch (_: Exception) { null }
        val token = tokenOrNull(null) ?: return emptyList()
        once(token)?.let { return it }
        val refreshed = AuthTokenHelper.forceRefresh() ?: return emptyList()
        return once(refreshed) ?: emptyList()
    }

    suspend fun getContinueWatching(
        accessToken: String? = null,
        userId: String? = null,
    ): List<WatchProgressEntry> = getContinueWatchingRaw(accessToken, userId).also { list ->
        runCatching {
            list.filter { it.media_type == "tv" }.take(20).forEach {
                com.streamflixvip.app.notify.FollowedSeries.watched(it.tmdb_id, it.title, it.poster_path)
            }
        }
    }

    private suspend fun getContinueWatchingRaw(
        accessToken: String? = null,
        userId: String? = null,
    ): List<WatchProgressEntry> {
        com.streamflixvip.app.profiles.ProfileLocalData.localScope()?.let {
            return com.streamflixvip.app.profiles.ProfileLocalData.progress(it)
        }
        val uid = uidOrNull(userId) ?: return emptyList()
        suspend fun once(t: String): List<WatchProgressEntry>? = try {
            api.getContinueWatching(
                apiKey = anonKey,
                bearerToken = "Bearer $t",
                userIdFilter = PostgrestFilter.eq(uid),
            ).distinctBy { it.tmdb_id to it.media_type }
        } catch (_: Exception) { null }

        val token = tokenOrNull(accessToken) ?: return emptyList()
        once(token)?.let { return it }
        val refreshed = AuthTokenHelper.forceRefresh() ?: return emptyList()
        return once(refreshed) ?: emptyList()
    }

    suspend fun removeProgress(
        accessToken: String? = null,
        userId: String? = null,
        tmdbId: Int,
        mediaType: String,
        season: Int,
        episode: Int,
    ) {
        com.streamflixvip.app.profiles.ProfileLocalData.localScope()?.let {
            com.streamflixvip.app.profiles.ProfileLocalData.removeProgress(it, tmdbId, mediaType, season, episode); return
        }
        val uid = uidOrNull(userId) ?: return
        val token = tokenOrNull(accessToken) ?: return
        try {
            api.deleteProgress(
                apiKey = anonKey,
                bearerToken = "Bearer $token",
                userIdFilter = PostgrestFilter.eq(uid),
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
                seasonFilter = PostgrestFilter.eq(season),
                episodeFilter = PostgrestFilter.eq(episode),
            )
        } catch (_: Exception) {
            val refreshed = AuthTokenHelper.forceRefresh() ?: return
            try {
                api.deleteProgress(
                    apiKey = anonKey,
                    bearerToken = "Bearer $refreshed",
                    userIdFilter = PostgrestFilter.eq(uid),
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq(mediaType),
                    seasonFilter = PostgrestFilter.eq(season),
                    episodeFilter = PostgrestFilter.eq(episode),
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun removeFromContinueWatching(
        accessToken: String? = null,
        userId: String? = null,
        tmdbId: Int,
        mediaType: String,
    ) {
        com.streamflixvip.app.profiles.ProfileLocalData.localScope()?.let {
            com.streamflixvip.app.profiles.ProfileLocalData.removeProgress(it, tmdbId, mediaType); return
        }
        val uid = uidOrNull(userId) ?: return
        val token = tokenOrNull(accessToken) ?: return
        try {
            api.deleteProgressByTitle(
                apiKey = anonKey,
                bearerToken = "Bearer $token",
                userIdFilter = PostgrestFilter.eq(uid),
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            )
        } catch (_: Exception) {
            val refreshed = AuthTokenHelper.forceRefresh() ?: return
            try {
                api.deleteProgressByTitle(
                    apiKey = anonKey,
                    bearerToken = "Bearer $refreshed",
                    userIdFilter = PostgrestFilter.eq(uid),
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq(mediaType),
                )
            } catch (_: Exception) {}
        }
    }
}
