#!/usr/bin/env python3
"""Botao Hyper na ficha ja abre pelo stream-proxy (mesmo caminho do Fontes)."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "android/app/src/main/java/com/streamflixvip/app/network/SupabaseApi.kt"
t = p.read_text()

old = """    fun resolvedPlaybackUrl(apiBaseUrl: String): String {
        if (source_url.contains("/stream-proxy")) {
            return source_url
        }
        val isIptv = source_url.contains("/movie/") or
                     source_url.contains("/series/") or
                     source_url.contains("/live/")

        if (isIptv || source_url.startsWith("https://", ignoreCase = true)) {
            return source_url
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return "${apiBaseUrl}api/stream-proxy?url=$encoded"
    }

    fun candidatePlaybackUrls(koyebBaseUrl: String, zeaburBaseUrl: String): List<String> {
        if (source_url.contains("/stream-proxy")) {
            return listOf(source_url)
        }
        val isIptv = source_url.contains("/movie/") or
                     source_url.contains("/series/") or
                     source_url.contains("/live/")

        if (isIptv || source_url.startsWith("https://", ignoreCase = true)) {
            return listOf(source_url)
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return listOf(
            "${koyebBaseUrl}api/stream-proxy?url=$encoded",
            "${zeaburBaseUrl}api/stream-proxy?url=$encoded",
        )
    }""".replace(" or\n", " ||\n")

new = """    fun resolvedPlaybackUrl(apiBaseUrl: String): String {
        if (source_url.contains("/stream-proxy")) {
            return source_url
        }
        if (needsRefererProxy(source_url)) {
            return wrapRefererProxy(apiBaseUrl, source_url)
        }
        val isIptv = source_url.contains("/movie/") or
                     source_url.contains("/series/") or
                     source_url.contains("/live/")

        if (isIptv || source_url.startsWith("https://", ignoreCase = true)) {
            return source_url
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return "${apiBaseUrl}api/stream-proxy?url=$encoded"
    }

    fun candidatePlaybackUrls(koyebBaseUrl: String, zeaburBaseUrl: String): List<String> {
        if (source_url.contains("/stream-proxy")) {
            return listOf(source_url)
        }
        if (needsRefererProxy(source_url)) {
            return listOf(wrapRefererProxy(koyebBaseUrl, source_url))
        }
        val isIptv = source_url.contains("/movie/") or
                     source_url.contains("/series/") or
                     source_url.contains("/live/")

        if (isIptv || source_url.startsWith("https://", ignoreCase = true)) {
            return listOf(source_url)
        }
        val encoded = java.net.URLEncoder.encode(source_url, "UTF-8")
        return listOf(
            "${koyebBaseUrl}api/stream-proxy?url=$encoded",
            "${zeaburBaseUrl}api/stream-proxy?url=$encoded",
        )
    }""".replace(" or\n", " ||\n")

helpers = """
fun needsRefererProxy(url: String): Boolean {
    val h = url.lowercase()
    return h.contains("hakunaymatata") || h.contains("mzfi.me")
}

fun wrapRefererProxy(apiBaseUrl: String, rawUrl: String): String {
    val encoded = java.net.URLEncoder.encode(rawUrl, "UTF-8")
    val referer = java.net.URLEncoder.encode("https://mzfi.me/", "UTF-8")
    val base = if (apiBaseUrl.endsWith("/")) apiBaseUrl else "$apiBaseUrl/"
    return "${base}api/stream-proxy?url=$encoded&referer=$referer"
}

"""

if "fun needsRefererProxy" in t:
    print("ok button proxy ja")
elif old not in t:
    print("aviso button proxy")
else:
    t = t.replace(old, new, 1)
    needle = "fun urlLooksDirectPlayable(url: String): Boolean {"
    if needle in t and "fun needsRefererProxy" not in t:
        t = t.replace(needle, helpers + needle, 1)
    p.write_text(t)
    print("ok button proxy")
print("fim apply_hyper_button_proxy")
