#!/usr/bin/env python3
"""Apply canais manuais to live-tv.js, admin-vip.js, admin.html. Run from repo root."""
from pathlib import Path

# --- live-tv ---
t = Path("api/live-tv.js").read_text()
if "loadManualChannels" not in t:
    idx = t.find("async function loadSourceRows")
    if idx < 0: raise SystemExit("loadSourceRows not found")
    t = t[:idx] + """
async function loadManualChannels(serviceKey) {
  try {
    const rows = await sbSelect(
      serviceKey,
      'live_tv_manual_channels',
      'is_active=eq.true&select=id,name,logo,group_title,stream_url,priority&order=priority.asc.nullslast&limit=500',
    );
    return Array.isArray(rows) ? rows : [];
  } catch (e) {
    console.warn('[live-tv] manual channels:', e.message);
    return [];
  }
}

""" + t[idx:]
    old = """    if (!withCreds.length) {
      res.status(200).json({
        categories: [],
        channels: [],
        sourcesUsed: 0,
        diagnostic: {
          origin,
          sourcesInDb: sources.length,
          withCredentials: 0,
          hint: origin === 'live_tv_sources'
            ? 'Nenhuma fonte em live_tv_sources com host/user/pass. Cadastre na aba TV ao vivo do admin.'
            : 'Nenhuma fonte IPTV com credenciais. Cadastre live_tv_sources (recomendado) ou ative iptv_sources.',
        },
      });
      return;
    }

    const results = await Promise.all(
      withCreds.map((s) =>
        loadFromSource(s).catch((err) => {
          console.error('[live-tv] fonte falhou', s.name, err.message);
          return {
            sourceName: s.name,
            priority: s.priority,
            categories: [],
            streams: [],
            skipReason: err.message,
          };
        }),
      ),
    );"""
    new = """    const results = withCreds.length
      ? await Promise.all(
          withCreds.map((s) =>
            loadFromSource(s).catch((err) => {
              console.error('[live-tv] fonte falhou', s.name, err.message);
              return {
                sourceName: s.name,
                priority: s.priority,
                categories: [],
                streams: [],
                skipReason: err.message,
              };
            }),
          ),
        )
      : [];

    const manuals = await loadManualChannels(serviceKey);
    if (!withCreds.length && !manuals.length) {
      res.status(200).json({
        categories: [],
        channels: [],
        sourcesUsed: 0,
        diagnostic: {
          origin,
          sourcesInDb: sources.length,
          withCredentials: 0,
          manuals: 0,
          hint: origin === 'live_tv_sources'
            ? 'Nenhuma fonte Xtream nem canal manual. Cadastre na aba TV ao vivo.'
            : 'Nenhuma fonte IPTV com credenciais nem canal manual.',
        },
      });
      return;
    }"""
    if old not in t: raise SystemExit("live-tv: old empty block not found")
    t = t.replace(old, new)
    inj = t.find("    let channels = Array.from(channelMap.values()).map((ch) => {")
    if inj < 0: raise SystemExit("live-tv: inject not found")
    t = t[:inj] + """
    for (const m of manuals) {
      const url = String(m.stream_url || '').trim();
      const name = String(m.name || '').trim() || 'Canal manual';
      if (!url) continue;
      const key = channelMergeKey(name) || ('manual-' + (m.id || url).toString().slice(0, 24));
      const groupRaw = String(m.group_title || 'Manuais').trim() || 'Manuais';
      const catKey = normalizeName(groupRaw) || 'manuais';
      if (!categoryMap.has(catKey)) {
        categoryMap.set(catKey, { id: catKey, name: groupRaw });
      }
      let ch = channelMap.get(key);
      if (!ch) {
        ch = { id: key, name, logo: m.logo || '', categoryId: catKey, streams: [] };
        channelMap.set(key, ch);
      } else if (!ch.logo && m.logo) {
        ch.logo = m.logo;
      }
      if (!ch.streams.some((x) => x.url === url)) {
        ch.streams.push({
          url,
          label: 'Manual',
          priority: Number.isFinite(Number(m.priority)) ? Number(m.priority) : 1,
          quality: 40,
        });
      }
    }
    console.log(`[live-tv] manuais: ${manuals.length} cadastrados`);

""" + t[inj:]
    Path("api/live-tv.js").write_text(t)
    print("live-tv OK")
else:
    print("live-tv already")

# --- admin-vip ---
s = Path("api/admin-vip.js").read_text()
if "list-live-tv-manual" not in s:
    ins = s.find("  if (action === 'list-ads')")
    if ins < 0: raise SystemExit("list-ads not found")
    block = r"""
  if (action === 'list-live-tv-manual') {
    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_manual_channels?select=id,name,logo,group_title,stream_url,priority,is_active,created_at&order=priority.asc.nullslast,name.asc`, { headers: svcHeaders });
    if (!r.ok) { res.status(502).json({ error: 'Tabela live_tv_manual_channels ausente. Rode o SQL no Supabase.', detail: await r.text() }); return; }
    res.status(200).json({ channels: await r.json() }); return;
  }
"""
