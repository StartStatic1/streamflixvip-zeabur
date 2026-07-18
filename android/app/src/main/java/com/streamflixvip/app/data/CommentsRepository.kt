package com.streamflixvip.app.data

import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PostgrestFilter
import com.streamflixvip.app.network.TitleComment
import com.streamflixvip.app.network.TitleCommentInsert

/**
 * Comentários por título (tabela title_comments no Supabase). Leitura é
 * pública — qualquer visitante vê os comentários de um título, mesmo sem
 * login — mas postar exige usuário autenticado, igual watch_progress
 * (RLS usa auth.uid() = user_id na policy de INSERT).
 */
class CommentsRepository {

    private val api = NetworkModule.commentsApi
    private val anonKey = NetworkModule.supabaseAnonKey

    suspend fun getComments(tmdbId: Int, mediaType: String): List<TitleComment> =
        try {
            api.getComments(
                apiKey = anonKey,
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            )
        } catch (_: Exception) {
            emptyList()
        }

    /** Retorna true se o comentário foi publicado com sucesso. */
    suspend fun postComment(
        accessToken: String,
        userId: String,
        userDisplayName: String?,
        isVipAuthor: Boolean,
        tmdbId: Int,
        mediaType: String,
        text: String,
    ): Boolean =
        try {
            api.postComment(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                body = TitleCommentInsert(
                    tmdb_id = tmdbId,
                    media_type = mediaType,
                    user_id = userId,
                    user_display_name = userDisplayName,
                    is_vip_author = isVipAuthor,
                    comment_text = text,
                ),
            )
            true
        } catch (_: Exception) {
            false
        }
}
