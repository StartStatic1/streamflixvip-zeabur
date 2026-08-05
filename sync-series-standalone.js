// sync-series-standalone.js — M3U series sync (restaurado + match por titulo)
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U } = require('./lib/iptv-parser');

const MAX_RUNTIME_MS = 55 * 60 * 1000;

function supabaseHeaders(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };
}

async function sbSelect(serviceKey, table, query) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${table}?${query}`, {
    headers: supabaseHeaders(serviceKey),
  });
  if (!r.ok) throw new Error(`Supabase select falhou (${table}): ${r.status} ${await r.text()}`);
  return r.json();
}

async function sbUpsert(serviceKey, table, rows, onConflict) {
  if (!rows.length) return;
  const post = async (payload) => {
    const r = await fetch(`${SUPABASE_URL}/rest/v1/${table}?on_conflict=${onConflict}`, {
      method: 'POST',
      headers: { ...supabaseHeaders(serviceKey), Prefer: 'resolution=merge-duplicates' },
      body: JSON.stringify(payload),
    });
    if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
  };
  try {
    await post(rows);
  } catch (batchErr) {
    for (const row of rows) {
      try { await post([row]); } catch (_) {}
    }
  }
}

async function sbUpdate(serviceKey, table, filter, patch) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${table}?${filter}`, {
    method: 'PATCH',
    headers: supabaseHeaders(serviceKey),
    body: JSON.stringify(patch),
  });
  if (!r.ok) throw new Error(`Supabase update falhou (${table}): ${r.status} ${await r.text()}`);
}

function normalizeTitle(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
const TITLE_STOP = new Set(['a','o','as','os','de','da','do','das','dos','e','em','um','uma','the','of','and','in','on','to']);
function titleTokens(s) {
  return normalizeTitle(s).split(' ').filter((t) => t && !TITLE_STOP.has(t));
}
function titleSimilarity(query, candidate) {
  const nq = normalizeTitle(query);
  const nc = normalizeTitle(candidate);
  if (!nq || !nc) return 0;
  if (nq === nc) return 1;
  if (nc.startsWith(nq) || nq.startsWith(nc)) {
    const lenRatio = Math.min(nq.length, nc.length) / Math.max(nq.length, nc.length);
    return Math.max(0.82, lenRatio);
  }
  const q = titleTokens(query);
  const c = titleTokens(candidate);
  if (!q.length || !c.length) return 0;
  const cSet = new Set(c);
  const hit = q.filter((t) => cSet.has(t)).length;
  if (hit === 0) return 0;
  const precision = hit / q.length;
  const recall = hit / c.length;
  const f1 = (2 * precision * recall) / (precision + recall);
  const lenRatio = Math.min(nq.length, nc.length) / Math.max(nq.length, nc.length);
  if (q.length <= 2 && precision < 0.99) return f1 * lenRatio * 0.5;
  return f1 * (0.45 + 0.55 * lenRatio);
}
const MIN_TITLE_SCORE = 0.55;

async function searchTmdbSeries(baseTitle, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/tv');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', baseTitle);
  url.searchParams.set('language', 'pt-BR');
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);
  let res;
  try { res = await fetch(url.toString(), { signal: controller.signal }); }
  finally { clearTimeout(timeoutId); }
  if (!res.ok) throw new Error(`TMDB search/tv falhou: ${res.status}`);
  const data = await res.json();
  if (!data.results || data.results.length === 0) return null;
  const ranked = data.results.map((r) => {
    const score = Math.max(
      titleSimilarity(baseTitle, r.name || ''),
      titleSimilarity(baseTitle, r.original_name || ''),
    );
    return { r, score };
  }).filter((x) => x.score >= MIN_TITLE_SCORE)
    .sort((a, b) => b.score - a.score || (b.r.popularity || 0) - (a.r.popularity || 0));
  if (ranked.length === 0) return null;
  return ranked[0].r;
}

function parseSeriesEntry(entry) {
  // S01E02 / 1x02 / T1 EP2 patterns
  const name = entry.title || entry.name || '';
  const se = name.match(/[Ss](\d{1,2})\s*[EeXx](\d{1,3})/) || name.match(/(\d{1,2})[xX](\d{1,3})/);
  let season = se ? parseInt(se[1], 10) : (entry.season || null);
  let episode = se ? parseInt(se[2], 10) : (entry.episode || null);
  let baseTitle = name
    .replace(/[Ss]\d{1,2}\s*[EeXx]\d{1,3}.*/i, '')
    .replace(/\d{1,2}[xX]\d{1,3}.*/i, '')
    .replace(/\(\d{4}\)/g, '')
    .replace(/\[.*?\]/g, '')
    .trim();
  return { baseTitle, season, episode, url: entry.url };
}

async function downloadM3U(source) {
  const fs = require('fs');
  const path = require('path');
  const os = require('os');
  const host = String(source.xtream_host || '').replace(/\/+$/, '');
  const user = source.xtream_user;
  const pass = source.xtream_pass;
  if (!host || !user || !pass) throw new Error('Fonte sem host/user/pass');
  const urls = [
    `${host}/get.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&type=m3u_plus&output=ts`,
    `${host}/get.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&type=m3u_plus`,
  ];
  const tmp = path.join(os.tmpdir(), `m3u-series-${source.id}-${Date.now()}.m3u`);
  let lastErr = null;
  for (const url of urls) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 120000);
      const res = await fetch(url, {
        signal: controller.signal,
        headers: { 'User-Agent': 'IPTVSmarters/1.0 (Linux; Android)', Accept: '*/*' },
      });
      clearTimeout(timeoutId);
      if (!res.ok) { lastErr = new Error(`HTTP ${res.status}`); continue; }
      const text = await res.text();
      if (!text || !text.includes('#EXT')) { lastErr = new Error('Nao e M3U'); continue; }
      fs.writeFileSync(tmp, text, 'utf8');
      console.log(`[m3u-series] baixado ${text.length} bytes`);
      return tmp;
    } catch (e) { lastErr = e; }
  }
  throw lastErr || new Error('Falha download M3U series');
}

async function processSource({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache }) {
  const fs = require('fs');
  console.log(`[m3u-series] Fonte "${source.name}"`);
  const filePath = await downloadM3U(source);
  let entries;
  try {
    const raw = fs.readFileSync(filePath, 'utf8');
    entries = parseM3U(raw);
  } finally {
    try { fs.unlinkSync(filePath); } catch (_) {}
  }

  // so entradas que parecem episodio
  const seriesEntries = [];
  for (const e of entries) {
    const p = parseSeriesEntry(e);
    if (p.season != null && p.episode != null && p.baseTitle) seriesEntries.push({ ...p, raw: e });
  }
  console.log(`[m3u-series] ${seriesEntries.length} episodios detectados`);

  let cursor = source.sync_cursor >= seriesEntries.length ? 0 : (source.sync_cursor || 0);
  // series sync may use different cursor field - try series_sync_cursor if present
  if (source.series_sync_cursor != null) {
    cursor = source.series_sync_cursor >= seriesEntries.length ? 0 : source.series_sync_cursor;
  }

  let matched = 0, unmatchedCount = 0, processedThisRun = 0;
  let vipRows = [];
  const CONCURRENCY = 6;

  while (cursor < seriesEntries.length && timeLeft() > 5000) {
    const chunk = seriesEntries.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (item) => {
      try {
        const key = item.baseTitle.toLowerCase();
        let series = seriesCache.get(key);
        if (series === undefined) {
          series = await searchTmdbSeries(item.baseTitle, tmdbApiKey);
          seriesCache.set(key, series);
        }
        return { item, series, error: null };
      } catch (err) {
        return { item, series: null, error: err };
      }
    }));

    for (const { item, series, error } of results) {
      if (error || !series) { unmatchedCount++; continue; }
      matched++;
      vipRows.push({
        tmdb_id: series.id,
        media_type: 'tv',
        season: item.season,
        episode: item.episode,
        title: series.name || item.baseTitle,
        poster_path: series.poster_path || null,
        source_url: item.url,
        source_label: source.name,
        priority: source.priority,
        is_active: true,
      });
    }

    cursor += chunk.length;
    processedThisRun += chunk.length;
    if (vipRows.length >= 80) {
      await sbUpsert(serviceKey, 'vip_sources', vipRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
      vipRows = [];
    }
  }

  await sbUpsert(serviceKey, 'vip_sources', vipRows, 'tmdb_id,media_type,season_key,episode_key,source_label');

  const isLastBatch = cursor >= seriesEntries.length;
  const patch = {
    last_batch_at: new Date().toISOString(),
  };
  // prefer series cursor fields if schema has them
  patch.sync_cursor = isLastBatch ? 0 : cursor;
  if (isLastBatch) {
    patch.last_synced_at = new Date().toISOString();
    patch.last_sync_stats = { series_matched: matched, series_unmatched: unmatchedCount, series_total: seriesEntries.length };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[m3u-series] "${source.name}": ${processedThisRun} ep, ${matched} ok`);
  return { done: isLastBatch };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[m3u-series] Faltam secrets');
    process.exit(1);
  }
  const startTime = Date.now();
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);
  const seriesCache = new Map();

  const sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    "is_active=eq.true&or=(source_type.eq.m3u,source_type.is.null)&select=*&order=last_batch_at.asc.nullsfirst",
  );
  if (!sources.length) {
    console.log('[m3u-series] Nenhuma fonte M3U ativa.');
    return;
  }

  for (const source of sources) {
    if (timeLeft() <= 5000) break;
    try {
      await processSource({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache });
    } catch (err) {
      console.error(`[m3u-series] "${source.name}" falhou: ${err.message}`);
    }
  }
  console.log('[m3u-series] Fim.');
}

main().catch((e) => { console.error(e); process.exit(1); });
