package com.streamflixvip.tv.network

import kotlinx.coroutines.delay

/**
 * Busca servidores aos poucos (igual ao app de celular 16.3): pede com fast=1,
 * mostra o que ja chegou e pede de novo enquanto o servidor disser partial=true.
 * Servidor antigo (sem "partial") = 1 pedido so.
 *
 * @return a ultima resposta (final). Erro no 1o pedido sobe; erro nos seguintes = usa a ultima.
 */
suspend fun fetchSourcesProgressively(
    fetch: suspend () -> MediaSourcesResponse,
    onPartial: suspend (MediaSourcesResponse) -> Unit,
    maxPolls: Int = 8,
): MediaSourcesResponse {
    var res = fetch()
    var polls = 0
    while (res.partial && res.code == null && polls < maxPolls) {
        if (res.sources.isNotEmpty()) onPartial(res)
        delay((res.pollAfterMs ?: 1500L).coerceIn(500L, 5000L))
        val next = try { fetch() } catch (_: Exception) { null } ?: return res.copy(partial = false)
        // nunca perde servidores que ja apareceram
        res = if (next.sources.size >= res.sources.size || next.code != null) next
        else next.copy(sources = res.sources)
        polls++
    }
    return res.copy(partial = false)
}

/** Mensagem amigavel quando nao ha servidor. */
fun sourcesMessage(res: MediaSourcesResponse?): String = when {
    res == null -> "Não foi possível buscar os servidores. Verifique a internet."
    res.code == "VIP_REQUIRED" -> res.error ?: "Este título é exclusivo VIP."
    res.code == "AUTH_REQUIRED" -> res.error ?: "Ative este aparelho para assistir."
    !res.error.isNullOrBlank() -> res.error
    else -> "Nenhum servidor disponível para este título agora."
}
