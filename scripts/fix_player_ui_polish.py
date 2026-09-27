#!/usr/bin/env python3
"""Fix player: remove timeline duplicada; auto-busca legendas online ao abrir menu."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
t = TARGET.read_text(encoding="utf-8")

OLD_BOTTOM = """                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatPlayerTime(scrubPosition), color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                        Text(formatPlayerTime(scrubDuration), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                    Slider(
                        value = if (scrubDuration > 0) (scrubPosition.toFloat() / scrubDuration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { v ->
                            isScrubbing = true
                            scrubPosition = (v * scrubDuration).toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(scrubPosition)
                            isScrubbing = false
                        },
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFFFFB547),
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                        ),
                    )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {"""

NEW_BOTTOM = """                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {"""

if OLD_BOTTOM in t:
    t = t.replace(OLD_BOTTOM, NEW_BOTTOM, 1)
    old_end = """                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.MAIN
                    }) {
                        Text("Mais", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                }
            }
        }"""
    new_end = """                    Surface(color = Color.White.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable {
                        settingsPanel = SettingsPanel.MAIN
                    }) {
                        Text("Mais", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }"""
    if old_end in t:
        t = t.replace(old_end, new_end, 1)
        print("ok removed duplicate timeline")
    else:
        print("WARN close braces")
else:
    if "formatPlayerTime(scrubPosition)" not in t:
        print("timeline already removed")
    else:
        print("ERROR: bottom block not exact match")
        sys.exit(1)

if "LaunchedEffect(settingsPanel)" not in t and "autoSearchOnlineSubs" not in t:
    anchor = """    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                scrubPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
"""
    inject = """    // Ao abrir menu Legendas, busca PT-BR online automaticamente se lista vazia
    LaunchedEffect(settingsPanel) {
        if (settingsPanel == SettingsPanel.SUBTITLE &&
            onlineSubtitleResults.isEmpty() &&
            !onlineSubtitlesLoading
        ) {
            searchOnlineSubtitles()
        }
    }

"""
    if anchor in t:
        t = t.replace(anchor, inject + anchor, 1)
        print("ok auto-search on Legendas open")
    else:
        anchor2 = """    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
"""
        if anchor2 in t:
            t = t.replace(anchor2, inject + anchor2, 1)
            print("ok auto-search (alt anchor)")
        else:
            print("WARN auto-search inject failed")

t = t.replace('"Online PT-BR"', '"ONLINE · PT-BR"', 1)
t = t.replace('"ONLINE (INTERNET)"', '"ONLINE · PT-BR"', 1)

TARGET.write_text(t, encoding="utf-8")
print("written", len(t), "lines", t.count(chr(10)) + 1)
print("has formatPlayerTime in bottom", "formatPlayerTime(scrubPosition)" in t)
print("has Slider(", "Slider(" in t)
print("has auto LaunchedEffect settingsPanel", "LaunchedEffect(settingsPanel)" in t)
