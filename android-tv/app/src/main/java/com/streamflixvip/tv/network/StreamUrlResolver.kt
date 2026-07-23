package com.streamflixvip.tv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request

/**
 * Resolve, entre os backends que servem /api/stream-proxy (Koyeb e
 * Zeabur), qual responde primeiro pra uma fonte específica — e usa esse.
 *
 * Existe porque alguns provedores de vídeo apresentavam erro diferente
 * dependendo de qual plataforma fazia o proxy (timeout numa, CORS/erro
 * 5xx na outra); em vez de a pessoa ficar presa a um backend fixo que
 * às vezes falha, os dois competem de verdade (por tempo de resposta) e
 * o primeiro que responder com sucesso (HTTP 2xx/3xx) "ganha" e é o que
 * o ExoPlayer usa.
 *
 * Só faz sentido pra fontes que passam pelo /api/stream-proxy (fontes
 * .b-cdn.net ou já apontando pro próprio proxy tocam direto, sem
 * precisar de race nenhuma — ver VipSource.candidatePlaybackUrls).
 */
object StreamUrlResolver {

    /**
     * Testa as URLs candidatas em paralelo com um HEAD leve (sem baixar
     * o vídeo inteiro) e retorna a primeira que respondeu OK, na ordem
     * real de chegada — não na ordem da lista. Cada probe roda na sua
     * própria coroutine (filha do coroutineScope local, então nada vaza
     * pra fora da função) e publica o resultado num Channel assim que
     * termina; o primeiro valor não-nulo recebido do canal vence.
     *
     * Se nenhuma responder dentro do timeout total (6s), cai no primeiro
     * candidato da lista mesmo assim — melhor tentar tocar e deixar o
     * ExoPlayer reportar erro (já tratado na UI) do que travar a tela
     * pra sempre numa race que nunca resolve.
     */
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
