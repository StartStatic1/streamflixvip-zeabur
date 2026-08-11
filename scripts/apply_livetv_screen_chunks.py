#!/usr/bin/env python3
import base64, gzip
from pathlib import Path
b64 = "".join([
    Path("scripts/livetv_screen_b64/part0.txt").read_text().strip(),
    Path("scripts/livetv_screen_b64/part1.txt").read_text().strip(),
    Path("scripts/livetv_screen_b64/part2.txt").read_text().strip()
])
Path("android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvScreen.kt").write_bytes(
    gzip.decompress(base64.b64decode(b64))
)
print("Screen restored OK")
