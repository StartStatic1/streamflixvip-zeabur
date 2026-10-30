package com.streamflixvip.tv.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Progresso ("Continuar assistindo") e favoritos ("Minha lista") da TV.
 *
 * A TV autentica por código VIP + device_id, sem login de e-mail/senha.
 * As tabelas do Supabase (watch_progress / favorites) exigem auth.uid()
 * via JWT — então aqui persistimos LOCALMENTE no aparelho. Funciona offline,
 * não depende de RLS e não quebra a ativação VIP já existente.
 *
 * Chave de progresso: tmdbId + mediaType + season + episode
 * Chave de favorito: tmdbId + mediaType
 */
class LocalLibraryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Cada perfil tem o seu "Continuar assistindo", assistidos e Minha lista (igual ao celular).
    // Dados antigos (sem perfil) ficam com o perfil que estava em uso na 1ª abertura após a atualização.
    private fun pid(): String = com.streamflixvip.tv.profiles.ProfileStore.active.value?.id ?: "default"
    private fun k(base: String): String = base + "__" + pid()

    init {
        runCatching {
            val owner = com.streamflixvip.tv.profiles.ProfileStore.active.value?.id
            if (owner != null && !prefs.getBoolean(KEY_MIGRATED, false)) {
                val e = prefs.edit()
                for (base in listOf(KEY_PROGRESS, KEY_FAVORITES, KEY_WATCHED)) {
                    val old = prefs.getString(base, null)
                    if (old != null && prefs.getString(base + "__" + owner, null) == null) e.putString(base + "__" + owner, old)
                    e.remove(base)
                }
                e.putBoolean(KEY_MIGRATED, true).apply()
            }
        }
    }

    // ── Continuar assistindo ────────────────────────────────────────────────

    fun saveProgress(entry: LocalWatchProgress) {
        if (entry.durationSeconds <= 0 || entry.positionSeconds < 5) return
        val fraction = entry.positionSeconds.toFloat() / entry.durationSeconds
        if (fraction >= 0.95f) {
            markWatched(entry.tmdbId, entry.mediaType, entry.season, entry.episode)
            removeProgress(entry.tmdbId, entry.mediaType, entry.season, entry.episode)
            return
        }
        val list = loadProgressRaw().toMutableList()
        val key = progressKey(entry.tmdbId, entry.mediaType, entry.season, entry.episode)
        list.removeAll { progressKey(it.tmdbId, it.mediaType, it.season, it.episode) == key }
        list.add(0, entry.copy(updatedAt = System.currentTimeMillis()))
        // Limita histórico pra não crescer sem fim no aparelho
        saveProgressRaw(list.take(40))
    }

    /**
     * Uma entrada por título (filme ou série): o episódio mais recente
     * assistido. Mesma regra do mobile ProgressRepository.
     */
    fun getContinueWatching(): List<LocalWatchProgress> {
        return loadProgressRaw()
            .sortedByDescending { it.updatedAt }
            .distinctBy { it.tmdbId to it.mediaType }
            .take(20)
    }

    fun removeProgress(tmdbId: Int, mediaType: String, season: Int, episode: Int) {
        val key = progressKey(tmdbId, mediaType, season, episode)
        val list = loadProgressRaw().filter {
            progressKey(it.tmdbId, it.mediaType, it.season, it.episode) != key
        }
        saveProgressRaw(list)
    }

    fun clearAllProgress() {
        prefs.edit().remove(k(KEY_PROGRESS)).remove(k(KEY_WATCHED)).apply()
    }

    /** Tira o titulo inteiro do "Continuar assistindo" (todas as entradas dele). */
    fun removeTitleProgress(tmdbId: Int, mediaType: String) {
        saveProgressRaw(loadProgressRaw().filterNot { it.tmdbId == tmdbId && it.mediaType == mediaType })
    }

    /** Entrada mais recente desse titulo (qualquer episodio). */
    fun getLatestProgressForTitle(tmdbId: Int, mediaType: String): LocalWatchProgress? =
        loadProgressRaw().filter { it.tmdbId == tmdbId && it.mediaType == mediaType }
            .maxByOrNull { it.updatedAt }

    /** Todos os progressos desse titulo (para barrinhas nos episodios). */
    fun getProgressListForTitle(tmdbId: Int, mediaType: String): List<LocalWatchProgress> =
        loadProgressRaw().filter { it.tmdbId == tmdbId && it.mediaType == mediaType }

    // ── Assistidos (>= 95%) ────────────────────────────────────────────────

    fun markWatched(tmdbId: Int, mediaType: String, season: Int, episode: Int) {
        val set = watchedRaw().toMutableList()
        val key = progressKey(tmdbId, mediaType, season, episode)
        set.remove(key)
        set.add(0, key)
        prefs.edit().putString(k(KEY_WATCHED), JSONArray(set.take(600)).toString()).apply()
    }

    /** Pares (temporada, episodio) ja assistidos desse titulo. */
    fun watchedEpisodes(tmdbId: Int, mediaType: String): Set<Pair<Int, Int>> {
        val prefix = "$tmdbId|$mediaType|"
        return watchedRaw().filter { it.startsWith(prefix) }.mapNotNull {
            val parts = it.split("|")
            val s = parts.getOrNull(2)?.toIntOrNull()
            val e = parts.getOrNull(3)?.toIntOrNull()
            if (s != null && e != null) s to e else null
        }.toSet()
    }

    private fun watchedRaw(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(k(KEY_WATCHED), "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    fun getProgressFor(
        tmdbId: Int,
        mediaType: String,
        season: Int,
        episode: Int,
    ): LocalWatchProgress? {
        val key = progressKey(tmdbId, mediaType, season, episode)
        return loadProgressRaw().firstOrNull {
            progressKey(it.tmdbId, it.mediaType, it.season, it.episode) == key
        }
    }

    // ── Favoritos ───────────────────────────────────────────────────────────

    fun addFavorite(entry: LocalFavorite) {
        val list = loadFavoritesRaw().toMutableList()
        val key = favKey(entry.tmdbId, entry.mediaType)
        list.removeAll { favKey(it.tmdbId, it.mediaType) == key }
        list.add(0, entry.copy(createdAt = System.currentTimeMillis()))
        saveFavoritesRaw(list.take(100))
    }

    fun removeFavorite(tmdbId: Int, mediaType: String) {
        val key = favKey(tmdbId, mediaType)
        saveFavoritesRaw(loadFavoritesRaw().filter { favKey(it.tmdbId, it.mediaType) != key })
    }

    fun isFavorite(tmdbId: Int, mediaType: String): Boolean {
        val key = favKey(tmdbId, mediaType)
        return loadFavoritesRaw().any { favKey(it.tmdbId, it.mediaType) == key }
    }

    fun getFavorites(): List<LocalFavorite> =
        loadFavoritesRaw().sortedByDescending { it.createdAt }

    fun clearAllFavorites() {
        prefs.edit().remove(k(KEY_FAVORITES)).apply()
    }

    fun toggleFavorite(entry: LocalFavorite): Boolean {
        return if (isFavorite(entry.tmdbId, entry.mediaType)) {
            removeFavorite(entry.tmdbId, entry.mediaType)
            false
        } else {
            addFavorite(entry)
            true
        }
    }

    // ── Persistência JSON ───────────────────────────────────────────────────

    private fun progressKey(tmdbId: Int, mediaType: String, season: Int, episode: Int) =
        "$tmdbId|$mediaType|$season|$episode"

    private fun favKey(tmdbId: Int, mediaType: String) = "$tmdbId|$mediaType"

    private fun loadProgressRaw(): List<LocalWatchProgress> {
        val raw = prefs.getString(k(KEY_PROGRESS), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                LocalWatchProgress(
                    tmdbId = o.optInt("tmdbId"),
                    mediaType = o.optString("mediaType", "movie"),
                    season = o.optInt("season"),
                    episode = o.optInt("episode"),
                    title = o.optString("title", "Sem título"),
                    posterPath = o.optString("posterPath").takeIf { it.isNotBlank() && it != "null" },
                    positionSeconds = o.optInt("positionSeconds"),
                    durationSeconds = o.optInt("durationSeconds"),
                    updatedAt = o.optLong("updatedAt", 0L),
                    backdropPath = o.optString("backdropPath").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveProgressRaw(list: List<LocalWatchProgress>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("tmdbId", e.tmdbId)
                put("mediaType", e.mediaType)
                put("season", e.season)
                put("episode", e.episode)
                put("title", e.title)
                put("posterPath", e.posterPath ?: JSONObject.NULL)
                put("positionSeconds", e.positionSeconds)
                put("durationSeconds", e.durationSeconds)
                put("updatedAt", e.updatedAt)
                put("backdropPath", e.backdropPath ?: JSONObject.NULL)
            })
        }
        prefs.edit().putString(k(KEY_PROGRESS), arr.toString()).apply()
    }

    private fun loadFavoritesRaw(): List<LocalFavorite> {
        val raw = prefs.getString(k(KEY_FAVORITES), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                LocalFavorite(
                    tmdbId = o.optInt("tmdbId"),
                    mediaType = o.optString("mediaType", "movie"),
                    title = o.optString("title", "Sem título"),
                    posterPath = o.optString("posterPath").takeIf { it.isNotBlank() && it != "null" },
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveFavoritesRaw(list: List<LocalFavorite>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("tmdbId", e.tmdbId)
                put("mediaType", e.mediaType)
                put("title", e.title)
                put("posterPath", e.posterPath ?: JSONObject.NULL)
                put("createdAt", e.createdAt)
            })
        }
        prefs.edit().putString(k(KEY_FAVORITES), arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "sfv_tv_library"
        private const val KEY_PROGRESS = "watch_progress"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_WATCHED = "watched"
        private const val KEY_MIGRATED = "migrated_profiles_v1"
    }
}

data class LocalWatchProgress(
    val tmdbId: Int,
    val mediaType: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val posterPath: String?,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val backdropPath: String? = null,
) {
    val remainingMinutes: Int
        get() = ((durationSeconds - positionSeconds).coerceAtLeast(0) / 60)

    val progressFraction: Float
        get() = if (durationSeconds > 0) {
            (positionSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f)
        } else 0f

    val displaySubtitle: String?
        get() = if (mediaType == "tv" && (season > 0 || episode > 0)) {
            "T$season · E$episode"
        } else null
}

data class LocalFavorite(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val createdAt: Long = System.currentTimeMillis(),
)
