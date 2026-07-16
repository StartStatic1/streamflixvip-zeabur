package com.streamflixvip.app.data

import com.streamflixvip.app.network.FavoriteEntry
import com.streamflixvip.app.network.FavoriteUpsert
import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PostgrestFilter

/**
 * Salva e lê a "Minha Lista" (favoritos) no Supabase, por usuário — mesmo
 * padrão do ProgressRepository (tabela própria, RLS por auth.uid()).
 *
 * Toda escrita/leitura exige usuário logado (accessToken), porque a RLS
 * da tabela favorites usa auth.uid() = user_id.
 */
class FavoritesRepository {

    private val api = NetworkModule.favoritesApi
    private val anonKey = NetworkModule.supabaseAnonKey

    suspend fun addFavorite(
        accessToken: String,
        userId: String,
        tmdbId: Int,
        mediaType: String,
        title: String?,
        posterPath: String?,
    ): Boolean =
        try {
            api.addFavorite(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                body = FavoriteUpsert(
                    user_id = userId,
                    tmdb_id = tmdbId,
                    media_type = mediaType,
                    title = title,
                    poster_path = posterPath,
                ),
            )
            true
        } catch (_: Exception) {
            false
        }

    suspend fun removeFavorite(
        accessToken: String,
        userId: String,
        tmdbId: Int,
        mediaType: String,
    ): Boolean =
        try {
            api.removeFavorite(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                userIdFilter = PostgrestFilter.eq(userId),
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            )
            true
        } catch (_: Exception) {
            false
        }

    suspend fun getFavorites(accessToken: String, userId: String): List<FavoriteEntry> =
        try {
            api.getFavorites(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                userIdFilter = PostgrestFilter.eq(userId),
            )
        } catch (_: Exception) {
            emptyList()
        }

    /** Consulta só ESTE título — mais barato que baixar a lista inteira pra checar 1 item. */
    suspend fun isFavorite(accessToken: String, userId: String, tmdbId: Int, mediaType: String): Boolean =
        try {
            api.getFavoriteByTitle(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                userIdFilter = PostgrestFilter.eq(userId),
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            ).isNotEmpty()
        } catch (_: Exception) {
            false
        }
}
