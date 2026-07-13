package com.streamflixvip.app.network

import retrofit2.http.GET
import retrofit2.http.Header
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
}

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
