// sync-standalone.js — see repo history for full docs
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U, shouldKeepMovie } = require('./lib/iptv-parser');
const fs = require('fs');
const os = require('os');
const path = require('path');

const DEFAULT_SOURCE_LABEL_PREFIX = 'MegaEmbed VIP';

// ── Bloqueio permanente: tabela vip_source_blocks (exclusao no painel) ──
async function loadBlockedKeys(serviceKey, sourceLabel) {
  const blocked = new Set();
  try {
    let q = 'select=tmdb_id,media_type,season,episode,source_label&limit=10000';
    if (sourceLabel) q += '&or=(source_label.eq.' + encodeURIComponent(sourceLabel) + ',source_label.is.null)';
    const rows = await sbSelect(serviceKey, 'vip_source_blocks', q);
    for (const r of (rows || [])) {
      const s = r.season == null ? 0 : r.season;
      const e = r.episode == null ? 0 : r.episode;
      const label = r.source_label == null ? '' : String(r.source_label);
      blocked.add(String(r.tmdb_id) + '|' + (r.media_type || 'movie') + '|' + s + '|' + e + '|' + label);
      if (!label) blocked.add(String(r.tmdb_id) + '|' + (r.media_type || 'movie') + '|' + s + '|' + e + '|*');
    }
  } catch (err) {
    console.warn('[block] vip_source_blocks:', err.message || err);
  }
  return blocked;
}

function filterUnblockedRows(rows, blocked) {
  if (!blocked || !blocked.size) return rows;
  const out = [];
  let skipped = 0;
  for (const row of rows) {
    const s = row.season == null ? 0 : row.season;
    const e = row.episode == null ? 0 : row.episode;
    const label = row.source_label == null ? '' : String(row.source_label);
    const key = String(row.tmdb_id) + '|' + (row.media_type || 'movie') + '|' + s + '|' + e + '|' + label;
    const keyAny = String(row.tmdb_id) + '|' + (row.media_type || 'movie') + '|' + s + '|' + e + '|*';
    if (blocked.has(key) || blocked.has(keyAny)) { skipped++; continue; }
    out.push(row);
  }
  if (skipped) console.log('[block] sync pulou ' + skipped + ' fonte(s) bloqueada(s)');
  return out;
}
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
    const k = keys.map((key) => String(row[key] ?? '')).join('|');
    map.set(k, row);
  }
  return [...map.values()];
}

async function sbUpsert(serviceKey, table, rows, onConflict) {
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
    console.error(`[sync] upsert lote falhou (${table}): ${batchErr.message}`);
    for (const row of rows) {
      try { await post([row]); } catch (rowErr) {
        console.error(`[sync] linha ignorada (${table}): ${rowErr.message}`);
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

function normalizeTitle(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
const TITLE_STOP = new Set(['a','o','as','os','de','da','do','das','dos','e','em','um','uma','the','of','and','in','on','to','le','la','el','los','las','y']);
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

async function searchTmdbMovie(title, year, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/movie');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', title);
  url.searchParams.set('language', 'pt-BR');
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);
  let res;
  try { res = await fetch(url.toString(), { signal: controller.signal }); }
  finally { clearTimeout(timeoutId); }
  if (!res.ok) throw new Error(`TMDB search falhou: ${res.status}`);
  const data = await res.json();
  if (!data.results || data.results.length === 0) return null;
  let pool = data.results;
  if (year) {
    pool = pool.filter((r) => {
      const releaseYear = r.release_date ? parseInt(r.release_date.slice(0, 4), 10) : null;
      return releaseYear && Math.abs(releaseYear - year) <= 1;
    });
    if (pool.length === 0) return null;
  }
  const ranked = pool.map((r) => {
    const score = Math.max(
      titleSimilarity(title, r.title || ''),
      titleSimilarity(title, r.original_title || ''),
    );
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
    if (!entry.classification || entry.classification.kind !== 'movie') return;
    if (!shouldKeepMovie(entry)) return;
    allMovies.push(entry);
  }, { dedupe: true });
  fs.unlink(filePath, () => {});
  console.log(`[sync-standalone] ${allMovies.length} filmes encontrados na playlist.`);

  let cursor = source.sync_cursor >= allMovies.length ? 0 : (source.sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
  const blockedKeys = await loadBlockedKeys(serviceKey, source.name || '');
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
        unmatchedRows.push({
          source_id: source.id, raw_title: entry.name, parsed_year: year,
          stream_url: entry.url, reason: 'tmdb_not_found',
        });
      }
    }

    cursor += chunk.length;
    processedThisRun += chunk.length;
    if (vipSourcesRows.length >= 100) {
      await sbUpsert(serviceKey, 'vip_sources', filterUnblockedRows(vipSourcesRows, blockedKeys), 'tmdb_id,media_type,season_key,episode_key,source_label');
      vipSourcesRows = [];
    }
    if (unmatchedRows.length >= 100) {
      await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
      unmatchedRows = [];
    }
    if (processedThisRun % 200 === 0) {
      console.log(`[sync-standalone] progresso ${cursor}/${allMovies.length} (${matched} ok, ${unmatchedCount} sem match)`);
    }
  }

  await sbUpsert(serviceKey, 'vip_sources', filterUnblockedRows(vipSourcesRows, blockedKeys), 'tmdb_id,media_type,season_key,episode_key,source_label');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');

  const isLastBatch = cursor >= allMovies.length;
  const patch = {
    sync_cursor: isLastBatch ? 0 : cursor,
    last_batch_at: new Date().toISOString(),
  };
  if (isLastBatch) {
    patch.last_synced_at = new Date().toISOString();
    patch.last_sync_stats = { total: allMovies.length, matched, unmatched: unmatchedCount, errors };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[sync-standalone] "${source.name}": ${processedThisRun} processados, ${matched} ok, ${unmatchedCount} sem match`);
  return { done: isLastBatch };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[sync-standalone] Faltam SUPABASE_SERVICE_ROLE_KEY e/ou TMDB_API_KEY');
    process.exit(1);
  }
  const startTime = Date.now();
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);

  const sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    "is_active=eq.true&or=(source_type.eq.m3u,source_type.is.null)&select=*&order=last_batch_at.asc.nullsfirst",
  );
  if (!sources.length) {
    console.log('[sync-standalone] Nenhuma fonte M3U ativa.');
    return;
  }

  for (const source of sources) {
    if (timeLeft() <= 5000) break;
    try {
      await processSource({ source, serviceKey, tmdbApiKey, timeLeft });
    } catch (err) {
      console.error(`[sync-standalone] Fonte "${source.name}" falhou: ${err.message}`);
      try {
        await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, {
          last_batch_at: new Date().toISOString(),
          last_sync_stats: { error: err.message },
        });
      } catch (_) {}
    }
  }
  console.log('[sync-standalone] Execucao finalizada.');
}

main().catch((e) => { console.error(e); process.exit(1); });
