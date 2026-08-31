#!/usr/bin/env python3
from pathlib import Path
keep = {
    "build-android-apk.yml",
    "build-android-tv-apk.yml",
    "release-android-apk.yml",
    "release-android-tv-apk.yml",
    "iptv-sync.yml",
    "iptv-sync-series.yml",
    "xtream-sync.yml",
    "cleanup-old-workflows.yml",
}
root = Path(".github/workflows")
removed = []
for p in sorted(root.glob("*.yml")):
    if p.name in keep:
        continue
    p.unlink()
    removed.append(p.name)
print("removed", len(removed))
for n in removed:
    print("-" , n)
