#!/usr/bin/env python3
from pathlib import Path
import base64, zlib
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/ServerPickerUi.kt"
a = (ROOT / "scripts" / "sp_b64_a.txt").read_text().strip()
b = (ROOT / "scripts" / "sp_b64_b.txt").read_text().strip()
text = zlib.decompress(base64.b64decode(a+b)).decode("utf-8")
assert "fontSize = 9.sp" in text and "PLACEHOLDER" not in text
DEST.parent.mkdir(parents=True, exist_ok=True)
DEST.write_text(text, encoding="utf-8")
print("ok", DEST, len(text))
