#!/usr/bin/env python3
"""Rode em /root/streamflix: python3 scripts/vps_apply_block.py"""
from pathlib import Path
import subprocess, sys

ROOT = Path('/root/streamflix')

HELPER = r'''
// ── Bloqueio permanente: inativos do painel + tabela vip_source_blocks ──
async function loadBlockedKeys(serviceKey, sourceLabel) {
  const blocked = new Set();
  const label = sourceLabel || '';
  try {
    const rows = await sbSelect(
      serviceKey,
      'vip_sources',
      `source_label=eq.${encodeURIComponent(label)}&is_active=eq.false&select=tmdb_id,media_type,season,episode&limit=20000`
    );
    for (const r of rows || []) {
      blocked.add(`${r.tmdb_id}|${r.media_type || 'movie'}|${r.season == null ? 0 : r.season}|${r.episode == null ? 0 : r.episode}`);
    }
  } catch (e) {
    console.error('[sync] loadBlocked inactive:', e.message);
  }
  try {
    const rows = await sbSelect(
      serviceKey,
      'vip_source_blocks',
      `source_label=eq.${encodeURIComponent(label)}&select=tmdb_id,media_type,season,episode&limit=20000`
    );
    for (const r of rows || []) {
      blocked.add(`${r.tmdb_id}|${r.media_type || 'movie'}|${r.season == null ? 0 : r.season}|${r.episode == null ? 0 : r.episode}`);
    }
  } catch (e) { /* tabela pode nao existir ainda */ }
  return blocked;
}

function filterUnblockedRows(rows, blocked) {
  if (!blocked || !blocked.size) return rows;
  const out = [];
  let skipped = 0;
  for (const row of rows) {
    const key = `${row.tmdb_id}|${row.media_type || 'movie'}|${row.season == null ? 0 : row.season}|${row.episode == null ? 0 : row.episode}`;
    if (blocked.has(key)) { skipped++; continue; }
    out.push(row);
  }
  if (skipped) console.log(`[sync] ${skipped} fonte(s) bloqueadas pelo painel (nao recriadas)`);
  return out;
}

async function sbUpsertVipSources(serviceKey, rows, sourceLabel, blockedCache) {
  if (!rows.length) return;
  let blocked = blockedCache;
  if (!blocked) blocked = await loadBlockedKeys(serviceKey, sourceLabel);
  const filtered = filterUnblockedRows(rows, blocked);
  if (!filtered.length) return;
  await sbUpsert(serviceKey, 'vip_sources', filtered, 'tmdb_id,media_type,season_key,episode_key,source_label');
}

'''

TITLE_GUARD = r'''
  const norm = (s) => String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
  const iptvTokens = new Set(norm(title).split(' ').filter((w) => w.length > 2));
  if (iptvTokens.size > 0) {
    const scored = data.results.map((r) => {
      const tmdbTokens = norm(r.title || r.name || '').split(' ').filter((w) => w.length > 2);
      let hit = 0;
      for (const w of tmdbTokens) if (iptvTokens.has(w)) hit++;
      const denom = Math.max(iptvTokens.size, tmdbTokens.length, 1);
      return { r, score: hit / denom };
    }).filter((x) => x.score >= 0.4).sort((a, b) => b.score - a.score || (b.r.popularity || 0) - (a.r.popularity || 0));
    if (scored.length) return scored[0].r;
    return null;
  }
'''

def patch_sync(path: Path):
    t = path.read_text()
    if 'function loadBlockedKeys' not in t:
        idx = t.find('async function sbUpsert')
        if idx < 0:
            print('SKIP no sbUpsert', path)
            return False
        t = t[:idx] + HELPER + t[idx:]
        print('helper', path.name)
    if 'blockedKeys = await loadBlockedKeys' not in t:
        for m in ['let matched = 0', 'let matched=0']:
            if m in t:
                t = t.replace(m, "const blockedKeys = await loadBlockedKeys(serviceKey, source.name || '');\n    " + m, 1)
                print('blockedKeys', path.name)
                break
    old = "await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');"
    new = "await sbUpsertVipSources(serviceKey, vipSourcesRows, (typeof source !== 'undefined' && source && source.name) ? source.name : ((vipSourcesRows[0] && vipSourcesRows[0].source_label) || ''), typeof blockedKeys !== 'undefined' ? blockedKeys : null);"
    if old in t:
        n = t.count(old)
        t = t.replace(old, new)
        print('upserts', n, path.name)
    pop = '  return data.results.sort((a, b) => (b.popularity || 0) - (a.popularity || 0))[0];'
    if 'iptvTokens' not in t and pop in t:
        t = t.replace(pop, TITLE_GUARD + '\n  return null;', 1)
        print('title-guard', path.name)
    path.write_text(t)
    r = subprocess.run(['node', '--check', str(path)], capture_output=True, text=True)
    if r.returncode != 0:
        print('SYNTAX FAIL', path, r.stderr[:300])
        return False
    print('OK', path.name)
    return True

def patch_admin(path: Path):
    t = path.read_text()
    if t.strip() in ('PLACEHOLDER', 'PLACEHOLDER_WILL_FAIL') or len(t) < 500:
        r = subprocess.run(['git', 'show', 'd261b99:api/admin-vip.js'], cwd=str(ROOT), capture_output=True, text=True)
        if r.returncode != 0 or len(r.stdout) < 500:
            print('FAIL restore admin-vip')
            return False
        t = r.stdout
        print('restored admin-vip from d261b99')

    if 'function sourceRowId' not in t:
        end = t.find('};', t.find('const svcHeaders')) + 2
        t = t[:end] + '''

  function sourceRowId(b) {
    return b.sourceId != null ? b.sourceId : b.id;
  }
  function sourceActiveFlag(b) {
    if (b.isActive !== undefined) return !!b.isActive;
    if (b.is_active !== undefined) return !!b.is_active;
    return undefined;
  }
''' + t[end:]
        print('helpers admin')

    t = t.replace(
        "const { id, is_active } = body;",
        "const id = sourceRowId(body);\n    const is_active = sourceActiveFlag(body);",
        1,
    )
    t = t.replace(
        "const { id, source_url, source_label, priority, season, episode, title } = body;",
        "const id = sourceRowId(body);\n    const source_url = body.source_url;\n    const source_label = body.source_label;\n    const priority = body.priority;\n    const season = body.season;\n    const episode = body.episode;\n    const title = body.title;",
        1,
    )

    if 'vip_source_blocks' not in t:
        old = """  if (action === 'delete-source') {
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
        old2 = """  if (action === 'delete-source') {
    const id = sourceRowId(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao excluir fonte', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }"""
        new = """  if (action === 'delete-source') {
    const id = sourceRowId(body);
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }
    let row = null;
    try {
      const getR = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}&select=tmdb_id,media_type,source_label,season,episode&limit=1`, { headers: svcHeaders });
      const rows = await getR.json();
      if (Array.isArray(rows) && rows[0]) row = rows[0];
    } catch (e) {}
    if (row && row.tmdb_id != null && row.source_label) {
      try {
        await fetch(`${SUPABASE_URL}/rest/v1/vip_source_blocks?on_conflict=tmdb_id,media_type,source_label,season,episode`, {
          method: 'POST',
          headers: { ...svcHeaders, Prefer: 'resolution=merge-duplicates,return=minimal' },
          body: JSON.stringify({
            tmdb_id: row.tmdb_id,
            media_type: row.media_type || 'movie',
            source_label: row.source_label,
            season: row.season == null ? 0 : row.season,
            episode: row.episode == null ? 0 : row.episode,
          }),
        });
      } catch (e) {}
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro ao excluir fonte', detail: await r.text() }); return; }
    res.status(200).json({ success: true, blocked: !!(row && row.tmdb_id) });
    return;
  }"""
        if old in t:
            t = t.replace(old, new, 1)
            print('delete block v1')
        elif old2 in t:
            t = t.replace(old2, new, 1)
            print('delete block v2')
        else:
            print('WARN delete-source pattern not found')

    path.write_text(t)
    r = subprocess.run(['node', '--check', str(path)], capture_output=True, text=True)
    if r.returncode != 0:
        print('SYNTAX FAIL admin', r.stderr[:400])
        return False
    print('OK admin-vip.js')
    return True

def main():
    ok = True
    for name in ['sync-standalone.js', 'sync-series-standalone.js', 'xtream-sync-standalone.js', 'api/iptv-sync.js']:
        p = ROOT / name
        if not p.exists():
            print('missing', p)
            ok = False
            continue
        if not patch_sync(p):
            ok = False
    if not patch_admin(ROOT / 'api/admin-vip.js'):
        ok = False
    sys.exit(0 if ok else 1)

if __name__ == '__main__':
    main()
