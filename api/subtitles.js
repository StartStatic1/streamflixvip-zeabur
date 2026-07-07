// /api/subtitles.js
//
// Endpoint UNIFICADO de legendas (busca + download), fundindo o que antes
// eram dois arquivos separados (subtitle-search.js e subtitle-download.js).
// Motivo da fusão: o plano Hobby da Vercel permite no máximo 12 Serverless
// Functions por deploy — cada arquivo em api/ conta como uma função, e ao
// somar o endpoint novo de embed (/api/embed-config) o projeto passou de 12.
// Fundir os dois endpoints de legenda em um só (roteado por ?action=) foi
// a forma mais simples de voltar pro limite sem perder nenhuma funcionalidade.
//
// Uso no front-end:
//   GET /api/subtitles?action=search&tmdb_id=<id>&season=<n>&episode=<n>
//   GET /api/subtitles?action=download&file_id=<id>&tmdb_id=<id>&media_type=movie|tv&season=<n>&episode=<n>

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

// ════════════════════════════════════════════════════════════
// BUSCA (era subtitle-search.js)
// ════════════════════════════════════════════════════════════
async function handleSearch(req, res) {
  const apiKey = process.env.OPENSUBTITLES_API_KEY;
  if (!apiKey) {
    res.status(500).json({ error: 'OPENSUBTITLES_API_KEY não configurada nas variáveis de ambiente da Vercel.' });
    return;
  }

  const { tmdb_id, season, episode } = req.query;
  if (!tmdb_id) {
    res.status(400).json({ error: 'Parâmetro tmdb_id obrigatório.' });
    return;
  }

  const params = new URLSearchParams({ languages: 'pt-br', tmdb_id });
  if (season) params.set('season_number', season);
  if (episode) params.set('episode_number', episode);

  try {
    const r = await fetch(`https://api.opensubtitles.com/api/v1/subtitles?${params.toString()}`, {
      headers: {
        'Api-Key': apiKey,
        'User-Agent': 'streamflixvip v1',
      },
    });
    const data = await r.json();

    if (!r.ok) {
      res.status(r.status).json({ error: data.message || 'Erro na busca da OpenSubtitles.' });
      return;
    }

    const results = (data.data || [])
      .map((item) => ({
        id: item.id,
        release: item.attributes?.release || item.attributes?.feature_details?.title || 'Legenda',
        downloads: item.attributes?.download_count || 0,
        fps: item.attributes?.fps || null,
        hd: item.attributes?.hd || false,
        file_id: item.attributes?.files?.[0]?.file_id,
      }))
      .filter((r) => r.file_id)
      .sort((a, b) => b.downloads - a.downloads); // mais baixadas primeiro = geralmente mais confiáveis

    res.status(200).json({ results });
  } catch (e) {
    res.status(502).json({ error: 'Falha ao contatar a OpenSubtitles: ' + e.message });
  }
}

// ════════════════════════════════════════════════════════════
// DOWNLOAD (era subtitle-download.js)
//
// Fluxo em 2 camadas:
// 1) CACHE (Supabase): se alguém já baixou legenda pra esse tmdb_id/temporada/
//    episódio antes, devolve na hora, sem gastar cota da OpenSubtitles.
// 2) LOGIN + DOWNLOAD (OpenSubtitles): se não tem no cache, loga com usuário/
//    senha (conta grátis) antes de baixar — sobe a cota de 5 pra 20/dia.
// ════════════════════════════════════════════════════════════
async function getCachedSubtitle({ tmdbId, mediaType, season, episode, language, svcHeaders }) {
  const params = new URLSearchParams({
    tmdb_id: `eq.${tmdbId}`,
    media_type: `eq.${mediaType}`,
    season: `eq.${season}`,
    episode: `eq.${episode}`,
    language: `eq.${language}`,
    select: 'vtt_content,release_name',
    limit: '1',
  });
  const r = await fetch(`${SUPABASE_URL}/rest/v1/subtitle_cache?${params.toString()}`, { headers: svcHeaders });
  if (!r.ok) return null;
  const rows = await r.json();
  return rows[0] || null;
}

async function saveCachedSubtitle({ tmdbId, mediaType, season, episode, language, vttContent, releaseName, svcHeaders }) {
  try {
    await fetch(`${SUPABASE_URL}/rest/v1/subtitle_cache`, {
      method: 'POST',
      headers: {
        ...svcHeaders,
        'Content-Type': 'application/json',
        'Prefer': 'resolution=merge-duplicates',
      },
      body: JSON.stringify([{
        tmdb_id: tmdbId,
        media_type: mediaType,
        season,
        episode,
        language,
        vtt_content: vttContent,
        release_name: releaseName || null,
      }]),
    });
  } catch (e) {
    // Falha ao salvar cache não deve quebrar a resposta pro usuário —
    // ele já tem a legenda na tela, só não vai ficar cacheada dessa vez.
    console.error('Falha ao salvar cache de legenda:', e.message);
  }
}

async function loginOpenSubtitles(apiKey) {
  const username = process.env.OPENSUBTITLES_USERNAME;
  const password = process.env.OPENSUBTITLES_PASSWORD;
  if (!username || !password) return null; // segue sem login (cota anônima: 5/dia)

  try {
    const r = await fetch('https://api.opensubtitles.com/api/v1/login', {
      method: 'POST',
      headers: {
        'Api-Key': apiKey,
        'User-Agent': 'streamflixvip v1',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    });
    if (!r.ok) return null;
    const data = await r.json();
    return data.token || null;
  } catch (e) {
    return null; // login falhou — segue sem token (degrada pra cota anônima)
  }
}

async function handleDownload(req, res) {
  const apiKey = process.env.OPENSUBTITLES_API_KEY;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!apiKey) {
    res.status(500).json({ error: 'OPENSUBTITLES_API_KEY não configurada nas variáveis de ambiente da Vercel.' });
    return;
  }

  const { file_id, tmdb_id, media_type, season, episode } = req.query;
  if (!file_id) {
    res.status(400).json({ error: 'Parâmetro file_id obrigatório.' });
    return;
  }

  // Identificação pro cache. Filmes usam season/episode = 0 (não null) —
  // assim a constraint UNIQUE do Postgres funciona (NULL nunca é igual a NULL).
  const mediaType = media_type === 'tv' || media_type === 'anime' ? 'tv' : 'movie';
  const seasonNum = mediaType === 'tv' ? Number(season) || 0 : 0;
  const episodeNum = mediaType === 'tv' ? Number(episode) || 0 : 0;
  const language = 'pt-br';
  const canUseCache = !!(serviceKey && tmdb_id);

  const svcHeaders = serviceKey ? {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  } : null;

  // ── 1) Tenta o cache primeiro ──
  if (canUseCache) {
    try {
      const cached = await getCachedSubtitle({
        tmdbId: Number(tmdb_id), mediaType, season: seasonNum, episode: episodeNum, language, svcHeaders,
      });
      if (cached) {
        res.status(200).json({ content: cached.vtt_content, remaining: null, from_cache: true });
        return;
      }
    } catch (e) {
      console.error('Erro ao consultar cache de legenda:', e.message);
    }
  }

  // ── 2) Login (sobe a cota de 5 para 20/dia) + download ──
  try {
    const token = await loginOpenSubtitles(apiKey);

    const downloadHeaders = {
      'Api-Key': apiKey,
      'User-Agent': 'streamflixvip v1',
      'Content-Type': 'application/json',
    };
    if (token) downloadHeaders['Authorization'] = `Bearer ${token}`;

    const linkRes = await fetch('https://api.opensubtitles.com/api/v1/download', {
      method: 'POST',
      headers: downloadHeaders,
      body: JSON.stringify({ file_id: Number(file_id) }),
    });
    const linkData = await linkRes.json();

    if (!linkRes.ok) {
      res.status(linkRes.status).json({ error: linkData.message || 'Erro ao gerar link de download.' });
      return;
    }

    const fileRes = await fetch(linkData.link);
    const buffer = Buffer.from(await fileRes.arrayBuffer());

    let text;
    try {
      text = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
    } catch (e) {
      text = new TextDecoder('windows-1252').decode(buffer);
    }

    const vttContent = text.trim().toLowerCase().startsWith('webvtt')
      ? text
      : 'WEBVTT\n\n' + text.replace(/\r+/g, '').replace(/^\d+\n/gm, '').replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g, '$1.$2');

    if (canUseCache) {
      await saveCachedSubtitle({
        tmdbId: Number(tmdb_id), mediaType, season: seasonNum, episode: episodeNum, language,
        vttContent, releaseName: linkData.file_name || null, svcHeaders,
      });
    }

    res.status(200).json({ content: vttContent, remaining: linkData.remaining ?? null, from_cache: false });
  } catch (e) {
    res.status(502).json({ error: 'Falha ao baixar a legenda: ' + e.message });
  }
}

// ── Roteador ──
module.exports = async function handler(req, res) {
  const action = req.query.action;
  if (action === 'search') { await handleSearch(req, res); return; }
  if (action === 'download') { await handleDownload(req, res); return; }
  res.status(400).json({ error: 'Informe action=search ou action=download' });
};
