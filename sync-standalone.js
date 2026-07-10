// sync-standalone.js
//
// Versão standalone da sincronização IPTV, feita pra rodar FORA do Zeabur
// (via GitHub Actions), tirando essa carga de processamento do servidor
// que hoje também serve o site e faz proxy de vídeo.
//
// Roda até processar a playlist inteira de uma vez (sem orçamento de tempo
// artificial como no Zeabur/Vercel), já que o job do GitHub Actions tem até
// 6 horas de limite por execução — bem mais do que qualquer sincronização
// deveria precisar. Se ainda assim demorar muito, ajuste MAX_RUNTIME_MS.
//
// Reaproveita a MESMA lógica e o MESMO schema do Supabase que o projeto já
// usa (vip_sources, iptv_sources, iptv_unmatched_items) — o site no Zeabur
// nem fica sabendo que a sincronização rodou em outro lugar, só lê os
// dados atualizados normalmente.
//
// Variáveis de ambiente necessárias (configuradas como GitHub Secrets):
//   SUPABASE_SERVICE_ROLE_KEY
//   TMDB_API_KEY
//   SUPABASE_URL (opcional — tem um valor padrão abaixo, igual ao do projeto)

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U, shouldKeepMovie } = require('./lib/iptv-parser');
const fs = require('fs');
const os = require('os');
const path = require('path');

const DEFAULT_SOURCE_LABEL_PREFIX = 'StreamFliXtream';

// Orçamento de tempo generoso (55 minutos) — bem acima do necessário pra
// uma playlist típica, mas evita que o job fique preso indefinidamente
// caso algo trave. Ajuste se sua playlist for muito grande.
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

// Busca na TMDB diretamente (não passa pelo proxy do projeto, já que este
// script roda fora do Zeabur e chama a API pública direto).
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
    res = await fetch(url, { signal: controller.signal });
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

async function runOnce({ serviceKey, tmdbApiKey, startTime }) {
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);

  const sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    'is_active=eq.true&select=*&order=last_batch_at.asc.nullsfirst&limit=1'
  );
  if (!sources.length) {
    console.log('[sync-standalone] Nenhuma fonte IPTV ativa cadastrada.');
    return { done: true };
  }
  const source = sources[0];
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
      if (error) {
        errors++;
        console.error('[sync-standalone] erro casando', title, error.message);
        continue;
      }
      if (found) {
        matched++;
        vipSourcesRows.push({
          tmdb_id: found.id,
          media_type: 'movie',
          season: null,
          episode: null,
          title: found.title || title,
          poster_path: found.poster_path || null,
          source_url: entry.url,
          source_label: source.name || DEFAULT_SOURCE_LABEL_PREFIX,
          priority: source.priority,
          is_active: true,
        });
      } else {
        unmatchedCount++;
        unmatchedRows.push({
          source_id: source.id,
          raw_title: entry.name,
          parsed_year: year,
          stream_url: entry.url,
          reason: 'tmdb_not_found',
        });
      }
    }

    cursor += chunk.length;
    processedThisRun += chunk.length;

    if (vipSourcesRows.length >= 100) {
      await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,source_url');
      vipSourcesRows = [];
    }
    if (unmatchedRows.length >= 100) {
      await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
      unmatchedRows = [];
    }

    if (processedThisRun % 200 === 0) {
      console.log(`[sync-standalone] progresso: ${cursor}/${allMovies.length} (${matched} encontrados, ${unmatchedCount} não encontrados, ${errors} erros)`);
    }
  }

  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,source_url');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');

  const isLastBatch = cursor >= allMovies.length;
  const patch = {
    sync_cursor: isLastBatch ? 0 : cursor,
    sync_phase: isLastBatch ? 'done' : 'processing',
    last_batch_at: new Date().toISOString(),
  };
  if (isLastBatch) {
    patch.last_synced_at = new Date().toISOString();
    patch.last_sync_stats = {
      total_movies_in_playlist: allMovies.length,
      last_run_matched: matched,
      last_run_unmatched: unmatchedCount,
    };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);

  console.log(`[sync-standalone] Ciclo concluído: ${processedThisRun} processados, ${matched} encontrados, ${unmatchedCount} não encontrados, ${errors} erros. isLastBatch=${isLastBatch}`);

  return { done: isLastBatch, processedThisRun };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[sync-standalone] Faltam variáveis de ambiente: SUPABASE_SERVICE_ROLE_KEY e/ou TMDB_API_KEY');
    process.exit(1);
  }

  const startTime = Date.now();
  try {
    // Processa em ciclos sucessivos até terminar a playlist inteira ou o
    // tempo se esgotar — diferente do Zeabur, aqui não precisamos parar
    // no meio e esperar o próximo setInterval; podemos só continuar no
    // mesmo processo até acabar.
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
