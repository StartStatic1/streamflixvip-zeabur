package com.streamflixvip.app.data

import com.streamflixvip.app.network.FavoriteEntry
import com.streamflixvip.app.network.FavoriteUpsert
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PostgrestFilter

/**
 * Minha Lista (favoritos). Sempre resolve JWT fresco via AuthTokenHelper
 * (nao confia em token passado pelo ViewModel — evita lista vazia apos 1h).
 */
class FavoritesRepository {

    private val api = NetworkModule.favoritesApi
    private val anonKey = NetworkModule.supabaseAnonKey

    private suspend fun tokenOrNull(preferred: String? = null): String? {
        val fresh = AuthTokenHelper.validAccessToken()
        if (!fresh.isNullOrBlank()) return fresh
        if (!preferred.isNullOrBlank()) return preferred
        return AuthTokenHelper.forceRefresh()
    }

    private fun uidOrNull(preferred: String? = null): String? =
        preferred?.takeIf { it.isNotBlank() } ?: AuthTokenHelper.currentUserId()

    suspend fun addFavorite(
        accessToken: String? = null,
        userId: String? = null,
        tmdbId: Int,
        mediaType: String,
        title: String?,
        posterPath: String?,
        originalLanguage: String? = null,
    ): Boolean {
        val uid = uidOrNull(userId) ?: return false
        suspend fun once(token: String): Boolean =
            try {
                api.addFavorite(
                    apiKey = anonKey,
                    bearerToken = "Bearer $token",
                    body = FavoriteUpsert(
                        user_id = uid,
                        tmdb_id = tmdbId,
                        media_type = mediaType,
                        title = title,
                        poster_path = posterPath,
                        original_language = originalLanguage,
                    ),
                )
                true
            } catch (_: Exception) {
                false
            }

        val token = tokenOrNull(accessToken) ?: return false
        if (once(token)) return true
        val refreshed = AuthTokenHelper.forceRefresh() ?: return false
        return once(refreshed)
    }

    suspend fun removeFavorite(
        accessToken: String? = null,
        userId: String? = null,
        tmdbId: Int,
        mediaType: String,
    ): Boolean {
        val uid = uidOrNull(userId) ?: return false
        suspend fun once(token: String): Boolean =
            try {
                api.removeFavorite(
                    apiKey = anonKey,
                    bearerToken = "Bearer $token",
                    userIdFilter = PostgrestFilter.eq(uid),
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq(mediaType),
                )
                true
            } catch (_: Exception) {
                false
            }

        val token = tokenOrNull(accessToken) ?: return false
        if (once(token)) return true
        val refreshed = AuthTokenHelper.forceRefresh() ?: return false
        return once(refreshed)
    }

    suspend fun getFavorites(
        accessToken: String? = null,
        userId: String? = null,
    ): List<FavoriteEntry> {
        val uid = uidOrNull(userId) ?: return emptyList()
        suspend fun once(token: String): List<FavoriteEntry>? =
            try {
                api.getFavorites(
                    apiKey = anonKey,
                    bearerToken = "Bearer $token",
                    userIdFilter = PostgrestFilter.eq(uid),
                )
            } catch (_: Exception) {
                null
            }

        val token = tokenOrNull(accessToken) ?: return emptyList()
        once(token)?.let { return it }
        val refreshed = AuthTokenHelper.forceRefresh() ?: return emptyList()
        return once(refreshed) ?: emptyList()
    }

    suspend fun isFavorite(
        accessToken: String? = null,
        userId: String? = null,
        tmdbId: Int,
        mediaType: String,
    ): Boolean {
        val uid = uidOrNull(userId) ?: return false
        suspend fun once(token: String): Boolean? =
            try {
                val rows = api.getFavoriteByTitle(
                    apiKey = anonKey,
                    bearerToken = "Bearer $token",
                    userIdFilter = PostgrestFilter.eq(uid),
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq(mediaType),
                )
                rows.isNotEmpty()
            } catch (_: Exception) {
                null
            }

        val token = tokenOrNull(accessToken) ?: return false
        once(token)?.let { return it }
        val refreshed = AuthTokenHelper.forceRefresh() ?: return false
        return once(refreshed) ?: false
    }
}
