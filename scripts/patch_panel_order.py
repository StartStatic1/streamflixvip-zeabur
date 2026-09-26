#!/usr/bin/env python3
"""Ordem do painel de addons: bloco por addon (x100). Server 1/2/3 so dentro do FlixHub."""
from pathlib import Path
import re

p = Path(__file__).resolve().parents[1] / "lib" / "stremio-addons.js"
t = p.read_text(encoding="utf-8")

block = """  // Ordem do painel (priority) manda: cada addon em bloco (x100). Server N so ordena dentro do addon.
  let prio = Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY;
  prio = prio * 100;
  const sm = String(streamName || label || '').match(/(?:server|servidor)\\s*(\\d+)/i);
  if (sm) prio = prio + Number(sm[1]);
"""

if "prio = prio * 100" in t:
    print("already patched", p)
    raise SystemExit(0)

replaced = False

v1 = """  let prio = Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY;
  const sm = String(streamName || label || '').match(/(?:server|servidor)\\s*(\\d+)/i);
  if (sm) prio = prio + Number(sm[1]);"""
if v1 in t:
    t = t.replace(v1, block.rstrip(), 1)
    replaced = True

if not replaced:
    old_ret = """  return { source: {
    source_url: url,
    source_label: label,
    priority: Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY,
    _score: streamScore(q, a),
    meta: hasMeta ? meta : undefined,
  }, reason: null };
}"""
    new_ret = block + """
  return { source: {
    source_url: url,
    source_label: label,
    priority: prio,
    _score: streamScore(q, a),
    meta: hasMeta ? meta : undefined,
  }, reason: null };
}"""
    if old_ret in t:
        t = t.replace(old_ret, new_ret, 1)
        replaced = True

if not replaced:
    m = re.search(
        r"  let prio = Number\.isFinite\(Number\(addonPriority\)\).*?if \(sm\) prio = prio \+ Number\(sm\[1\]\);",
        t,
        re.S,
    )
    if m:
        t = t[: m.start()] + block.rstrip() + t[m.end() :]
        replaced = True

if not replaced:
    raise SystemExit("block not found — rode: python3 scripts/install_stremio_meta.py && python3 scripts/patch_server_order.py")

p.write_text(t, encoding="utf-8")
print("ok", p, "panel order x100")
