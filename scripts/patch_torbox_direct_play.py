#!/usr/bin/env python3
"""TorBox CDN nao termina em .mp4 — o app abria WebView e ficava branco."""
from pathlib import Path

OLD_MOBILE = '''            return path.endsWith(".m3u8") ||
                path.endsWith(".mp4") ||
                path.endsWith(".mkv") ||
                path.endsWith(".webm") ||
                path.endsWith(".m4v") ||
                path.endsWith(".mov") ||
                path.endsWith(".ts") ||
                path.endsWith(".m2ts") ||
                lower.contains("/stream-proxy") // ja e o proxy do site, serve stream direto'''

NEW_MOBILE = '''            return path.endsWith(".m3u8") ||
                path.endsWith(".mp4") ||
                path.endsWith(".mkv") ||
                path.endsWith(".webm") ||
                path.endsWith(".m4v") ||
                path.endsWith(".mov") ||
                path.endsWith(".ts") ||
                path.endsWith(".m2ts") ||
                lower.contains("/stream-proxy") ||
                lower.contains("tb-cdn") ||
                lower.contains("torbox.app") ||
                lower.contains("/dld/")'''

OLD_TV = '''            return lower.endsWith(".m3u8") ||
                lower.endsWith(".mp4") ||
                lower.contains("/stream-proxy")'''

NEW_TV = '''            return lower.endsWith(".m3u8") ||
                lower.endsWith(".mp4") ||
                lower.contains("/stream-proxy") ||
                lower.contains("tb-cdn") ||
                lower.contains("torbox.app") ||
                lower.contains("/dld/")'''

OLD_HDR = '''    val origin = when {
        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.isNotBlank() -> "https://$host/"
        else -> url
    }
    return mapOf(
        "Referer" to origin,
        "Accept" to "*/*",
        "Connection" to "keep-alive",
    )'''

NEW_HDR = '''    if (host.contains("tb-cdn", ignoreCase = true) ||
        host.contains("torbox", ignoreCase = true)) {
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
    )'''

def patch(path: Path, old: str, new: str, label: str):
    if not path.exists():
        print('ausente', path)
        return
    t = path.read_text()
    if new in t:
        print('ok ja tem', label)
        return
    if old not in t:
        print('trecho nao achado', label)
        return
    path.write_text(t.replace(old, new, 1))
    print('ok', label)

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    patch(root / 'android/app/src/main/java/com/streamflixvip/app/network/SupabaseApi.kt', OLD_MOBILE, NEW_MOBILE, 'mobile direct')
    patch(root / 'android-tv/app/src/main/java/com/streamflixvip/tv/network/SupabaseApi.kt', OLD_TV, NEW_TV, 'tv direct')
    patch(root / 'android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt', OLD_HDR, NEW_HDR, 'player headers')
    print('fim patch_torbox_direct_play')
