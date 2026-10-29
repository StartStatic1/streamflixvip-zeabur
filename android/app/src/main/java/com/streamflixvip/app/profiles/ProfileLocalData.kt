package com.streamflixvip.app.profiles

import android.content.Context
import android.content.SharedPreferences
import com.streamflixvip.app.network.FavoriteEntry
import com.streamflixvip.app.network.WatchProgressEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dados separados por perfil (Minha Lista e Continuar assistindo).
 * O perfil PRINCIPAL (o primeiro da conta) continua usando a nuvem, como sempre.
 * Os outros perfis guardam tudo neste aparelho, em chaves com o id do perfil — um perfil novo começa vazio.
 * Nunca lança exceção.
 */
object ProfileLocalData {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        runCatching { prefs = context.applicationContext.getSharedPreferences("profile_local_data", Context.MODE_PRIVATE) }
    }

    /** id do perfil ativo quando NÃO é o principal; null = usar a nuvem. */
    fun localScope(): String? = runCatching {
        val active = ProfileStore.active.value ?: return null
        val main = ProfileStore.profiles.value.firstOrNull() ?: return null
        if (active.id == main.id) null else active.id
    }.getOrNull()

    private fun read(key: String): JSONArray =
        runCatching { JSONArray(prefs?.getString(key, null) ?: "[]") }.getOrDefault(JSONArray())

    private fun write(key: String, arr: JSONArray) {
        runCatching { prefs?.edit()?.putString(key, arr.toString())?.apply() }
    }

    // ---- Minha Lista ----
    fun favorites(scope: String): List<FavoriteEntry> = runCatching {
        val a = read("fav_$scope")
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let {
                FavoriteEntry(
                    tmdb_id = it.optInt("id"),
                    media_type = it.optString("mt", "movie"),
                    title = it.optString("t").ifBlank { null },
                    poster_path = it.optString("p").ifBlank { null },
                    original_language = it.optString("l").ifBlank { null },
                )
            }
        }
    }.getOrDefault(emptyList())

    fun addFavorite(scope: String, tmdbId: Int, mediaType: String, title: String?, poster: String?, lang: String?): Boolean {
        val cur = favorites(scope).filterNot { it.tmdb_id == tmdbId && it.media_type == mediaType }
        val arr = JSONArray()
        arr.put(JSONObject().put("id", tmdbId).put("mt", mediaType).put("t", title ?: "").put("p", poster ?: "").put("l", lang ?: ""))
        cur.take(500).forEach { f ->
            arr.put(JSONObject().put("id", f.tmdb_id).put("mt", f.media_type).put("t", f.title ?: "").put("p", f.poster_path ?: "").put("l", f.original_language ?: ""))
        }
        write("fav_$scope", arr)
        return true
    }

    fun removeFavorite(scope: String, tmdbId: Int, mediaType: String): Boolean {
        val arr = JSONArray()
        favorites(scope).filterNot { it.tmdb_id == tmdbId && it.media_type == mediaType }.forEach { f ->
            arr.put(JSONObject().put("id", f.tmdb_id).put("mt", f.media_type).put("t", f.title ?: "").put("p", f.poster_path ?: "").put("l", f.original_language ?: ""))
        }
        write("fav_$scope", arr)
        return true
    }

    fun isFavorite(scope: String, tmdbId: Int, mediaType: String) =
        favorites(scope).any { it.tmdb_id == tmdbId && it.media_type == mediaType }

    // ---- Continuar assistindo ----
    fun progress(scope: String): List<WatchProgressEntry> = runCatching {
        val a = read("prog_$scope")
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let {
                WatchProgressEntry(
                    tmdb_id = it.optInt("id"),
                    media_type = it.optString("mt", "movie"),
                    season = it.optInt("s"),
                    episode = it.optInt("e"),
                    title = it.optString("t").ifBlank { null },
                    poster_path = it.optString("p").ifBlank { null },
                    position_seconds = it.optInt("pos"),
                    duration_seconds = it.optInt("dur"),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeProgress(scope: String, list: List<WatchProgressEntry>) {
        val arr = JSONArray()
        list.take(100).forEach { w ->
            arr.put(
                JSONObject().put("id", w.tmdb_id).put("mt", w.media_type).put("s", w.season).put("e", w.episode)
                    .put("t", w.title ?: "").put("p", w.poster_path ?: "").put("pos", w.position_seconds).put("dur", w.duration_seconds),
            )
        }
        write("prog_$scope", arr)
    }

    fun saveProgress(scope: String, entry: WatchProgressEntry) {
        val rest = progress(scope).filterNot { it.tmdb_id == entry.tmdb_id && it.media_type == entry.media_type }
        writeProgress(scope, listOf(entry) + rest)
    }

    fun removeProgress(scope: String, tmdbId: Int, mediaType: String, season: Int? = null, episode: Int? = null) {
        writeProgress(scope, progress(scope).filterNot {
            it.tmdb_id == tmdbId && it.media_type == mediaType &&
                (season == null || it.season == season) && (episode == null || it.episode == episode)
        })
    }

    /** Ao apagar um perfil, apaga os dados dele. */
    fun clear(scope: String) {
        runCatching { prefs?.edit()?.remove("fav_$scope")?.remove("prog_$scope")?.apply() }
    }
}
