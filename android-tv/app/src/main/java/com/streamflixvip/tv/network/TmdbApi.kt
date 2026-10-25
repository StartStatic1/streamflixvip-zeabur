package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface TmdbApi {

    @GET("api/tmdb")
    suspend fun request(
        @Query("path") path: String,
        @Query("page") page: Int? = null,
        @Query("query") query: String? = null,
        @Query("append_to_response") appendToResponse: String? = null,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_original_language") withOriginalLanguage: String? = null,
        @Query("primary_release_year") primaryReleaseYear: Int? = null,
        @Query("first_air_date_year") firstAirDateYear: Int? = null,
    ): TmdbResponse

    @GET("api/tmdb")
    suspend fun requestSeasonDetail(@Query("path") path: String): TmdbSeasonDetail

    /** Pedido generico (qualquer filtro do TMDB: sort_by, with_genres, page...). */
    @GET("api/tmdb")
    suspend fun requestMap(
        @Query("path") path: String,
        @QueryMap params: Map<String, String> = emptyMap(),
    ): TmdbResponse
}

@JsonClass(generateAdapter = true)
data class TmdbResponse(
    val page: Int? = null,
    val results: List<TmdbItem>? = null,
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
    val runtime: Int? = null,
    val original_language: String? = null,
    val videos: TmdbVideosResponse? = null,
    val credits: TmdbCredits? = null,
    val total_pages: Int? = null,
    val total_results: Int? = null,
    val number_of_episodes: Int? = null,
    val status: String? = null,
    val images: TmdbImages? = null,
    val recommendations: TmdbPage? = null,
    val similar: TmdbPage? = null,
    val content_ratings: TmdbContentRatings? = null,
    val release_dates: TmdbReleaseDates? = null,
    val imdb_id: String? = null,
    val external_ids: TmdbExternalIds? = null,
) {
    /** Logo (PNG transparente) em pt, senao en, senao qualquer. */
    val logoPath: String?
        get() {
            val logos = images?.logos.orEmpty().filter { !it.file_path.isNullOrBlank() && !it.file_path.endsWith(".svg") }
            return (logos.firstOrNull { it.iso_639_1 == "pt" }
                ?: logos.firstOrNull { it.iso_639_1 == "en" }
                ?: logos.firstOrNull())?.file_path
        }

    /** Classificacao indicativa BR (ex.: "14", "L"). */
    val ageRating: String?
        get() {
            content_ratings?.results?.firstOrNull { it.iso_3166_1 == "BR" }?.rating
                ?.takeIf { it.isNotBlank() }?.let { return it }
            return release_dates?.results?.firstOrNull { it.iso_3166_1 == "BR" }
                ?.release_dates?.mapNotNull { it.certification?.takeIf { c -> c.isNotBlank() } }?.firstOrNull()
        }

    val displayRuntime: String?
        get() = runtime?.takeIf { it > 0 }?.let {
            val h = it / 60
            val m = it % 60
            if (h > 0) "${h}h ${m}m" else "${m}min"
        }

    val trailerKey: String?
        get() {
            val results = videos?.results.orEmpty().filter { it.site == "YouTube" }
            return results.firstOrNull { it.type == "Trailer" }?.key
                ?: results.firstOrNull { it.type == "Teaser" }?.key
        }
}

@JsonClass(generateAdapter = true)
data class TmdbImages(val logos: List<TmdbImageFile>? = null)

@JsonClass(generateAdapter = true)
data class TmdbImageFile(val file_path: String? = null, val iso_639_1: String? = null)

@JsonClass(generateAdapter = true)
data class TmdbPage(val results: List<TmdbItem>? = null)

@JsonClass(generateAdapter = true)
data class TmdbContentRatings(val results: List<TmdbCountryRating>? = null)

@JsonClass(generateAdapter = true)
data class TmdbCountryRating(val iso_3166_1: String? = null, val rating: String? = null)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDates(val results: List<TmdbCountryRelease>? = null)

@JsonClass(generateAdapter = true)
data class TmdbCountryRelease(val iso_3166_1: String? = null, val release_dates: List<TmdbReleaseDate>? = null)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDate(val certification: String? = null)

@JsonClass(generateAdapter = true)
data class TmdbExternalIds(val imdb_id: String? = null)

@JsonClass(generateAdapter = true)
data class TmdbVideosResponse(val results: List<TmdbVideo>? = null)

@JsonClass(generateAdapter = true)
data class TmdbVideo(
    val key: String,
    val site: String,
    val type: String,
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(val cast: List<TmdbCastMember>? = null)

@JsonClass(generateAdapter = true)
data class TmdbCastMember(
    val name: String,
    val character: String? = null,
    val profile_path: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double? = null,
    val media_type: String? = null,
    val popularity: Double? = null,
    val original_language: String? = null,
    val genre_ids: List<Int>? = null,
    val adult: Boolean? = null,
) {
    /** Conteudo infantil/familia (para o Modo Infantil). */
    val isKidsFriendly: Boolean
        get() = adult != true && genre_ids.orEmpty().any { it == 16 || it == 10751 || it == 10762 }

    val displayTitle: String get() = title ?: name ?: "Sem título"
    val resolvedMediaType: String get() = media_type ?: if (title != null) "movie" else "tv"
    val displayYear: String? get() = (release_date ?: first_air_date)?.take(4)?.takeIf { it.length == 4 }
    val displayRating: String? get() = vote_average?.takeIf { it > 0 }?.let { "%.1f".format(it) }
    val displayMediaLabel: String
        get() = when {
            resolvedMediaType == "tv" && original_language == "ja" -> "ANIME"
            resolvedMediaType == "movie" -> "FILME"
            else -> "SÉRIE"
        }
}

@JsonClass(generateAdapter = true)
data class TmdbGenre(val id: Int, val name: String)

@JsonClass(generateAdapter = true)
data class TmdbSeason(
    val season_number: Int,
    val episode_count: Int = 0,
    val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetail(
    val season_number: Int,
    val episodes: List<TmdbEpisode>? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbEpisode(
    val episode_number: Int,
    val name: String? = null,
    val overview: String? = null,
    val still_path: String? = null,
    val runtime: Int? = null,
    val air_date: String? = null,
    val vote_average: Double? = null,
) {
    val displayRuntime: String? get() = runtime?.takeIf { it > 0 }?.let { "$it min" }
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: "Episódio $episode_number"
}
