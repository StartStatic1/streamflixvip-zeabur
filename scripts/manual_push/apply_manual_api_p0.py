#!/usr/bin/env python3
"""Apply manual channels support to admin-vip.js and live-tv.js."""
from pathlib import Path

av = Path("api/admin-vip.js")
s = av.read_text()
if "list-live-tv-manual" not in s:
    insert_after = s.find("  if (action === 'list-ads')")
    if insert_after < 0:
        raise SystemExit("list-ads not found")
    block = """  // ── Canais manuais (URL .ts / M3U) ──────────────────────────────────────
  if (action === 'list-live-tv-manual') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/live_tv_manual_channels?select=id,name,logo,group_title,stream_url,priority,is_active,created_at&order=priority.asc.nullslast,name.asc`,
      { headers: svcHeaders },
    );
    if (!r.ok) {
      const detail = await r.text();
      res.status(502).json({
        error: 'Tabela live_tv_manual_channels ausente. Rode o SQL no Supabase.',
        detail,
        sqlHint: 'scripts/live_tv_manual_channels.sql',
      });
      return;
    }
    res.status(200).json({ channels: await r.json() });
    return;
  }

  if (action === 'create-live-tv-manual') {
    let name = body.name != null ? String(body.name).trim() : '';
    let logo = body.logo != null ? String(body.logo).trim() : '';
    let groupTitle = body.groupTitle != null || body.group_title != null
      ? String(body.groupTitle || body.group_title || '').trim()
      : 'Manuais';
    let streamUrl = body.streamUrl != null || body.stream_url != null
      ? String(body.streamUrl || body.stream_url || '').trim()
      : '';
    const priority = body.priority != null ? Number(body.priority) : 1;

    // Aceita colar bloco M3U (#EXTINF + URL)
    const raw = body.raw != null ? String(body.raw).trim() : '';
    if (raw) {
      const lines = raw.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
      let ext = '';
      let urlLine = '';
      for (const line of lines) {
        if (line.startsWith('#EXTINF')) ext = line;
        else if (!line.startsWith('#') && /https?:\/\//i.test(line)) urlLine = line;
      }
      if (urlLine) streamUrl = urlLine;
      if (ext) {
        const logoM = ext.match(/tvg-logo="([^"]*)"/i);
        const nameM = ext.match(/tvg-name="([^"]*)"/i);
        const groupM = ext.match(/group-title="([^"]*)"/i);
        const commaName = ext.includes(',') ? ext.slice(ext.lastIndexOf(',') + 1).trim() : '';
        if (logoM && logoM[1] && !logo) logo = logoM[1];
        if (nameM && nameM[1] && !name) name = nameM[1];
        if (groupM && groupM[1]) groupTitle = groupM[1] || groupTitle;
        if (!name && commaName) name = commaName;
      }
    }

    if (!streamUrl || !/^https?:\/\//i.test(streamUrl)) {
      res.status(400).json({ error: 'Informe uma URL de stream válida (http/https)' });
      return;
    }
    if (!name) {
      try {
        const u = new URL(streamUrl);
        name = decodeURIComponent(u.pathname.split('/').filter(Boolean).pop() || 'Canal manual');
      } catch (_) {
        name = 'Canal manual';
      }
    }
    if (!groupTitle) groupTitle = 'Manuais';

    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_manual_channels`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({
        name,
        logo: logo || null,
        group_title: groupTitle,
        stream_url: streamUrl,
        priority: Number.isFinite(priority) ? priority : 1,
        is_active: true,
      }),
    });
    const txt = await r.text();
    if (!r.ok) {
      res.status(502).json({
        error: 'Erro criar canal manual (tabela existe?). ' + txt.slice(0, 180),
        sqlHint: 'scripts/live_tv_manual_channels.sql',
      });
      return;
    }
    let json;
    try { json = JSON.parse(txt); } catch (_) { json = []; }
    res.status(200).json({ success: true, channel: Array.isArray(json) ? json[0] : json });
    return;
  }

"""
