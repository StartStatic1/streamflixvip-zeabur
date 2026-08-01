// sync-standalone.js — see repo history for full docs
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U, shouldKeepMovie } = require('./lib/iptv-parser');
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

function dedupeUpsertRows(rows, onConflict) {
  const keys = String(onConflict || '').split(',').map((s) => s.trim()).filter(Boolean);
  if (!keys.length) return rows;
  const map = new Map();
  for (const row of rows) {
    const key = keys.map((k) => {
      let v = row[k];
      if (v === undefined || v === null) {
        if (k === 'season_key') v = row.season == null ? -1 : row.season;
        else if (k === 'episode_key') v = row.episode == null ? -1 : row.episode;
        else v = '';
      }
      return String(v);
    }).join('|');
    map.set(key, row);
  }
  return Array.from(map.values());
}

async function sbUpsert(serviceKey, table, rows, onConflict) {
  if (!rows.length) return;
  rows = dedupeUpsertRows(rows, onConflict);
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
      try {
        await post([row]);
      } catch (rowErr) {
        console.error(`[sync] linha ignorada em "${table}" (tmdb_id=${row.tmdb_id ?? '?'}, source_label=${row.source_label ?? '?'}): ${rowErr.message}`);
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

async function searchTmdbMovie(title, year, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/movie');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', title);
  url.searchParams.set('language', 'pt-BR');
  if (year) url.searchParams.set('primary_release_year', String(year));
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
  if (year) {
    const withMatchingYear = data.results.filter((r) => {
      const releaseYear = r.release_date ? parseInt(r.release_date.slice(0, 4), 10) : null;
      return releaseYear && Math.abs(releaseYear - year) <= 1;
    });
    if (withMatchingYear.length > 0) {
      return withMatchingYear.sort((a, b) => (b.popularity || 0) - (a.popularity || 0))[0];
    }
    return null;
  }
  return data.results.sort((a, b) => (b.popularity || 0) - (a.popularity || 0))[0];
}

async function downloadM3U(source) {
  const url = `${source.xtream_host}/get.php?username=${source.xtream_user}&password=${source.xtream_pass}&type=m3u_plus`;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000);
  let res;
  try {
    res = await fetch(url, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'IPTVSmarters/1.0 (Linux; Android)',
        Accept: '*/*',
        Connection: 'keep-alive',
      },
    });
  } finally {
    clearTimeout(timeoutId);
  }
  if (!res.ok) throw new Error(`Falha ao baixar M3U: HTTP ${res.status}`);
  const tmpPath = path.join(os.tmpdir(), `iptv-standalone-${source.id}.m3u`);
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

async function processSource({ source, serviceKey, tmdbApiKey, timeLeft }) {
  console.log(`[sync-standalone] Processando fonte: ${source.name || source.id}`);
  const filePath = await downloadM3U(source);
  console.log('[sync-standalone] M3U baixado, iniciando parse...');
  const allMovies = [];
  await parseM3U(filePath, (entry) => {
    if (entry.classification.kind !== 'movie') return;
    if (!shouldKeepMovie(entry)) return;
    allMovies.push(entry);
  }, { dedupe: true });
  fs.unlink(filePath, () => {});
  console.log(`[sync-standalone] ${allMovies.length} filmes encontrados na playlist.`);
  let cursor = source.sync_cursor >= allMovies.length ? 0 : source.sync_cursor;
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
  let vipSourcesRows = [];
  let unmatchedRows = [];
  const CONCURRENCY = 10;
  while (cursor < allMovies.length && timeLeft() > 5000) {
    const chunk = allMovies.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (entry) => {
      const { title, year } = entry.classification;
      try {
        const found = await searchTmdbMovie(title, year, tmdbApiKey);
        return { entry, found, error: null };
      } catch (err) {
        return { entry, found: null, error: err };
      }
    }));
    for (const { entry, found, error } of results) {
      const { title, year } = entry.classification;
      if (error) { errors++; console.error('[sync-standalone] erro casando', title, error.message); continue; }
      if (found) {
        matched++;
        vipSourcesRows.push({
          tmdb_id: found.id, media_type: 'movie', season: null, episode: null,
          title: found.title || title, poster_path: found.poster_path || null,
          source_url: entry.url, source_label: source.name || DEFAULT_SOURCE_LABEL_PREFIX,
          priority: source.priority, is_active: true,
        });
      } else {
        unmatchedCount++;
        unmatchedRows.push({ source_id: source.id, raw_title: entry.name, parsed_year: year, stream_url: entry.url, reason: 'tmdb_not_found' });
      }
    }
    cursor += chunk.length;
    processedThisRun += chunk.length;
    if (vipSourcesRows.length >= 100) {
      await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
      vipSourcesRows = [];
    }
    if (unmatchedRows.length >= 100) {
      await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
      unmatchedRows = [];
    }
    if (processedThisRun % 200 === 0) {
      console.log(`[sync-standalone] progresso: ${cursor}/${allMovies.length} (${matched} ok, ${unmatchedCount} não, ${errors} erros)`);
    }
  }
  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
  const isLastBatch = cursor >= allMovies.length;
  const patch = { sync_cursor: isLastBatch ? 0 : cursor, sync_phase: isLastBatch ? 'done' : 'processing', last_batch_at: new Date().toISOString() };
  if (isLastBatch) {
    patch.last_synced_at = new Date().toISOString();
    patch.last_sync_stats = { total_movies_in_playlist: allMovies.length, last_run_matched: matched, last_run_unmatched: unmatchedCount };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[sync-standalone] Ciclo: ${processedThisRun} processados, ${matched} ok, ${unmatchedCount} não, ${errors} erros. last=${isLastBatch}`);
  return { done: isLastBatch, processedThisRun };
}

async function runOnce({ serviceKey, tmdbApiKey, startTime }) {
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);
  const sources = await sbSelect(serviceKey, 'iptv_sources', 'is_active=eq.true&select=*&order=last_batch_at.asc.nullsfirst');
  if (!sources.length) {
    console.log('[sync-standalone] Nenhuma fonte IPTV ativa cadastrada.');
    return { done: true };
  }
  let allDone = true;
  for (const source of sources) {
    if (timeLeft() <= 5000) { allDone = false; break; }
    try {
      const result = await processSource({ source, serviceKey, tmdbApiKey, timeLeft });
      if (!result.done) allDone = false;
    } catch (err) {
      console.error(`[sync-standalone] Fonte "${source.name || source.id}" falhou (${err.message}), pulando.`);
      try {
        await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, { last_batch_at: new Date().toISOString(), sync_phase: 'error' });
      } catch (patchErr) {
        console.error('[sync-standalone] Falha ao registrar erro da fonte:', patchErr.message);
      }
    }
  }
  return { done: allDone };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[sync-standalone] Faltam SUPABASE_SERVICE_ROLE_KEY e/ou TMDB_API_KEY');
    process.exit(1);
  }
  const startTime = Date.now();
  try {
    let done = false;
    while (!done && (MAX_RUNTIME_MS - (Date.now() - startTime)) > 10000) {
      const result = await runOnce({ serviceKey, tmdbApiKey, startTime });
      done = result.done;
    }
    console.log('[sync-standalone] Execução finalizada com sucesso.');
  } catch (err) {
    console.error('[sync-standalone] Falha:', err.message);
    process.exit(1);
  }
}

main();
