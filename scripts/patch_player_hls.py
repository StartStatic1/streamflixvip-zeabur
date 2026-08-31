#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt')
t = p.read_text()
if 'fun playbackHttpFactory' in t:
    print('ja patchado')
    raise SystemExit(0)

old_hls = '''private fun isLikelyHls(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("type=m3u8") ||
        lower.contains("/hls/") || (lower.contains("playlist") && lower.contains("m3u"))
}'''
new_hls = '''private fun isLikelyHls(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".m3u8") || lower.contains("format=m3u8") || lower.contains("type=m3u8") ||
        lower.contains("/hls/") || lower.contains("mpegurl") || lower.contains("index.m3u") ||
        (lower.contains("playlist") && (lower.contains("m3u") || lower.contains("/hls/"))) ||
        (lower.contains("pengu.uk") && lower.contains("/hls/"))
}

private fun playbackHeaders(url: String): Map<String, String> {
    val host = try { java.net.URI(url).host.orEmpty() } catch (_: Exception) { "" }
    val origin = when {
        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.isNotBlank() -> "https://$host/"
        else -> url
    }
    return mapOf(
        "Referer" to origin,
        "Origin" to origin.trimEnd('/'),
        "Accept" to "*/*",
        "Connection" to "keep-alive",
    )
}

private fun playbackHttpFactory(url: String): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
        .setDefaultRequestProperties(playbackHeaders(url))'''

if old_hls not in t:
    raise SystemExit('bloco isLikelyHls nao encontrado')
t = t.replace(old_hls, new_hls, 1)

# replace the three factory constructions
import re
pat = re.compile(
    r'vel httpDataSourceFactory = DefaultHttpDataSource\.Factory\(\)\s*\n'
    r'\s*\.setUserAgent\("[^"]+"\)\s*\n'
    r'\s*\.setDefaultRequestProperties\(mapOf\("Referer" to (url|streamUrl), "Connection" to "keep-alive", "Icy-MetaData" to "1"\)\)',
)
def repl(m):
    arg = 'url' if m.group(1)=='url' else 'streamUrl'
    return f'val httpDataSourceFactory = playbackHttpFactory({arg})'
t2, n = pat.subn(repl, t)
print('factory blocks', n)
t = t2

old_sub = '''            val httpDs = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to activeUrl,
                        "Connection" to "keep-alive",
                        "Icy-MetaData" to "1",
                    ),
                )'''
new_sub = '            val httpDs = playbackHttpFactory(activeUrl)'
if old_sub in t:
    t = t.replace(old_sub, new_sub, 1)
    print('subtitle factory ok')
else:
    print('subtitle factory skip')

p.write_text(t)
print('bytes', p.stat().st_size)
