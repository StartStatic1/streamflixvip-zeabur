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

    /**
     * Detalhes completos de um filme (sinopse, gêneros, nota, etc), já
     * incluindo os vídeos (trailer/teaser do YouTube) na mesma chamada —
     * append_to_response evita uma segunda requisição só pro trailer.
     */
    suspend fun getMovieDetails(tmdbId: Int) =
        tmdb.request(path = "/movie/$tmdbId", appendToResponse = "videos")

    /** Detalhes completos de uma série, incluindo lista de temporadas e vídeos. */
    suspend fun getSeriesDetails(tmdbId: Int) =
        tmdb.request(path = "/tv/$tmdbId", appendToResponse = "videos")

    /**
     * Títulos parecidos com o que está sendo exibido na tela de Detalhes
     * — preenche a fileira "Você também pode gostar" no lugar do espaço
     * vazio que sobrava embaixo da lista de fontes/episódios. Mesmo
     * endpoint padrão da TMDB (/movie/{id}/similar ou /tv/{id}/similar),
     * passando pelo mesmo proxy genérico que os outros métodos já usam.
     * Falha silenciosa: sem similares, a seção inteira só não aparece.
     */
    suspend fun getSimilarTitles(tmdbId: Int, mediaType: String): List<TmdbItem> =
        try {
            tmdb.request(path = "/$mediaType/$tmdbId/similar").results.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

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

    /**
     * Títulos de um gênero específico, pra grade da aba Gêneros e pra
     * tela de listagem por gênero. category decide o filtro extra:
     * - Tudo: só filtra por gênero, sem restringir idioma/tipo
     * - Filmes/Séries: restringe mediaType (movie/tv)
     * - Animes/Doramas: filtra por idioma original (ja/ko) — a TMDB não
     *   tem um "tipo" de anime/dorama separado, então usar idioma
     *   original é o jeito prático de aproximar esse filtro sem precisar
     *   de uma tabela de curadoria manual.
     * Sem número de contagem total por decisão explícita: contar
     * exigiria 1 chamada de rede extra só pra mostrar um número.
     */
    suspend fun getTitlesByGenre(genreId: Int, category: GenreCategory, page: Int = 1): List<TmdbItem> {
        val mediaType = category.mediaTypeOrDefault
        return tmdb.request(
            path = "/discover/$mediaType",
            page = page,
            withGenres = genreId.toString(),
            withOriginalLanguage = category.originalLanguage,
        ).results.orEmpty()
    }

    /**
     * Busca combinada da aba Explorar: categoria (Tudo/Filmes/Séries/
     * Animes/Doramas) + gênero opcional + ano opcional, com paginação.
     *
     * Quando category == ALL, chama discover/movie E discover/tv em
     * paralelo e intercala os resultados — sem isso, "Tudo" mostraria só
     * filmes (mediaTypeOrDefault cai pra "movie"), o que não bate com a
     * expectativa de um filtro "Tudo" de verdade.
     */
    suspend fun exploreCatalog(
        category: GenreCategory,
        genreId: Int?,
        year: Int?,
        page: Int = 1,
    ): List<TmdbItem> {
        suspend fun fetch(mediaType: String, originalLanguage: String?): List<TmdbItem> =
            tmdb.request(
                path = "/discover/$mediaType",
                page = page,
                withGenres = genreId?.toString(),
                withOriginalLanguage = originalLanguage,
                primaryReleaseYear = if (mediaType == "movie") year else null,
                firstAirDateYear = if (mediaType == "tv") year else null,
            ).results.orEmpty()

        return if (category == GenreCategory.ALL) {
            kotlinx.coroutines.coroutineScope {
                val movies = kotlinx.coroutines.async { fetch("movie", null) }
                val series = kotlinx.coroutines.async { fetch("tv", null) }
                (movies.await() + series.await()).sortedByDescending { it.popularity ?: 0.0 }
            }
        } else {
            fetch(category.mediaTypeOrDefault, category.originalLanguage)
        }
    }
}

/** Gênero fixo pra grade da aba Gêneros — nome + IDs oficiais da TMDB (iguais pra filme e série). */
data class GenreDefinition(val id: Int, val displayName: String)

/**
 * Filtro de categoria da aba Gêneros (pills Tudo/Filmes/Séries/Animes/
 * Doramas). A TMDB não modela "anime" ou "dorama" como tipo próprio —
 * são aproximados via idioma original (japonês/coreano) sobre o
 * catálogo de séries, que é o padrão mais comum usado por esse tipo de
 * app de streaming.
 */
enum class GenreCategory(val label: String, val mediaType: String?, val originalLanguage: String?) {
    ALL("Tudo", null, null),
    MOVIES("Filmes", "movie", null),
    SERIES("Séries", "tv", null),
    ANIME("Animes", "tv", "ja"),
    DORAMA("Doramas", "tv", "ko"),
    ;

    /** discover exige um mediaType válido mesmo pra "Tudo" — usa movie como padrão nesse caso. */
    val mediaTypeOrDefault: String get() = mediaType ?: "movie"
}

/**
 * Lista fixa (sem chamada de rede) dos gêneros mais comuns — evita ter
 * que buscar /genre/movie/list e /genre/tv/list toda vez que a aba abre.
 * IDs conferem com a documentação oficial da TMDB.
 */
val TMDB_GENRES = listOf(
    GenreDefinition(80, "Crime"),
    GenreDefinition(27, "Terror"),
    GenreDefinition(18, "Drama"),
    GenreDefinition(35, "Comédia"),
    GenreDefinition(28, "Ação e Aventura"),
    GenreDefinition(14, "Ficção e Fantasia"),
    GenreDefinition(53, "Suspense"),
    GenreDefinition(9648, "Mistério"),
    GenreDefinition(10751, "Família"),
    GenreDefinition(16, "Animação"),
    GenreDefinition(10749, "Romance"),
    GenreDefinition(10752, "Guerra e Política"),
    GenreDefinition(37, "Faroeste"),
)
