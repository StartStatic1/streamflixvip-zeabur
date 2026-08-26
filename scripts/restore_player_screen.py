#!/usr/bin/env python3
import base64
from pathlib import Path
parts = []
i = 0
while True:
    f = Path(f"scripts/player_b64_{i}.txt")
    if not f.exists():
        break
    parts.append(f.read_text().strip())
    i += 1
data = base64.b64decode("".join(parts))
out = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
out.write_bytes(data)
text = data.decode()
assert "reapplyTrackPreferences" in text
assert "sourceModeScore" in text
assert "PlayerBottomChip" in text
print("restored", out, "bytes", len(data))
