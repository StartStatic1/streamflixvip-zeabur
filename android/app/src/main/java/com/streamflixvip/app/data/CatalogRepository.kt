package com.streamflixvip.app.data

import com.streamflixvip.app.network.NetworkModule
import com.streamflixvip.app.network.PostgrestFilter
import com.streamflixvip.app.network.TmdbEpisode
import com.streamflixvip.app.network.TmdbItem
import com.streamflixvip.app.network.VipSource
import retrofit2.HttpException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Repositório central: combina metadados (TMDB, via proxy Express) com
 * fontes de vídeo.
 *
 * Fontes de filme/série: preferência /api/media-sources (servidor, com
 * gate de auth/VIP). Fallback para leitura direta no Supabase se a API
 * ainda estiver soft ou indisponível — assim app antigo e transição
 * não quebram.
 */
class CatalogRepository {

    private val tmdb = NetworkModule.tmdbApi
    private val supabase = NetworkModule.supabaseApi
    private val mediaSources = NetworkModule.mediaSourcesApi
    private val anonKey = NetworkModule.supabaseAnonKey

    suspend fun getPopularMovies(page: Int = 1): List<TmdbItem> =
        tmdb.request(path = "/movie/popular", page = page).results.orEmpty()

    suspend fun getPopularSeries(page: Int = 1): List<TmdbItem> =
        tmdb.request(path = "/tv/popular", page = page).results.orEmpty()

    suspend fun getNowPlayingMovies(page: Int = 1): List<TmdbItem> =
        tmdb.request(path = "/movie/now_playing", page = page).results.orEmpty()

    suspend fun getTrendingWeek(page: Int = 1): List<TmdbItem> =
        tmdb.request(path = "/trending/all/week", page = page).results.orEmpty()

    suspend fun searchCatalog(
        query: String,
        mediaType: String? = null,
        originalLanguage: String? = null,
    ): List<TmdbItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        val path = when (mediaType) {
            "movie" -> "/search/movie"
            "tv" -> "/search/tv"
            else -> "/search/multi"
        }

        return tmdb.request(path = path, query = normalizedQuery, page = 1)
            .results.orEmpty()
            .asSequence()
            .filter { it.poster_path != null }
            .filter { it.resolvedMediaType in setOf("movie", "tv") }
            .filter { originalLanguage == null || it.original_language == originalLanguage }
            .distinctBy { "${it.id}_${it.resolvedMediaType}" }
            .toList()
    }

    suspend fun getMovieDetails(tmdbId: Int) =
        tmdb.request(path = "/movie/$tmdbId", appendToResponse = "videos")

    suspend fun getSeriesDetails(tmdbId: Int) =
        tmdb.request(path = "/tv/$tmdbId", appendToResponse = "videos")

    suspend fun getSimilarTitles(tmdbId: Int, mediaType: String): List<TmdbItem> =
        try {
            tmdb.request(path = "/$mediaType/$tmdbId/similar").results.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

    suspend fun getSeasonEpisodes(tmdbId: Int, season: Int): List<TmdbEpisode> =
        try {
            tmdb.requestSeasonDetail(path = "/tv/$tmdbId/season/$season").episodes.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Fontes de filme: tenta API Express (manda JWT). Se falhar, cai no
     * Supabase direto (comportamento antigo).
     */
    suspend fun getSourcesForMovie(tmdbId: Int): List<VipSource> {
        // Sem fallback Supabase: senão free fura o vip_lock do painel.
        try {
            val res = mediaSources.getMovieSources(tmdbId)
            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED") return emptyList()
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
            return emptyList()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) return emptyList()
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    suspend fun getSourcesForEpisode(tmdbId: Int, season: Int, episode: Int): List<VipSource> {
        // Sem fallback Supabase: EP acima do limite / série trancada não pode vazar URL.
        try {
            val res = mediaSources.getEpisodeSources(tmdbId, season = season, episode = episode)
            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED") return emptyList()
            if (res.sources.isNotEmpty()) return prioritize(res.sources)
            return emptyList()
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) return emptyList()
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun prioritize(sources: List<VipSource>): List<VipSource> =
        sources.sortedByDescending { it.source_label == "MegaEmbed VIP" }

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

    suspend fun getTitlesByGenre(genreId: Int, category: GenreCategory, page: Int = 1): List<TmdbItem> {
        val mediaType = category.mediaTypeOrDefault
        return tmdb.request(
            path = "/discover/$mediaType",
            page = page,
            withGenres = genreId.toString(),
            withOriginalLanguage = category.originalLanguage,
        ).results.orEmpty()
    }

    suspend fun exploreCatalog(
        category: GenreCategory,
        genreId: Int?,
        year: Int?,
        page: Int = 1,
    ): List<TmdbItem> {
        if (category != GenreCategory.ALL) {
            return fetchExploreCatalog(category.mediaTypeOrDefault, category.originalLanguage, genreId, year, page)
        }
        return coroutineScope {
            val moviesDeferred: Deferred<List<TmdbItem>> =
                async { fetchExploreCatalog("movie", null, genreId, year, page) }
            val seriesDeferred: Deferred<List<TmdbItem>> =
                async { fetchExploreCatalog("tv", null, genreId, year, page) }
            val combined: List<TmdbItem> = moviesDeferred.await() + seriesDeferred.await()
            combined.sortedByDescending { item: TmdbItem -> item.popularity ?: 0.0 }
        }
    }

    private suspend fun fetchExploreCatalog(
        mediaType: String,
        originalLanguage: String?,
        genreId: Int?,
        year: Int?,
        page: Int,
    ): List<TmdbItem> =
        tmdb.request(
            path = "/discover/$mediaType",
            page = page,
            withGenres = genreId?.toString(),
            withOriginalLanguage = originalLanguage,
            primaryReleaseYear = if (mediaType == "movie") year else null,
            firstAirDateYear = if (mediaType == "tv") year else null,
        ).results.orEmpty()
}

data class GenreDefinition(val id: Int, val displayName: String)

enum class GenreCategory(val label: String, val mediaType: String?, val originalLanguage: String?) {
    ALL("Tudo", null, null),
    MOVIES("Filmes", "movie", null),
    SERIES("Séries", "tv", null),
    ANIME("Animes", "tv", "ja"),
    DORAMA("Doramas", "tv", "ko"),
    ;

    val mediaTypeOrDefault: String get() = mediaType ?: "movie"
}

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
