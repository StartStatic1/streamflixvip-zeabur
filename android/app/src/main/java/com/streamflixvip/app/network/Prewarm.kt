package com.streamflixvip.app.network

import com.streamflixvip.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Durante a abertura: já abre as conexões (DNS + TLS) com a API e o servidor de
 * imagens, para a Home carregar mais rápido. Nunca lança; sem internet só termina.
 */
object Prewarm {
    suspend fun run() = withContext(Dispatchers.IO) {
        coroutineScope {
            listOf(BuildConfig.API_BASE_URL, "https://image.tmdb.org/", "https://api.themoviedb.org/").map { url ->
                async {
                    runCatching {
                        NetworkModule.okHttpClient.newCall(Request.Builder().url(url).head().build()).execute().close()
                    }
                }
            }.awaitAll()
        }
        Unit
    }
}
