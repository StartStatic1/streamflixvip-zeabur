#!/usr/bin/env python3
"""Player: usa User-Agent okhttp em hosts Fenix (workers.dev) — exigido pelo proxy."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
if not p.exists():
    raise SystemExit("PlayerScreen.kt missing")
t = p.read_text()

if "okhttp/4.12.0" in t and "workers.dev" in t:
    print("already fenix ua")
    raise SystemExit(0)

OLD = '''private fun httpDataSourceFactoryFor(streamUrl: String): DefaultHttpDataSource.Factory {
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
}'''

NEW = '''private fun httpDataSourceFactoryFor(streamUrl: String): DefaultHttpDataSource.Factory {
    val host = try { Uri.parse(streamUrl).host.orEmpty() } catch (_: Exception) { "" }
    val origin = try {
        val u = Uri.parse(streamUrl)
        val scheme = u.scheme ?: "http"
        val h = u.host ?: return DefaultHttpDataSource.Factory()
        "$scheme://$h/"
    } catch (_: Exception) {
        streamUrl
    }
    // Fenix / CDN workers exigem UA okhttp (behaviorHints.proxyHeaders)
    val fenixHost = host.contains("workers.dev", ignoreCase = true) ||
        host.contains("fenix", ignoreCase = true) ||
        host.contains("crunchy", ignoreCase = true)
    val ua = if (fenixHost) {
        "okhttp/4.12.0"
    } else {
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
    return DefaultHttpDataSource.Factory()
        .setUserAgent(ua)
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
}'''

if OLD in t:
    t = t.replace(OLD, NEW, 1)
    p.write_text(t)
    print("fenix ua OK")
elif "httpDataSourceFactoryFor" in t:
    print("factory exists but pattern mismatch — manual check")
    raise SystemExit(2)
else:
    print("no httpDataSourceFactoryFor — skip")
    raise SystemExit(0)
