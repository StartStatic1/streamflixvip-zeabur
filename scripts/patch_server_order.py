#!/usr/bin/env python3
"""Ajusta prioridade FlixHub Server N (ordem 1,2,3... no app)."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "lib" / "stremio-addons.js"
t = p.read_text(encoding="utf-8")
old = """  return { source: {
    source_url: url,
    source_label: label,
    priority: Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY,
    _score: streamScore(q, a),
    meta: hasMeta ? meta : undefined,
  }, reason: null };
}"""
new = """  let prio = Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY;
  const sm = String(streamName || label || '').match(/(?:server|servidor)\\s*(\\d+)/i);
  if (sm) prio = prio + Number(sm[1]);
  return { source: {
    source_url: url,
    source_label: label,
    priority: prio,
    _score: streamScore(q, a),
    meta: hasMeta ? meta : undefined,
  }, reason: null };
}"""
if "prio = Number.isFinite" in t and "server|servidor" in t:
    print("already patched", p)
elif old not in t:
    raise SystemExit("block not found — rode antes: python3 scripts/install_stremio_meta.py")
else:
    p.write_text(t.replace(old, new, 1), encoding="utf-8")
    print("ok", p, "server order patched")
