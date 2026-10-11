#!/usr/bin/env python3
"""Restaura VipSection original (compila) e troca Vitalicio por Anual 89,90."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
target = root / "android/app/src/main/java/com/streamflixvip/app/ui/vip/VipSection.kt"

try:
    content = subprocess.check_output(
        ["git", "show", "a59efa4:android/app/src/main/java/com/streamflixvip/app/ui/vip/VipSection.kt"],
        cwd=root, text=True,
    )
except Exception as e:
    print("falha git show a59efa4:", e)
    raise SystemExit(1)

old = '''                VipPlanCard(
                    title = "Vitalicio",
                    price = "R$ 149,90",
                    priceSuffix = "uma vez · para sempre",
                    badge = "Melhor valor",
                    highlighted = false,
                    accentOutline = true,
                    onClick = { viewModel.startPayment(149.90, "VIP Vitalicio", 876000) }
                )'''
new = '''                VipPlanCard(
                    title = "Anual",
                    price = "R$ 89,90",
                    priceSuffix = "12 meses",
                    badge = "Melhor valor",
                    highlighted = false,
                    accentOutline = true,
                    onClick = { viewModel.startPayment(89.90, "VIP Anual", 8760) }
                )'''
if old not in content:
    if "Anual" in content and "89.90" in content and "formatVipExpiry" in content and "redeemCode" not in content:
        print("ok ja correto")
        raise SystemExit(0)
    print("aviso: bloco Vitalicio nao encontrado")
    raise SystemExit(1)
content = content.replace(old, new, 1)
if "codeInput" in content or "::redeemCode" in content:
    print("erro: ainda tem simbolos quebrados")
    raise SystemExit(1)
target.write_text(content)
print("ok VipSection Anual restaurado")
print("  89.90", "89.90" in content)
print("  formatVipExpiry", "formatVipExpiry" in content)
print("  redeem", "::redeem" in content)
