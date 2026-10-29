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
    companion object {
        /** null = ainda não sabemos se a migration de comentários foi aplicada. */
        @Volatile var extended: Boolean? = null
    }

    private val api = NetworkModule.commentsApi
    private val anonKey = NetworkModule.supabaseAnonKey

    suspend fun getComments(tmdbId: Int, mediaType: String): List<TitleComment> {
        // 1º tenta com as colunas novas (avatar + esconder moderados). Se a migration ainda não
        // foi aplicada o servidor responde 400 — aí volta para a consulta antiga, sem erro.
        if (extended != false) {
            try {
                return api.getComments(
                    apiKey = anonKey,
                    tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                    mediaTypeFilter = PostgrestFilter.eq(mediaType),
                    select = "id,user_id,user_display_name,is_vip_author,comment_text,created_at,up_count,down_count,parent_id,author_avatar",
                    hiddenFilter = "is.false",
                ).also { extended = true }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 400) extended = false
            } catch (_: Exception) { }
        }
        return try {
            api.getComments(
                apiKey = anonKey,
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Denuncia (tabela comment_reports). Devolve false se a tabela ainda não existir. */
    suspend fun report(accessToken: String, userId: String, commentId: Long, reason: String): Boolean =
        try {
            api.reportComment(anonKey, "Bearer $accessToken", com.streamflixvip.app.network.CommentReport(commentId, userId, reason.take(200)))
            true
        } catch (_: Exception) { false }

    /** Retorna true se o comentário foi publicado com sucesso. */
    suspend fun postComment(
        accessToken: String,
        userId: String,
        userDisplayName: String?,
        isVipAuthor: Boolean,
        tmdbId: Int,
        mediaType: String,
        text: String,
        parentId: Long? = null,
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
                    parent_id = parentId,
                    author_avatar = if (extended == true) com.streamflixvip.app.profiles.ProfileStore.active.value?.avatar else null,
                ),
            )
            true
        } catch (_: Exception) {
            false
        }

    suspend fun vote(accessToken: String, commentId: Long, value: Int): Boolean =
        try {
            api.voteComment(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                body = com.streamflixvip.app.network.VoteRequest(commentId, value),
            )
            true
        } catch (_: Exception) { false }

    suspend fun deleteComment(accessToken: String, commentId: Long): Boolean =
        try {
            api.deleteComment(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                idFilter = PostgrestFilter.eq(commentId),
            )
            true
        } catch (_: Exception) {
            false
        }
}
