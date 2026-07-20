// api/xtream-sync.js
//
// Sincroniza filmes e séries de uma API Xtream (como MegaEmbed) com o catálogo StreamFlixVIP.
// Diferente do iptv-sync.js que baixa M3U, este faz requisições diretas aos endpoints da API Xtream.
//
// Endpoints utilizados:
// - get_vod_streams: lista de filmes
// - get_series: lista de séries
// - get_vod_info: detalhes do filme (sinopse, etc.)
// - get_series_info: detalhes da série (episódios, etc.)
//
// Roda por orçamento de tempo (60s na Vercel Hobby), processando filmes até o tempo acabar.
// Próxima execução continua de onde parou (sync_cursor).

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

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

// Busca na TMDB para enriquecer metadados
async function searchTmdbMovie(title, year, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/movie');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', title);
  url.searchParams.set('language', 'pt-BR');
  if (year) url.searchParams.set('primary_release_year', String(year));

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 5000);
  try {
    const res = await fetch(url.toString(), { signal: controller.signal });
    const data = await res.json();
    return data.results?.[0] || null;
  } catch {
    return null;
  } finally {
    clearTimeout(timeoutId);
  }
}

// Busca na TMDB para séries
async function searchTmdbSeries(title, year, tmdbApiKey) {
  const url = new URL('https://api.themoviedb.org/3/search/tv');
  url.searchParams.set('api_key', tmdbApiKey);
  url.searchParams.set('query', title);
  url.searchParams.set('language', 'pt-BR');
  if (year) url.searchParams.set('first_air_date_year', String(year));

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 5000);
  try {
    const res = await fetch(url.toString(), { signal: controller.signal });
    const data = await res.json();
    return data.results?.[0] || null;
  } catch {
    return null;
  } finally {
    clearTimeout(timeoutId);
  }
}

// Faz requisição para a API Xtream
async function xtreamFetch(baseUrl, username, password, action, params = {}) {
  const url = new URL(baseUrl);
  url.searchParams.set('username', username);
  url.searchParams.set('password', password);
  if (action) url.searchParams.set('action', action);
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null) url.searchParams.set(k, String(v));
  });

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 10000);
  try {
    const res = await fetch(url.toString(), { signal: controller.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } finally {
    clearTimeout(timeoutId);
  }
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada' }); return; }

  const tmdbApiKey = process.env.TMDB_API_KEY;
  if (!tmdbApiKey) { res.status(500).json({ error: 'TMDB_API_KEY não configurada' }); return; }

  const { sourceId } = req.body;
  if (!sourceId) { res.status(400).json({ error: 'sourceId obrigatório' }); return; }

  try {
    // Buscar a fonte Xtream
    const sources = await sbSelect(serviceKey, 'iptv_sources', `id=eq.${encodeURIComponent(sourceId)}&select=*`);
    if (!sources.length) { res.status(404).json({ error: 'Fonte não encontrada' }); return; }
    const source = sources[0];

    const baseUrl = source.xtream_host;
    const username = source.xtream_user;
    const password = source.xtream_pass;
    const sourceName = source.name;
    const timeBudgetMs = 55000; // 55s de 60s disponíveis
    const startTime = Date.now();

    let matched = 0, unmatched = 0, errors = 0;
    const toUpsert = [];

    // 1. Sincronizar filmes (VOD)
    console.log(`[xtream-sync] Iniciando sincronização de filmes para "${sourceName}"`);
    try {
      const vods = await xtreamFetch(baseUrl, username, password, 'get_vod_streams');
      if (Array.isArray(vods)) {
        for (const vod of vods) {
          if (Date.now() - startTime > timeBudgetMs) {
            console.log(`[xtream-sync] Tempo expirado, parando sincronização`);
            break;
          }

          const title = vod.name || 'Sem título';
          const poster = vod.stream_icon || '';
          const streamId = vod.stream_id;
          const extension = vod.container_extension || 'mp4';
          const playbackUrl = `${baseUrl}/movie/${username}/${password}/${streamId}.${extension}`;

          // Buscar na TMDB
          let tmdbId = null, tmdbData = null;
          try {
            tmdbData = await searchTmdbMovie(title, null, tmdbApiKey);
            if (tmdbData) {
              tmdbId = tmdbData.id;
              matched++;
            } else {
              unmatched++;
            }
          } catch (e) {
            errors++;
            console.error(`[xtream-sync] Erro ao buscar TMDB para "${title}":`, e.message);
          }

          if (tmdbId) {
            toUpsert.push({
              tmdb_id: tmdbId,
              media_type: 'movie',
              title: title,
              poster_path: poster,
              source_label: sourceName,
              source_url: playbackUrl,
              is_direct_playable: true,
              priority: source.priority || 10,
            });
          }
        }
      }
    } catch (e) {
      console.error(`[xtream-sync] Erro ao buscar filmes:`, e.message);
      errors++;
    }

    // 2. Sincronizar séries
    console.log(`[xtream-sync] Iniciando sincronização de séries para "${sourceName}"`);
    try {
      const series = await xtreamFetch(baseUrl, username, password, 'get_series');
      if (Array.isArray(series)) {
        for (const seriesItem of series) {
          if (Date.now() - startTime > timeBudgetMs) {
            console.log(`[xtream-sync] Tempo expirado, parando sincronização`);
            break;
          }

          const title = seriesItem.name || 'Sem título';
          const poster = seriesItem.cover || '';
          const seriesId = seriesItem.series_id;

          // Buscar detalhes da série (episódios)
          let tmdbId = null, seriesInfo = null;
          try {
            seriesInfo = await xtreamFetch(baseUrl, username, password, 'get_series_info', { series_id: seriesId });
            
            // Buscar na TMDB
            const tmdbData = await searchTmdbSeries(title, null, tmdbApiKey);
            if (tmdbData) {
              tmdbId = tmdbData.id;
              matched++;
            } else {
              unmatched++;
            }
          } catch (e) {
            errors++;
            console.error(`[xtream-sync] Erro ao buscar série "${title}":`, e.message);
            continue;
          }

          if (tmdbId && seriesInfo && seriesInfo.episodes) {
            // Processar episódios
            for (const [season, episodes] of Object.entries(seriesInfo.episodes)) {
              for (const ep of episodes) {
                const episodeId = ep.id;
                const extension = ep.container_extension || 'mp4';
                const playbackUrl = `${baseUrl}/series/${username}/${password}/${episodeId}.${extension}`;

                toUpsert.push({
                  tmdb_id: tmdbId,
                  media_type: 'tv',
                  title: title,
                  poster_path: poster,
                  season: parseInt(season, 10),
                  episode: ep.episode_num || 0,
                  source_label: sourceName,
                  source_url: playbackUrl,
                  is_direct_playable: true,
                  priority: source.priority || 10,
                });
              }
            }
          }
        }
      }
    } catch (e) {
      console.error(`[xtream-sync] Erro ao buscar séries:`, e.message);
      errors++;
    }

    // 3. Upsert em lotes
    if (toUpsert.length > 0) {
      console.log(`[xtream-sync] Inserindo ${toUpsert.length} fontes`);
      await sbUpsert(serviceKey, 'vip_sources', toUpsert, 'tmdb_id,media_type,season,episode,source_label');
    }

    // 4. Atualizar status da fonte
    const stats = { matched, unmatched, errors, total: matched + unmatched };
    await sbUpdate(
      serviceKey,
      'iptv_sources',
      `id=eq.${encodeURIComponent(sourceId)}`,
      {
        sync_phase: 'done',
        last_synced_at: new Date().toISOString(),
        last_sync_stats: stats,
      }
    );

    console.log(`[xtream-sync] Sincronização concluída: ${matched} encontrados, ${unmatched} não encontrados, ${errors} erros`);
    res.status(200).json({ success: true, stats });
  } catch (err) {
    console.error('[xtream-sync] Erro:', err);
    await sbUpdate(
      serviceKey,
      'iptv_sources',
      `id=eq.${encodeURIComponent(sourceId)}`,
      {
        sync_phase: 'error',
        last_sync_stats: { error: String(err) },
      }
    ).catch(() => {});
    res.status(502).json({ error: 'Erro na sincronização', detail: String(err) });
  }
};
