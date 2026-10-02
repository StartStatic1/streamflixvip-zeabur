#!/usr/bin/env python3
"""Free com fonte no banco nao espera addon. Sem fonte, espera no maximo 1.2s."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "api/media-sources.js"
t = p.read_text()
old = """        pushUnique(
          await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, (vipConfig && vipConfig.is_free === true) ? 2500 : undefined),
          seen,
          sources,
        );"""
new = """        const freeFast = vipConfig && vipConfig.is_free === true;
        if (!(freeFast && sources.length)) {
          pushUnique(
            await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode, freeFast ? 1200 : undefined),
            seen,
            sources,
          );
        }"""
if "freeFast && sources.length" in t:
    print("ok ja")
elif old in t:
    p.write_text(t.replace(old, new, 1))
    print("ok early free")
else:
    print("aviso bloco")
print("fim patch_free_early")
