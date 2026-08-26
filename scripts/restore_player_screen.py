#!/usr/bin/env python3
import base64
from pathlib import Path
parts = []
for i in range(3):
    parts.append(Path(f"scripts/player_b64_{i}.txt").read_text().strip())
data = base64.b64decode("".join(parts))
out = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
out.write_bytes(data)
print("restored", out, "bytes", len(data))
text = data.decode()
assert "series_prefs" in text
assert "fun reapplyTrackPreferences" in text
assert "Barra inferior" in text
print("verify OK")
