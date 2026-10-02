#!/usr/bin/env python3
"""Area Free: nao espera 8s de addon. Prazo curto se o titulo e free."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "lib/stremio-addons.js"
t = p.read_text()
old = "async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode) {"
new = "async function collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, deadlineMs) {"
if "deadlineMs" in t:
    print("ok sig ja")
else:
    t = t.replace(old, new, 1)
    t = t.replace(
        "withDeadline(fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear), COLLECT_DEADLINE_MS)",
        "withDeadline(fetchStreamsFromAddon(a, mediaType, tmdbId, imdbId, season, episode, animeIds, queryYear), Number(deadlineMs) > 0 ? Number(deadlineMs) : COLLECT_DEADLINE_MS)",
        1,
    )
    p.write_text(t)
    print("ok deadline arg")

ms = root / "api/media-sources.js"
mt = ms.read_text()
old_call = "await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode),"
new_call = "await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, (vipConfig && vipConfig.is_free === true) ? 2500 : undefined),"
if "is_free === true) ? 2500" in mt:
    print("ok call ja")
elif old_call in mt:
    mt = mt.replace(old_call, new_call, 1)
    ms.write_text(mt)
    print("ok call free 2.5s")
else:
    print("aviso call")
print("fim patch_free_fast")
