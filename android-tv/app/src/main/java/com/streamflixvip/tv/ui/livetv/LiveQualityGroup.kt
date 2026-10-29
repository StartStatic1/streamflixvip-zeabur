package com.streamflixvip.tv.ui.livetv

import com.streamflixvip.tv.network.LiveChannel
import com.streamflixvip.tv.network.LiveStreamOption

/**
 * Junta o mesmo canal em qualidades diferentes ("Telecine Pipoca HD", "... FHD", "... SD", "... 4K")
 * numa entrada só, com todas as fontes e a qualidade marcada em cada uma (como no Hugo Player).
 * Sem regex (seguro em qualquer Android). Qualquer erro devolve a lista original.
 */
object LiveQualityGroup {
    private val QUALITY = mapOf(
        "sd" to "SD", "hd" to "HD", "fhd" to "FHD", "fullhd" to "FHD", "1080p" to "FHD", "720p" to "HD",
        "4k" to "4K", "uhd" to "4K", "2160p" to "4K",
    )
    private val NOISE = setOf("h265", "h.265", "hevc", "h264", "alt", "hdr", "full")
    private const val SEPS = "[]()|_-:"

    private fun tokens(name: String): List<String> {
        val sb = StringBuilder(name.length)
        for (c in name) sb.append(if (SEPS.indexOf(c) >= 0) ' ' else c)
        return sb.toString().split(' ').filter { it.isNotBlank() }
    }

    /** nome sem as marcas de qualidade + qualidade encontrada (ou null) */
    fun split(name: String): Pair<String, String?> {
        val t = tokens(name)
        var q: String? = null
        val keep = ArrayList<String>(t.size)
        var i = 0
        while (i < t.size) {
            val low = t[i].lowercase()
            // "FULL HD" -> FHD
            if (low == "full" && i + 1 < t.size && t[i + 1].lowercase() == "hd") { q = "FHD"; i += 2; continue }
            val mapped = QUALITY[low]
            when {
                mapped != null -> { if (q == null || rank(mapped) > rank(q)) q = mapped }
                low in NOISE -> {}
                else -> keep.add(t[i])
            }
            i++
        }
        val base = keep.joinToString(" ").trim()
        return (if (base.length >= 2) base else name.trim()) to q
    }

    private fun rank(q: String?) = when (q) { "4K" -> 4; "FHD" -> 3; "HD" -> 2; "SD" -> 1; else -> 0 }

    fun group(channels: List<LiveChannel>): List<LiveChannel> = runCatching {
        val order = LinkedHashMap<String, MutableList<LiveChannel>>()
        val names = HashMap<String, String>()
        for (ch in channels) {
            val (base, _) = split(ch.name)
            val key = base.lowercase() + "|" + (ch.categoryId ?: "")
            order.getOrPut(key) { mutableListOf() }.add(ch)
            names.putIfAbsent(key, base)
        }
        order.map { (key, list) ->
            if (list.size == 1) return@map list[0]
            val streams = ArrayList<LiveStreamOption>()
            val seenUrls = HashSet<String>()
            for (ch in list) {
                val q = split(ch.name).second
                for (s in ch.streams) {
                    if (!seenUrls.add(s.url)) continue
                    streams.add(if (s.quality.isNullOrBlank() && q != null) s.copy(quality = q) else s)
                }
            }
            val first = list.first()
            first.copy(
                name = names[key] ?: first.name,
                logo = list.firstOrNull { !it.logo.isNullOrBlank() }?.logo ?: first.logo,
                streams = streams.sortedByDescending { rank(it.quality) },
            )
        }
    }.getOrElse { channels }
}
