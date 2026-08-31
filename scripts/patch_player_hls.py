#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt')
t = p.read_text()

# headers: sem Origin (muitos HLS devolvem 403)
old_h = '''    return mapOf(
        "Referer" to origin,
        "Origin" to origin.trimEnd('/'),
        "Accept" to "*/*",
        "Connection" to "keep-alive",
    )'''
new_h = '''    return mapOf(
        "Referer" to origin,
        "Accept" to "*/*",
        "Connection" to "keep-alive",
    )'''
if old_h in t:
    t = t.replace(old_h, new_h, 1)
    print('headers sem Origin')

t = t.replace(
    '.setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")',
    '.setUserAgent("VLC/3.0.20 LibVLC/3.0.20")',
)

old1 = '''        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
            .setDefaultRequestProperties(mapOf("Referer" to url, "Connection" to "keep-alive", "Icy-MetaData" to "1"))'''
new1 = '        val httpDataSourceFactory = playbackHttpFactory(url)'
if old1 in t:
    t = t.replace(old1, new1, 1)
    print('factory inicial ok')
else:
    print('factory inicial ja era ou mudou')

old2 = '''            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("VLC/3.0.4 LibVLC/3.0.4")
                .setDefaultRequestProperties(mapOf("Referer" to streamUrl, "Connection" to "keep-alive", "Icy-MetaData" to "1"))'''
new2 = '            val httpDataSourceFactory = playbackHttpFactory(streamUrl)'
if old2 in t:
    t = t.replace(old2, new2, 1)
    print('factory reload ok')
else:
    print('factory reload ja era ou mudou')

p.write_text(t)
print('done', 'playbackHttpFactory count', t.count('playbackHttpFactory'))
