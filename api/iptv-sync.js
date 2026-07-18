// api/iptv-sync.js
//
// Sincroniza automaticamente filmes de uma playlist Xtream com o catálogo
// StreamFlixVIP, gravando em vip_sources com source_label = 'StreamFliXtream'
// (aparece como um botão de servidor SEPARADO dos que você cadastra manual).
//
// RODA POR ORÇAMENTO DE TEMPO: o plano Hobby da Vercel limita cada execução
// a 60s. A cada chamada, esta função baixa o M3U inteiro (não confia em
// /tmp persistir entre invocações separadas), e processa filmes a partir
// de onde parou (sync_cursor, salvo no Supabase) até o tempo acabar. A
// próxima chamada do cron continua de onde essa parou. Ao terminar o
// arquivo inteiro, reinicia do zero automaticamente na passada seguinte,
// pra manter o catálogo atualizado com filmes novos da playlist.
//
// Chamado automaticamente 1x/dia pelo Vercel Cron (ver vercel.json).
// Protegido por IPTV_SYNC_SECRET pra ninguém mais disparar e gastar sua
// quota TMDB.

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const { parseM3U, shouldKeepMovie } = require('../lib/iptv-parser');
const fs = require('fs');
const os = require('os');
const path = require('path');

const DEFAULT_SOURCE_LABEL_PREFIX = 'StreamFliXtream'; // prefixo padrão; cada fonte pode ter seu próprio nome via iptv_sources.name

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

// Busca na TMDB via o proxy interno que o projeto já tem (api/tmdb.js),
// reaproveitando a mesma TMDB_API_KEY já configurada — sem chamar a API
// externa duas vezes nem duplicar lógica de auth.
async function searchTmdbMovie(title, year, appBaseUrl, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/movie');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', title);
  url.searchParams.set('language', 'pt-BR');
  if (year) url.searchParams.set('primary_release_year', String(year));

  // Timeout explícito de 5s: sem isso, uma rede instável ou uma resolução
  // de DNS lenta pode deixar o fetch pendurado até o timeout default do
  // runtime (bem mais alto), e com centenas de filmes por ciclo isso
  // consome o timeBudgetMs inteiro em poucas tentativas, travando o
  // progresso da sincronização. Falhando rápido aqui, o loop principal
  // segue pro próximo filme em vez de ficar preso num só.
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 5000);
  let res;
  try {
    res = await fetch(url.toString(), { signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
  if (!res.ok) throw new Error(`TMDB search falhou: ${res.status}`);
  const data = await res.json();
  if (!data.results || data.results.length === 0) return null;

  // IMPORTANTE: não confiar só em "mais popular". O parâmetro
  // primary_release_year às vezes não filtra 100% no lado da TMDB, e um
  // filme homônimo/remake mais popular pode vir na frente do certo.
  // Prioriza resultados cujo ano de lançamento bate exatamente (ou ±1 ano,
  // pra cobrir diferença de estreia entre países/fuso) antes de olhar
  // popularidade. Só cai pro "mais popular sem filtro de ano" se year for
  // null (não conseguimos extrair ano do nome no M3U).
  if (year) {
    const withMatchingYear = data.results.filter((r) => {
      const releaseYear = r.release_date ? parseInt(r.release_date.slice(0, 4), 10) : null;
      return releaseYear && Math.abs(releaseYear - year) <= 1;
    });
    if (withMatchingYear.length > 0) {
      return withMatchingYear.sort((a, b) => (b.popularity || 0) - (a.popularity || 0))[0];
    }
    // Nenhum resultado com ano compatível — mais seguro NÃO advinhar
    // (evita cadastrar o filme errado). Fica como "não encontrado".
    return null;
  }

  return data.results.sort((a, b) => (b.popularity || 0) - (a.popularity || 0))[0];
}

async function downloadM3U(source) {
  const url = `${source.xtream_host}/get.php?username=${source.xtream_user}&password=${source.xtream_pass}&type=m3u_plus`;
  // Timeout de 20s: a fonte Xtream às vezes está lenta/instável (visto nos
  // logs: ConnectTimeoutError repetido). Sem limite aqui, uma fonte fora
  // do ar prende o ciclo inteiro até o timeout default do runtime.
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 20000);
  let res;
  try {
    // Adicionado headers de navegador para evitar erro 403 (Forbidden)
    // Muitos servidores IPTV bloqueiam requisições que não tenham User-Agent.
    res = await fetch(url, { 
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
        'Accept': '*/*',
        'Connection': 'keep-alive'
      },
      signal: controller.signal 
    });
  } finally {
    clearTimeout(timeoutId);
  }
  if (!res.ok) throw new Error(`Falha ao baixar M3U: HTTP ${res.status}`);

  const tmpPath = path.join(os.tmpdir(), `iptv-${source.id}.m3u`);
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

// ── Núcleo da sincronização, independente de HTTP ──
// Extraído do handler pra poder ser chamado tanto pela rota (compatibilidade
// com o cron externo / teste manual pelo navegador) quanto internamente por
// um setInterval no server.js — sem depender de nenhum serviço de cron de
// terceiros, já que o Zeabur roda como container 24/7 (não serverless).
//
// timeBudgetMs: no Zeabur não existe limite de 60s (isso era coisa da
// Vercel Serverless Functions), mas ainda vale processar em fatias de
// tempo pra não segurar a chamada HTTP por tempo indefinido nem monopolizar
// o event loop com um request gigante. Ajustável por quem chama.
async function runIptvSync({ timeBudgetMs = 50_000 } = {}) {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!serviceKey || !tmdbApiKey) {
    throw new Error('SUPABASE_SERVICE_ROLE_KEY ou TMDB_API_KEY não configuradas');
  }

  const TIME_BUDGET_MS = timeBudgetMs;
  const startTime = Date.now();
  const timeLeft = () => TIME_BUDGET_MS - (Date.now() - startTime);

  {
    // Suporta múltiplas fontes cadastradas: cada chamada processa a fonte
    // "mais atrasada" (que não sincroniza há mais tempo, ou nunca
    // sincronizou ainda). Chamadas seguintes do cron vão revezando entre
    // as fontes ativas automaticamente, sem precisar configurar nada extra.
    const sources = await sbSelect(
      serviceKey,
      'iptv_sources',
      'is_active=eq.true&select=*&order=last_batch_at.asc.nullsfirst&limit=1'
    );
    if (!sources.length) {
      return { success: true, message: 'Nenhuma fonte IPTV ativa cadastrada.' };
    }
    const source = sources[0];

    // Baixa o M3U SEMPRE nesta mesma execução — não confia em /tmp
    // persistir entre chamadas HTTP separadas (instâncias serverless "frias"
    // não garantem isso). O download conta dentro do orçamento de tempo.
    const filePath = await downloadM3U(source);

    const allMovies = [];
    await parseM3U(filePath, (entry) => {
      if (entry.classification.kind !== 'movie') return;
      if (!shouldKeepMovie(entry)) return;
      allMovies.push(entry);
    }, { dedupe: true });
    fs.unlink(filePath, () => {}); // já não precisa mais do arquivo em disco

    let cursor = source.sync_cursor >= allMovies.length ? 0 : source.sync_cursor;
    let matched = 0, unmatchedCount = 0, errors = 0, processedThisRun = 0;
    let vipSourcesRows = [];
    let unmatchedRows = [];

    const CONCURRENCY = 10; // chamadas TMDB em paralelo — TMDB aguenta bem esse volume

    // Processa em mini-lotes concorrentes até o tempo acabar. Isso multiplica
    // o throughput por ~10x em relação a uma busca de cada vez, já que a
    // maior parte do tempo é esperando a resposta da rede, não processamento.
    while (cursor < allMovies.length && timeLeft() > 3000) {
      const chunk = allMovies.slice(cursor, cursor + CONCURRENCY);
      const results = await Promise.all(chunk.map(async (entry) => {
        const { title, year } = entry.classification;
        try {
          const found = await searchTmdbMovie(title, year, null, tmdbApiKey);
          return { entry, found, error: null };
        } catch (err) {
          return { entry, found: null, error: err };
        }
      }));

      for (const { entry, found, error } of results) {
        const { title, year } = entry.classification;
        if (error) {
          errors++;
          console.error('[iptv-sync] erro casando', title, error.message);
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
            source_url: entry.url, // URL crua http:// — o stream-proxy.js já detecta e usa proxy automaticamente, igual no cadastro manual
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

      // Grava em lotes de 100 pra não acumular tudo em memória até o fim
      if (vipSourcesRows.length >= 100) {
        await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,source_url');
        vipSourcesRows = [];
      }
      if (unmatchedRows.length >= 100) {
        await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');
        unmatchedRows = [];
      }
    }

    // Descarrega o que sobrou nos buffers
    await sbUpsert(serviceKey, 'vip_sources', vipSourcesRows, 'tmdb_id,media_type,source_url');
    await sbUpsert(serviceKey, 'iptv_unmatched_items', unmatchedRows, 'source_id,stream_url');

    const isLastBatch = cursor >= allMovies.length;
    const patch = {
      sync_cursor: isLastBatch ? 0 : cursor, // reinicia do zero ao terminar, pra pegar filmes novos na próxima passada completa
      sync_phase: isLastBatch ? 'done' : 'processing',
      last_batch_at: new Date().toISOString(), // sempre atualiza, garante revezamento justo entre múltiplas fontes
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

    return {
      success: true,
      source: source.name,
      processedThisRun,
      matched,
      unmatched: unmatchedCount,
      errors,
      progress: `${Math.min(cursor, allMovies.length)}/${allMovies.length}`,
      isLastBatch,
      elapsedMs: Date.now() - startTime,
    };
  }
}

// ── Handler HTTP (rota /api/iptv-sync) ──
// Mantido por compatibilidade: continua protegido por secret e pode ser
// chamado manualmente pelo navegador ou por um serviço de cron externo.
// Só que agora ele NÃO é mais a única forma de disparar a sincronização —
// veja startAutoSync() logo abaixo, usado pelo server.js pra rodar sozinho.
async function handler(req, res) {
  // Padrão oficial da Vercel: quando o Cron chama este endpoint, ele envia
  // automaticamente o header "Authorization: Bearer <CRON_SECRET>" — não
  // precisa (e não deve) colocar o secret na URL do vercel.json, pois isso
  // ficaria visível no repositório. O parâmetro ?secret= na URL continua
  // funcionando só como forma de você testar manualmente pelo navegador.
  const cronSecret = process.env.CRON_SECRET;
  const authHeader = req.headers['authorization'] || '';
  const headerOk = cronSecret && authHeader === `Bearer ${cronSecret}`;
  const queryOk = cronSecret && req.query.secret === cronSecret;
  if (!headerOk && !queryOk) {
    res.status(401).json({ error: 'Não autorizado' });
    return;
  }

  try {
    const result = await runIptvSync({ timeBudgetMs: 50_000 });
    res.status(200).json(result);
  } catch (err) {
    console.error('[iptv-sync] falhou:', err);
    res.status(500).json({ success: false, error: err.message });
  }
}

// ── Auto-sync interno (sem cron externo) ──
// Chamado pelo server.js num setInterval. Roda em loop dentro do próprio
// processo Node — que já fica de pé 24/7 no Zeabur (container, diferente
// da Vercel serverless) — então não depende de NENHUM serviço de cron de
// terceiro (cron-job.org, etc) nem sofre o timeout de 30s que esses
// serviços costumam ter no plano grátis, já que a chamada nunca sai pela
// rede: é só uma chamada de função dentro do mesmo processo.
//
// intervalMs: de quanto em quanto tempo tenta rodar um novo ciclo.
// timeBudgetMs: por quanto tempo cada ciclo processa filmes antes de parar
// e esperar o próximo ciclo (evita monopolizar o event loop por tempo
// indefinido caso a playlist seja enorme).
let autoSyncRunning = false;
function startAutoSync({ intervalMs = 5 * 60 * 1000, timeBudgetMs = 50_000 } = {}) {
  if (!process.env.SUPABASE_SERVICE_ROLE_KEY || !process.env.TMDB_API_KEY) {
    console.warn('[iptv-sync] auto-sync NÃO iniciado: faltam variáveis de ambiente (SUPABASE_SERVICE_ROLE_KEY / TMDB_API_KEY).');
    return;
  }

  console.log(`[iptv-sync] auto-sync interno ativado — roda a cada ${Math.round(intervalMs / 1000)}s, sem depender de cron externo.`);

  const tick = async () => {
    if (autoSyncRunning) {
      console.log('[iptv-sync] ciclo anterior ainda rodando, pulando este tick.');
      return;
    }
    autoSyncRunning = true;
    try {
      const result = await runIptvSync({ timeBudgetMs });
      console.log('[iptv-sync] auto-sync ciclo concluído:', JSON.stringify(result));
    } catch (err) {
      console.error('[iptv-sync] auto-sync ciclo falhou:', err.message);
    } finally {
      autoSyncRunning = false;
    }
  };

  // Primeiro ciclo logo na subida do servidor (não espera o intervalo
  // inteiro pra começar a trabalhar), os seguintes no intervalo definido.
  tick();
  setInterval(tick, intervalMs);
}

module.exports = handler;
module.exports.config = {
  api: { responseLimit: false },
};
module.exports.runIptvSync = runIptvSync;
module.exports.startAutoSync = startAutoSync;
