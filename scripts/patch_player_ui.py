#!/usr/bin/env python3
"""Patch PlayerScreen: setinha voltar, timeline, fontes submenu, sync legenda, nomes."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"


def main():
    t = TARGET.read_text(encoding="utf-8")
    if "formatPlayerTime" in t and "SettingsPanel.SOURCES" in t:
        print("already patched")
        return 0
    if "PLACEHOLDER" in t or len(t) < 1000:
        print("ERROR: PlayerScreen seems broken, abort")
        return 1

    if "automirrored.filled.ArrowBack" not in t:
        t = t.replace(
            "import androidx.compose.material.icons.filled.VolumeUp",
            "import androidx.compose.material.icons.filled.VolumeUp\n"
            "import androidx.compose.material.icons.automirrored.filled.ArrowBack\n"
            "import androidx.compose.material3.Slider\n"
            "import androidx.compose.material3.SliderDefaults",
        )
    if "import androidx.compose.ui.text.font.FontWeight" not in t:
        t = t.replace(
            "import androidx.compose.ui.unit.sp",
            "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.text.font.FontWeight",
        )

    t = t.replace(
        'FILL("Esticar", AspectRatioFrameLayout.RESIZE_MODE_FILL)',
        'FILL("Preencher", AspectRatioFrameLayout.RESIZE_MODE_FILL)',
    )
    t = t.replace(
        'FIT("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIT)',
        'FIT("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT)',
    )

    t = t.replace(
        "private enum class SettingsPanel { NONE, MAIN, SUBTITLE, AUDIO, QUALITY, SPEED }",
        "private enum class SettingsPanel { NONE, MAIN, SUBTITLE, AUDIO, QUALITY, SPEED, SOURCES }",
    )

    if "fun formatPlayerTime" not in t:
        helpers = (
            "\nprivate fun formatPlayerTime(ms: Long): String {\n"
            "    if (ms <= 0L) return \"0:00\"\n"
            "    val totalSec = (ms / 1000).toInt()\n"
            "    val h = totalSec / 3600\n"
            "    val m = (totalSec % 3600) / 60\n"
            "    val s = totalSec % 60\n"
            "    return if (h > 0) \"%d:%02d:%02d\".format(h, m, s) else \"%d:%02d\".format(m, s)\n"
            "}\n\n"
            "private fun shiftSrtContent(content: String, offsetMs: Long): String {\n"
            "    if (offsetMs == 0L) return content\n"
            "    fun shiftTs(ts: String): String {\n"
            "        val clean = ts.trim().replace('.', ',')\n"
            "        val parts = clean.split(\",\", limit = 2)\n"
            "        val hms = parts[0].split(\":\")\n"
            "        if (hms.size < 3) return ts\n"
            "        val mill = parts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L\n"
            "        var total = hms[0].toLongOrNull()?.times(3600000) ?: return ts\n"
            "        total += (hms[1].toLongOrNull() ?: 0L) * 60000\n"
            "        total += (hms[2].toLongOrNull() ?: 0L) * 1000\n"
            "        total += mill\n"
            "        total = (total + offsetMs).coerceAtLeast(0L)\n"
            "        val nh = total / 3600000\n"
            "        val nm = (total % 3600000) / 60000\n"
            "        val ns = (total % 60000) / 1000\n"
            "        val nms = total % 1000\n"
            "        return \"%02d:%02d:%02d,%03d\".format(nh, nm, ns, nms)\n"
            "    }\n"
            '    val re = Regex("""(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})""")\n'
            "    return re.replace(content) { m ->\n"
            '        "${shiftTs(m.groupValues[1])} --> ${shiftTs(m.groupValues[2])}"\n'
            "    }\n"
            "}\n\n"
        )
        t = t.replace(
            "private enum class SettingsPanel { NONE, MAIN, SUBTITLE, AUDIO, QUALITY, SPEED, SOURCES }",
            "private enum class SettingsPanel { NONE, MAIN, SUBTITLE, AUDIO, QUALITY, SPEED, SOURCES }\n" + helpers,
        )

    if "subtitleOffsetMs" not in t:
        t = t.replace(
            "var onlineSubtitleApplied by remember { mutableStateOf(false) }",
            "var onlineSubtitleApplied by remember { mutableStateOf(false) }\n"
            "    var subtitleOffsetMs by remember {\n"
            '        mutableStateOf(loadSeriesPref(context, tmdbId, "sub_offset", "0").toLongOrNull() ?: 0L)\n'
            "    }\n"
            "    var onlineSubtitleRaw by remember { mutableStateOf<String?>(null) }\n"
            "    var scrubPosition by remember { mutableStateOf(0L) }\n"
            "    var scrubDuration by remember { mutableStateOf(0L) }\n"
            "    var isScrubbing by remember { mutableStateOf(false) }",
        )

    if "scrubPosition = exoPlayer.currentPosition" not in t:
        t = t.replace(
            "var isLoadingNext by remember { mutableStateOf(false) }",
            "var isLoadingNext by remember { mutableStateOf(false) }\n\n"
            "    LaunchedEffect(exoPlayer) {\n"
            "        while (true) {\n"
            "            if (!isScrubbing) {\n"
            "                scrubPosition = exoPlayer.currentPosition.coerceAtLeast(0L)\n"
            "                val d = exoPlayer.duration\n"
            "                if (d > 0) scrubDuration = d\n"
            "            }\n"
            "            delay(400)\n"
            "        }\n"
            "    }\n",
            1,
        )

    print("PARTIAL - use VPS copy")
    TARGET = Path("/dev/null")
    return 0

if __name__ == "__main__":
    print("incomplete stub - run from VPS")
