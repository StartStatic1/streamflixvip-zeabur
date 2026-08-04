#!/usr/bin/env python3
"""Restaura actions faltando em api/admin-vip.js (create, deactivate, set-vip-title...)."""
from pathlib import Path
from datetime import datetime
import shutil

candidates = [
    Path("/root/streamflix/api/admin-vip.js"),
    Path("/root/streamflixvip-zeabur/api/admin-vip.js"),
]
idx = next((p for p in candidates if p.exists()), None)
if not idx:
    raise SystemExit("api/admin-vip.js nao encontrado")

bak = idx.with_suffix(".js.bak-" + datetime.now().strftime("%Y%m%d%H%M%S"))
shutil.copy2(idx, bak)
print("backup", bak)

t = idx.read_text(encoding="utf-8", errors="replace")

BLOCK = r'''
  // ─── RESTORED ACTIONS (create / codes / vip title) ───
  if (action === 'create') {
    const { codes } = body;
    if (!Array.isArray(codes) || codes.length === 0) {
      res.status(400).json({ error: 'Informe os codigos' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(codes),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
  }

  if (action === 'deactivate' || action === 'reactivate') {
    const { code } = body;
    if (!code) { res.status(400).json({ error: 'Informe o code' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({ is_active: action === 'reactivate' }),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao atualizar codigo', detail: result }); return; }
    res.status(200).json({ updated: result });
    return;
  }

  if (action === 'get-vip-title') {
    const tmdbId = body.tmdb_id;
    const mediaType = body.media_type || 'movie';
    if (tmdbId == null) { res.status(400).json({ error: 'tmdb_id obrigatorio' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_titles?tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.${encodeURIComponent(mediaType)}&select=*&limit=1`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro get-vip-title', detail: rows }); return; }
    const row = Array.isArray(rows) && rows[0] ? rows[0] : null;
    res.status(200).json({
      config: row || { vip_lock: false, vip_free_episode_limit: null, tmdb_id: tmdbId, media_type: mediaType },
    });
    return;
  }

  if (action === 'set-vip-title') {
    const tmdbId = body.tmdb_id;
    const mediaType = body.media_type || 'movie';
    const vipLock = !!body.vip_lock;
    let freeLimit = body.vip_free_episode_limit;
    if (freeLimit === '' || freeLimit === undefined) freeLimit = null;
    if (freeLimit != null) freeLimit = parseInt(freeLimit, 10);
    if (Number.isNaN(freeLimit)) freeLimit = null;
    if (vipLock) freeLimit = null;
    if (tmdbId == null) { res.status(400).json({ error: 'tmdb_id obrigatorio' }); return; }

    const payload = {
      tmdb_id: Number(tmdbId),
      media_type: mediaType,
      vip_lock: vipLock,
      vip_free_episode_limit: freeLimit,
      updated_at: new Date().toISOString(),
    };

    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_titles?on_conflict=tmdb_id,media_type`,
      {
        method: 'POST',
        headers: {
          ...svcHeaders,
          Prefer: 'resolution=merge-duplicates,return=representation',
        },
        body: JSON.stringify(payload),
      },
    );
    const result = await r.json();
    if (!r.ok) {
      const u = await fetch(
        `${SUPABASE_URL}/rest/v1/vip_titles?tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.${encodeURIComponent(mediaType)}`,
        {
          method: 'PATCH',
          headers: { ...svcHeaders, Prefer: 'return=representation' },
          body: JSON.stringify({
            vip_lock: vipLock,
            vip_free_episode_limit: freeLimit,
            updated_at: new Date().toISOString(),
          }),
        },
      );
      const updated = await u.json();
      if (!u.ok || (Array.isArray(updated) && updated.length === 0)) {
        const ins = await fetch(`${SUPABASE_URL}/rest/v1/vip_titles`, {
          method: 'POST',
          headers: { ...svcHeaders, Prefer: 'return=representation' },
          body: JSON.stringify(payload),
        });
        const created = await ins.json();
        if (!ins.ok) {
          res.status(502).json({ error: 'Erro set-vip-title', detail: { post: result, patch: updated, insert: created } });
          return;
        }
        res.status(200).json({ config: Array.isArray(created) ? created[0] : created });
        return;
      }
      res.status(200).json({ config: Array.isArray(updated) ? updated[0] : updated });
      return;
    }
    res.status(200).json({ config: Array.isArray(result) ? result[0] : result });
    return;
  }

  if (action === 'get-vip-titles-batch') {
    const items = body.items || [];
    if (!Array.isArray(items) || items.length === 0) {
      res.status(200).json({ configs: {} });
      return;
    }
    const byType = {};
    for (const it of items) {
      const mt = it.media_type || it.type || 'movie';
      const id = it.tmdb_id != null ? it.tmdb_id : it.id;
      if (id == null) continue;
      if (!byType[mt]) byType[mt] = [];
      byType[mt].push(id);
    }
    const configs = {};
    for (const [mt, ids] of Object.entries(byType)) {
      const uniq = [...new Set(ids.map(Number).filter((n) => !Number.isNaN(n)))];
      if (!uniq.length) continue;
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/vip_titles?media_type=eq.${encodeURIComponent(mt)}&tmdb_id=in.(${uniq.join(',')})&select=*`,
        { headers: svcHeaders },
      );
      const rows = await r.json();
      if (Array.isArray(rows)) {
        for (const row of rows) {
          configs[`${row.media_type}:${row.tmdb_id}`] = row;
        }
      }
    }
    res.status(200).json({ configs });
    return;
  }

'''

MARKER = "RESTORED ACTIONS (create / codes / vip title)"
if MARKER in t:
    print("ja restaurado")
else:
    needle = "res.status(501).json({\n    error: 'Acao ainda nao restaurada:"
    if needle in t:
        t = t.replace(needle, BLOCK + "\n  " + needle, 1)
        print("injected before 501")
    elif "Acao ainda nao restaurada" in t:
        t = t.replace(
            "  res.status(501).json({",
            BLOCK + "\n  res.status(501).json({",
            1,
        )
        print("injected soft")
    else:
        raise SystemExit("fallback 501 nao encontrado")

idx.write_text(t, encoding="utf-8")
print("OK", idx, idx.stat().st_size)
print("create count", t.count("action === 'create'"))
print("set-vip-title count", t.count("set-vip-title"))
