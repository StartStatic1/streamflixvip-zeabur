#!/usr/bin/env python3
"""Aplica bloqueio permanente: Desativar e Excluir gravam vip_source_blocks; Ativar remove."""
from pathlib import Path

p = Path('/root/streamflix/api/admin-vip.js')
t = p.read_text()

if "Desativar = bloqueia sync" in t or "blocked: !is_active" in t:
    print('JA_APLICADO')
else:
    start = t.find("  if (action === 'toggle-source')")
    end = t.find("  if (action === 'dashboard-stats')")
    if start < 0 or end < 0:
        raise SystemExit('marcadores nao encontrados')
    new = r"""  if (action === 'toggle-source') {
    const id = body.id || body.sourceId;
    const is_active = body.is_active !== undefined ? body.is_active : body.isActive;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    let row = null;
    try {
      const gr = await fetch(SUPABASE_URL + '/rest/v1/vip_sources?id=eq.' + encodeURIComponent(id) + '&select=tmdb_id,media_type,season,episode,source_label,source_url&limit=1', { headers: svcHeaders });
      if (gr.ok) { const arr = await gr.json(); row = Array.isArray(arr) ? arr[0] : null; }
    } catch (_) {}
    const r = await fetch(SUPABASE_URL + '/rest/v1/vip_sources?id=eq.' + encodeURIComponent(id), {
      method: 'PATCH',
      headers: Object.assign({}, svcHeaders, { Prefer: 'return=representation' }),
      body: JSON.stringify({ is_active: !!is_active }),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao alternar fonte', detail: await r.text() }); return; }
    // Desativar = bloqueia sync; Ativar = remove bloqueio
    if (row && row.tmdb_id != null) {
      try {
        if (!is_active) {
          await fetch(SUPABASE_URL + '/rest/v1/vip_source_blocks', {
            method: 'POST',
            headers: Object.assign({}, svcHeaders, { Prefer: 'resolution=ignore-duplicates,return=minimal' }),
            body: JSON.stringify({
              tmdb_id: row.tmdb_id,
              media_type: row.media_type || 'movie',
              season: row.season == null ? null : row.season,
              episode: row.episode == null ? null : row.episode,
              source_label: row.source_label || null,
              source_url: row.source_url || null,
            }),
          });
        } else {
          let q = 'tmdb_id=eq.' + encodeURIComponent(row.tmdb_id)
            + '&media_type=eq.' + encodeURIComponent(row.media_type || 'movie');
          if (row.source_label) q += '&source_label=eq.' + encodeURIComponent(row.source_label);
          if (row.season != null) q += '&season=eq.' + encodeURIComponent(row.season);
          else q += '&season=is.null';
          if (row.episode != null) q += '&episode=eq.' + encodeURIComponent(row.episode);
          else q += '&episode=is.null';
          await fetch(SUPABASE_URL + '/rest/v1/vip_source_blocks?' + q, { method: 'DELETE', headers: svcHeaders });
        }
      } catch (_) {}
    }
    res.status(200).json({ success: true, blocked: !is_active });
    return;
  }

  if (action === 'delete-source') {
    const id = body.id || body.sourceId;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    let row = null;
    try {
      const gr = await fetch(SUPABASE_URL + '/rest/v1/vip_sources?id=eq.' + encodeURIComponent(id) + '&select=tmdb_id,media_type,season,episode,source_label,source_url&limit=1', { headers: svcHeaders });
      if (gr.ok) { const arr = await gr.json(); row = Array.isArray(arr) ? arr[0] : null; }
    } catch (_) {}
    const r = await fetch(SUPABASE_URL + '/rest/v1/vip_sources?id=eq.' + encodeURIComponent(id), { method: 'DELETE', headers: svcHeaders });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao excluir fonte', detail: await r.text() }); return; }
    let blocked = false;
    if (row && row.tmdb_id != null) {
      try {
        const br = await fetch(SUPABASE_URL + '/rest/v1/vip_source_blocks', {
          method: 'POST',
          headers: Object.assign({}, svcHeaders, { Prefer: 'resolution=ignore-duplicates,return=minimal' }),
          body: JSON.stringify({
            tmdb_id: row.tmdb_id,
            media_type: row.media_type || 'movie',
            season: row.season == null ? null : row.season,
            episode: row.episode == null ? null : row.episode,
            source_label: row.source_label || null,
            source_url: row.source_url || null,
          }),
        });
        blocked = br.ok || br.status === 201 || br.status === 200;
      } catch (_) {}
    }
    res.status(200).json({ success: true, blocked: !!blocked });
    return;
  }

"""
    t = t[:start] + new + t[end:]
    p.write_text(t)
    print('APLICADO', p.stat().st_size)

import subprocess
r = subprocess.run(['node', '--check', str(p)], capture_output=True, text=True)
print('SYNTAX', 'OK' if r.returncode == 0 else r.stderr)
