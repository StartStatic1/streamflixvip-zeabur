package com.streamflixvip.app.ui.livetv

import com.streamflixvip.app.network.LiveStreamOption

/**
 * Junta fontes iguais do painel "Servidores" (mesmo nome, qualidade, LEG e host).
 * O provedor manda o mesmo canal varias vezes (FHD, FHD², H265, versao .ts) com URLs
 * diferentes no mesmo servidor; antes cada uma virava uma linha "New21 FHD" repetida.
 * As alternativas seguem na lista interna para a troca automatica quando uma falha.
 */
internal data class LiveSourceGroup(
    val indices: List<Int>,
    val label: String,
    val quality: String,
    val leg: Boolean,
    val host: String,
) {
    val firstIndex: Int get() = indices.first()
}

private val ORDER = listOf("4K", "FHD", "HD", "Padrão", "SD")

internal fun liveQualityName(q: String?): String = when ((q ?: "").uppercase().replace(" ", "")) {
    "4K", "UHD" -> "4K"
    "FHD", "FULLHD" -> "FHD"
    "HD" -> "HD"
    "SD" -> "SD"
    else -> "Padrão"
}

internal fun liveShortHost(url: String): String {
    val h = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()?.removePrefix("www.")
    return if (h.isNullOrBlank() || h.length > 40) "servidor" else h
}

internal fun groupLiveSources(streams: List<LiveStreamOption>): List<LiveSourceGroup> {
    val map = LinkedHashMap<String, MutableList<Int>>()
    val meta = HashMap<String, LiveSourceGroup>()
    streams.forEachIndexed { i, s ->
        val label = s.label?.trim()?.takeIf { it.isNotEmpty() } ?: "Fonte"
        val q = liveQualityName(s.quality)
        val leg = s.leg == true
        val host = liveShortHost(s.url)
        val key = "${label.lowercase()}|$q|$leg|$host"
        map.getOrPut(key) { mutableListOf() }.add(i)
        if (key !in meta) meta[key] = LiveSourceGroup(listOf(i), label, q, leg, host)
    }
    return map.map { (k, idx) -> meta.getValue(k).copy(indices = idx) }
        .sortedWith(compareBy<LiveSourceGroup> { ORDER.indexOf(it.quality).let { r -> if (r < 0) 3 else r } }.thenBy { it.firstIndex })
}
