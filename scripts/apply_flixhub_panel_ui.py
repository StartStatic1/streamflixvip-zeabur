#!/usr/bin/env python3
import gzip, base64
from pathlib import Path
root = Path(__file__).resolve().parents[1]
b64_path = root / "scripts" / "flixhub_ui.b64"
if not b64_path.exists():
    raise SystemExit("falta scripts/flixhub_ui.b64 — git pull de novo")
p = root / "Public" / "admin-flixhub.html"
p.parent.mkdir(parents=True, exist_ok=True)
p.write_bytes(gzip.decompress(base64.b64decode(b64_path.read_text().strip())))
print("ok admin-flixhub.html", p.stat().st_size)
