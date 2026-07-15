package com.streamflixvip.app.data

import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PostgrestFilter
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.VipSource

/**
 * Repositório central: combina metadados (TMDB, via proxy Express) com
 * fontes de vídeo reais (Supabase, direto — mesmo padrão do index.html).
 * As telas (Home/Detail/Player) só falam com este repositório, nunca
 * chamam as APIs diretamente — assim, se amanhã a origem de dados mudar
 * (ex: um endpoint novo dedicado ao app), só este arquivo muda.
 */
class CatalogRepository {

    private val tmdb = NetworkModule.tmdbApi
    private val supabase = NetworkModule.supabaseApi
    private val anonKey = NetworkModule.supabaseAnonKey

    /** Filmes em destaque/populares pra Home. */
    suspend fun getPopularMovies(page: Int = 1): List<TmdbItem> =
        tmdb.request(path = "/movie/popular", page = page).results.orEmpty()

    /** Séries em destaque/populares pra Home. */
    suspend fun getPopularSeries(page: Int = 1): List<TmdbItem> =
        tmdb.request(path = "/tv/popular", page = page).results.orEmpty()

    /** Lançamentos recentes — outra fileira comum de Home. */
    suspend fun getNowPlayingMovies(page: Int = 1): List<TmdbItem> =
        tmdb.request(path = "/movie/now_playing", page = page).results.orEmpty()

    /** Detalhes completos de um filme (sinopse, gêneros, nota, etc). */
    suspend fun getMovieDetails(tmdbId: Int) =
        tmdb.request(path = "/movie/$tmdbId")

    /** Detalhes completos de uma série, incluindo lista de temporadas. */
    suspend fun getSeriesDetails(tmdbId: Int) =
        tmdb.request(path = "/tv/$tmdbId")

    /**
     * Lista de episódios de UMA temporada, com título/sinopse/imagem/duração
     * de cada um — alimenta os cards de episódio na tela de Detalhes.
     * Falha silenciosa: se a TMDB não responder, a UI cai pra números
     * simples em vez de travar a tela inteira.
     */
    suspend fun getSeasonEpisodes(tmdbId: Int, season: Int): List<TmdbEpisode> =
        try {
            tmdb.requestSeasonDetail(path = "/tv/$tmdbId/season/$season").episodes.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Fontes de vídeo cadastradas pra um FILME — mesma query que
     * fetchCustomSources(tmdbId, 'movie', null, null) faz no index.html.
     */
    suspend fun getSourcesForMovie(tmdbId: Int): List<VipSource> =
        supabase.getSourcesForMovie(
            apiKey = anonKey,
            tmdbIdFilter = PostgrestFilter.eq(tmdbId),
            mediaTypeFilter = PostgrestFilter.eq("movie"),
        )

    /**
     * Fontes de vídeo cadastradas pra um EPISÓDIO específico de série —
     * mesma query que fetchCustomSources(tmdbId, 'tv', season, episode)
     * faz no index.html.
     */
    suspend fun getSourcesForEpisode(tmdbId: Int, season: Int, episode: Int): List<VipSource> =
        supabase.getSourcesForEpisode(
            apiKey = anonKey,
            tmdbIdFilter = PostgrestFilter.eq(tmdbId),
            seasonFilter = PostgrestFilter.eq(season),
            episodeFilter = PostgrestFilter.eq(episode),
        )

    /**
     * Config de bloqueio VIP do título inteiro (filme OU série), vinda da
     * tabela dedicada vip_titles — 1 consulta simples e direta, sem
     * depender de nenhuma fonte/servidor estar cadastrada de um jeito
     * específico. Retorna null se o título nunca foi marcado (= sem
     * bloqueio nenhum, comportamento padrão pra todo título novo).
     */
    suspend fun getVipTitleConfig(tmdbId: Int, mediaType: String): com.streamflixvip.app.network.VipTitleConfig? =
        try {
            supabase.getVipTitleConfig(
                apiKey = anonKey,
                tmdbIdFilter = PostgrestFilter.eq(tmdbId),
                mediaTypeFilter = PostgrestFilter.eq(mediaType),
            ).firstOrNull()
        } catch (e: Exception) {
            null
        }
}
