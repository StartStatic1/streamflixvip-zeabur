package com.streamflixvip.tv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request

object StreamUrlResolver {

    suspend fun resolveFastest(candidates: List<String>): String {
        if (candidates.size <= 1) return candidates.firstOrNull().orEmpty()

        val winner = withTimeoutOrNull(6_000L) {
            coroutineScope {
                val results = Channel<String?>(capacity = candidates.size)
                candidates.forEach { url ->
                    launch {
                        results.send(if (probe(url)) url else null)
                    }
                }
                var found: String? = null
                repeat(candidates.size) {
                    if (found == null) {
                        val value = results.receive()
                        if (value != null) found = value
                    }
                }
                found
            }
        }
        return winner ?: candidates.first()
    }

    private suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head().build()
            NetworkModule.fastProbeClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 300..399
            }
        } catch (_: Exception) {
            false
        }
    }
}
