#!/usr/bin/env python3
"""Melhora HTTP do ExoPlayer: UA browser, Referer origem, redirects http->https."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

HELPER = '''
/** Factory HTTP para IPTV/CDN: muitos painéis bloqueiam UA do ExoPlayer e
 *  exigem redirect http→https (CDN). Referer = origem do host, nao a URL inteira. */
private fun httpDataSourceFactoryFor(streamUrl: String): DefaultHttpDataSource.Factory {
    val origin = try {
        val u = Uri.parse(streamUrl)
        val scheme = u.scheme ?: "http"
        val host = u.host ?: return DefaultHttpDataSource.Factory()
        "$scheme://$host/"
    } catch (_: Exception) {
        streamUrl
    }
    return DefaultHttpDataSource.Factory()
        .setUserAgent(
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to origin,
                "Origin" to origin.trimEnd('/'),
                "Accept" to "*/*",
                "Connection" to "keep-alive",
            ),
        )
}

'''

if "httpDataSourceFactoryFor" not in t:
    t = t.replace(
        "private fun isLikelyHls(url: String): Boolean {",
        HELPER + "private fun isLikelyHls(url: String): Boolean {",
        1,
    )
    print("helper inserted")
else:
    print("helper already")

# Replace factory blocks - pattern without allowCrossProtocol yet
old1 = '''        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setDefaultRequestProperties(mapOf("Referer" to url, "Connection" to "keep-alive", "Icy-MetaData" to "1"))'''
new1 = '''        val httpDataSourceFactory = httpDataSourceFactoryFor(url)'''

old2 = '''            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(mapOf("Referer" to streamUrl, "Connection" to "keep-alive", "Icy-MetaData" to "1"))'''
new2 = '''            val httpDataSourceFactory = httpDataSourceFactoryFor(streamUrl)'''

old3 = '''        val dsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to activeUrl,
                    "Connection" to "keep-alive",
                    "Icy-MetaData" to "1",
                ),
            )'''
new3 = '''        val dsFactory = httpDataSourceFactoryFor(activeUrl)'''

for name, old, new in [("init", old1, new1), ("reload", old2, new2), ("sub", old3, new3)]:
    if old in t:
        t = t.replace(old, new, 1)
        print(f"replaced {name}")
    elif new.strip() in t or (name == "init" and "httpDataSourceFactoryFor(url)" in t):
        print(f"already {name}")
    else:
        print(f"WARN missing {name}")

p.write_text(t)
print("DONE")
