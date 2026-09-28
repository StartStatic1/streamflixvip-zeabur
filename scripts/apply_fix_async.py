#!/usr/bin/env python3
"""Corrige 'async async function' que quebrou o login dos paineis."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
n = 0
for p in sorted((root / "Public").glob("admin*.html")):
    t = p.read_text()
    if "async async function" not in t:
        print("ok", p.name)
        continue
    t = t.replace("async async function", "async function")
    p.write_text(t)
    n += 1
    print("corrigiu", p.name)
print("fim apply_fix_async", n)
