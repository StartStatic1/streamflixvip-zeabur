#!/usr/bin/env python3
"""Troca ciano hardcoded pelo branco neutro. Nao mexe em logica."""
from pathlib import Path
root = Path(__file__).resolve().parents[1] / "android"
old = "Color(0xFF00E5FF)"
new = "Color(0xFFFFFFFF)"
n = 0
for p in root.rglob("*.kt"):
    t = p.read_text(encoding="utf-8")
    if old not in t:
        continue
    p.write_text(t.replace(old, new), encoding="utf-8")
    print("ok", p.relative_to(root.parent))
    n += 1
print("arquivos", n)
