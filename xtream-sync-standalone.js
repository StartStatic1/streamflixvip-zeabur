// xtream-sync-standalone.js — match TMDB por similaridade de titulo
// Ver historico: evita Dia D -> Homem-Aranha so por popularidade.

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { shouldKeepMovieTitle } = require('./lib/iptv-parser');
const DEFAULT_SOURCE_LABEL_PREFIX = 'Xtream VIP';
const MAX_RUNTIME_MS = 55 * 60 * 1000;
const TMDB_TITLE_MIN_SCORE = 0.55;

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
    console.error(`[sync] upsert em lote falhou (${table}): ${batchErr.message}. Tentando linha a linha...`);
    for (const row of rows) {
      try { await post([row]); } catch (rowErr) {
        console.error(`[sync] linha ignorada em "${table}" (tmdb_id=${row.tmdb_id ?? '?'}): ${rowErr.message}`);
      }
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

function normalizeTitleForMatch(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function titleTokensForMatch(s) {
  const stop = new Set(['a','o','os','as','de','da','do','das','dos','e','um','uma','the','of','and','in','on']);
  return normalizeTitleForMatch(s).split(' ').filter((w) => w.length > 0 && !stop.has(w));
}

/** Score 0..1 — evita "Dia D" casar com "Homem-Aranha: Um Novo Dia". */
function titleSimilarityScore(query, candidate) {
  const nq = normalizeTitleForMatch(query);
  const nc = normalizeTitleForMatch(candidate);
  if (!nq || !nc) return 0;
  if (nq === nc) return 1;
  if (nc.startsWith(nq + ' ') || nq.startsWith(nc + ' ')) return 0.92;
  const q = titleTokensForMatch(query);
  const c = titleTokensForMatch(candidate);
  if (!q.length || !c.length) return 0;
  const qSet = new Set(q);
  const cSet = new Set(c);
  let inter = 0;
  for (const t of qSet) if (cSet.has(t)) inter++;
  if (inter === 0) return 0;
  const coverage = inter / qSet.size;
  const jaccard = inter / new Set([...qSet, ...cSet]).size;
  let prefix = 0;
  if (q[0] && c[0] && q[0] === c[0]) prefix = 0.12;
  if (q.length >= 2 && c.length >= 2 && q[0] === c[0] && q[1] === c[1]) prefix = 0.22;
  let lenPenalty = 0;
  if (qSet.size <= 2 && cSet.size >= 4) lenPenalty = 0.25;
  return Math.max(0, Math.min(1, coverage * 0.65 + jaccard * 0.25 + prefix - lenPenalty));
}

async function searchTmdbMovie(title, year, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/movie');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', title);
  url.searchParams.set('language', 'pt-BR');

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);
  let res;
  try {
    res = await fetch(url.toString(), { signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
  if (!res.ok) throw new Error(`TMDB search falhou: ${res.status}`);
  const data = await res.json();
  if (!data.results || data.results.length === 0) return null;

  let candidates = data.results;
  if (year) {
    const withYear = candidates.filter((r) => {
      const releaseYear = r.release_date ? parseInt(r.release_date.slice(0, 4), 10) : null;
      return releaseYear && Math.abs(releaseYear - year) <= 1;
    });
    if (withYear.length === 0) return null;
    candidates = withYear;
  }

  const scored = candidates.map((r) => {
    const names = [r.title, r.original_title].filter(Boolean);
    const best = Math.max(...names.map((n) => titleSimilarityScore(title, n)));
    return { r, score: best };
  });
  scored.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score;
    return (b.r.popularity || 0) - (a.r.popularity || 0);
  });
  const top = scored[0];
  if (!top || top.score < TMDB_TITLE_MIN_SCORE) return null;
  return top.r;
}

async function searchTmdbSeries(title, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/tv');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', title);
  url.searchParams.set('language', 'pt-BR');

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);
  let res;
  try {
    res = await fetch(url.toString(), { signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
  if (!res.ok) throw new Error(`TMDB search/tv falhou: ${res.status}`);
  const data = await res.json();
  if (!data.results || data.results.length === 0) return null;
  const scored = data.results.map((r) => {
    const names = [r.name, r.original_name].filter(Boolean);
    const best = Math.max(...names.map((n) => titleSimilarityScore(title, n)));
    return { r, score: best };
  });
  scored.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score;
    return (b.r.popularity || 0) - (a.r.popularity || 0);
  });
  const top = scored[0];
  if (!top || top.score < TMDB_TITLE_MIN_SCORE) return null;
  return top.r;
}

async function xtreamFetch(baseUrl, username, password, action, params = {}) {
  const url = new URL(`${baseUrl.replace(/\/+$/, '')}/player_api.php`);
  url.searchParams.set('username', username);
  url.searchParams.set('password', password);
  if (action) url.searchParams.set('action', action);
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null) url.searchParams.set(k, String(v));
  });
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 20000);
  let res;
  try {
    res = await fetch(url.toString(), {
      signal: controller.signal,
      headers: {
        'User-Agent': 'IPTVSmarters/1.0 (Linux; Android)',
        Accept: 'application/json, */*',
        Connection: 'keep-alive',
      },
    });
  } finally {
    clearTimeout(timeoutId);
  }
  if (!res.ok) throw new Error(`Xtream API falhou (${action || 'auth'}): HTTP ${res.status}`);
  return res.json();
}

async function processMovies({ source, serviceKey, tmdbApiKey, timeLeft }) {
  console.log(`[xtream-sync-standalone] Buscando filmes de "${source.name}"`);
  const vods = await xtreamFetch(source.xtream_host, source.xtream_user, source.xtream_pass, 'get_vod_streams');
  if (!Array.isArray(vods)) throw new Error('get_vod_streams nao retornou lista');
  console.log(`[xtream-sync-standalone] ${vods.length} filmes na API.`);

  let cursor = source.xtream_sync_cursor >= vods.length ? 0 : (source.xtream_sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
  const bestByTmdbId = new Map();
  let unmatchedRows = [];
  const EXT_RANK = { mp4: 0, mkv: 1, avi: 1 };
  const rankOf = (ext) => (ext in EXT_RANK ? EXT_RANK[ext] : 2);
  const CONCURRENCY = 10;

  while (cursor < vods.length && timeLeft() > 5000) {
    const chunk = vods.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (vod) => {
      const title = vod.name || 'Sem titulo';
      if (!shouldKeepMovieTitle(title)) return { vod, found: null, error: null, skippedQuality: true };
      try {
        let yearFromTitle = null;
        const ym = String(title).match(/\b(19|20)\d{2}\b/);
        if (ym) yearFromTitle = parseInt(ym[0], 10);
        const cleanTitle = title.replace(/\b(19|20)\d{2}\b/g, ' ').replace(/\s+/g, ' ').trim();
        const found = await searchTmdbMovie(cleanTitle, yearFromTitle, tmdbApiKey);
        return { vod, found, error: null, skippedQuality: false };
      } catch (err) {
        return { vod, found: null, error: err, skippedQuality: false };
      }
    }));

    for (const { vod, found, error, skippedQuality } of results) {
      const title = vod.name || 'Sem titulo';
      if (skippedQuality) { unmatchedCount++; continue; }
      if (error) { errors++; console.error('[xtream] erro', title, error.message); continue; }
      if (found) {
        matched++;
        const extension = (vod.container_extension || 'mp4').toLowerCase();
        const existing = bestByTmdbId.get(found.id);
        if (!existing || rankOf(extension) < rankOf(existing.extension)) {
          bestByTmdbId.set(found.id, { vod, found, title, extension });
        }
      } else {
        unmatchedCount++;
        unmatchedRows.push({
          source_id: source.id,
          raw_title: title,
          parsed_year: null,
          stream_url: `stream_id:${vod.stream_id}`,
          reason: 'tmdb_not_found',
        });
      }
    }

    cursor += chunk.length;
    processedThisRun += chunk.length;
    if (unmatchedRows.length >= 100) {
      await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
      unmatchedRows = [];
    }
  }

  let vipSourcesRows = [];
  for (const { vod, found, title, extension } of bestByTmdbId.values()) {
    const playbackUrl = `${source.xtream_host.replace(/\/+$/, '')}/movie/${source.xtream_user}/${source.xtream_pass}/${vod.stream_id}.${extension}`;
    vipSourcesRows.push({
      tmdb_id: found.id,
      media_type: 'movie',
      season: null,
      episode: null,
      title: found.title || title,
      poster_path: found.poster_path || null,
      source_url: playbackUrl,
      source_label: source.name || DEFAULT_SOURCE_LABEL_PREFIX,
      priority: source.priority,
      is_active: true,
    });
    if (vipSourcesRows.length >= 100) {
      await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
      vipSourcesRows = [];
    }
  }
  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');

  const isLastBatch = cursor >= vods.length;
  const patch = {
    xtream_sync_cursor: isLastBatch ? 0 : cursor,
    xtream_last_batch_at: new Date().toISOString(),
  };
  if (isLastBatch) {
    patch.xtream_last_synced_at = new Date().toISOString();
    patch.xtream_last_sync_stats = { total_movies_in_api: vods.length, last_run_matched: matched, last_run_unmatched: unmatchedCount };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[xtream] Filmes: ${processedThisRun} processados, ${matched} ok, ${unmatchedCount} sem match`);
  return { done: isLastBatch, processedThisRun };
}

async function processSeries({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache }) {
  const seriesList = await xtreamFetch(source.xtream_host, source.xtream_user, source.xtream_pass, 'get_series');
  if (!Array.isArray(seriesList)) throw new Error('get_series nao retornou lista');
  let cursor = source.xtream_series_sync_cursor >= seriesList.length ? 0 : (source.xtream_series_sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
  let vipSourcesRows = [];
  let unmatchedRows = [];
  const CONCURRENCY = 5;

  while (cursor < seriesList.length && timeLeft() > 5000) {
    const chunk = seriesList.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (seriesItem) => {
      const title = seriesItem.name || 'Sem titulo';
      try {
        const cacheKey = title.toLowerCase();
        let tmdbSeries = seriesCache.get(cacheKey);
        if (tmdbSeries === undefined) {
          tmdbSeries = await searchTmdbSeries(title, tmdbApiKey);
          seriesCache.set(cacheKey, tmdbSeries);
        }
        if (!tmdbSeries) return { seriesItem, tmdbSeries: null, info: null, error: null };
        const info = await xtreamFetch(source.xtream_host, source.xtream_user, source.xtream_pass, 'get_series_info', { series_id: seriesItem.series_id });
        return { seriesItem, tmdbSeries, info, error: null };
      } catch (err) {
        return { seriesItem, tmdbSeries: null, info: null, error: err };
      }
    }));

    for (const { seriesItem, tmdbSeries, info, error } of results) {
      const title = seriesItem.name || 'Sem titulo';
      if (error) { errors++; continue; }
      if (!tmdbSeries) {
        unmatchedCount++;
        unmatchedRows.push({ source_id: source.id, raw_title: title, parsed_year: null, stream_url: `series_id:${seriesItem.series_id}`, reason: 'tmdb_not_found' });
        continue;
      }
      matched++;
      const episodesBySeason = (info && info.episodes) || {};
      for (const [season, episodes] of Object.entries(episodesBySeason)) {
        for (const ep of episodes) {
          const extension = ep.container_extension || 'mp4';
          const playbackUrl = `${source.xtream_host.replace(/\/+$/, '')}/series/${source.xtream_user}/${source.xtream_pass}/${ep.id}.${extension}`;
          vipSourcesRows.push({
            tmdb_id: tmdbSeries.id,
            media_type: 'tv',
            season: parseInt(season, 10),
            episode: ep.episode_num || 0,
            title: tmdbSeries.name || title,
            poster_path: tmdbSeries.poster_path || null,
            source_url: playbackUrl,
            source_label: source.name || DEFAULT_SOURCE_LABEL_PREFIX,
            priority: source.priority,
            is_active: true,
          });
        }
      }
    }
    cursor += chunk.length;
    processedThisRun += chunk.length;
    if (vipSourcesRows.length >= 100) {
      await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
      vipSourcesRows = [];
    }
  }
  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
  const isLastBatch = cursor >= seriesList.length;
  const patch = { xtream_series_sync_cursor: isLastBatch ? 0 : cursor, xtream_series_last_batch_at: new Date().toISOString() };
  if (isLastBatch) {
    patch.xtream_series_last_synced_at = new Date().toISOString();
    patch.xtream_series_last_sync_stats = { total_series_in_api: seriesList.length, last_run_matched: matched, last_run_unmatched: unmatchedCount };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  return { done: isLastBatch, processedThisRun };
}

async function runOnce({ serviceKey, tmdbApiKey, startTime, seriesCache }) {
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);
  const sources = await sbSelect(serviceKey, 'iptv_sources', "is_active=eq.true&source_type=eq.xtream_api&select=*&order=xtream_last_batch_at.asc.nullsfirst");
  if (!sources.length) {
    console.log('[xtream] Nenhuma fonte Xtream API ativa.');
    return { done: true };
  }
  for (const source of sources) {
    if (timeLeft() <= 5000) return { done: false };
    try {
      const movieResult = await processMovies({ source, serviceKey, tmdbApiKey, timeLeft });
      if (timeLeft() <= 5000) return { done: false };
      const seriesResult = await processSeries({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache });
      return { done: movieResult.done && seriesResult.done };
    } catch (err) {
      console.error(`[xtream] Fonte "${source.name}" falhou: ${err.message}`);
      try {
        await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, { xtream_last_batch_at: new Date().toISOString(), xtream_last_sync_stats: { error: err.message } });
      } catch (_) {}
    }
  }
  return { done: false };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[xtream] Faltam SUPABASE_SERVICE_ROLE_KEY e/ou TMDB_API_KEY');
    process.exit(1);
  }
  const startTime = Date.now();
  const seriesCache = new Map();
  try {
    let done = false;
    while (!done && (MAX_RUNTIME_MS - (Date.now() - startTime)) > 10000) {
      const result = await runOnce({ serviceKey, tmdbApiKey, startTime, seriesCache });
      done = result.done;
    }
    console.log('[xtream] Execucao finalizada.');
  } catch (err) {
    console.error('[xtream] Falha:', err.message);
    process.exit(1);
  }
}

main();
