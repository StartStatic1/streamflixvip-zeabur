#!/usr/bin/env python3
"""1) Label = PackBrand · ServerName (grupo no app = pack, nao cada server)
2) Corrige Regex Kotlin (\\s \\d) que quebrava o build
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]

# ---------- Backend: prefixar label com brand do pack ----------
addons = root / "lib/stremio-addons.js"
t = addons.read_text(encoding="utf-8")

old = """  const host = shortAddonName(addonName);
  let label;
  if (looksNumberedServer(streamName)) {
    label = streamName.slice(0, 42);
    if (q && !streamName.toLowerCase().includes(q.toLowerCase())) label += ' \\u00b7 ' + q;
    if (a && !/dublad|legendad/i.test(streamName)) label += ' \\u00b7 ' + a;
  } else {
"""
# actual file uses real unicode middot in JS string as \u00b7
old = """  const host = shortAddonName(addonName);
  let label;
  if (looksNumberedServer(streamName)) {
    label = streamName.slice(0, 42);
    if (q && !streamName.toLowerCase().includes(q.toLowerCase())) label += ' \u00b7 ' + q;
    if (a && !/dublad|legendad/i.test(streamName)) label += ' \u00b7 ' + a;
  } else {
"""

new = """  const host = shortAddonName(addonName);
  // Marca do pack (stream.name no flixhub.js = StreamHub / FlixHub / etc.)
  const packBrand = (function () {
    const n = stripNoise(stream && stream.name);
    if (n && n.length >= 2 && n.length <= 24 && !/server|servidor/i.test(n)) return n;
    if (host && !/^addon$/i.test(host)) return host;
    return null;
  })();
  let label;
  if (looksNumberedServer(streamName)) {
    let core = streamName;
    // Evita "FlixHub FlixHub Server 2" / garante "StreamHub · Kraps Server 8"
    if (packBrand && !new RegExp('^' + packBrand.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&') + '\\b', 'i').test(core)) {
      core = packBrand + ' \u00b7 ' + core;
    }
    label = core.slice(0, 52);
    if (q && !core.toLowerCase().includes(String(q).toLowerCase())) label += ' \u00b7 ' + q;
    if (a && !/dublad|legendad/i.test(core)) label += ' \u00b7 ' + a;
  } else {
"""

if "packBrand" in t and "StreamHub \u00b7" in t.replace('·','\u00b7') or "packBrand" in t:
    if "const packBrand" in t:
        print("ok packBrand ja no backend")
    else:
        print("check packBrand")
elif old in t:
    t = t.replace(old, new, 1)
    addons.write_text(t, encoding="utf-8")
    print("ok backend packBrand no label")
else:
    # looser
    marker = "  if (looksNumberedServer(streamName)) {\n    label = streamName.slice(0, 42);"
    if marker in t and "packBrand" not in t:
        rep = """  const packBrand = (function () {
    const n = stripNoise(stream && stream.name);
    if (n && n.length >= 2 && n.length <= 24 && !/server|servidor/i.test(n)) return n;
    const h = shortAddonName(addonName);
    if (h && !/^addon$/i.test(h)) return h;
    return null;
  })();
  if (looksNumberedServer(streamName)) {
    let core = streamName;
    if (packBrand && !new RegExp('^' + packBrand.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&') + '\\b', 'i').test(core)) {
      core = packBrand + ' \u00b7 ' + core;
    }
    label = core.slice(0, 52);"""
        # Fix the replace pattern - the JS escape is messy. Simpler approach:
        pass
    # Simple reliable replace
    simple_old = """  if (looksNumberedServer(streamName)) {
    label = streamName.slice(0, 42);
    if (q && !streamName.toLowerCase().includes(q.toLowerCase())) label += ' \u00b7 ' + q;
    if (a && !/dublad|legendad/i.test(streamName)) label += ' \u00b7 ' + a;
  }"""
    simple_new = """  const packBrand = (function () {
    const n = stripNoise(stream && stream.name);
    if (n && n.length >= 2 && n.length <= 24 && !/server|servidor/i.test(n)) return n;
    const h = shortAddonName(addonName);
    if (h && !/^addon$/i.test(h)) return h;
    return null;
  })();
  if (looksNumberedServer(streamName)) {
    let core = String(streamName || '');
    if (packBrand && core.toLowerCase().indexOf(packBrand.toLowerCase()) !== 0) {
      core = packBrand + ' \u00b7 ' + core;
    }
    label = core.slice(0, 52);
    if (q && core.toLowerCase().indexOf(String(q).toLowerCase()) < 0) label += ' \u00b7 ' + q;
    if (a && !/dublad|legendad/i.test(core)) label += ' \u00b7 ' + a;
  }"""
    if simple_old in t:
        t = t.replace(simple_old, simple_new, 1)
        addons.write_text(t, encoding="utf-8")
        print("ok backend packBrand (simple)")
    elif "const packBrand" in t:
        print("ok packBrand already")
    else:
        print("aviso backend: bloco looksNumberedServer nao encontrado")
        idx = t.find("looksNumberedServer(streamName)")
        print(repr(t[idx:idx+200]) if idx>=0 else "not found")

# ---------- Kotlin: fix broken regex escapes ----------
kt = root / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
tk = kt.read_text(encoding="utf-8")

# Broken patterns from previous script (single backslash in source)
fixes = [
    (
        'Regex("([A-Za-z0-9]+\s+)?Server\s*\d+", RegexOption.IGNORE_CASE)',
        'Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE)',
    ),
    (
        'Regex("^[\p{So}\s]+")',
        'Regex("^[\\p{So}\\s]+")',
    ),
]

# More reliable: find the actual lines
changed = False
lines = tk.splitlines(keepends=True)
out = []
for ln in lines:
    if "serverMatch" in ln and "Regex(" in ln and isHubPack_line_broken(ln) if False else True:
        if "serverMatch" in ln and "Regex(" in ln:
            # Fix single-backslash \s \d in Kotlin string for regex
            if '\\s' not in ln and '\s' in ln:
                ln2 = ln.replace("\s", "\\s").replace("\d", "\\d")
                # careful not to double
                if ln2 != ln:
                    ln = ln2
                    changed = True
                    print("ok fixed serverMatch regex")
    if "cutAt" in ln or ("replace(Regex(" in ln and "So" in ln):
        if "\\p{So}" not in ln and "p{So}" in ln:
            ln = ln.replace(r"\p{So}", r"\p{So}")  # noop-ish
        # rewrite the movieName replace line entirely if fragile
    out.append(ln)

tk2 = "".join(out)

# Direct known-bad substrings from github raw
bad1 = 'Regex("([A-Za-z0-9]+\s+)?Server\s*\d+", RegexOption.IGNORE_CASE)'
good1 = 'Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE)'
# In Python reading the file, the single \s is one char backslash+s or just s?
# From curl output: Regex("([A-Za-z0-9]+\s+)?Server\s*\d+"  - in the file these are literal backslash-s

if 'Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+"' in tk:
    print("ok serverMatch regex already escaped")
elif re.search(r'Regex\("\(\[A-Za-z0-9\]\+\\s\+\)\?Server\\s\*\\d\+"', tk):
    print("ok serverMatch ok")
else:
    # Replace any broken version
    tk = re.sub(
        r'Regex\("\(\[A-Za-z0-9\]\+\\s\+\)\?Server\\s\*\\d\+"',
        'Regex("([A-Za-z0-9]+\\\\s+)?Server\\\\s*\\\\d+"',
        tk,
    )
    # The above might be wrong. Let's read exact bytes around serverMatch
    m = re.search(r'val serverMatch = .*', tk)
    if m:
        print("current serverMatch line:", repr(m.group(0)[:120]))

# Explicit rewrite of the three fragile lines using unique anchors
old_block = None
m = re.search(
    r'(    val serverMatch = if \(isHubPack\) Regex\(".*?", RegexOption\.IGNORE_CASE\)\.find\(desc \+ " " \+ label\) else null\n'
    r'    val serverName = serverMatch\?\.value\?\.trim\(\)\n'
    r'    val movieName = if \(isHubPack && serverMatch != null\) \{\n'
    r'        val cutAt = serverMatch\.range\.first\.coerceAtMost\(desc\.length\)\n'
    r'        desc\.substring\(0, cutAt\)\.replace\(Regex\(".*?"\), ""\)\.trim\(\)\.ifBlank \{ null \}\n'
    r'    \} else null\n)',
    tk,
    re.S,
)
if m:
    replacement = (
        '    val serverMatch = if (isHubPack) Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE).find(desc + " " + label) else null\n'
        '    val serverName = serverMatch?.value?.trim()\n'
        '    val movieName = if (isHubPack && serverMatch != null) {\n'
        '        val cutAt = serverMatch.range.first.coerceAtMost(desc.length)\n'
        '        desc.substring(0, cutAt).replace(Regex("^[\\p{So}\\s]+"), "").trim().ifBlank { null }\n'
        '    } else null\n'
    )
    tk = tk[: m.start()] + replacement + tk[m.end() :]
    print("ok kotlin regex block fixed")
    changed = True
else:
    print("aviso: bloco serverMatch nao casou")
    m2 = re.search(r'val serverMatch.*', tk)
    if m2:
        print(repr(m2.group(0)[:150]))

kt.write_text(tk, encoding="utf-8")
print("braces", tk.count("{"), tk.count("}"))
print("fim apply_pack_group_labels")
