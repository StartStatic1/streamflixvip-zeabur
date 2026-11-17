package com.streamflixvip.tv.data

import com.streamflixvip.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** "Indique e ganhe" na TV: só mostra o código do dono da TV ativada (somente leitura). Nunca lança. */
object ReferralTvClient {
    data class Info(val code: String?, val rewardDays: Int, val message: String?)

    suspend fun load(deviceId: String): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val u = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/referral?deviceId=" + URLEncoder.encode(deviceId, "UTF-8")
            val c = URL(u).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 8000
                c.readTimeout = 8000
                if (c.responseCode !in 200..299) return@runCatching null
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                Info(
                    code = j.optString("code").takeIf { it.isNotBlank() && it != "null" },
                    rewardDays = j.optInt("rewardDays", 7),
                    message = j.optString("message").takeIf { it.isNotBlank() && it != "null" },
                )
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
