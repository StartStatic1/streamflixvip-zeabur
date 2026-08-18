#!/usr/bin/env python3
from pathlib import Path
import base64
parts = [Path(f"scripts/sub_parts/{i}.b64") for i in range(4)]
data = base64.b64decode("".join(p.read_text().strip() for p in parts))
Path("scripts/patch_online_subs.py").write_bytes(data)
print("wrote", len(data))
assert b"fetchOnlineSubtitles" in data or b"new_select" in data
