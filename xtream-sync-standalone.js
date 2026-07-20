// xtream-sync-standalone.js
//
// Versão standalone da sincronização via API XTREAM (player_api.php),
// irmã de sync-standalone.js (M3U) e sync-series-standalone.js (M3U séries).
// Roda como GitHub Action separado, com orçamento de tempo próprio e
// cursores próprios (xtream_sync_cursor / xtream_series_sync_cursor) —
// nunca disputa progresso com os sync M3U, mesmo que rodem ao mesmo tempo.
//
// Por que este arquivo existe: fontes Xtream "de verdade" (como a
// MegaEmbed) frequentemente NÃO expõem get.php (M3U), só o player_api.php
// (JSON). O sync-standalone.js tentava as duas como se fossem a mesma
// coisa e tomava 404 nessas fontes. Este script fala só o protocolo
// Xtream JSON, e só roda em fontes marcadas com source_type = 'xtream_api'.
//
// Diferença de arquitetura pro sync M3U: aqui não existe "arquivo pra
// baixar" — get_vod_streams()/get_series() já retornam a lista inteira em
// JSON de uma vez. Por isso o cursor aqui é sobre o ARRAY já recebido
// (mesma ideia do M3U, só que sem o parse de arquivo).
//
// Variáveis de ambiente necessárias (configuradas como GitHub Secrets):
//   SUPABASE_SERVICE_ROLE_KEY
//   TMDB_API_KEY
//   SUPABASE_URL (opcional — tem um valor padrão abaixo, igual ao do projeto)

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const DEFAULT_SOURCE_LABEL_PREFIX = 'Xtream VIP';

// Mesmo orçamento generoso dos outros standalones (55 min de 60 disponíveis
// no GitHub Actions) — evita ficar preso indefinidamente se algo travar.
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
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${table}?on_conflict=${onConflict}`, {
    method: 'POST',
    headers: { ...supabaseHeaders(serviceKey), Prefer: 'resolution=merge-duplicates' },
    body: JSON.stringify(rows),
  });
  if (!r.ok) throw new Error(`Supabase upsert falhou (${table}): ${r.status} ${await r.text()}`);
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
  return data.results.sort((a, b) => (b.popularity || 0) - (a.popularity || 0))[0];
}

// Requisição genérica à API Xtream (player_api.php). Envia um User-Agent
// de player IPTV reconhecido — mesmo motivo do sync M3U: alguns provedores
// bloqueiam requests "cruas" do Node sem UA de app conhecido.
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

/**
 * Processa filmes (VOD) de UMA fonte Xtream, com cursor próprio
 * (xtream_sync_cursor) sobre a lista já retornada pela API.
 *
 * PREFERÊNCIA DE FORMATO: quando o mesmo filme (mesmo tmdb_id) aparece em
 * mais de um stream_id na API Xtream — comum quando o provedor cadastrou
 * a mesma obra duas vezes, uma servida em .mp4 e outra em .m3u8 — mantemos
 * só UMA fonte por filme por servidor, preferindo .mp4. Motivo: .mp4 é
 * arquivo direto (sem manifesto, sem segmentos, sem depender de reescrita
 * de proxy) e por isso mais resiliente; .m3u8 só é usado quando aquele
 * filme não tiver nenhuma versão .mp4 disponível na fonte. Isso resolve a
 * causa raiz das duplicatas tipo "M3GAN 2.0 com 8 fontes" — antes, cada
 * stream_id virava uma linha própria porque tinha source_url diferente.
 */
async function processMovies({ source, serviceKey, tmdbApiKey, timeLeft }) {
  console.log(`[xtream-sync-standalone] Buscando filmes (get_vod_streams) de "${source.name}"`);
  const vods = await xtreamFetch(source.xtream_host, source.xtream_user, source.xtream_pass, 'get_vod_streams');
  if (!Array.isArray(vods)) {
    throw new Error('get_vod_streams não retornou uma lista (credenciais erradas ou API fora do ar?)');
  }
  console.log(`[xtream-sync-standalone] ${vods.length} filmes na API.`);

  let cursor = source.xtream_sync_cursor >= vods.length ? 0 : (source.xtream_sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;

  // tmdb_id -> { vod, found, extension } — a melhor entrada vista até agora
  // pra esse filme nesta fonte. "Melhor" = .mp4 vence .m3u8; entre duas do
  // mesmo tipo, a primeira encontrada fica (ordem estável, sem preferência
  // adicional já que ambas serviriam igualmente bem).
  const bestByTmdbId = new Map();
  let unmatchedRows = [];

  const EXT_RANK = { mp4: 0, mkv: 1, avi: 1 }; // menor = preferido; qualquer extensão fora daqui (inclui m3u8) fica em 2
  const rankOf = (ext) => (ext in EXT_RANK ? EXT_RANK[ext] : 2);

  const CONCURRENCY = 10;

  while (cursor < vods.length && timeLeft() > 5000) {
    const chunk = vods.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (vod) => {
      const title = vod.name || 'Sem título';
      try {
        const found = await searchTmdbMovie(title, null, tmdbApiKey);
        return { vod, found, error: null };
      } catch (err) {
        return { vod, found: null, error: err };
      }
    }));

    for (const { vod, found, error } of results) {
      const title = vod.name || 'Sem título';
      if (error) {
        errors++;
        console.error('[xtream-sync-standalone] erro casando', title, error.message);
        continue;
      }
      if (found) {
        matched++;
        const extension = (vod.container_extension || 'mp4').toLowerCase();
        const existing = bestByTmdbId.get(found.id);
        if (!existing || rankOf(extension) < rankOf(existing.extension)) {
          bestByTmdbId.set(found.id, { vod, found, title, extension });
        }
        // Se já existe uma entrada melhor ou igual pra esse tmdb_id, esta é
        // descartada silenciosamente — é a mesma obra, outro stream_id.
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
    if (processedThisRun % 200 === 0) {
      console.log(`[xtream-sync-standalone] filmes progresso: ${cursor}/${vods.length} (${matched} ok, ${unmatchedCount} sem match, ${errors} erros)`);
    }
  }

  // Só agora, com a "melhor" entrada por filme já decidida, montamos as
  // linhas finais pro Supabase — uma por tmdb_id nesta fonte, no máximo.
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
    patch.xtream_last_sync_stats = {
      total_movies_in_api: vods.length,
      last_run_matched: matched,
      last_run_unmatched: unmatchedCount,
    };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[xtream-sync-standalone] Filmes: ${processedThisRun} processados, ${matched} ok, ${unmatchedCount} sem match, ${errors} erros. isLastBatch=${isLastBatch}`);
  return { done: isLastBatch, processedThisRun };
}

/**
 * Processa séries de UMA fonte Xtream, com cursor próprio
 * (xtream_series_sync_cursor). Cada série exige uma chamada extra
 * (get_series_info) pra pegar os episódios, então isso é mais lento
 * que filmes — CONCURRENCY menor de propósito.
 */
async function processSeries({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache }) {
  console.log(`[xtream-sync-standalone] Buscando séries (get_series) de "${source.name}"`);
  const seriesList = await xtreamFetch(source.xtream_host, source.xtream_user, source.xtream_pass, 'get_series');
  if (!Array.isArray(seriesList)) {
    throw new Error('get_series não retornou uma lista');
  }
  console.log(`[xtream-sync-standalone] ${seriesList.length} séries na API.`);

  let cursor = source.xtream_series_sync_cursor >= seriesList.length ? 0 : (source.xtream_series_sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
  let vipSourcesRows = [];
  let unmatchedRows = [];

  const CONCURRENCY = 5;

  while (cursor < seriesList.length && timeLeft() > 5000) {
    const chunk = seriesList.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (seriesItem) => {
      const title = seriesItem.name || 'Sem título';
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
      const title = seriesItem.name || 'Sem título';
      if (error) {
        errors++;
        console.error('[xtream-sync-standalone] erro casando série', title, error.message);
        continue;
      }
      if (!tmdbSeries) {
        unmatchedCount++;
        unmatchedRows.push({
          source_id: source.id,
          raw_title: title,
          parsed_year: null,
          stream_url: `series_id:${seriesItem.series_id}`,
          reason: 'tmdb_not_found',
        });
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
    if (unmatchedRows.length >= 100) {
      await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
      unmatchedRows = [];
    }
    if (processedThisRun % 50 === 0) {
      console.log(`[xtream-sync-standalone] séries progresso: ${cursor}/${seriesList.length} (${matched} ok, ${unmatchedCount} sem match, ${errors} erros)`);
    }
  }

  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');

  const isLastBatch = cursor >= seriesList.length;
  const patch = {
    xtream_series_sync_cursor: isLastBatch ? 0 : cursor,
    xtream_series_last_batch_at: new Date().toISOString(),
  };
  if (isLastBatch) {
    patch.xtream_series_last_synced_at = new Date().toISOString();
    patch.xtream_series_last_sync_stats = {
      total_series_in_api: seriesList.length,
      last_run_matched: matched,
      last_run_unmatched: unmatchedCount,
    };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);
  console.log(`[xtream-sync-standalone] Séries: ${processedThisRun} processadas, ${matched} ok, ${unmatchedCount} sem match, ${errors} erros. isLastBatch=${isLastBatch}`);
  return { done: isLastBatch, processedThisRun };
}

async function runOnce({ serviceKey, tmdbApiKey, startTime, seriesCache }) {
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);

  // Só fontes marcadas como source_type = 'xtream_api' entram aqui — as
  // fontes M3U continuam exclusivas do sync-standalone.js / sync-series-standalone.js.
  const sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    "is_active=eq.true&source_type=eq.xtream_api&select=*&order=xtream_last_batch_at.asc.nullsfirst"
  );
  if (!sources.length) {
    console.log('[xtream-sync-standalone] Nenhuma fonte Xtream API ativa cadastrada.');
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
      console.error(`[xtream-sync-standalone] Fonte "${source.name || source.id}" falhou (${err.message}), pulando para a próxima fonte.`);
      try {
        await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, {
          xtream_last_batch_at: new Date().toISOString(),
          xtream_last_sync_stats: { error: err.message },
        });
      } catch (patchErr) {
        console.error('[xtream-sync-standalone] Falha ao registrar erro da fonte:', patchErr.message);
      }
      // continua o for — próxima fonte da fila
    }
  }

  console.log('[xtream-sync-standalone] Todas as fontes Xtream ativas falharam ou o tempo acabou neste ciclo.');
  return { done: false };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[xtream-sync-standalone] Faltam variáveis de ambiente: SUPABASE_SERVICE_ROLE_KEY e/ou TMDB_API_KEY');
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
    console.log('[xtream-sync-standalone] Execução finalizada com sucesso.');
  } catch (err) {
    console.error('[xtream-sync-standalone] Falha:', err.message);
    process.exit(1);
  }
}

main();
