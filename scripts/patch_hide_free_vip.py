#!/usr/bin/env python3
"""Esconde a faixa Area Free quando o usuario e VIP."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/home/HomeViewModel.kt"
t = p.read_text()
if "import com.streamflixvip.app.data.VipStatusHolder" not in t:
    t = t.replace("import com.streamflixvip.app.data.", "import com.streamflixvip.app.data.VipStatusHolder\nimport com.streamflixvip.app.data.", 1)
    print("ok import")
else:
    print("ok import ja")
old = """                        freeArea.takeIf { it.isNotEmpty() }?.let {
                            HomeRow(\"Area Free\", it, \"movie\")
                        },"""
new = """                        freeArea.takeIf { it.isNotEmpty() && !VipStatusHolder.isVipNow() }?.let {
                            HomeRow(\"Area Free\", it, \"movie\")
                        },"""
if "isVipNow()" in t and "Area Free" in t:
    print("ok row ja")
elif old in t:
    t = t.replace(old, new, 1)
    print("ok row")
else:
    print("aviso row")
p.write_text(t)
print("fim patch_hide_free_vip")
