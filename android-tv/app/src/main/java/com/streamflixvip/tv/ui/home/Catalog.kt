package com.streamflixvip.tv.ui.home

enum class RowStyle { FEATURED, POSTER, TOP10 }

enum class BrowseSection { HOME, MOVIES, SERIES, KIDS }

/** Uma fileira do catalogo: de onde vem (TMDB) e como aparece. */
data class RowSpec(
    val key: String,
    val title: String,
    val path: String,
    val params: Map<String, String> = emptyMap(),
    val style: RowStyle = RowStyle.POSTER,
    /** "movie"/"tv" quando o endpoint nao devolve media_type */
    val mediaType: String? = null,
)

object Catalog {
    private fun movieGenre(key: String, title: String, genre: String, extra: Map<String, String> = emptyMap()) =
        RowSpec(key, title, "/discover/movie", mapOf("with_genres" to genre, "sort_by" to "popularity.desc") + extra, mediaType = "movie")

    private fun tvGenre(key: String, title: String, genre: String, extra: Map<String, String> = emptyMap()) =
        RowSpec(key, title, "/discover/tv", mapOf("with_genres" to genre, "sort_by" to "popularity.desc") + extra, mediaType = "tv")

    private val home = listOf(
        RowSpec("featured", "Destaques de hoje", "/trending/all/day", style = RowStyle.FEATURED),
        RowSpec("top10", "Top 10 filmes da semana", "/trending/movie/week", style = RowStyle.TOP10, mediaType = "movie"),
        RowSpec("tv_trending", "Séries em alta", "/trending/tv/week", mediaType = "tv"),
        RowSpec("now_playing", "Lançamentos do cinema", "/movie/now_playing", mediaType = "movie"),
        RowSpec("br", "Feito no Brasil", "/discover/tv", mapOf("with_original_language" to "pt", "sort_by" to "popularity.desc"), mediaType = "tv"),
        movieGenre("action", "Ação e aventura", "28"),
        movieGenre("comedy", "Comédia", "35"),
        movieGenre("horror", "Terror", "27"),
        tvGenre("anime", "Animes", "16", mapOf("with_original_language" to "ja")),
        movieGenre("scifi", "Ficção científica", "878"),
        movieGenre("family", "Para toda a família", "10751"),
    )

    private val movies = listOf(
        RowSpec("m_featured", "Filmes em destaque", "/trending/movie/week", style = RowStyle.FEATURED, mediaType = "movie"),
        RowSpec("m_popular", "Populares agora", "/movie/popular", mediaType = "movie"),
        RowSpec("m_top", "Mais bem avaliados", "/movie/top_rated", style = RowStyle.TOP10, mediaType = "movie"),
        RowSpec("m_upcoming", "Em breve", "/movie/upcoming", mediaType = "movie"),
        movieGenre("m_action", "Ação", "28"),
        movieGenre("m_adventure", "Aventura", "12"),
        movieGenre("m_comedy", "Comédia", "35"),
        movieGenre("m_drama", "Drama", "18"),
        movieGenre("m_thriller", "Suspense", "53"),
        movieGenre("m_horror", "Terror", "27"),
        movieGenre("m_romance", "Romance", "10749"),
        movieGenre("m_animation", "Animação", "16"),
        movieGenre("m_crime", "Crime", "80"),
        movieGenre("m_scifi", "Ficção científica", "878"),
        RowSpec("m_br", "Cinema nacional", "/discover/movie", mapOf("with_original_language" to "pt", "sort_by" to "popularity.desc"), mediaType = "movie"),
        movieGenre("m_docs", "Documentários", "99"),
    )

    private val series = listOf(
        RowSpec("s_featured", "Séries em destaque", "/trending/tv/week", style = RowStyle.FEATURED, mediaType = "tv"),
        RowSpec("s_popular", "Populares agora", "/tv/popular", mediaType = "tv"),
        RowSpec("s_top", "Mais bem avaliadas", "/tv/top_rated", style = RowStyle.TOP10, mediaType = "tv"),
        RowSpec("s_air", "No ar esta semana", "/tv/on_the_air", mediaType = "tv"),
        tvGenre("s_action", "Ação e aventura", "10759"),
        tvGenre("s_comedy", "Comédia", "35"),
        tvGenre("s_drama", "Drama", "18"),
        tvGenre("s_crime", "Crime", "80"),
        tvGenre("s_scifi", "Ficção e fantasia", "10765"),
        tvGenre("s_mystery", "Mistério", "9648"),
        tvGenre("s_anime", "Animes", "16", mapOf("with_original_language" to "ja")),
        RowSpec("s_kdrama", "Doramas coreanos", "/discover/tv", mapOf("with_original_language" to "ko", "with_genres" to "18", "sort_by" to "popularity.desc"), mediaType = "tv"),
        RowSpec("s_cdrama", "Doramas chineses", "/discover/tv", mapOf("with_original_language" to "zh", "with_genres" to "18", "sort_by" to "popularity.desc"), mediaType = "tv"),
        RowSpec("s_novela", "Novelas", "/discover/tv", mapOf("with_genres" to "10766", "sort_by" to "popularity.desc"), mediaType = "tv"),
        RowSpec("s_br", "Novelas e séries brasileiras", "/discover/tv", mapOf("with_original_language" to "pt", "sort_by" to "popularity.desc"), mediaType = "tv"),
        tvGenre("s_reality", "Reality shows", "10764"),
        tvGenre("s_docs", "Documentários", "99"),
    )

    private val kids = listOf(
        RowSpec("k_featured", "Diversão para a criançada", "/discover/movie", mapOf("with_genres" to "16", "sort_by" to "popularity.desc"), style = RowStyle.FEATURED, mediaType = "movie"),
        tvGenre("k_series", "Desenhos", "10762"),
        RowSpec("k_anim", "Filmes de animação", "/discover/movie", mapOf("with_genres" to "16,10751", "sort_by" to "popularity.desc"), mediaType = "movie"),
        tvGenre("k_toons", "Séries animadas", "16", mapOf("with_original_language" to "en")),
        movieGenre("k_family", "Filmes para a família", "10751"),
        RowSpec("k_classics", "Clássicos da animação", "/discover/movie", mapOf("with_genres" to "16", "sort_by" to "vote_count.desc"), style = RowStyle.TOP10, mediaType = "movie"),
    )

    fun rowsFor(section: BrowseSection): List<RowSpec> = when (section) {
        BrowseSection.HOME -> home
        BrowseSection.MOVIES -> movies
        BrowseSection.SERIES -> series
        BrowseSection.KIDS -> kids
    }

    private val legacy = mapOf(
        "trending" to RowSpec("trending", "Em alta", "/trending/all/week"),
        "cinema" to RowSpec("cinema", "No cinema", "/movie/now_playing", mediaType = "movie"),
        "upcoming" to RowSpec("upcoming", "Em breve", "/movie/upcoming", mediaType = "movie"),
        "series" to RowSpec("series", "Séries", "/tv/popular", mediaType = "tv"),
    )

    fun spec(key: String): RowSpec =
        (home + movies + series + kids).firstOrNull { it.key == key } ?: legacy[key] ?: legacy.getValue("trending")

    /** Generos impróprios que nunca aparecem no Modo Infantil. */
    private val kidsBlocked = setOf(27, 53, 80, 10752, 18, 9648, 10749)

    fun kidsSafe(items: List<com.streamflixvip.tv.network.TmdbItem>): List<com.streamflixvip.tv.network.TmdbItem> =
        items.filter { it.adult != true && it.genre_ids.orEmpty().none { g -> g in kidsBlocked } }
}
