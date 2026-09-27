#!/usr/bin/env python3
"""Move scrub LaunchedEffect to after exoPlayer is defined."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"

t = TARGET.read_text(encoding="utf-8")

BAD = """
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                scrubPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0) scrubDuration = d
            }
            delay(400)
        }
    }

"""

if BAD not in t:
    if "scrubPosition = exoPlayer.currentPosition" in t:
        i_scrub = t.find("scrubPosition = exoPlayer.currentPosition")
        i_exo = t.find("val exoPlayer = remember")
        if i_exo >= 0 and i_scrub > i_exo:
            print("already ok: scrub after exoPlayer")
            sys.exit(0)
    print("ERROR: expected block not found")
    sys.exit(1)

t = t.replace(BAD, "\n", 1)

anchor = """    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
"""

SCRUB = """    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                scrubPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0) scrubDuration = d
            }
            delay(400)
        }
    }

"""

if anchor not in t:
    print("ERROR: progress LaunchedEffect anchor not found")
    sys.exit(1)

if "scrubPosition = exoPlayer.currentPosition" in t:
    print("ERROR: scrub still present after remove")
    sys.exit(1)

t = t.replace(anchor, SCRUB + anchor, 1)

i_scrub = t.find("scrubPosition = exoPlayer.currentPosition")
i_exo = t.find("val exoPlayer = remember")
if not (i_exo >= 0 and i_scrub > i_exo):
    print("ERROR: order still wrong", i_exo, i_scrub)
    sys.exit(1)

TARGET.write_text(t, encoding="utf-8")
print("ok fixed order", TARGET, "lines", t.count(chr(10)) + 1)
