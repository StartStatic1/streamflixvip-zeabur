#!/usr/bin/env python3
"""Corrige ServerInfoCard para compilar e tratar StreamHub igual FlixHub."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text(encoding="utf-8")

old = '''    val isStreamHub = label.contains("StreamHub", ignoreCase = true) || desc.contains("StreamHub", ignoreCase = true)
    val isFlix = label.contains("FlixHub", ignoreCase = true) || desc.contains("FlixHub", ignoreCase = true)
    val isHubPack = isStreamHub || isFlix
    val isJackett = label.contains("Jackettio", ignoreCase = true) || desc.contains("Jackettio", ignoreCase = true)
    val head = when { isStreamHub -> "StreamHub"; isFlix -> "FlixHub"; else -> label.split("\u00b7", "\u2022", "|").first().trim().ifBlank { source.displayName } }
    val serverMatch = if (isFlix) Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE).find(desc) else null
    val serverName = serverMatch?.value?.trim()
    val movieName = if (isFlix && serverMatch != null) {
        desc.substring(0, serverMatch.range.first).replace(Regex("^[\U0001F525\U0001F680\U0001F3AC\U0001F4FA\\s]+"), "").trim().ifBlank { null }
    } else null
    // Filename: pega ate a primeira bandeira ou TorBox ou tamanho
    val filename = if (!isFlix) {'''

# Match by structure without fragile unicode in old string
import re

pat = re.compile(
    r'    val isStreamHub = label\.contains\("StreamHub".*?\n'
    r'    val isFlix = label\.contains\("FlixHub".*?\n'
    r'    val isHubPack = isStreamHub \|\| isFlix\n'
    r'    val isJackett = label\.contains\("Jackettio".*?\n'
    r'    val head = when \{.*?\}\n'
    r'    val serverMatch = if \(isFlix\).*?\n'
    r'    val serverName = serverMatch\?\.value\?\.trim\(\)\n'
    r'    val movieName = if \(isFlix && serverMatch != null\) \{\n'
    r'        desc\.substring\(0, serverMatch\.range\.first\)\.replace\(Regex\("[^"]+"\), ""\)\.trim\(\)\.ifBlank \{ null \}\n'
    r'    \} else null\n'
    r'    // Filename: pega ate a primeira bandeira ou TorBox ou tamanho\n'
    r'    val filename = if \(!isFlix\) \{',
    re.S,
)

new = '''    val isStreamHub = label.contains("StreamHub", ignoreCase = true) || desc.contains("StreamHub", ignoreCase = true)
    val isFlix = label.contains("FlixHub", ignoreCase = true) || desc.contains("FlixHub", ignoreCase = true)
    val isHubPack = isStreamHub || isFlix
    val isJackett = label.contains("Jackettio", ignoreCase = true) || desc.contains("Jackettio", ignoreCase = true)
    val head = when {
        isStreamHub -> "StreamHub"
        isFlix -> "FlixHub"
        else -> label.split("\u00b7", "\u2022", "|").first().trim().ifBlank { source.displayName }
    }
    val serverMatch = if (isHubPack) Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE).find(desc + " " + label) else null
    val serverName = serverMatch?.value?.trim()
    val movieName = if (isHubPack && serverMatch != null) {
        val cutAt = serverMatch.range.first.coerceAtMost(desc.length)
        desc.substring(0, cutAt).replace(Regex("^[\\p{So}\\s]+"), "").trim().ifBlank { null }
    } else null
    // Filename: pega ate a primeira bandeira ou TorBox ou tamanho
    val filename = if (!isHubPack) {'''

m = pat.search(t)
if m:
    t = t[: m.start()] + new + t[m.end() :]
    print("ok ServerInfoCard reescrito")
elif "isStreamHub -> \"StreamHub\"" in t and "isHubPack) Regex" in t:
    print("ok ja corrigido")
else:
    # simpler replacements
    if 'val head = when { isStreamHub -> "StreamHub"; isFlix -> "FlixHub"; else ->' in t:
        t = t.replace(
            'val head = when { isStreamHub -> "StreamHub"; isFlix -> "FlixHub"; else -> label.split("\u00b7", "\u2022", "|").first().trim().ifBlank { source.displayName } }',
            'val head = when {\n        isStreamHub -> "StreamHub"\n        isFlix -> "FlixHub"\n        else -> label.split("\u00b7", "\u2022", "|").first().trim().ifBlank { source.displayName }\n    }',
            1,
        )
        # try with actual unicode
        t2 = p.read_text(encoding="utf-8") if False else t
        print("ok head multiline attempt")
    # force isFlix -> isHubPack for server/movie/filename
    t = t.replace(
        "val serverMatch = if (isFlix) Regex",
        "val serverMatch = if (isHubPack) Regex",
        1,
    )
    t = t.replace(
        "val movieName = if (isFlix && serverMatch != null)",
        "val movieName = if (isHubPack && serverMatch != null)",
        1,
    )
    t = t.replace(
        "val filename = if (!isFlix) {",
        "val filename = if (!isHubPack) {",
        1,
    )
    print("ok isHubPack replacements")

# Also fix head one-liner with real unicode chars from file
old_head_uni = None
for ln in t.splitlines():
    if "val head = when { isStreamHub" in ln and "; isFlix" in ln:
        old_head_uni = ln
        break
if old_head_uni:
    new_head = '''    val head = when {
        isStreamHub -> "StreamHub"
        isFlix -> "FlixHub"
        else -> label.split("\u00b7", "\u2022", "|").first().trim().ifBlank { source.displayName }
    }'''
    # use same separators as original line
    import re as _re
    msep = _re.search(r'label\.split\(([^)]+)\)', old_head_uni)
    if msep:
        new_head = f'''    val head = when {{
        isStreamHub -> "StreamHub"
        isFlix -> "FlixHub"
        else -> label.split({msep.group(1)}).first().trim().ifBlank {{ source.displayName }}
    }}'''
    t = t.replace(old_head_uni + "\n", new_head + "\n", 1)
    print("ok head multiline from original")

p.write_text(t, encoding="utf-8")
print("braces", t.count("{"), t.count("}"))
print("fim")
