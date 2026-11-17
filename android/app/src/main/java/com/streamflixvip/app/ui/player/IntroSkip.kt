package com.streamflixvip.app.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tempos de abertura e créditos de um episódio.
 * Fonte: TheIntroDB (base aberta e gratuita, uso pelo app é permitido; sem chave).
 * Sem dados: o player usa o botão "Pular abertura" por estimativa (+85 s nos
 * primeiros 3 min) e aprende com o que a pessoa faz em cada série.
 */
internal data class IntroTimes(
    val introStartMs: Long,
    val introEndMs: Long,
    val creditsStartMs: Long = 0L,
    val fromDatabase: Boolean = true,
)

internal object IntroSkip {
    const val HEURISTIC_JUMP_MS = 85_000L
    const val HEURISTIC_FROM_MS = 5_000L
    const val HEURISTIC_UNTIL_MS = 180_000L

    private val cache = HashMap<String, IntroTimes?>()

    suspend fun fetch(tmdbId: Int, season: Int, episode: Int, durationMs: Long): IntroTimes? {
        if (tmdbId <= 0 || season <= 0 || episode <= 0) return null
        val key = "$tmdbId:$season:$episode"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dur = if (durationMs > 0) "&duration_ms=$durationMs" else ""
                val c = URL("https://api.theintrodb.org/v4/media?tmdb_id=$tmdbId&season=$season&episode=$episode$dur")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 6_000; c.readTimeout = 8_000
                try {
                    if (c.responseCode != 200) return@runCatching null
                    parse(c.inputStream.bufferedReader().readText(), durationMs)
                } finally { c.disconnect() }
            }.getOrNull()
        }
        synchronized(cache) { cache[key] = result }
        return result
    }

    fun parse(json: String, durationMs: Long): IntroTimes? {
        val o = JSONObject(json)
        val intro = o.optJSONArray("intro")?.optJSONObject(0)
        val credits = o.optJSONArray("credits")?.optJSONObject(0)
        val iStart = intro?.let { if (it.isNull("start_ms")) 0L else it.optLong("start_ms", 0L) } ?: -1L
        val iEnd = intro?.let { if (it.isNull("end_ms")) -1L else it.optLong("end_ms", -1L) } ?: -1L
        val cStart = credits?.let { if (it.isNull("start_ms")) 0L else it.optLong("start_ms", 0L) } ?: 0L
        // créditos só valem se fizerem sentido para a duração deste arquivo
        val creditsOk = cStart > 0 && (durationMs <= 0 || (cStart < durationMs && durationMs - cStart < 8 * 60_000L))
        val introOk = iStart >= 0 && iEnd > iStart + 3_000 && iEnd - iStart < 200_000 &&
            (durationMs <= 0 || iEnd < durationMs / 2)
        if (!introOk && !creditsOk) return null
        return IntroTimes(
            introStartMs = if (introOk) iStart else -1L,
            introEndMs = if (introOk) iEnd else -1L,
            creditsStartMs = if (creditsOk) cStart else 0L,
        )
    }

    /** Abertura aprendida desta série ("início,fim" em ms), salva quando a pessoa pula. */
    fun parseLearned(raw: String): IntroTimes? {
        val p = raw.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (p.size != 2 || p[1] <= p[0] || p[1] - p[0] > 200_000) return null
        return IntroTimes(p[0], p[1], fromDatabase = false)
    }
}
