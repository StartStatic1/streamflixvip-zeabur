package com.streamflixvip.app.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProgressiveSourcesTest {
    private fun src(n: Int) = (1..n).map { VipSource(source_url = "https://x/$it", source_label = "S$it", priority = it) }

    @Test
    fun pedeDeNovoAteCompletarENaoPerdeServidores() = runBlocking {
        val replies = ArrayDeque(
            listOf(
                MediaSourcesResponse(sources = src(2), partial = true, pollAfterMs = 500),
                MediaSourcesResponse(sources = src(1), partial = true, pollAfterMs = 500), // encolheu: ignora
                MediaSourcesResponse(sources = src(4), partial = false),
            ),
        )
        val partials = mutableListOf<Int>()
        val res = fetchSourcesProgressively(
            fetch = { replies.removeFirst() },
            onPartial = { partials += it.sources.size },
        )
        assertEquals(listOf(2, 2), partials)
        assertEquals(4, res.sources.size)
        assertFalse(res.partial)
    }

    @Test
    fun servidorAntigoUmPedidoSo() = runBlocking {
        var calls = 0
        val res = fetchSourcesProgressively(
            fetch = { calls++; MediaSourcesResponse(sources = src(3)) },
            onPartial = { error("nao deveria") },
        )
        assertEquals(1, calls)
        assertEquals(3, res.sources.size)
    }

    @Test
    fun erroNoMeioUsaUltimaLista() = runBlocking {
        var calls = 0
        val res = fetchSourcesProgressively(
            fetch = {
                calls++
                if (calls == 1) MediaSourcesResponse(sources = src(2), partial = true, pollAfterMs = 500)
                else throw java.io.IOException("rede")
            },
            onPartial = {},
        )
        assertEquals(2, res.sources.size)
        assertFalse(res.partial)
    }
}
