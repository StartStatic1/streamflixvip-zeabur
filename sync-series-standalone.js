// sync-series-standalone.js — original + match por titulo
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U } = require('./lib/iptv-parser');
const fs = require('fs');
const os = require('os');
const path = require('path');

const DEFAULT_SOURCE_LABEL_PREFIX = 'MegaEmbed VIP';
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
  try { await post(rows); } catch (batchErr) {
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
  return String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
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
    const score = Math.max(titleSimilarity(baseTitle, r.name || ''), titleSimilarity(baseTitle, r.original_name || ''));
    return { r, score };
  }).filter((x) => x.score >= MIN_TITLE_SCORE)
    .sort((a, b) => b.score - a.score || (b.r.popularity || 0) - (a.r.popularity || 0));
  if (ranked.length === 0) return null;
  return ranked[0].r;
}

async function downloadM3U(source) {
  const url = `${source.xtream_host}/get.php?username=${source.xtream_user}&password=${source.xtream_pass}&type=m3u_plus`;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 120000);
  let res;
  try {
    res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'IPTVSmarters/1.0 (Linux; Android)', Accept: '*/*', Connection: 'keep-alive' },
    });
  } finally { clearTimeout(timeoutId); }
  if (!res.ok) throw new Error(`Falha ao baixar M3U: HTTP ${res.status}`);
  const tmpPath = path.join(os.tmpdir(), `iptv-series-${source.id}.m3u`);
  const fileStream = fs.createWriteStream(tmpPath);
  const reader = res.body.getReader();
  await new Promise((resolve, reject) => {
    function pump() {
      reader.read().then(({ done, value }) => {
        if (done) { fileStream.end(); return; }
        fileStream.write(Buffer.from(value));
        pump();
      }).catch(reject);
    }
    pump();
    fileStream.on('finish', resolve);
    fileStream.on('error', reject);
  });
  return tmpPath;
}

async function processSource({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache }) {
  console.log(`[sync-series] Processando fonte: ${source.name || source.id}`);
  const filePath = await downloadM3U(source);
  console.log('[sync-series] M3U baixado, parse...');
  const allEpisodes = [];
  await parseM3U(filePath, (entry) => {
    if (!entry.classification) return;
    if (entry.classification.kind !== 'episode' && entry.classification.kind !== 'anime') return;
    allEpisodes.push(entry);
  }, { dedupe: true });
  fs.unlink(filePath, () => {});
  console.log(`[sync-series] ${allEpisodes.length} episodios na playlist.`);

  let cursor = source.sync_cursor >= allEpisodes.length ? 0 : (source.sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, processedThisRun = 0;
  let vipSourcesRows = [];
  const CONCURRENCY = 6;

  while (cursor < allEpisodes.length && timeLeft() > 5000) {
    const chunk = allEpisodes.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (entry) => {
      const { baseTitle, season, episode } = entry.classification;
      try {
        const key = String(baseTitle || '').toLowerCase();
        let series = seriesCache.get(key);
        if (series === undefined) {
          series = await searchTmdbSeries(baseTitle, tmdbApiKey);
          seriesCache.set(key, series);
        }
        return { entry, series, error: null };
      } catch (err) {
        return { entry, series: null, error: err };
      }
    }));

    for (const { entry, series, error } of results) {
      const { baseTitle, season, episode } = entry.classification;
      if (error || !series) { unmatchedCount++; continue; }
      matched++;
      vipSourcesRows.push({
        tmdb_id: series.id,
        media_type: 'tv',
        season,
        episode,
        title: series.name || baseTitle,
        poster_path: series.poster_path || null,
        source_url: entry.url,
        source_label: source.name || DEFAULT_SOURCE_LABEL_PREFIX,
        priority: source.priority,
        is_active: true,
      });
    }

    cursor += chunk.length;
    processedThisRun += chunk.length;
    if (vipSourcesRows.length >= 100) {
      await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
      vipSourcesRows = [];
    }
  }

  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');

  const isLastBatch = cursor >= allEpisodes.length;
  const patch = {
    sync_cursor: isLastBatch ? 0 : cursor,
    last_batch_at: new Date().toISOString(),
  };
  if (isLastBatch) {
    patch.last_synced_at = new Date().toISOString();
    patch.last_sync_stats = { series_total: allEpisodes.length, series_matched: matched, series_unmatched: unmatchedCount };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[sync-series] "${source.name}": ${processedThisRun} ep, ${matched} ok`);
  return { done: isLastBatch };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[sync-series] Faltam secrets');
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
    console.log('[sync-series] Nenhuma fonte M3U ativa.');
    return;
  }

  for (const source of sources) {
    if (timeLeft() <= 5000) break;
    try {
      await processSource({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache });
    } catch (err) {
      console.error(`[sync-series] "${source.name}" falhou: ${err.message}`);
    }
  }
  console.log('[sync-series] Fim.');
}

main().catch((e) => { console.error(e); process.exit(1); });
