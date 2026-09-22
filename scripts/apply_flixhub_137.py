#!/usr/bin/env python3
import base64
from pathlib import Path
root = Path(__file__).resolve().parents[1]
scripts = Path(__file__).resolve().parent
b64 = (scripts/"flixhub.p1.b64").read_text().strip() + (scripts/"flixhub.p2.b64").read_text().strip()
data = base64.b64decode(b64)
out = root / "api" / "flixhub.js"
out.write_bytes(data)
print("ok", out, len(data))
