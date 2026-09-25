#!/usr/bin/env python3
import base64, gzip, pathlib, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[1]
parts = []
for i in range(20):
    f = pathlib.Path(__file__).parent / f"android_chips_p{i}.b64"
    if not f.exists():
        break
    parts.append(f.read_text().strip())
if not parts:
    raise SystemExit("missing android_chips_p*.b64")
script = ROOT / "scripts" / "_apply_android_chips.py"
script.write_bytes(gzip.decompress(base64.b64decode("".join(parts))))
subprocess.check_call([sys.executable, str(script)])
