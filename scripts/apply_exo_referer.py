#!/usr/bin/env python3
"""Exo: Referer mzfi.me em CDN Hyper / proxy. Primeiro clique deixa de falhar."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
old = """        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.isNotBlank() -> "https://$host/"
        else -> url"""
new = """        host.contains("pengu.uk", ignoreCase = true) -> "https://pengu.uk/"
        host.contains("hakunaymatata", ignoreCase = true) -> "https://mzfi.me/"
        host.contains("mzfi.me", ignoreCase = true) -> "https://mzfi.me/"
        host.contains("streamflixvip", ignoreCase = true) -> "https://mzfi.me/"
        host.isNotBlank() -> "https://$host/"
        else -> url"""

n = 0
for rel in [
    "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt",
    "android-tv/app/src/main/java/com/streamflixvip/tv/ui/player/PlayerTvScreen.kt",
]:
    p = root / rel
    if not p.exists():
        print("skip", rel)
        continue
    t = p.read_text()
    if 'host.contains("hakunaymatata"' in t:
        print("ok ja", p.name)
        continue
    if old not in t:
        print("aviso", p.name)
        continue
    p.write_text(t.replace(old, new, 1))
    n += 1
    print("ok exo referer", p.name)
print("fim apply_exo_referer", n)
