#!/usr/bin/env python3
"""Free + is_free: permite collectAddonSources (antes so VIP)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "api/media-sources.js"
t = p.read_text(encoding="utf-8")

old = """    if (access.isVip) {
      try {
        pushUnique(
          await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode),
          seen,
          sources,
        );
      } catch (addonErr) {
        console.warn('[media-sources] addons skip:', addonErr.message);
      }
    } else {
      try {
        pushUnique(await lockedAddonStubs(serviceKey), seen, sources);
      } catch (stubErr) {
        console.warn('[media-sources] addon stubs skip:', stubErr.message);
      }
    }"""

new = """    // Area Free: is_free libera addons reais (nao so R2/DB)
    const allowAddons = access.isVip || (vipConfig && vipConfig.is_free === true);
    if (allowAddons) {
      try {
        pushUnique(
          await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode),
          seen,
          sources,
        );
      } catch (addonErr) {
        console.warn('[media-sources] addons skip:', addonErr.message);
      }
    } else {
      try {
        pushUnique(await lockedAddonStubs(serviceKey), seen, sources);
      } catch (stubErr) {
        console.warn('[media-sources] addon stubs skip:', stubErr.message);
      }
    }"""

if "allowAddons" in t:
    print("already patched")
elif old in t:
    t = t.replace(old, new, 1)
    p.write_text(t, encoding="utf-8")
    print("patched ok")
else:
    print("block not found - check media-sources.js")
    raise SystemExit(1)
