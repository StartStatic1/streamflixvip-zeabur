#!/usr/bin/env python3
"""Remove 'private' solto antes de parseTsToMs (Repeated 'private')."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
t = TARGET.read_text(encoding="utf-8")

old = "private \nprivate fun parseTsToMs"
new = "private fun parseTsToMs"
if old in t:
    t = t.replace(old, new, 1)
    print("ok removed orphan private")
elif "private private fun parseTsToMs" in t:
    t = t.replace("private private fun parseTsToMs", "private fun parseTsToMs", 1)
    print("ok removed private private")
else:
    # line-based
    lines = t.splitlines(keepends=True)
    out = []
    i = 0
    fixed = False
    while i < len(lines):
        if lines[i].strip() == "private" and i + 1 < len(lines) and "fun parseTsToMs" in lines[i + 1]:
            i += 1
            fixed = True
            continue
        out.append(lines[i])
        i += 1
    if not fixed:
        print("already ok or pattern not found")
        sys.exit(0)
    t = "".join(out)
    print("ok fixed line-based")

# ensure shiftSrtContent is private
if "\nfun shiftSrtContent" in t and "\nprivate fun shiftSrtContent" not in t:
    t = t.replace("\nfun shiftSrtContent", "\nprivate fun shiftSrtContent", 1)
    print("ok private on shiftSrtContent")

TARGET.write_text(t, encoding="utf-8")
print("lines", t.count(chr(10)) + 1)
assert "private \nprivate fun parseTsToMs" not in t
assert "private private fun" not in t
print("done")
