#!/usr/bin/env python3
from pathlib import Path
import base64, zlib
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
parts = [(ROOT / "scripts" / f"ps{i}.b64").read_text().strip() for i in range(4)]
text = zlib.decompress(base64.b64decode("".join(parts))).decode("utf-8")
assert "package com.streamflixvip.app.ui.player" in text
assert "PLACEHOLDER" not in text
assert "formatPlayerTime" in text
DEST.parent.mkdir(parents=True, exist_ok=True)
DEST.write_text(text, encoding="utf-8")
print("ok", DEST, len(text))
