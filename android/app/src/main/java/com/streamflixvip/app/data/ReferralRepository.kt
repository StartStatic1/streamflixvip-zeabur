package com.streamflixvip.app.data

import android.content.Context
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** "Indique e ganhe": código pessoal, contagem de amigos e uso do código de um amigo. */
data class ReferralInfo(
    val code: String,
    val link: String,
    val shareText: String,
    val rewardDays: Int,
    val invited: Int,
    val paid: Int,
    val daysEarned: Int,
    val usedCode: String?,
)

/** Números da retrospectiva — só o que o servidor conseguiu contar de verdade. */
data class RetroTitle(val tmdbId: Int, val mediaType: String, val title: String, val posterPath: String?, val minutes: Int, val episodes: Int)
data class RetroStats(
    val year: Int,
    val hasData: Boolean,
    val titlesWatched: Int,
    val moviesWatched: Int,
    val seriesWatched: Int,
    val episodesWatched: Int,
    val minutesWatched: Int,
    val topGenres: List<String>,
    val topSeries: List<RetroTitle>,
    val topTitles: List<RetroTitle>,
    val favoritesAdded: Int,
    val favoriteTitles: List<RetroTitle>,
    val comments: Int,
    val dataSince: String?,
)

object ReferralRepository {
    private val json = "application/json; charset=utf-8".toMediaType()
    private fun url(path: String) = BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')

    /** GET/POST autenticado. Devolve (código HTTP, corpo JSON) ou null sem rede/sessão. */
    private suspend fun call(path: String, body: JSONObject? = null): Pair<Int, JSONObject?>? = withContext(Dispatchers.IO) {
        val token = AuthTokenHelper.validAccessToken() ?: return@withContext null
        runCatching {
            val b = Request.Builder().url(url(path)).header("Authorization", "Bearer $token")
            if (body != null) b.post(body.toString().toRequestBody(json))
            NetworkModule.okHttpClient.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                r.code to runCatching { JSONObject(text) }.getOrNull()
            }
        }.getOrNull()
    }

    suspend fun load(): ReferralInfo? {
        val (code, j) = call("api/referral") ?: return null
        if (code !in 200..299 || j == null || j.optString("code").isBlank()) return null
        return ReferralInfo(
            code = j.optString("code"),
            link = j.optString("link"),
            shareText = j.optString("shareText"),
            rewardDays = j.optInt("rewardDays", 7),
            invited = j.optInt("invited"),
            paid = j.optInt("paid"),
            daysEarned = j.optInt("daysEarned"),
            usedCode = j.optString("usedCode").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    /** Usa o código de um amigo. Devolve (deu certo?, mensagem em pt-BR). */
    suspend fun apply(code: String): Pair<Boolean, String> {
        val res = call("api/referral", JSONObject().put("action", "apply").put("code", code.trim()))
            ?: return false to "Sem conexão. Tente de novo."
        val (http, j) = res
        val ok = http in 200..299 && j?.optBoolean("ok") == true
        val msg = j?.optString(if (ok) "message" else "error")?.takeIf { it.isNotBlank() }
            ?: if (ok) "Código aplicado!" else "Não foi possível usar o código."
        return ok to msg
    }

    suspend fun retro(year: Int): RetroStats? {
        val (code, j) = call("api/retro?year=$year") ?: return null
        if (code !in 200..299 || j == null) return null
        fun titles(a: JSONArray?): List<RetroTitle> = (0 until (a?.length() ?: 0)).mapNotNull { i ->
            a!!.optJSONObject(i)?.let {
                RetroTitle(
                    it.optInt("tmdbId"), it.optString("mediaType"), it.optString("title"),
                    it.optString("posterPath").takeIf { p -> p.isNotBlank() && p != "null" },
                    it.optInt("minutes"), it.optInt("episodes"),
                )
            }
        }
        val genres = j.optJSONArray("topGenres")
        return RetroStats(
            year = j.optInt("year", year),
            hasData = j.optBoolean("hasData"),
            titlesWatched = j.optInt("titlesWatched"),
            moviesWatched = j.optInt("moviesWatched"),
            seriesWatched = j.optInt("seriesWatched"),
            episodesWatched = j.optInt("episodesWatched"),
            minutesWatched = j.optInt("minutesWatched"),
            topGenres = (0 until (genres?.length() ?: 0)).map { genres!!.optString(it) }.filter { it.isNotBlank() },
            topSeries = titles(j.optJSONArray("topSeries")),
            topTitles = titles(j.optJSONArray("topTitles")),
            favoritesAdded = j.optInt("favoritesAdded"),
            favoriteTitles = titles(j.optJSONArray("favoriteTitles")),
            comments = j.optInt("comments"),
            dataSince = j.optString("dataSince").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    // ── Código digitado no login: guardado até a conta existir ──
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sfv_referral", Context.MODE_PRIVATE)
    fun savePending(ctx: Context, code: String) {
        val c = code.uppercase().filter { it.isLetterOrDigit() }.take(12)
        prefs(ctx).edit().apply { if (c.isBlank()) remove("pending") else putString("pending", c) }.apply()
    }
    fun pending(ctx: Context): String? = prefs(ctx).getString("pending", null)
    fun clearPending(ctx: Context) { prefs(ctx).edit().remove("pending").apply() }
}
