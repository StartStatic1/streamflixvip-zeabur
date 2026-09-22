#!/usr/bin/env python3
import gzip, base64
from pathlib import Path
root = Path(__file__).resolve().parents[1]
scripts = root / "scripts"
parts = []
for i in (1, 2, 3):
    f = scripts / ("flixhub_ui.p%d.b64" % i)
    if not f.exists():
        raise SystemExit("falta " + str(f) + " — git pull de novo")
    parts.append(f.read_text().strip())
p = root / "Public" / "admin-flixhub.html"
p.parent.mkdir(parents=True, exist_ok=True)
p.write_bytes(gzip.decompress(base64.b64decode("".join(parts))))
print("ok admin-flixhub.html", p.stat().st_size)
