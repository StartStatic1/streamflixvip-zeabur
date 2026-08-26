#!/usr/bin/env python3
import base64
from pathlib import Path

def load(prefix, n):
    parts = [Path(f"scripts/{prefix}_{i}.txt").read_text().strip() for i in range(n)]
    return base64.b64decode("".join(parts))

main = load("mainb", 5)
player = load("playerb", 9)
Path("android/app/src/main/java/com/streamflixvip/app/MainActivity.kt").write_bytes(main)
Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt").write_bytes(player)
assert b"onBack = { navController.popBackStack() }" in main
assert b'Text("Voltar"' in player
print("OK", len(main), len(player))
