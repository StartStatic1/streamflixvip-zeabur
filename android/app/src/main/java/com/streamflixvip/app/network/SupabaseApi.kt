package com.streamflixvip.app.network

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
        @Query("select") select: String = "source_url,source_label,priority,vip_lock,vip_free_episode_limit",
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
        @Query("select") select: String = "source_url,source_label,priority,vip_lock,vip_free_episode_limit",
        @Query("order") order: String = "priority.desc",
    ): List<VipSource>

    /**
     * Busca QUALQUER linha cadastrada pra um tmdb_id de série (sem filtrar
     * por season/episode) — usada só pra descobrir vip_lock /
     * vip_free_episode_limit do título, que são setados uma vez por
     * título e valem pra todos os episódios. Mais confiável que assumir
     * que o episódio 1 sempre tem fonte cadastrada (pode não ter, se o
     * cadastro começou por outro episódio).
     */
    @GET("rest/v1/vip_sources")
    suspend fun getAnySourceForSeries(
        @Header("apikey") apiKey: String,
        @Query("tmdb_id") tmdbIdFilter: String,
        @Query("media_type") mediaTypeFilter: String = "eq.tv",
        @Query("select") select: String = "vip_lock,vip_free_episode_limit",
        @Query("limit") limit: Int = 1,
    ): List<VipConfigOnly>
}

/** Resposta enxuta de getAnySourceForSeries — só os 2 campos que importam pra decidir bloqueio. */
data class VipConfigOnly(
    val vip_lock: Boolean? = null,
    val vip_free_episode_limit: Int? = null,
)

data class VipSource(
    val source_url: String,
    val source_label: String?,
    val priority: Int?,
    // Marca se ESTE título/episódio exige VIP. Vem de vip_sources.vip_lock —
    // setado manualmente no painel pra lançamentos/títulos mais procurados.
    val vip_lock: Boolean? = null,
    // Só relevante pra série: número do último episódio liberado de graça
    // (ex: 5 = episódios 1-5 livres, 6 em diante exige VIP). Null = sem
    // limite (segue vip_lock normalmente). Setado no título, então todas
    // as linhas de episódio daquele tmdb_id compartilham o mesmo valor.
    val vip_free_episode_limit: Int? = null,
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
        if (source_url.contains("/stream-proxy") || source_url.startsWith("https://") && source_url.contains(".b-cdn.net")) {
            return source_url
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return "${apiBaseUrl}api/stream-proxy?url=$encoded"
    }
}

/** Helpers pra formatar os filtros no formato que o PostgREST espera. */
object PostgrestFilter {
    fun eq(value: Any) = "eq.$value"
}

/**
 * Decide se um episódio/filme específico exige VIP, dado o conjunto de
 * fontes já carregadas pra aquele título (todas compartilham o mesmo
 * vip_lock / vip_free_episode_limit, setados por título no painel).
 *
 * Regra: vip_lock=true trava o título inteiro. Senão, se
 * vip_free_episode_limit estiver setado, episódios com número MAIOR que
 * o limite exigem VIP (ex: limite=5 → episódio 6+ trava). Filme não usa
 * o segundo critério (episodeNumber fica null pra filme).
 */
fun requiresVip(sources: List<VipSource>, episodeNumber: Int?): Boolean {
    val sample = sources.firstOrNull() ?: return false
    if (sample.vip_lock == true) return true
    val limit = sample.vip_free_episode_limit
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
