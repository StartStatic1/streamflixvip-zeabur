// sync-standalone.js — M3U movies sync (restaurado + match por titulo)
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U, shouldKeepMovie } = require('./lib/iptv-parser');

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
  const fs = require('fs');
  const path = require('path');
  const os = require('os');
  const host = String(source.xtream_host || '').replace(/\/+$/, '');
  const user = source.xtream_user;
  const pass = source.xtream_pass;
  if (!host || !user || !pass) throw new Error('Fonte M3U sem host/user/pass');
  const urls = [
    `${host}/get.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&type=m3u_plus&output=ts`,
    `${host}/get.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&type=m3u_plus`,
    `${host}/playlist.m3u?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}`,
  ];
  const tmp = path.join(os.tmpdir(), `m3u-${source.id || 'x'}-${Date.now()}.m3u`);
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
      if (!text || text.length < 50 || !text.includes('#EXT')) {
        lastErr = new Error('Resposta nao parece M3U');
        continue;
      }
      fs.writeFileSync(tmp, text, 'utf8');
      console.log(`[m3u] baixado ${text.length} bytes de ${url.slice(0, 60)}...`);
      return tmp;
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr || new Error('Falha ao baixar M3U');
}

async function processSource({ source, serviceKey, tmdbApiKey, timeLeft }) {
  const fs = require('fs');
  console.log(`[m3u] Fonte "${source.name}"`);
  const filePath = await downloadM3U(source);
  let entries;
  try {
    const raw = fs.readFileSync(filePath, 'utf8');
    entries = parseM3U(raw).filter((e) => shouldKeepMovie(e));
  } finally {
    try { fs.unlinkSync(filePath); } catch (_) {}
  }
  console.log(`[m3u] ${entries.length} entradas de filme apos filtro`);

  let cursor = source.sync_cursor >= entries.length ? 0 : (source.sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
  let vipRows = [];
  let unmatchedRows = [];
  const CONCURRENCY = 8;

  while (cursor < entries.length && timeLeft() > 5000) {
    const chunk = entries.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (entry) => {
      const title = entry.title || entry.name || '';
      const year = entry.year || null;
      try {
        const found = await searchTmdbMovie(title, year, tmdbApiKey);
        return { entry, found, error: null };
      } catch (err) {
        return { entry, found: null, error: err };
      }
    }));

    for (const { entry, found, error } of results) {
      if (error) { errors++; continue; }
      if (found) {
        matched++;
        vipRows.push({
          tmdb_id: found.id,
          media_type: 'movie',
          season: null,
          episode: null,
          title: found.title || entry.title,
          poster_path: found.poster_path || null,
          source_url: entry.url,
          source_label: source.name,
          priority: source.priority,
          is_active: true,
        });
      } else {
        unmatchedCount++;
        unmatchedRows.push({
          source_id: source.id,
          raw_title: entry.title || '',
          parsed_year: entry.year || null,
          stream_url: entry.url || '',
          reason: 'tmdb_not_found',
        });
      }
    }

    cursor += chunk.length;
    processedThisRun += chunk.length;
    if (vipRows.length >= 80) {
      await sbUpsert(serviceKey, 'vip_sources', vipRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
      vipRows = [];
    }
    if (unmatchedRows.length >= 80) {
      await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
      unmatchedRows = [];
    }
    if (processedThisRun % 200 === 0) {
      console.log(`[m3u] progresso ${cursor}/${entries.length} (${matched} ok, ${unmatchedCount} sem match)`);
    }
  }

  await sbUpsert(serviceKey, 'vip_sources', vipRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');

  const isLastBatch = cursor >= entries.length;
  const patch = {
    sync_cursor: isLastBatch ? 0 : cursor,
    last_batch_at: new Date().toISOString(),
  };
  if (isLastBatch) {
    patch.last_synced_at = new Date().toISOString();
    patch.last_sync_stats = { total: entries.length, matched, unmatched: unmatchedCount, errors };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[m3u] "${source.name}": ${processedThisRun} processados, ${matched} ok, ${unmatchedCount} sem match`);
  return { done: isLastBatch };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[m3u] Faltam SUPABASE_SERVICE_ROLE_KEY e/ou TMDB_API_KEY');
    process.exit(1);
  }
  const startTime = Date.now();
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);

  // Fontes M3U (nao xtream_api)
  const sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    "is_active=eq.true&or=(source_type.eq.m3u,source_type.is.null)&select=*&order=last_batch_at.asc.nullsfirst",
  );
  if (!sources.length) {
    console.log('[m3u] Nenhuma fonte M3U ativa.');
    return;
  }

  for (const source of sources) {
    if (timeLeft() <= 5000) break;
    try {
      await processSource({ source, serviceKey, tmdbApiKey, timeLeft });
    } catch (err) {
      console.error(`[m3u] Fonte "${source.name}" falhou: ${err.message}`);
      try {
        await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, {
          last_batch_at: new Date().toISOString(),
          last_sync_stats: { error: err.message },
        });
      } catch (_) {}
    }
  }
  console.log('[m3u] Execucao finalizada.');
}

main().catch((e) => { console.error(e); process.exit(1); });
