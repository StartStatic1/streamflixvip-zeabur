package com.streamflixvip.app.network

import kotlinx.coroutines.delay

/**
 * Busca servidores aos poucos: pede com fast=1, mostra o que ja chegou e pede de novo
 * enquanto o servidor disser partial=true. Servidor antigo (sem "partial") = 1 pedido so.
 *
 * @param onPartial chamado com cada lista parcial (nao vazia) antes da final
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
