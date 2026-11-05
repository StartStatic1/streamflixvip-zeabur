package com.streamflixvip.tv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AIOStreams / ElfHosted prendem o link do vídeo ao IP que pediu a LISTA de streams.
 * Por isso a TV pede a lista direto (da própria internet) e manda ao nosso servidor só para
 * formatar/filtrar. O servidor nunca abre esses links. Endereços nunca vão para log.
 */
object AddonRelay {
    private val direct = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    private val cache = HashMap<String, Pair<Long, List<VipSource>>>()
    private const val CACHE_MS = 5 * 60_000L

    suspend fun fetch(tmdbId: Int, type: String, season: Int?, episode: Int?, plan: List<RelayAddon>?): List<VipSource> {
        if (plan.isNullOrEmpty()) return emptyList()
        val key = "$type:$tmdbId:$season:$episode"
        synchronized(cache) { cache[key]?.let { (at, v) -> if (System.currentTimeMillis() - at < CACHE_MS && v.isNotEmpty()) return v } }
        val out = withContext(Dispatchers.IO) {
            coroutineScope {
                plan.map { a -> async { runCatching { one(a, tmdbId, type, season, episode) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
            }
        }
        if (out.isNotEmpty()) synchronized(cache) { cache[key] = System.currentTimeMillis() to out; if (cache.size > 30) cache.keys.firstOrNull()?.let { cache.remove(it) } }
        return out
    }

    private fun one(a: RelayAddon, tmdbId: Int, type: String, season: Int?, episode: Int?): List<VipSource> {
        var streams: JSONArray? = null
        for (u in a.urls.take(4)) {
            val text = runCatching {
                direct.newCall(Request.Builder().url(u).header("Accept", "application/json").build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() else null
                }
            }.getOrNull() ?: continue
            val arr = runCatching { JSONObject(text).optJSONArray("streams") }.getOrNull()
            if (arr != null && arr.length() > 0) { streams = arr; break }
        }
        val s = streams ?: return emptyList()
        val body = JSONObject().put("addonId", a.addonId).put("streams", s).toString()
        val q = StringBuilder("api/media-sources?relay_format=1&tmdb_id=$tmdbId&type=$type")
        if (season != null) q.append("&season=$season")
        if (episode != null) q.append("&episode=$episode")
        val req = Request.Builder().url(NetworkModule.ZEABUR_BASE_URL + q)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val resp = NetworkModule.authedClient.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null } ?: return emptyList()
        val arr = runCatching { JSONObject(resp).optJSONArray("sources") }.getOrNull() ?: return emptyList()
        val list = ArrayList<VipSource>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("source_url").takeIf { it.isNotBlank() } ?: continue
            list.add(VipSource(source_url = url, source_label = o.optString("source_label").takeIf { it.isNotBlank() }, priority = if (o.has("priority")) o.optInt("priority") else null, requires_vpn = if (o.has("requires_vpn")) o.optBoolean("requires_vpn") else null))
        }
        return list
    }

    /** Junta a lista do servidor com a da TV (sem repetir), na ordem do servidor + AIOStreams depois. */
    fun merge(server: List<VipSource>, relay: List<VipSource>): List<VipSource> =
        (server + relay).distinctBy { it.source_url.trim() }

    suspend fun fetchWithin(ms: Long, tmdbId: Int, type: String, season: Int?, episode: Int?, plan: List<RelayAddon>?): List<VipSource> =
        withTimeoutOrNull(ms) { fetch(tmdbId, type, season, episode, plan) } ?: emptyList()
}
