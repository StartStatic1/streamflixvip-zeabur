#!/usr/bin/env python3
"""VIP: requiresVip na resposta NAO significa sem fonte.
So code VIP_REQUIRED / AUTH_REQUIRED zera a lista.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailViewModel.kt"
t = p.read_text(encoding="utf-8")

bad = 'if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED" || res.requiresVip)'
good = 'if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED")'

if bad in t:
    t = t.replace(bad, good)
    p.write_text(t, encoding="utf-8")
    print("ok fixed VIP sources (removed requiresVip from empty gate)")
elif good in t and "|| res.requiresVip)" not in t:
    print("already fixed")
else:
    print("pattern not found")
    raise SystemExit(1)
