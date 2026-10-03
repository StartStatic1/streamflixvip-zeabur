#!/usr/bin/env python3
"""Sobe limite de bridges live de 8 para 20 e loga quantas entraram."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "api/live-tv.js"
t = p.read_text(encoding="utf-8")

old = "is_active=eq.true&use_live=eq.true&select=id,name,xtream_host,xtream_user,xtream_pass,live_cats&order=created_at.desc&limit=8"
new = "is_active=eq.true&use_live=eq.true&select=id,name,xtream_host,xtream_user,xtream_pass,live_cats&order=created_at.desc&limit=20"
if old in t:
    t = t.replace(old, new)
    print("ok limit 20")
elif "limit=20" in t and "use_live=eq.true" in t:
    print("already limit 20")
else:
    print("query block not found")

needle = "results = results.concat(br);"
if needle in t and "pontes live carregadas" not in t:
    t = t.replace(
        needle,
        "results = results.concat(br);\n"
        "      console.log('[live-tv] pontes live carregadas:', br.map((x) => x.sourceName + ':' + ((x.streams&&x.streams.length)||0)).join(', '));",
        1,
    )
    print("ok log pontes")

p.write_text(t, encoding="utf-8")
print("done")
