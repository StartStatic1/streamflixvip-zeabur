// lib/title-confirm.js — título exato SEM ano na lista (ex. "Resident Evil"): só aceita se no TMDB
// não existir outro filme/série com esse mesmo nome em outro ano (senão pode ser o filme errado).
const cache = new Map();
function norm(s) {
  return String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/\[[^\]]*\]|\([^)]*\)/g, ' ').replace(/\b(19|20)\d{2}\b/g, ' ').replace(/[^a-z0-9]+/g, ' ').trim();
}
function yearOf(s) { const m = String(s || '').match(/\b((?:19|20)\d{2})\b/); return m ? Number(m[1]) : null; }

/** true = pode usar; false = nome ambíguo (há outro título igual com outro ano). Falha de rede = aceita. */
async function exactIsSafe(itemName, kind, wantYear) {
  if (!wantYear || yearOf(itemName)) return true;
  const key = kind + ':' + norm(itemName) + ':' + wantYear;
  const c = cache.get(key);
  if (c && Date.now() - c.at < 12 * 3600e3) return c.ok;
  const apiKey = process.env.TMDB_API_KEY || '';
  if (!apiKey) return true;
  let ok = true;
  try {
    const u = new URL('https://api.themoviedb.org/3/search/' + (kind === 'tv' || kind === 'series' ? 'tv' : 'movie'));
    u.searchParams.set('api_key', apiKey); u.searchParams.set('query', norm(itemName));
    const r = await fetch(u.toString(), { signal: AbortSignal.timeout(2500) });
    if (r.ok) {
      const j = await r.json(); const n = norm(itemName);
      const same = (j.results || []).filter((x) => [x.title, x.name, x.original_title, x.original_name].some((t) => t && norm(t) === n));
      const otherYears = same.filter((x) => { const y = yearOf(x.release_date || x.first_air_date); return y && y !== Number(wantYear); });
      ok = otherYears.length === 0;
    }
  } catch (_) { ok = true; }
  cache.set(key, { at: Date.now(), ok }); if (cache.size > 3000) cache.clear();
  return ok;
}
module.exports = { exactIsSafe };
