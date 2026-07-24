package com.streamflixvip.tv.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

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
) {
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
) {
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
    val episode_count: Int,
    val name: String,
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
