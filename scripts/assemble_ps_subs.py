#!/usr/bin/env python3
from pathlib import Path
import base64
n = 21
parts = [Path(f"scripts/ps_subs/{i}.b64") for i in range(n)]
missing = [str(p) for p in parts if not p.exists()]
if missing:
    raise SystemExit("missing parts: " + ",".join(missing))
data = base64.b64decode("".join(p.read_text().strip() for p in parts))
out = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
out.write_bytes(data)
print("wrote", len(data))
assert b"fetchOnlineSubtitles" in data
assert b"Online (OpenSubtitles)" in data
print("ok")
