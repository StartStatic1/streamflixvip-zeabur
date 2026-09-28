#!/usr/bin/env python3
"""Legenda online em overlay — sem remount, sem tela preta."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
t = TARGET.read_text(encoding="utf-8")

if "parseSubtitleCues" in t and "overlayCues" in t and "setMediaSource(merged)" not in t:
    print("already applied")
    sys.exit(0)

# 1 cue class
marker = "data class TrackOption(val label: String, val group: TrackGroup, val trackIndex: Int)\n"
if "data class SubtitleCue" not in t:
    if marker not in t:
        print("ERROR: TrackOption missing"); sys.exit(1)
    t = t.replace(marker, marker + "\ndata class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)\n", 1)

# 2 state
state_anchor = "    var onlineSubtitleRaw by remember { mutableStateOf<String?>(null) }\n"
if "overlayCues" not in t:
    if state_anchor not in t:
        print("ERROR: onlineSubtitleRaw missing"); sys.exit(1)
    t = t.replace(
        state_anchor,
        state_anchor
        + "    var overlayCues by remember { mutableStateOf(listOf<SubtitleCue>()) }\n"
        + "    var overlaySubText by remember { mutableStateOf<String?>(null) }\n",
        1,
    )

# 3 applyOnline: replace remount block
a = t.find("suspend fun applyOnlineSubtitle")
if a < 0:
    print("ERROR: applyOnlineSubtitle missing"); sys.exit(1)
start = t.find("            onlineSubtitleRaw = content\n", a)
end = t.find("            settingsPanel = SettingsPanel.MAIN", start)
if start < 0 or end < 0:
    print("ERROR: applyOnline region"); sys.exit(1)
end = end + len("            settingsPanel = SettingsPanel.MAIN")
new_apply = """            onlineSubtitleRaw = content
            // Overlay: nao remonta o video (evita tela preta)
            overlayCues = parseSubtitleCues(shiftSrtContent(content, subtitleOffsetMs))
            onlineSubtitleApplied = true
            persistSubtitleKey("online")
            val short = (item.release ?: "PT-BR").let { if (it.length > 28) it.take(28) + "…" else it }
            selectedSubtitleLabel = "Online: $short"
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            settingsPanel = SettingsPanel.MAIN"""
t = t[:start] + new_apply + t[end:]

# 4 reapply
ra = t.find("fun reapplySubtitleOffset")
s = t.find("                val shifted = shiftSrtContent(raw, newOffset)", ra)
e = t.find("            } catch", s)
if s < 0 or e < 0:
    print("ERROR: reapply region"); sys.exit(1)
new_re = """                overlayCues = parseSubtitleCues(shiftSrtContent(raw, newOffset))
                val sign = if (newOffset >= 0) "+" else ""
                Toast.makeText(context, "Sync ${sign}${newOffset / 1000.0}s", Toast.LENGTH_SHORT).show()
"""
t = t[:s] + new_re + t[e:]

# 5 selectSubtitle
old_sel = """    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        trackSelector.parameters = if (option == null) {"""
new_sel = """    fun selectSubtitle(option: TrackOption?) {
        onlineSubtitleApplied = false
        overlayCues = emptyList()
        overlaySubText = null
        trackSelector.parameters = if (option == null) {"""
if old_sel not in t:
    print("ERROR: selectSubtitle"); sys.exit(1)
t = t.replace(old_sel, new_sel, 1)

# 6 LaunchedEffect
if "overlaySubText = cue" not in t:
    overlay_le = """    LaunchedEffect(exoPlayer, overlayCues, onlineSubtitleApplied) {
        while (true) {
            if (onlineSubtitleApplied && overlayCues.isNotEmpty()) {
                val pos = exoPlayer.currentPosition
                val cue = overlayCues.firstOrNull { pos >= it.startMs && pos < it.endMs }
                overlaySubText = cue?.text
            } else if (overlaySubText != null) {
                overlaySubText = null
            }
            delay(200)
        }
    }

"""
    alt = """    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
"""
    scrub = """    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                scrubPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
"""
    if scrub in t:
        t = t.replace(scrub, overlay_le + scrub, 1)
    elif alt in t:
        t = t.replace(alt, overlay_le + alt, 1)
    else:
        print("ERROR: LaunchedEffect inject"); sys.exit(1)

# 7 UI overlay
if "Legenda online em overlay" not in t and "subLine = overlaySubText" not in t:
    a2 = "            update = { v -> v.resizeMode = aspectMode.resizeMode },\n        )"
    if a2 not in t:
        print("ERROR: AndroidView update anchor"); sys.exit(1)
    t = t.replace(
        a2,
        a2
        + """

        // Legenda online em overlay (sem remount / sem tela preta)
        val subLine = overlaySubText
        if (!subLine.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = subLine,
                        color = Color.White,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }""",
        1,
    )

# 8 helpers
if "fun parseSubtitleCues" not in t:
    helper = (
        "\nprivate fun parseTsToMs(ts: String): Long {\n"
        "    val clean = ts.trim().replace('.', ',')\n"
        '    val parts = clean.split(",", limit = 2)\n'
        '    val hms = parts[0].split(":")\n'
        "    if (hms.size < 3) return 0L\n"
        "    val mill = parts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L\n"
        "    var total = (hms[0].toLongOrNull() ?: 0L) * 3_600_000L\n"
        "    total += (hms[1].toLongOrNull() ?: 0L) * 60_000L\n"
        "    total += (hms[2].toLongOrNull() ?: 0L) * 1_000L\n"
        "    total += mill\n"
        "    return total.coerceAtLeast(0L)\n"
        "}\n\n"
        "private fun parseSubtitleCues(content: String): List<SubtitleCue> {\n"
        '    val text = content.replace("\\r", "")\n'
        "    val cues = mutableListOf<SubtitleCue>()\n"
        '    val re = Regex("""(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})""")\n'
        "    val lines = text.lineSequence().toList()\n"
        "    var i = 0\n"
        "    while (i < lines.size) {\n"
        "        val line = lines[i]\n"
        "        val m = re.find(line)\n"
        "        if (m != null) {\n"
        "            val start = parseTsToMs(m.groupValues[1])\n"
        "            val end = parseTsToMs(m.groupValues[2])\n"
        "            val buf = mutableListOf<String>()\n"
        "            i++\n"
        "            while (i < lines.size && lines[i].isNotBlank() && re.find(lines[i]) == null) {\n"
        "                val l = lines[i].trim()\n"
        "                if (l.isNotEmpty() && !l.all { it.isDigit() }) buf.add(l)\n"
        "                i++\n"
        "            }\n"
        "            if (buf.isNotEmpty() && end > start) {\n"
        '                cues.add(SubtitleCue(start, end, buf.joinToString("\\n")))\n'
        "            }\n"
        "            continue\n"
        "        }\n"
        "        i++\n"
        "    }\n"
        "    return cues\n"
        "}\n\n"
    )
    if "fun shiftSrtContent" not in t:
        print("ERROR: shiftSrtContent missing"); sys.exit(1)
    t = t.replace("fun shiftSrtContent", helper + "fun shiftSrtContent", 1)

if "setMediaSource(merged)" in t:
    print("WARN: still has setMediaSource(merged)")

TARGET.write_text(t, encoding="utf-8")
print("ok overlay applied", TARGET, "lines", t.count(chr(10)) + 1)
print("parseSubtitleCues", "parseSubtitleCues" in t)
print("overlayCues", "overlayCues" in t)
print("merged gone", "setMediaSource(merged)" not in t)
