#!/usr/bin/env python3
from pathlib import Path
import base64, zlib
ROOT = Path(__file__).resolve().parents[1]
parts = [(ROOT / "scripts" / f"pui{i}.b64").read_text().strip() for i in range(2)]
text = zlib.decompress(base64.b64decode("".join(parts))).decode()
dest = ROOT / "scripts" / "patch_player_ui.py"
dest.write_text(text)
print("ok wrote", dest, len(text))
