#!/usr/bin/env python3
"""Um comando depois do git pull: restaura stubs e reaplica patches."""
import runpy
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[1]
scripts = Path(__file__).resolve().parent

ADDONS_GOOD = (
    "https://raw.githubusercontent.com/StartStatic1/streamflixvip-zeabur/"
    "a0d4671b134a91a978568f74d946f5e37c1f1ecd/lib/stremio-addons.js"
)
IPTV_GOOD = (
    "https://raw.githubusercontent.com/StartStatic1/streamflixvip-zeabur/"
    "ec5b5c7/api/iptv-sync.js"
)


def download(url, dest: Path):
    dest.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(url, dest)
    print("baixou", dest, dest.stat().st_size)


def restore_addons():
    p = root / "lib" / "stremio-addons.js"
    cache = root / "lib" / "stremio-addons.cache.js"
    t = p.read_text() if p.exists() else ""
    if "temporary loader" in t or len(t) < 3000:
        if cache.exists() and cache.stat().st_size > 3000:
            print("addons: usa cache", cache.stat().st_size)
        else:
            download(ADDONS_GOOD, cache)
    else:
        print("addons: arquivo cheio", p.stat().st_size)


def restore_iptv():
    p = root / "api" / "iptv-sync.js"
    t = p.read_text() if p.exists() else ""
    if "RESTORE NEEDED" in t or "temporariamente offline" in t or len(t) < 2000:
        download(IPTV_GOOD, p)
    else:
        print("iptv-sync: arquivo cheio", p.stat().st_size)


def run(name):
    f = scripts / name
    if not f.exists():
        print("pula", name)
        return
    print(">>", name)
    runpy.run_path(str(f), run_name="__main__")


restore_addons()
restore_iptv()

for name in (
    "apply_source_meta.py",
    "apply_source_meta_fix.py",
    "apply_wave2_filter.py",
    "apply_flixhub_strict_match.py",
    "apply_bridge_strict_match.py",
    "apply_wave1_panels.py",
):
    run(name)

print("fim apply_all_safe")
