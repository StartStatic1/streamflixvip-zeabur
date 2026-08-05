#!/usr/bin/env python3
"""Corrige Informe id (sourceId) + delete grava vip_source_blocks."""
from pathlib import Path
from datetime import datetime
import shutil

p = Path("/root/streamflix/api/admin-vip.js")
if not p.exists():
    p = Path("/root/streamflixvip-zeabur/api/admin-vip.js")
if not p.exists():
    raise SystemExit("admin-vip.js nao encontrado")

bak = p.with_suffix(".js.bak-" + datetime.now().strftime("%Y%m%d%H%M%S"))
shutil.copy2(p, bak)
t = p.read_text(encoding="utf-8", errors="replace")

HELPER = """
  function resolveSourceId(b) {
    if (b && b.id != null) return b.id;
    if (b && b.sourceId != null) return b.sourceId;
    return null;
  }
"""
if "function resolveSourceId" not in t:
    needle = "const action = body && body.action;"
    if needle in t:
        t = t.replace(needle, needle + "\n" + HELPER, 1)
        print("helper ok")

old_upd = "if (action === 'update-source') {\n    const { id, source_url, source_label, priority, season, episode, title } = body;\n    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"
new_upd = "if (action === 'update-source') {\n    const id = resolveSourceId(body);\n    const { source_url, source_label, priority, season, episode, title } = body;\n    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"
if old_upd in t:
    t = t.replace(old_upd, new_upd, 1)
    print("update-source ok")
else:
    print("update-source pattern miss")

old_tog = "if (action === 'toggle-source') {\n    const { id, is_active } = body;\n    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"
new_tog = "if (action === 'toggle-source') {\n    const id = resolveSourceId(body);\n    const is_active = body.is_active !== undefined ? body.is_active : body.isActive;\n    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"
if old_tog in t:
    t = t.replace(old_tog, new_tog, 1)
    print("toggle-source ok")
else:
    print("toggle-source pattern miss")

old_del = """  if (action === 'delete-source') {
    const { id } = body;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao excluir fonte', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }"""

new_del = """  if (action === 'delete-source') {
    const id = resolveSourceId(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    let row = null;
    try {
      const gr = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}&select=tmdb_id,media_type,source_label,source_url&limit=1`, { headers: svcHeaders });
      const rows = await gr.json();
      if (Array.isArray(rows) && rows[0]) row = rows[0];
    } catch (_) {}
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao excluir fonte', detail: await r.text() }); return; }
    if (row && row.tmdb_id != null && row.source_label) {
      try {
        await fetch(`${SUPABASE_URL}/rest/v1/vip_source_blocks?on_conflict=tmdb_id,source_label`, {
          method: 'POST',
          headers: { ...svcHeaders, Prefer: 'resolution=merge-duplicates' },
          body: JSON.stringify({
            tmdb_id: row.tmdb_id,
            media_type: row.media_type || 'movie',
            source_label: row.source_label,
            source_url: row.source_url || null,
            reason: 'panel-delete',
          }),
        });
      } catch (_) {}
    }
    res.status(200).json({ success: true, blocked: !!(row && row.source_label) });
    return;
  }"""

if old_del in t:
    t = t.replace(old_del, new_del, 1)
    print("delete-source ok")
elif "if (action === 'delete-source')" in t:
    t = t.replace(
        "if (action === 'delete-source') {\n    const { id } = body;",
        "if (action === 'delete-source') {\n    const id = resolveSourceId(body);",
        1,
    )
    print("delete soft id ok")
else:
    print("delete-source miss")

p.write_text(t, encoding="utf-8")
print("OK", p, p.stat().st_size)
