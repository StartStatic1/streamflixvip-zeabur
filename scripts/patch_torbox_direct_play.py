#!/usr/bin/env python3
"""TorBox/Guindex: nativo no ExoPlayer, sem Referer no CDN."""
from pathlib import Path

OLD_HDR = '''private fun playbackHeaders(url: String): Map<String, String> {
    val host = try { java.net.URI(url).host.orEmpty() } catch (_: Exception) { "" }
    val origin = when {
        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.isNotBlank() -> "https://$host/"
        else -> url
    }
    return mapOf(
        "Referer" to origin,
        "Accept" to "*/*",
        "Connection" to "keep-alive",
    )
}

private fun playbackHttpFactory(url: String): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
        .setDefaultRequestProperties(playbackHeaders(url))
'''

NEW_HDR = '''private fun isTorboxLike(url: String): Boolean {
    val host = try { java.net.URI(url).host.orEmpty().lowercase() } catch (_: Exception) { "" }
    val lower = url.lowercase()
    return host.contains("tb-cdn") || host.contains("torbox") ||
        lower.contains("/dld/") || lower.contains("requestdl")
}

private fun playbackHeaders(url: String): Map<String, String> {
    val host = try { java.net.URI(url).host.orEmpty() } catch (_: Exception) { "" }
    if (isTorboxLike(url)) {
        return mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
        )
    }
    val origin = when {
        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.isNotBlank() -> "https://$host/"
        else -> url
    }
    return mapOf(
        "Referer" to origin,
        "Accept" to "*/*",
        "Connection" to "keep-alive",
    )
}

private fun playbackHttpFactory(url: String): DefaultHttpDataSource.Factory {
    val debrid = isTorboxLike(url)
    val ua = if (debrid) {
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
    } else {
        "VLC/3.0.20 LibVLC/3.0.20"
    }
    return DefaultHttpDataSource.Factory()
        .setUserAgent(ua)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(if (debrid) 45000 else 15000)
        .setReadTimeoutMs(if (debrid) 60000 else 20000)
        .setDefaultRequestProperties(playbackHeaders(url))
}
'''

OLD_RES = '''    var resolvedUrl by remember(sourceUrl) { mutableStateOf<String?>(if (isDirectPlayable) null else sourceUrl) }
    LaunchedEffect(sourceUrl, isDirectPlayable) {
        if (isDirectPlayable) {
            val candidates = VipSource(source_url = sourceUrl, source_label = null, priority = null)
                .candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
            resolvedUrl = StreamUrlResolver.resolveFastest(candidates)
        }
    }
'''

NEW_RES = '''    val playNative = isDirectPlayable || com.streamflixvip.app.network.urlLooksDirectPlayable(sourceUrl)
    var resolvedUrl by remember(sourceUrl) { mutableStateOf<String?>(if (playNative) null else sourceUrl) }
    LaunchedEffect(sourceUrl, playNative) {
        if (playNative) {
            val candidates = VipSource(source_url = sourceUrl, source_label = null, priority = null)
                .candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)
            resolvedUrl = StreamUrlResolver.resolveFastest(candidates).ifBlank { sourceUrl }
        }
    }
'''

OLD_IF = '''    if (isDirectPlayable) {
        NativePlayer(
'''

NEW_IF = '''    if (playNative) {
        NativePlayer(
'''

def patch(path: Path, old: str, new: str, label: str):
    if not path.exists():
        print('ausente', path)
        return False
    t = path.read_text()
    if new in t:
        print('ok ja tem', label)
        return True
    if old not in t:
        print('trecho nao achado', label)
        return False
    path.write_text(t.replace(old, new, 1))
    print('ok', label)
    return True

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    player = root / 'android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt'
    patch(player, OLD_HDR, NEW_HDR, 'headers')
    patch(player, OLD_RES, NEW_RES, 'resolve')
    patch(player, OLD_IF, NEW_IF, 'if native')
    print('fim patch_torbox_direct_play')
