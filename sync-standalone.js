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

const DEFAULT_SOURCE_LABEL_PREFIX = 'MegaEmbed VIP';

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

// Uma linha problemática (conflito não resolvido, schema cache do
// PostgREST desatualizado após um ALTER TABLE recente, etc.) NÃO PODE
// derrubar o lote inteiro — antes, um erro aqui fazia processMovies/
// processSeries jogar a exceção pra cima ANTES de salvar o cursor,
// então a próxima execução recomeçava do zero, refazia o mesmo trabalho
// (buscar todos os títulos de novo, casar com TMDB de novo) e batia no
// mesmo erro de novo — um loop infinito que nunca avança nem termina.
// Agora: se o lote falhar, tenta linha a linha; loga e PULA só a linha
// que realmente falhar, sem nunca lançar erro pra quem chamou.
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
    console.error(`[sync] upsert em lote falhou (${table}): ${batchErr.message}. Tentando linha a linha para não perder o lote inteiro...`);
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
    // Muitos provedores Xtream Codes bloqueiam com HTTP 403 requests sem
    // User-Agent reconhecido como player de IPTV — funciona no navegador
    // e no VLC porque eles mandam UA próprio; o fetch() puro do Node não
    // mandava nenhum. Ver mesma correção em sync-series-standalone.js.
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

/**
 * Processa UMA fonte específica já escolhida — parse do M3U, casamento
 * com TMDB, upsert em lote. Extraído de runOnce pra poder ser chamado
 * várias vezes em sequência (uma por fonte) sem duplicar essa lógica.
 */
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
      await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
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

  await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,season_key,episode_key,source_label');
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

async function runOnce({ serviceKey, tmdbApiKey, startTime }) {
  const timeLeft = () => MAX_RUNTIME_MS - (Date.now() - startTime);

  // Busca TODAS as fontes ativas (não só a mais atrasada) — se a
  // primeira falhar, precisamos ter as outras à mão pra tentar em
  // seguida, sem fazer uma consulta nova ao Supabase pra cada tentativa.
  const sources = await sbSelect(
    serviceKey,
    'iptv_sources',
    'is_active=eq.true&select=*&order=last_batch_at.asc.nullsfirst'
  );
  if (!sources.length) {
    console.log('[sync-standalone] Nenhuma fonte IPTV ativa cadastrada.');
    return { done: true };
  }

  // Tenta cada fonte em ordem (mais atrasada primeiro). Se uma falhar
  // (ex: 403 de bloqueio por IP de datacenter), grava o erro nela —
  // isso atualiza last_batch_at, então ela deixa de ser "a mais
  // atrasada" e vai pro fim da fila — e PASSA PRA PRÓXIMA em vez de
  // travar o ciclo inteiro. Antes, um erro numa única fonte matava o
  // processo com process.exit(1) e as fontes saudáveis nunca chegavam
  // a ser tentadas nessa execução.
  let allDone = true;
  for (const source of sources) {
    if (timeLeft() <= 5000) {
      allDone = false;
      break;
    }
    try {
      const result = await processSource({ source, serviceKey, tmdbApiKey, timeLeft });
      if (!result.done) allDone = false;
    } catch (err) {
      console.error(`[sync-standalone] Fonte "${source.name || source.id}" falhou (${err.message}), pulando para a próxima fonte.`);
      try {
        await sbUpdate(serviceKey, 'iptv_sources', `id=eq.${source.id}`, {
          last_batch_at: new Date().toISOString(),
          sync_phase: 'error',
        });
      } catch (patchErr) {
        // Não deixa uma falha ao GRAVAR o erro derrubar o ciclo — só loga
        // e segue tentando a próxima fonte de qualquer forma.
        console.error('[sync-standalone] Falha ao registrar erro da fonte:', patchErr.message);
      }
      // continua o for — próxima fonte da fila
    }
  }

  return { done: allDone };
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
