#!/usr/bin/env python3
from pathlib import Path
import base64, zlib
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "lib" / "stremio-addons.js"
a = (ROOT / "scripts" / "sf_a.b64").read_text().strip()
b = (ROOT / "scripts" / "sf_b.b64").read_text().strip()
text = zlib.decompress(base64.b64decode(a + b)).decode("utf-8")
assert "buildStreamMeta" in text and "prio = prio * 100" in text
DEST.parent.mkdir(parents=True, exist_ok=True)
DEST.write_text(text, encoding="utf-8")
print("ok", DEST, len(text))
print("meta ok + panel order x100")
