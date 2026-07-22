package com.streamflixvip.app.network

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interface Retrofit pra REST API do Supabase (PostgREST) — SEM passar
 * pelo backend Express, exatamente como o index.html faz hoje com
 * `db.from('vip_sources').select(...)` usando o cliente supabase-js.
 *
 * Replicamos aqui em Kotlin puro a mesma query que existe em
 * fetchCustomSources() no index.html:
 *
 *   db.from('vip_sources').select('source_url,source_label,priority')
 *     .eq('tmdb_id', tmdbId).eq('media_type', type).eq('is_active', true)
 *     .order('priority', {ascending:false})
 *     // + eq('season', season).eq('episode', episode) se for série
 *     // + is('season', null) se for filme
 *
 * A anon key usada aqui é a MESMA que já roda em qualquer navegador
 * visitante do site (embutida em Public/index.html) — segura de embutir
 * no app porque a segurança real está nas políticas de RLS configuradas
 * no Supabase, não em esconder essa chave.
 */
interface SupabaseApi {

    @GET("rest/v1/vip_sources")
    suspend fun getSourcesForMovie(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,      // formato PostgREST: "eq.123"
        @Query("media_type") mediaTypeFilter: String, // "eq.movie"
        @Query("is_active") isActiveFilter: String = "eq.true",
        @Query("season") seasonFilter: String = "is.null",
        @Query("select") select: String = "source_url,source_label,priority",
        @Query("order") order: String = "priority.desc",
    ): List<VipSource>

    @GET("rest/v1/vip_sources")
    suspend fun getSourcesForEpisode(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String = "eq.tv",
        @Query("season") seasonFilter: String,       // "eq.1"
        @Query("episode") episodeFilter: String,     // "eq.5"
        @Query("is_active") isActiveFilter: String = "eq.true",
        @Query("select") select: String = "source_url,source_label,priority",
        @Query("order") order: String = "priority.desc",
    ): List<VipSource>

    /**
     * Config de bloqueio VIP do TÍTULO inteiro — vem de uma tabela
     * separada (vip_titles), com no máximo 1 linha por (tmdb_id,
     * media_type). Independente de quantas fontes/servidores o título
     * tenha cadastradas em vip_sources: marcar ou desmarcar VIP aqui não
     * exige tocar em nenhuma fonte. Retorna lista vazia se o título nunca
     * foi configurado (equivale a "sem bloqueio").
     */
    @GET("rest/v1/vip_titles")
    suspend fun getVipTitleConfig(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("select") select: String = "vip_lock,vip_free_episode_limit",
    ): List<VipTitleConfig>
}

/** Config de bloqueio de um título inteiro, vinda da tabela vip_titles (1 linha por tmdb_id+media_type). */
@JsonClass(generateAdapter = true)
data class VipTitleConfig(
    val vip_lock: Boolean? = null,
    val vip_free_episode_limit: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VipSource(
    val source_url: String,
    val source_label: String?,
    val priority: Int?,
) {
    /** Nome amigável pra exibir na lista de servidores da tela de player. */
    val displayName: String get() = source_label ?: "Servidor"

    /**
     * Verdade se a URL é um arquivo de vídeo direto (MP4/HLS) que o
     * ExoPlayer consegue tocar nativamente. Falso se for um iframe de
     * player de terceiro (ex: MegaEmbed), que precisa cair pra WebView.
     * Mesma lógica de decisão que orienta a escolha "híbrido" combinada
     * com o usuário: nativo pro que a gente controla, WebView só onde
     * é inevitável.
     */
    val isDirectPlayable: Boolean
        get() {
            val lower = source_url.lowercase()
            return lower.endsWith(".m3u8") ||
                lower.endsWith(".mp4") ||
                lower.contains("/stream-proxy") // já é o proxy do site, serve stream direto
        }

    /**
     * URL final pra entregar ao ExoPlayer. Fontes .mp4/.m3u8 que apontam
     * pra origem http:// (comum em provedores Xtream) precisam passar
     * pelo /api/stream-proxy do backend — o mesmo mecanismo que o site
     * usa em STREAM_PROXY_ZEABUR — porque o proxy contorna bloqueio de
     * mixed-content/CORS e adiciona headers (User-Agent/Referer) que
     * muitos provedores exigem pra não retornar erro. Fontes que já
     * apontam pro próprio proxy ou já são https de CDN confiável (ex:
     * Bunny) tocam direto, sem passar por proxy adicional.
     */
    fun resolvedPlaybackUrl(apiBaseUrl: String): String {
        if (source_url.contains("/stream-proxy")) {
            return source_url
        }
        if (source_url.startsWith("https://", ignoreCase = true)) {
            return source_url
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return "${apiBaseUrl}api/stream-proxy?url=$encoded"
    }

    /**
     * As URLs candidatas pra tocar essa fonte, uma por backend
     * (Koyeb + Zeabur) — usadas pelo StreamUrlResolver pra descobrir
     * qual responde primeiro antes de entregar ao ExoPlayer. Fontes que
     * já apontam pro próprio proxy ou são CDN confiável (.b-cdn.net)
     * retornam uma lista de 1 item só (mesmo valor nos dois backends,
     * já que nesse caso a URL não depende de qual backend a serviu).
     */
    fun candidatePlaybackUrls(koyebBaseUrl: String, zeaburBaseUrl: String): List<String> {
        if (source_url.contains("/stream-proxy")) {
            return listOf(source_url)
        }
        if (source_url.startsWith("https://", ignoreCase = true)) {
            return listOf(source_url)
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return listOf(
            "${koyebBaseUrl}api/stream-proxy?url=$encoded",
            "${zeaburBaseUrl}api/stream-proxy?url=$encoded",
        )
    }
}

/** Helpers pra formatar os filtros no formato que o PostgREST espera. */
object PostgrestFilter {
    fun eq(value: Any) = "eq.$value"
}

/**
 * Decide se um episódio/filme exige VIP, a partir da config do TÍTULO
 * (não mais das fontes individuais). vip_lock=true trava tudo; senão,
 * vip_free_episode_limit trava episódios acima do número informado
 * (só faz sentido pra série; filme sempre passa episodeNumber=null).
 */
fun requiresVip(config: VipTitleConfig?, episodeNumber: Int?): Boolean {
    if (config == null) return false
    if (config.vip_lock == true) return true
    val limit = config.vip_free_episode_limit
    if (limit != null && episodeNumber != null) {
        return episodeNumber > limit
    }
    return false
}

/**
 * Endpoints de progresso de reprodução (tabela watch_progress). Diferente
 * de vip_sources (leitura pública), aqui a RLS exige o token JWT do
 * usuário logado (Authorization: Bearer ...) além da anon key — só assim
 * o Postgres sabe "quem" está gravando/lendo pra aplicar auth.uid() = user_id.
 */
interface WatchProgressApi {

    @retrofit2.http.Headers("Content-Type: application/json", "Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/watch_progress")
    suspend fun upsertProgress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("on_conflict") onConflict: String = "user_id,tmdb_id,media_type,season,episode",
        @retrofit2.http.Body body: WatchProgressUpsert,
    )

    @GET("rest/v1/watch_progress")
    suspend fun getContinueWatching(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("order") order: String = "updated_at.desc",
        @Query("limit") limit: Int = 20,
    ): List<WatchProgressEntry>

    @retrofit2.http.DELETE("rest/v1/watch_progress")
    suspend fun deleteProgress(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("season") seasonFilter: String,
        @Query("episode") episodeFilter: String,
    )
}

@JsonClass(generateAdapter = true)
data class WatchProgressUpsert(
    val user_id: String,
    val tmdb_id: Int,
    val media_type: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val poster_path: String?,
    val position_seconds: Int,
    val duration_seconds: Int,
)

@JsonClass(generateAdapter = true)
data class WatchProgressEntry(
    val tmdb_id: Int,
    val media_type: String,
    val season: Int,
    val episode: Int,
    val title: String?,
    val poster_path: String?,
    val position_seconds: Int,
    val duration_seconds: Int,
) {
    /** Fração assistida (0.0 a 1.0) — usada pra desenhar a barrinha de progresso no poster. */
    val progressFraction: Float
        get() = if (duration_seconds > 0) (position_seconds.toFloat() / duration_seconds).coerceIn(0f, 1f) else 0f

    val displayTitle: String get() = title ?: "Sem título"
}

/**
 * Endpoints da "Minha Lista" (favoritos), tabela favorites. Mesmo padrão
 * de watch_progress: RLS por auth.uid() = user_id, exige o JWT do
 * usuário logado no header Authorization além da anon key.
 *
 * Requer criar a tabela no Supabase (SQL sugerido):
 *
 *   create table favorites (
 *     user_id uuid not null references auth.users(id),
 *     tmdb_id integer not null,
 *     media_type text not null,
 *     title text,
 *     poster_path text,
 *     original_language text,
 *     created_at timestamptz not null default now(),
 *     primary key (user_id, tmdb_id, media_type)
 *   );
 *   alter table favorites enable row level security;
 *   create policy "favorites_select_own" on favorites for select
 *     using (auth.uid() = user_id);
 *   create policy "favorites_insert_own" on favorites for insert
 *     with check (auth.uid() = user_id);
 *   create policy "favorites_delete_own" on favorites for delete
 *     using (auth.uid() = user_id);
 *
 * Se a tabela já existir sem a coluna original_language, rode antes:
 *   alter table favorites add column original_language text;
 */
interface FavoritesApi {

    @retrofit2.http.Headers("Content-Type: application/json", "Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/favorites")
    suspend fun addFavorite(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("on_conflict") onConflict: String = "user_id,tmdb_id,media_type",
        @retrofit2.http.Body body: FavoriteUpsert,
    )

    @GET("rest/v1/favorites")
    suspend fun getFavorites(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
    ): List<FavoriteEntry>

    @GET("rest/v1/favorites")
    suspend fun getFavoriteByTitle(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
    ): List<FavoriteEntry>

    @retrofit2.http.DELETE("rest/v1/favorites")
    suspend fun removeFavorite(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdFilter: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
    )
}

@JsonClass(generateAdapter = true)
data class FavoriteUpsert(
    val user_id: String,
    val tmdb_id: Int,
    val media_type: String,
    val title: String?,
    val poster_path: String?,
    // Idioma original TMDB (ex: "ja", "ko", "en") — usado só pra
    // aproximar os filtros Animes/Doramas dentro de Favoritos, já que a
    // TMDB não tem um "tipo" separado pra isso (mesma abordagem já usada
    // na aba Gêneros, ver GenreCategory).
    val original_language: String? = null,
)

@JsonClass(generateAdapter = true)
data class FavoriteEntry(
    val tmdb_id: Int,
    val media_type: String,
    val title: String?,
    val poster_path: String?,
    val original_language: String? = null,
) {
    val displayTitle: String get() = title ?: "Sem título"

    /** Aproximação de "é anime": série de idioma original japonês. */
    val isAnime: Boolean get() = media_type == "tv" && original_language == "ja"

    /** Aproximação de "é dorama": série de idioma original coreano. */
    val isDorama: Boolean get() = media_type == "tv" && original_language == "ko"
}


/**
 * Comentários por título (tabela title_comments) — mesma tabela que a
 * futura aba Social vai reaproveitar pra listar posts/atividade recente
 * em vez de duplicar uma tabela nova quando essa feature for construída.
 * Leitura é pública (qualquer um vê os comentários de um título), mas
 * postar exige o JWT do usuário logado, igual watch_progress.
 */
interface CommentsApi {

    @GET("rest/v1/title_comments")
    suspend fun getComments(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String,
        @Query("select") select: String = "id,user_display_name,is_vip_author,comment_text,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100,
    ): List<TitleComment>

    @retrofit2.http.Headers("Content-Type: application/json", "Prefer: return=minimal")
    @POST("rest/v1/title_comments")
    suspend fun postComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @retrofit2.http.Body body: TitleCommentInsert,
    )
}

@JsonClass(generateAdapter = true)
data class TitleComment(
    val id: Long,
    val user_display_name: String?,
    val is_vip_author: Boolean = false,
    val comment_text: String,
    val created_at: String,
) {
    val displayAuthor: String get() = user_display_name?.takeIf { it.isNotBlank() } ?: "Usuário"
}

@JsonClass(generateAdapter = true)
data class TitleCommentInsert(
    val tmdb_id: Int,
    val media_type: String,
    val user_id: String,
    val user_display_name: String?,
    val is_vip_author: Boolean,
    val comment_text: String,
)
