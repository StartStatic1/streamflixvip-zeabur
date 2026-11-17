package com.streamflixvip.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.streamflixvip.app.BuildConfig
import com.streamflixvip.app.data.PreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Pergunta ao servidor se há algo novo e mostra no máximo 3 notificações por vez. */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        if (!PreferencesStore(ctx).notificationsEnabled) return@withContext Result.success()
        FollowedSeries.init(ctx)
        val state = ctx.getSharedPreferences("sfv_notify_state", Context.MODE_PRIVATE)
        val since = state.getLong("since", 0L)
        val seen = LinkedHashSet((state.getString("seen_list", "") ?: "").split("\n").filter { it.isNotBlank() })
        val series = FollowedSeries.active()
        val base = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        val ids = URLEncoder.encode(series.joinToString(",") { it.tmdbId.toString() }, "UTF-8")
        val json = runCatching {
            val c = URL("${base}api/notify-feed?since=$since&tv=$ids").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 25_000
            c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode !in 200..299) null
                else JSONObject(c.inputStream.bufferedReader().readText())
            } finally { c.disconnect() }
        }.getOrNull() ?: return@withContext Result.retry()

        Notifier.ensureChannels(ctx)
        var posted = 0
        val broadcasts = json.optJSONArray("broadcasts")
        if (broadcasts != null) {
            for (i in 0 until broadcasts.length()) {
                val b = broadcasts.optJSONObject(i) ?: continue
                val key = "b:" + b.optString("id")
                if (key in seen || posted >= 3) continue
                seen += key
                val tmdb = b.optInt("linkTmdbId", 0).takeIf { it > 0 }
                val media = b.optString("linkMediaType").takeIf { it == "movie" || it == "tv" }
                Notifier.post(
                    ctx, key.hashCode(), Notifier.CHANNEL_NEWS,
                    b.optString("title"), b.optString("body"), tmdb, media,
                )
                posted += 1
            }
        }
        val episodes = json.optJSONArray("episodes")
        if (episodes != null) {
            for (i in 0 until episodes.length()) {
                val e = episodes.optJSONObject(i) ?: continue
                val id = e.optInt("tmdbId")
                val s = e.optInt("season"); val ep = e.optInt("episode")
                val key = "e:$id:$s:$ep"
                if (key in seen) continue
                seen += key
                if (posted >= 3) continue
                val name = e.optString("name").ifBlank { FollowedSeries.nameOf(id) ?: "Sua série" }
                val epName = e.optString("episodeName").takeIf { it.isNotBlank() && !it.startsWith("Episódio") }
                val body = "Saiu o episódio $ep da temporada $s" + (epName?.let { ": $it" } ?: "") + ". Toque para ver."
                Notifier.post(
                    ctx, key.hashCode(), Notifier.CHANNEL_EPISODES,
                    "Episódio novo de $name", body, id, "tv",
                    posterPath = e.optString("posterPath").takeIf { it.isNotBlank() && it != "null" },
                    season = s, episode = ep,
                )
                posted += 1
            }
        }
        // guarda só os últimos 300 avisos já mostrados
        val trimmed = seen.toList().takeLast(300)
        state.edit()
            .putLong("since", json.optLong("now", System.currentTimeMillis()))
            .putString("seen_list", trimmed.joinToString("\n"))
            .apply()
        Result.success()
    }
}
