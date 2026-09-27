#!/usr/bin/env python3
"""Completa o meta no return que o script anterior nao achou."""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "lib" / "stremio-addons.js"
t = p.read_text()

if "temporary loader" in t or len(t) < 3000:
    cache = root / "lib" / "stremio-addons.cache.js"
    if cache.exists() and cache.stat().st_size > 3000:
        t = cache.read_text()
        print("ok leu cache")
    else:
        raise SystemExit("arquivo addon stub sem cache")

if "const origin = detectOrigin" not in t:
    t2, n = re.subn(
        r"const a = detectAudio\(raw(?:, addonName)?\);\n",
        "const a = detectAudio(raw, addonName);\n  const origin = detectOrigin(stream, addonName);\n  const size = detectSize(raw, stream);\n",
        t,
        count=1,
    )
    if n:
        t = t2
        print("ok injetou origin/size")
    else:
        print("aviso nao achou detectAudio(raw)")

if "meta: { quality:" in t:
    print("ok meta ja estava no return")
else:
    t2, n = re.subn(
        r"(return \{ source: \{[\s\S]{0,400}?_score:\s*streamScore\([^)]*\),)",
        r"\1\n    meta: { quality: q || null, audio: a || null, origin: origin || null, size: size || null },",
        t,
        count=1,
    )
    if n:
        t = t2
        print("ok meta no return")
    else:
        t2, n = re.subn(
            r"(source_label:\s*label,\s*priority:[^\n]+\n)",
            r"\1    meta: { quality: q || null, audio: a || null, origin: origin || null, size: size || null },\n",
            t,
            count=1,
        )
        if n:
            t = t2
            print("ok meta no return (fallback)")
        else:
            print("aviso return ainda nao achado")
            i = t.find("function streamToSourceDetailed")
            print(t[i:i+1600] if i >= 0 else t[t.find("source_label"):t.find("source_label")+500])

t2, n = re.subn(
    r"\.map\(\(\{ source_url, source_label, priority \}\) => \(\{ source_url, source_label, priority \}\)\)",
    ".map(({ source_url, source_label, priority, meta }) => ({ source_url, source_label, priority, meta }))",
    t,
    count=1,
)
if n:
    t = t2
    print("ok map preserva meta")
elif "priority, meta" in t:
    print("ok map ja ok")
else:
    print("aviso map")

p.write_text(t)
cache = root / "lib" / "stremio-addons.cache.js"
cache.write_text(t)
print("fim apply_source_meta_fix", p.stat().st_size)
if "meta: { quality:" in t:
    print("ok conferido meta presente")
else:
    print("FALHOU meta ausente")
