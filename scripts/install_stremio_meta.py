#!/usr/bin/env python3
"""Reconstroi lib/stremio-addons.js (meta chips TorrentDB)."""
import base64, gzip, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
parts = []
for i in range(20):
    f = pathlib.Path(__file__).parent / f"stremio_meta_p{i}.b64"
    if not f.exists():
        break
    parts.append(f.read_text().strip())
if not parts:
    raise SystemExit("missing scripts/stremio_meta_p*.b64")
out = ROOT / "lib" / "stremio-addons.js"
out.write_bytes(gzip.decompress(base64.b64decode("".join(parts))))
print("ok", out, out.stat().st_size)
assert b"buildStreamMeta" in out.read_bytes()
print("meta ok")
