package com.streamflixvip.app.data

import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PostgrestFilter
import com.streamflixvip.app.network.WatchProgressEntry
import com.streamflixvip.app.network.WatchProgressUpsert

/**
 * Salva e lê o progresso de reprodução (posição atual / duração) no
 * Supabase, por usuário. É a base do carrossel "Continuar assistindo"
 * na Home.
 *
 * Toda escrita/leitura exige usuário logado (accessToken), porque a RLS
 * da tabela watch_progress usa auth.uid() = user_id — sem o JWT do
 * usuário no header Authorization, o Postgres rejeita a operação.
 */
class ProgressRepository {

    private val api = NetworkModule.watchProgressApi
    private val anonKey = NetworkModule.supabaseAnonKey

    /**
     * Salva a posição atual. Ignora silenciosamente:
     * - os primeiros segundos (a pessoa só abriu e fechou, não vale
     *   registrar como "assistindo");
     * - quando já passou de 95% (consideramos concluído — remove da
     *   lista de continuar assistindo em vez de manter lá parado em 99%).
     */
    suspend fun saveProgress(
        accessToken: String,
        userId: String,
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
        val effectiveDuration = if (durationSeconds > 0) {
            durationSeconds
        } else {
            maxOf(positionSeconds + 120, positionSeconds * 2)
        }
        if (durationSeconds > 0) {
            val fraction = positionSeconds.toFloat() / durationSeconds
            if (fraction >= 0.95f) {
                removeProgress(accessToken, userId, tmdbId, mediaType, season, episode)
                return
            }
        }
        try {
            api.upsertProgress(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                body = WatchProgressUpsert(
                    user_id = userId,
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
        } catch (e: Exception) {
            android.util.Log.w("ProgressRepo", "Falha ao salvar progresso tmdb=$tmdbId: ${e.message}")
        }
    }

    /**
     * Busca o progresso de todas as séries/filmes que a pessoa começou a
     * assistir. A tabela guarda uma linha por episódio (ex: 3 episódios
     * assistidos de "Origem" = 3 linhas), porque cada episódio precisa
     * lembrar sua própria posição — mas a Home deve mostrar só 1 card por
     * série, com o episódio mais recente. Por isso agrupamos aqui por
     * (tmdb_id, media_type): a API já devolve ordenado por updated_at
     * decrescente, então a primeira ocorrência de cada série na lista é
     * automaticamente a mais recente.
     */
    suspend fun getContinueWatching(accessToken: String, userId: String): List<WatchProgressEntry> =
        try {
            val allEntries = api.getContinueWatching(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                userIdFilter = PostgrestFilter.eq(userId),
            )
            allEntries
                .distinctBy { it.tmdb_id to it.media_type }
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun removeProgress(
        accessToken: String,
        userId: String,
        tmdbId: Int,
        mediaType: String,
        season: Int,
        episode: Int,
    ) {
        try {
            api.deleteProgress(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                userIdFilter = PostgrestFilter.eq(userId),
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
                seasonFilter = PostgrestFilter.eq(season),
                episodeFilter = PostgrestFilter.eq(episode),
            )
        } catch (_: Exception) {
        }
    }

    /** Remove o titulo da lista Continuar assistindo (todos os EPs desse tmdb). */
    suspend fun removeFromContinueWatching(
        accessToken: String,
        userId: String,
        tmdbId: Int,
        mediaType: String,
    ) {
        try {
            api.deleteProgressByTitle(
                apiKey = anonKey,
                bearerToken = "Bearer $accessToken",
                userIdFilter = PostgrestFilter.eq(userId),
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            )
        } catch (_: Exception) {
        }
    }

}
