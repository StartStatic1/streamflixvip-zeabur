#!/usr/bin/env python3
"""Cache VIP curto + invalida no resgate. Teste de 1h deixa de mentir."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]

cache_src = Path(__file__).with_name("vip-status-cache.js")
dest = root / "lib/vip-status-cache.js"
if cache_src.exists():
    dest.write_text(cache_src.read_text())
    print("ok cache file copy")
elif dest.exists():
    print("ok cache file ja")
else:
    print("aviso cache — use lib/vip-status-cache.js do git pull")

p = root / "api/vip-status.js"
t = p.read_text()
if "lib/vip-status-cache" not in t:
    t = t.replace(
        "const VIP_CACHE_TTL_MS = Math.max(",
        "const vipMem = require('../lib/vip-status-cache');\nconst VIP_CACHE_TTL_MS = Math.max(",
        1,
    )
    t = t.replace(
        """function cacheGet(userId) {\n  const hit = vipCache.get(userId);\n  if (!hit) return None\n""".replace("None","null") if False else
        """function cacheGet(userId) {\n  const hit = vipCache.get(userId);\n  if (!hit) return null;\n  if (Date.now() - hit.at > VIP_CACHE_TTL_MS) {\n    vipCache.delete(userId);\n    return null;\n  }\n  return hit.body;\n}""",
        """function cacheGet(userId) {\n  return vipMem.get(userId);\n}""",
        1,
    )
    t = t.replace(
        """function cacheSet(userId, body) {\n  if (vipCache.size >= VIP_CACHE_MAX) {\n    const first = vipCache.keys().next().value;\n    if (first) vipCache.delete(first);\n  }\n  vipCache.set(userId, { at: Date.now(), body });\n}""",
        """function cacheSet(userId, body) {\n  vipMem.set(userId, body);\n}""",
        1,
    )
    p.write_text(t)
    print("ok vip-status")
else:
    print("ok vip-status ja")

p = root / "api/redeem-vip.js"
t = p.read_text()
if "vip-status-cache" not in t:
    t = t.replace(
        "const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';",
        "const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';\nconst vipMem = require('../lib/vip-status-cache');",
        1,
    )
    t = t.replace(
        """    res.status(200).json({\n      success: true,\n      expiresAt: newExpiry.toISOString(),\n      planLabel: vipCode.plan_label || null,\n    });""",
        """    try { vipMem.invalidate(userId); } catch (_) {}\n    res.status(200).json({\n      success: true,\n      expiresAt: newExpiry.toISOString(),\n      planLabel: vipCode.plan_label || null,\n    });""",
        1,
    )
    p.write_text(t)
    print("ok redeem")
else:
    print("ok redeem ja")

p = root / "api/infinitepay.js"
t = p.read_text()
if "require('../lib/vip-status-cache')" not in t:
    t = t.replace(
        "const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';",
        "const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';\nconst vipMem = require('../lib/vip-status-cache');",
        1,
    )
    t = t.replace(
        "    console.log(`[infinitepay] VIP ok user=${userId} until=${newExpiry.toISOString()}`);",
        "    try { vipMem.invalidate(userId); } catch (_) {}\n    console.log(`[infinitepay] VIP ok user=${userId} until=${newExpiry.toISOString()}`);",
        1,
    )
    p.write_text(t)
    print("ok infinitepay invalidate")
else:
    print("ok infinitepay invalidate ja")

print("fim apply_vip_secure")
