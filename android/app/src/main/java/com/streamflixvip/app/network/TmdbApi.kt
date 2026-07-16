package com.streamflixvip.app.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interface Retrofit pro proxy TMDB que já existe em api/tmdb.js.
 *
 * IMPORTANTE: não chamamos api.themoviedb.org diretamente — usamos o MESMO
 * proxy Express que o site já usa (/api/tmdb?path=...), pelos mesmos
 * motivos que o comentário original do arquivo já explica: chamada direta
 * client-side pra TMDB retorna 403 em produção, e expor a API key no app
 * seria pior ainda que expor no bundle JS (um APK é trivial de descompilar
 * e extrair strings).
 */
interface TmdbApi {

    @GET("api/tmdb")
    suspend fun request(
        @Query("path") path: String,
        @Query("page") page: Int? = null,
        @Query("query") query: String? = null,
        @Query("append_to_response") appendToResponse: String? = null,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
    ): TmdbResponse

    /**
     * Mesmo proxy, mesmo path (ex: "/tv/123/season/1"), mas desserializado
     * como TmdbSeasonDetail em vez de TmdbResponse — o formato de retorno
     * de /tv/{id}/season/{n} é bem diferente (lista de episódios com
     * título/sinopse/imagem própria de cada um), então usa um método
     * Retrofit separado só pra não forçar TmdbResponse a acumular campos
     * de formatos completamente diferentes.
     */
    @GET("api/tmdb")
    suspend fun requestSeasonDetail(@Query("path") path: String): TmdbSeasonDetail
}

/**
 * Resposta genérica da TMDB. Usamos Map<String, Any?> pros campos que
 * variam MUITO entre endpoints (movie/tv details tem dezenas de campos
 * diferentes) e campos tipados só pro que a Home/Detail realmente
 * precisam ler — assim não é necessário modelar cada endpoint da TMDB
 * inteiro, só o que a UI usa.
 */
data class TmdbResponse(
    val page: Int? = null,
    val results: List<TmdbItem>? = null,
    // Campos usados quando a resposta é um item único (ex: /movie/123)
    val id: Int? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double? = null,
    val genres: List<TmdbGenre>? = null,
    val number_of_seasons: Int? = null,
    val seasons: List<TmdbSeason>? = null,
    val tagline: String? = null,
    val runtime: Int? = null, // em minutos; só filme retorna isso direto no endpoint de detalhe
    // Só preenchido quando a query usa append_to_response=videos — ver
    // CatalogRepository.getMovieDetails/getSeriesDetails, que já pedem
    // isso junto pra não precisar de uma segunda chamada de rede só pro
    // trailer.
    val videos: TmdbVideosResponse? = null,
) {
    /** Duração formatada (ex: "1h 40m"), ou null se a API não informou (comum em séries). */
    val displayRuntime: String?
        get() = runtime?.takeIf { it > 0 }?.let {
            val h = it / 60
            val m = it % 60
            if (h > 0) "${h}h ${m}m" else "${m}min"
        }

    /**
     * Chave do YouTube pro trailer oficial, se houver. Prioriza um vídeo
     * do tipo "Trailer" explicitamente marcado; cai pra "Teaser" se não
     * houver trailer completo cadastrado. Só considera YouTube — a TMDB
     * às vezes lista vídeos de outros sites que não temos como embutir.
     */
    val trailerKey: String?
        get() {
            val results = videos?.results.orEmpty().filter { it.site == "YouTube" }
            return results.firstOrNull { it.type == "Trailer" }?.key
                ?: results.firstOrNull { it.type == "Teaser" }?.key
        }
}

data class TmdbVideosResponse(val results: List<TmdbVideo>? = null)

data class TmdbVideo(
    val key: String,
    val site: String,
    val type: String,
)

data class TmdbItem(
    val id: Int,
    val title: String? = null,      // filmes usam "title"
    val name: String? = null,       // séries usam "name"
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double? = null,
    // Só vem preenchido em resultados de /search/multi, que mistura
    // filme e série no mesmo array — usado pra saber pra onde navegar
    // ao clicar num resultado de busca.
    val media_type: String? = null,
) {
    /** Nome de exibição: filme usa `title`, série usa `name`. */
    val displayTitle: String get() = title ?: name ?: "Sem título"

    /** Resolve o tipo mesmo fora do contexto de busca (filme sempre tem title, série sempre tem name). */
    val resolvedMediaType: String get() = media_type ?: if (title != null) "movie" else "tv"

    /** Ano de lançamento (filme usa release_date, série usa first_air_date). */
    val displayYear: String? get() = (release_date ?: first_air_date)?.take(4)?.takeIf { it.length == 4 }

    /** Nota formatada com 1 casa decimal (ex: "7.8"), ou null se o TMDB ainda não tem votos suficientes. */
    val displayRating: String? get() = vote_average?.takeIf { it > 0 }?.let { "%.1f".format(it) }
}

data class TmdbGenre(val id: Int, val name: String)

data class TmdbSeason(
    val season_number: Int,
    val episode_count: Int,
    val name: String,
)

/**
 * Detalhe de uma temporada específica, retornado por /tv/{id}/season/{n}
 * — é aqui que vem a lista real de episódios com título, sinopse,
 * imagem e duração (o endpoint /tv/{id} sozinho só traz o resumo das
 * temporadas, sem detalhar episódio por episódio).
 */
data class TmdbSeasonDetail(
    val season_number: Int,
    val episodes: List<TmdbEpisode>? = null,
)

data class TmdbEpisode(
    val episode_number: Int,
    val name: String? = null,
    val overview: String? = null,
    val still_path: String? = null,
    val runtime: Int? = null, // em minutos; pode vir null quando o TMDB ainda não tem o dado
    val air_date: String? = null,
    val vote_average: Double? = null,
) {
    /** Duração formatada (ex: "42 min"), ou null se o TMDB não informou. */
    val displayRuntime: String? get() = runtime?.takeIf { it > 0 }?.let { "$it min" }

    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Episódio $episode_number"
}

