#!/usr/bin/env python3
"""Corrige userId/showTicketPay fora de escopo no DetailContent."""
from pathlib import Path
import sys

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text(encoding="utf-8")
orig = t

# 1) DetailContent parameter
old_sig = """    onToggleFavorite: () -> Unit,
    skipHeroLoading: Boolean = false,
) {"""
new_sig = """    onToggleFavorite: () -> Unit,
    onTicketClick: () -> Unit = {},
    skipHeroLoading: Boolean = false,
) {"""
if "onTicketClick: () -> Unit" not in t[t.find("private fun DetailContent"):t.find("private fun DetailContent")+800]:
    if old_sig in t:
        t = t.replace(old_sig, new_sig, 1)
        print("ok DetailContent param")
    else:
        print("WARN DetailContent sig"); sys.exit(1)
else:
    print("DetailContent param already")

# 2) Call site
old_call = """                onToggleFavorite = viewModel::toggleFavorite,
            )"""
new_call = """                onToggleFavorite = viewModel::toggleFavorite,
                onTicketClick = {
                    if (!userId.isNullOrBlank()) showTicketPay = true
                },
            )"""
if "onTicketClick = {" not in t[t.find("DetailContent("):t.find("DetailContent(")+900]:
    if old_call in t:
        t = t.replace(old_call, new_call, 1)
        print("ok DetailContent call")
    else:
        print("WARN DetailContent call")
else:
    print("DetailContent call already")

# 3) VipLockCard usage inside DetailContent
old_usage = "VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = { if (!userId.isNullOrBlank()) showTicketPay = true })"
new_usage = "VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = onTicketClick)"
if old_usage in t:
    t = t.replace(old_usage, new_usage)
    print("ok VipLockCard usage")
elif "VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = onTicketClick)" in t:
    print("VipLockCard usage already")
else:
    # any broken variant
    import re
    t2, n = re.subn(
        r"VipLockCard\(onUpgradeClick = onUpgradeClick[^)]*\)",
        "VipLockCard(onUpgradeClick = onUpgradeClick, onTicketClick = onTicketClick)",
        t,
    )
    if n:
        t = t2
        print("ok VipLockCard usage regex", n)
    else:
        print("WARN VipLockCard usage")

# 4) VipLockCard function signature + button
if "private fun VipLockCard(onUpgradeClick: () -> Unit, onTicketClick" not in t:
    t = t.replace(
        "private fun VipLockCard(onUpgradeClick: () -> Unit)",
        "private fun VipLockCard(onUpgradeClick: () -> Unit, onTicketClick: () -> Unit = {})",
        1,
    )
    print("ok VipLockCard sig")

if "Ingresso R$" not in t:
    old_btn = """            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                Text("Seja VIP agora")
            }
        }
    }
}

/** Extrai um selo de qualidade"""
    new_btn = """            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                Text("Seja VIP agora")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onTicketClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ingresso R$ 2,50 \u00b7 24h s\u00f3 este t\u00edtulo")
            }
        }
    }
}

/** Extrai um selo de qualidade"""
    if old_btn in t:
        t = t.replace(old_btn, new_btn, 1)
        print("ok Ingresso button")
    else:
        print("WARN Ingresso button block")
else:
    print("Ingresso button already")

if t == orig:
    print("no changes?")
else:
    p.write_text(t, encoding="utf-8")
    print("written", p)

# sanity
assert "showTicketPay" in t
assert "onTicketClick = onTicketClick" in t or "onTicketClick = {" in t
assert "Unresolved" not in t
print("done")
