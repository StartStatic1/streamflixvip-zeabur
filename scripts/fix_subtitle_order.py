#!/usr/bin/env python3
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt")
t = p.read_text()

# Remove early LaunchedEffect that references functions not yet defined
if "Reaplica legenda online salva" in t:
    lines = t.splitlines(True)
    out = []
    i = 0
    removed = 0
    while i < len(lines):
        if "Reaplica legenda online salva" in lines[i]:
            while out and out[-1].strip() == "":
                out.pop()
            while i < len(lines) and "LaunchedEffect" not in lines[i]:
                i += 1
            if i < len(lines):
                depth = 0
                started = False
                while i < len(lines):
                    for ch in lines[i]:
                        if ch == "{":
                            depth += 1
                            started = True
                        elif ch == "}":
                            depth -= 1
                    i += 1
                    if started and depth <= 0:
                        break
            removed += 1
            continue
        out.append(lines[i])
        i += 1
    t = "".join(out)
    print("removed blocks", removed)

# Insert after function defs, before selectAudio
marker = "    fun selectAudio(option: TrackOption?)"
if marker not in t:
    raise SystemExit("selectAudio not found")
if "Reaplica legenda online salva" not in t:
    inject = (
        "\n"
        "    // Reaplica legenda online salva (filesDir) sem baixar de novo\n"
        "    LaunchedEffect(activeUrl, tmdbId, currentSeason, currentEpisode) {\n"
        "        val file = subtitleCacheFile()\n"
        "        val label = loadSavedSubtitleLabel()\n"
        "        if (file.exists() && file.length() > 10L && !label.isNullOrBlank()) {\n"
        "            kotlinx.coroutines.delay(800)\n"
        "            try {\n"
        "                applySubtitleFromFile(file, label)\n"
        "            } catch (_: Exception) {\n"
        "            }\n"
        "        }\n"
        "    }\n\n"
    )
    t = t.replace(marker, inject + marker, 1)
    print("inserted late autoload")
else:
    print("autoload already present")

p.write_text(t)
print("DONE")
