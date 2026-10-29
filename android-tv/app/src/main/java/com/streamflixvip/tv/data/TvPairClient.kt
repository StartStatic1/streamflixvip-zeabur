package com.streamflixvip.tv.data

import com.streamflixvip.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Pareamento por QR (servidor /api/tv-pair). Tudo devolve null em caso de falha — nunca lança. */
object TvPairClient {
    data class Session(val pairId: String, val pin: String, val url: String)

    private suspend fun post(body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/tv-pair").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode !in 200..299) return@runCatching null
                JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    suspend fun start(deviceId: String): Session? {
        val j = post(JSONObject().put("action", "start").put("deviceId", deviceId)) ?: return null
        val id = j.optString("pairId"); val pin = j.optString("pin"); val url = j.optString("url")
        return if (id.isNotBlank() && pin.length == 6 && url.startsWith("http")) Session(id, pin, url) else null
    }

    /** "waiting" | "done" | "expired" | null (sem rede) */
    suspend fun poll(s: Session, deviceId: String): String? =
        post(JSONObject().put("action", "poll").put("pairId", s.pairId).put("deviceId", deviceId))?.optString("status")
}
