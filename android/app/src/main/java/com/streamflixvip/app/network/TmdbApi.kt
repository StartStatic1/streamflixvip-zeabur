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
    ): TmdbResponse
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
}

data class TmdbGenre(val id: Int, val name: String)

data class TmdbSeason(
    val season_number: Int,
    val episode_count: Int,
    val name: String,
)
