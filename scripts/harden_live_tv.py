#!/usr/bin/env python3
"""TV ao vivo: timeouts maiores, so fontes com streams, scan admin."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "api/live-tv.js"
t = p.read_text(encoding="utf-8")

old_to = "const timeoutId = setTimeout(() => controller.abort(), 8000);"
new_to = "const timeoutId = setTimeout(() => controller.abort(), 14000);"
if old_to in t:
    t = t.replace(old_to, new_to)
    print("ok timeout 14s")
else:
    print("timeout already or missing")

if "\n          10000,\n        )\n      : [];" in t:
    t = t.replace("\n          10000,\n        )\n      : [];", "\n          28000,\n        )\n      : [];")
    print("ok budget 28s")
else:
    idx = t.find("withCreds.map")
    if idx >= 0:
        chunk = t[idx : idx + 600]
        if "10000" in chunk:
            t = t[:idx] + chunk.replace("10000", "28000", 1) + t[idx + 600 :]
            print("ok budget 28s (chunk)")
        else:
            print("budget skip")
    else:
        print("budget skip")

if "const MAX_SOURCES = 5;" in t:
    t = t.replace("const MAX_SOURCES = 5;", "const MAX_SOURCES = 8;")
    print("ok MAX_SOURCES 8")
else:
    print("MAX_SOURCES already or missing")

needle = "    const bridges = await loadBridgeRows(serviceKey);"
if "scan fontes:" not in t and needle in t:
    inject = """    // Relatorio: fontes mortas (0 streams) nao entram no merge
    const report = results.map((r) => ({
      name: r.sourceName,
      streams: (r.streams && r.streams.length) || 0,
      cats: (r.categories && r.categories.length) || 0,
      skip: r.skipReason || null,
    }));
    console.log('[live-tv] scan fontes:', JSON.stringify(report));
    const withStreams = results.filter((r) => r.streams && r.streams.length > 0);
    if (withStreams.length) {
      results = withStreams;
    } else {
      console.warn('[live-tv] NENHUMA fonte retornou streams — confira live_tv_sources');
    }

    const bridges = await loadBridgeRows(serviceKey);"""
    t = t.replace(needle, inject, 1)
    print("ok filter empty + report")
else:
    print("filter already or needle missing")

p.write_text(t, encoding="utf-8")
print("wrote live-tv.js", p.stat().st_size)

av = ROOT / "api/admin-vip.js"
a = av.read_text(encoding="utf-8")
if "scan-live-tv-all" in a:
    print("scan-live-tv-all already")
else:
    block = r'''
  if (action === 'scan-live-tv-all') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/live_tv_sources?select=id,name,xtream_host,xtream_user,xtream_pass,priority,is_active&order=priority.asc.nullslast`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) {
      res.status(502).json({ error: 'Falha ler live_tv_sources', detail: rows });
      return;
    }
    const list = Array.isArray(rows) ? rows : [];
    const out = [];
    for (const s of list) {
      const item = {
        id: s.id,
        name: s.name,
        is_active: s.is_active,
        priority: s.priority,
        host: s.xtream_host,
        user: s.xtream_user ? String(s.xtream_user).slice(0, 3) + '***' : null,
        ok: false,
        categories: 0,
        streams: 0,
        error: null,
        ms: 0,
      };
      if (!s.is_active) {
        item.error = 'inativa';
        out.push(item);
        continue;
      }
      if (!s.xtream_host || !s.xtream_user || !s.xtream_pass) {
        item.error = 'sem credenciais';
        out.push(item);
        continue;
      }
      const t0 = Date.now();
      try {
        const base = String(s.xtream_host).replace(/\/+$/, '');
        const makeUrl = (actionPath) => {
          const url = new URL(base + '/player_api.php');
          url.searchParams.set('username', s.xtream_user);
          url.searchParams.set('password', s.xtream_pass);
          url.searchParams.set('action', actionPath);
          return url.toString();
        };
        const ctrl = new AbortController();
        const to = setTimeout(() => ctrl.abort(), 14000);
        const [cr, sr] = await Promise.all([
          fetch(makeUrl('get_live_categories'), {
            signal: ctrl.signal,
            headers: { 'User-Agent': 'IPTVSmarters/1.0', Accept: 'application/json' },
          }),
          fetch(makeUrl('get_live_streams'), {
            signal: ctrl.signal,
            headers: { 'User-Agent': 'IPTVSmarters/1.0', Accept: 'application/json' },
          }),
        ]);
        clearTimeout(to);
        item.ms = Date.now() - t0;
        if (!cr.ok || !sr.ok) {
          item.error = 'HTTP cats=' + cr.status + ' streams=' + sr.status;
        } else {
          const cats = await cr.json();
          const streams = await sr.json();
          item.categories = Array.isArray(cats) ? cats.length : 0;
          item.streams = Array.isArray(streams) ? streams.length : 0;
          item.ok = item.streams > 0;
          if (!item.ok) item.error = '0 streams';
        }
      } catch (e) {
        item.ms = Date.now() - t0;
        item.error = e && e.name === 'AbortError' ? 'timeout 14s' : ((e && e.message) || 'erro');
      }
      out.push(item);
    }
    const alive = out.filter((x) => x.ok).length;
    res.status(200).json({
      total: out.length,
      alive,
      dead: out.length - alive,
      sources: out,
      tip:
        alive === 0
          ? 'Nenhuma Xtream de TV respondeu. Renove painel na aba TV ao vivo.'
          : alive === 1
            ? 'So 1 fonte viva — sem fallback. Adicione mais 1-2 paineis de pe.'
            : 'OK: ha fontes vivas para fallback.',
    });
    return;
  }

'''
    marker = "if (action === 'list-live-tv-sources')"
    if marker in a:
        a = a.replace(marker, block + "\n  " + marker, 1)
        av.write_text(a, encoding="utf-8")
        print("ok admin scan-live-tv-all")
    else:
        print("aviso: list-live-tv-sources nao achado")

print("done harden_live_tv")
