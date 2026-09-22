#!/usr/bin/env python3
from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'api/subtitles.js'
t = p.read_text()
old = '''async function loadSubtitleAddons(serviceKey) {
  if (!serviceKey) return [];
  try {
    const url = `${SUPABASE_URL}/rest/v1/stremio_addons?is_active=eq.true&select=name,manifest_url,base_url,priority&order=priority.asc`;
    const r = await fetch(url, { headers: sbHeaders(serviceKey) });
    if (!r.ok) return [];
    const rows = await r.json();
    const candidates = Array.isArray(rows) ? rows : [];
    const checked = await Promise.all(candidates.map(async (a) => {
      const manifest = await fetchJson(a.manifest_url, 5000);
      const resources = Array.isArray(manifest?.resources) ? manifest.resources : [];
      const isSubtitle = resources.some((x) => String(typeof x === 'string' ? x : x?.name || '').toLowerCase() === 'subtitles')
        || /subtitle|legenda|opensubtitle|caption|sub\\b/i.test(String(a.name || ''));
      return isSubtitle ? { ...a, manifest } : null;
    }));
    return checked.filter(Boolean);
  } catch (_) {
    return [];
  }
}'''
new = '''async function loadSubtitleAddons(serviceKey) {
  if (!serviceKey) return [];
  try {
    const url = `${SUPABASE_URL}/rest/v1/stremio_addons?is_active=eq.true&select=name,manifest_url,base_url,priority&order=priority.asc`;
    const r = await fetch(url, { headers: sbHeaders(serviceKey) });
    if (!r.ok) return [];
    const rows = await r.json();
    return Array.isArray(rows) ? rows : [];
  } catch (_) {
    return [];
  }
}'''
if old in t:
    t = t.replace(old, new, 1)
    print('ok load all addons')
elif 'return Array.isArray(rows) ? rows : [];' in t and 'isSubtitle' not in t:
    print('ok load ja sem filtro manifesto')
else:
    print('aviso load nao achado')

old2 = '''  const isSeries = season != null && episode != null && Number(season) > 0;
  const type = isSeries ? 'series' : 'movie';
  const id = isSeries ? `${imdbId}:${season}:${episode}` : imdbId;
  const preferred = new Set(['pob', 'por', 'pt', 'pt-br', 'pb']);
  const results = [];
  await Promise.all(addons.map(async (addon) => {
    const base = String(addon.base_url || String(addon.manifest_url || '').replace(/\\/?manifest\\.json$/i, '')).replace(/\\/+$/, '');
    if (!base) return;
    const data = await fetchJson(`${base}/subtitles/${type}/${encodeURIComponent(id)}.json`, 7000);'''
# simpler unique marker
marker = 'const data = await fetchJson(`${base}/subtitles/${type}/${encodeURIComponent(id)}.json`, 7000);'
if 'ids.push({ type, id:' in t:
    print('ok ja multi id')
elif marker in t:
    t = t.replace(
        marker,
        '''const ids = [{ type, id }];
    if (imdbId) ids.push({ type, id: (isSeries ? ('tmdb:' + imdbId) : ('tmdb:' + imdbId)) });
    let data = null;
    for (const cand of ids) {
      data = await fetchJson(`${base}/subtitles/${cand.type}/${encodeURIComponent(cand.id)}.json`, 5000);
      if (data && Array.isArray(data.subtitles) && data.subtitles.length) break;
    }''',
        1,
    )
    print('ok multi tentativa subtitles')
else:
    print('aviso search inner nao achado')

p.write_text(t)
print('fim apply_addon_subtitles_all')
