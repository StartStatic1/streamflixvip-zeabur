// sync-series-standalone.js
//
// Versão standalone da sincronização de SÉRIES, irmã de sync-standalone.js
// (que cuida só de filmes). Rodam como GitHub Actions SEPARADOS de
// propósito: cada um tem seu próprio cursor de progresso e seu próprio
// orçamento de tempo, então um não atrasa nem disputa tempo com o outro.
//
// Reaproveita a MESMA lógica de download/parse do M3U e o MESMO schema do
// Supabase (vip_sources, iptv_sources, iptv_unmatched_items) que o sync de
// filmes já usa — só muda o que é filtrado (episode em vez de movie) e como
// o casamento com o TMDB é feito (série + validação de season/episode).
//
// Variáveis de ambiente necessárias (configuradas como GitHub Secrets):
//   SUPABASE_SERVICE_ROLE_KEY
//   TMDB_API_KEY
//   SUPABASE_URL (opcional — tem um valor padrão abaixo, igual ao do projeto)

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U } = require('./lib/iptv-parser');
const fs = require('fs');
const os = require('os');
const path = require('path');

const DEFAULT_SOURCE_LABEL_PREFIX = 'StreamFliXtream';

// Orçamento de tempo generoso (55 minutos) — mesmo padrão do sync de filmes.
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

// Busca a SÉRIE (não o episódio) no TMDB pelo título base extraído do nome
// (ex: "Breaking Bad S01E05" -> baseTitle "Breaking Bad"). Diferente de
// filme, séries no M3U raramente vêm com ano no nome, então a busca aqui
// é só por título — o resultado mais popular geralmente é o correto.
async function searchTmdbSeries(baseTitle, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/tv');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', baseTitle);
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

// Confirma que a temporada/episódio existem de verdade na série encontrada
// no TMDB, antes de cadastrar — evita gravar "T99E01" por erro de
// numeração/digitação na fonte IPTV (algo relativamente comum em playlists
// piratas). Retorna true/false; em caso de erro de rede, assume true
// (não bloqueia o cadastro por uma falha de rede pontual na validação).
async function episodeExists(tvId, season, episode, tmdbApiKey) {
  const url = new URL(`https://api.themoviedb.org/3/tv/${tvId}/season/${season}/episode/${episode}`);
  url.searchParams.set('api_key', tmdbApiKey);

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 6000);
  try {
    const res = await fetch(url.toString(), { signal: controller.signal });
    if (res.status === 404) return false;
    return true; // 200 ou qualquer outro status inesperado: não bloqueia
  } catch {
    return true; // timeout/erro de rede: não bloqueia por uma falha pontual
  } finally {
    clearTimeout(timeoutId);
  }
}

async function downloadM3U(source) {
  const url = `${source.xtream_host}/get.php?username=${source.xtream_user}&password=${source.xtream_pass}&type=m3u_plus`;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000);
  let res;
  try {
    // Muitos provedores Xtream Codes bloqueiam com HTTP 403 qualquer
    // request sem um User-Agent reconhecido como player de IPTV — é
    // proteção deles contra scraping/redistribuição, não algo específico
    // desta fonte. Por isso funciona no navegador (manda UA de Chrome) e
    // no VLC/testador de playlist (manda UA de VLC), mas falhava aqui:
    // o fetch() puro do Node não manda nenhum User-Agent por padrão.
    // "IPTVSmarters" é aceito pela grande maioria dos provedores porque é
    // o app mais usado do mercado; os outros headers (Accept, Connection)
    // completam o perfil de um cliente de playlist real.
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

  const tmpPath = path.join(os.tmpdir(), `iptv-series-standalone-${source.id}.m3u`);
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

/**
 * Processa UMA fonte específica já escolhida — parse do M3U, casamento
 * com TMDB, upsert em lote. Extraído de runOnce pra poder ser chamado
 * várias vezes em sequência (uma por fonte) sem duplicar essa lógica.
 */
async function processSource({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache }) {
  console.log(`[sync-series-standalone] Processando fonte: ${source.name || source.id}`);

  const filePath = await downloadM3U(source);
  console.log('[sync-series-standalone] M3U baixado, iniciando parse...');

  const allEpisodes = [];
  await parseM3U(filePath, (entry) => {
    if (entry.classification.kind !== 'episode') return;
    allEpisodes.push(entry);
  }, { dedupe: true });
  fs.unlink(filePath, () => {});

  console.log(`[sync-series-standalone] ${allEpisodes.length} episódios encontrados na playlist.`);

  let cursor = source.series_sync_cursor >= allEpisodes.length ? 0 : (source.series_sync_cursor || 0);
  let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
  let vipSourcesRows = [];
  let unmatchedRows = [];

  const CONCURRENCY = 6; // um pouco menor que filmes: cada episódio pode disparar 2 chamadas TMDB (série + validação de episódio)

  while (cursor < allEpisodes.length && timeLeft() > 5000) {
    const chunk = allEpisodes.slice(cursor, cursor + CONCURRENCY);
    const results = await Promise.all(chunk.map(async (entry) => {
      const { baseTitle, season, episode } = entry.classification;
      try {
        // Cache por título base: se 20 episódios da mesma série aparecem
        // na playlist, busca a série no TMDB só na primeira vez e
        // reaproveita o resultado pros outros — economiza chamadas TMDB.
        const cacheKey = baseTitle.toLowerCase();
        let series = seriesCache.get(cacheKey);
        if (series === undefined) {
          series = await searchTmdbSeries(baseTitle, tmdbApiKey);
          seriesCache.set(cacheKey, series);
        }
        if (!series) return { entry, series: null, valid: false, error: null };

        const valid = await episodeExists(series.id, season, episode, tmdbApiKey);
        return { entry, series, valid, error: null };
      } catch (err) {
        return { entry, series: null, valid: false, error: err };
      }
    }));

    for (const { entry, series, valid, error } of results) {
      const { baseTitle, season, episode } = entry.classification;
      if (error) {
        errors++;
        console.error('[sync-series-standalone] erro casando', baseTitle, error.message);
        continue;
      }
      if (series && valid) {
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
      } else {
        unmatchedCount++;
        unmatchedRows.push({
          source_id: source.id,
          raw_title: entry.name,
          parsed_year: null,
          stream_url: entry.url,
          reason: series ? 'episode_not_found' : 'tmdb_not_found',
        });
      }
    }

    cursor += chunk.length;
    processedThisRun += chunk.length;

    if (vipSourcesRows.length >= 100) {
      await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season,episode,source_url');
      vipSourcesRows = [];
    }
    if (unmatchedRows.length >= 100) {
      await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
      unmatchedRows = [];
    }

    if (processedThisRun % 200 === 0) {
      console.log(`[sync-series-standalone] progresso: ${cursor}/${allEpisodes.length} (${matched} encontrados, ${unmatchedCount} não encontrados, ${errors} erros)`);
    }
  }

  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season,episode,source_url');
  await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');

  const isLastBatch = cursor >= allEpisodes.length;
  const patch = {
    series_sync_cursor: isLastBatch ? 0 : cursor,
    series_last_batch_at: new Date().toISOString(),
  };
  if (isLastBatch) {
    patch.series_last_synced_at = new Date().toISOString();
    patch.series_last_sync_stats = {
      total_episodes_in_playlist: allEpisodes.length,
      last_run_matched: matched,
      last_run_unmatched: unmatchedCount,
    };
  }
  await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, patch);

  console.log(`[sync-series-standalone] Ciclo concluído: ${processedThisRun} processados, ${matched} encontrados, ${unmatchedCount} não encontrados, ${errors} erros. isLastBatch=${isLastBatch}`);

  return { done: isLastBatch, processedThisRun };
}

async function runOnce({ serviceKey, tmdbApiKey, startTime, seriesCache }) {
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);

  // IMPORTANTE: usa um cursor próprio (series_sync_cursor), separado do
  // sync_cursor usado pelo sync de filmes — assim os dois Actions podem
  // rodar de forma totalmente independente na mesma fonte sem pisar um no
  // progresso do outro.
  //
  // Busca TODAS as fontes ativas (não só a mais atrasada) — se a
  // primeira falhar, precisamos ter as outras à mão pra tentar em
  // seguida, sem fazer uma consulta nova ao Supabase pra cada tentativa.
  const sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    'is_active=eq.true&select=*&order=series_last_batch_at.asc.nullsfirst'
  );
  if (!sources.length) {
    console.log('[sync-series-standalone] Nenhuma fonte IPTV ativa cadastrada.');
    return { done: true };
  }

  // Tenta cada fonte em ordem (mais atrasada primeiro). Se uma falhar
  // (ex: 403 de bloqueio por IP de datacenter), grava a falha nela —
  // isso atualiza series_last_batch_at, então ela deixa de ser "a mais
  // atrasada" e vai pro fim da fila — e PASSA PRA PRÓXIMA em vez de
  // travar o ciclo inteiro. Antes, um erro numa única fonte matava o
  // processo com process.exit(1) e as fontes saudáveis nunca chegavam
  // a ser tentadas nessa execução.
  for (const source of sources) {
    if (timeLeft() <= 5000) {
      return { done: false };
    }
    try {
      return await processSource({ source, serviceKey, tmdbApiKey, timeLeft, seriesCache });
    } catch (err) {
      console.error(`[sync-series-standalone] Fonte "${source.name || source.id}" falhou (${err.message}), pulando para a próxima fonte.`);
      try {
        await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, {
          series_last_batch_at: new Date().toISOString(),
        });
      } catch (patchErr) {
        // Não deixa uma falha ao GRAVAR o erro derrubar o ciclo — só loga
        // e segue tentando a próxima fonte de qualquer forma.
        console.error('[sync-series-standalone] Falha ao registrar erro da fonte:', patchErr.message);
      }
      // continua o for — próxima fonte da fila
    }
  }

  console.log('[sync-series-standalone] Todas as fontes ativas falharam ou o tempo acabou neste ciclo.');
  return { done: false };
}

async function main() {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    console.error('[sync-series-standalone] Faltam variáveis de ambiente: SUPABASE_SERVICE_ROLE_KEY e/ou TMDB_API_KEY');
    process.exit(1);
  }

  const startTime = Date.now();
  const seriesCache = new Map(); // baseTitle (lowercase) -> resultado TMDB (ou null), válido só durante esta execução
  try {
    let done = false;
    while (!done && (MAX_RUNTIME_MS - (Date.now() - startTime)) > 10000) {
      const result = await runOnce({ serviceKey, tmdbApiKey, startTime, seriesCache });
      done = result.done;
    }
    console.log('[sync-series-standalone] Execução finalizada com sucesso.');
  } catch (err) {
    console.error('[sync-series-standalone] Falha:', err.message);
    process.exit(1);
  }
}

main();
