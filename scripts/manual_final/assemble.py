#!/usr/bin/env python3
import base64
from pathlib import Path

def join(prefix):
    n = int(Path(f"scripts/manual_final/{prefix}_n.txt").read_text().strip())
    return base64.b64decode("".join(
        Path(f"scripts/manual_final/{prefix}{i}.b64").read_text().strip()
        for i in range(n)
    ))

Path("api/live-tv.js").write_bytes(join("l"))
Path("api/admin-vip.js").write_bytes(join("v"))
Path("Public/admin.html").write_bytes(join("a"))
assert "loadManualChannels" in Path("api/live-tv.js").read_text()
assert "list-live-tv-manual" in Path("api/admin-vip.js").read_text()
assert "loadLiveTvManuals" in Path("Public/admin.html").read_text()
assert "PLACEHOLDER" not in Path("api/live-tv.js").read_text()
print("SANITY OK", Path("api/live-tv.js").stat().st_size, Path("api/admin-vip.js").stat().st_size, Path("Public/admin.html").stat().st_size)
