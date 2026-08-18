#!/usr/bin/env python3
import base64
from pathlib import Path

def join(stem, n):
    return base64.b64decode("".join(Path(f"scripts/manual_push/{stem}_{i}.b64").read_text().strip() for i in range(n)))

Path("api/admin-vip.js").write_bytes(join("admin-vip_js", 7))
Path("api/live-tv.js").write_bytes(join("live-tv_js", 3))
Path("scripts/live_tv_manual_channels.sql").write_bytes(join("sql", 1))
print("admin-vip", Path("api/admin-vip.js").stat().st_size)
print("live-tv", Path("api/live-tv.js").stat().st_size)
assert "list-live-tv-manual" in Path("api/admin-vip.js").read_text()
assert "loadManualChannels" in Path("api/live-tv.js").read_text()
print("API SANITY OK")
