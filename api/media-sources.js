// api/media-sources.js
//
// Resolve fontes de filme/série (vip_sources) no SERVIDOR.
// O app antigo lia vip_sources direto no Supabase com anon key —
// qualquer MOD conseguia as URLs sem login real.
//
// Soft (padrão): devolve fontes sem exigir token (site/app antigo ok).
// Hard REQUIRE_AUTH_MEDIA=1: exige JWT Supabase válido.
//   - Título com vip_lock (vip_titles): exige VIP real no banco
//   - Demais títulos: login free basta (anúncio/espera continua no client)
//
// GET /api/media-sources?tmdb_id=123&type=movie
// GET /api/media-sources?tmdb_id=123&type=tv&season=1&episode=2

const { resolveVipAccess } = require('../lib/vip-gate');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

function isMediaAuthHard() {
  const v = String(process.env.REQUIRE_AUTH_MEDIA || '').trim().toLowerCase();
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

function sbHeaders(serviceKey) {
  return {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  };
}

async function loadVipTitleConfig(serviceKey, tmdbId, mediaType) {
  try {
    const url =
      `${SUPABASE_URL}/rest/v1/vip_titles?tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
      `&media_type=eq.${encodeURIComponent(mediaType)}` +
      `&select=vip_lock,vip_free_episode_limit`;
    const r = await fetch(url, { headers: sbHeaders(serviceKey) });
    if (!r.ok) return null;
    const rows = await r.json();
    return Array.isArray(rows) && rows.length ? rows[0] : null;
  } catch (e) {
    console.warn('[media-sources] vip_titles:', e.message);
    return null;
  }
}

function titleRequiresVip(config, episodeNumber) {
  if (!config) return false;
  if (config.vip_lock === true) return true;
  const limit = config.vip_free_episode_limit;
  if (limit != null && episodeNumber != null && Number.isFinite(episodeNumber)) {
    return episodeNumber > Number(limit);
  }
  return false;
}

async function loadSources(serviceKey, tmdbId, mediaType, season, episode) {
  let q =
    `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
    `&media_type=eq.${encodeURIComponent(mediaType)}` +
    `&is_active=eq.true` +
    `&select=source_url,source_label,priority` +
    `&order=priority.desc`;

  if (mediaType === 'tv') {
    if (season != null) q += `&season=eq.${season}`;
    if (episode != null) q += `&episode=eq.${episode}`;
  } else {
    q += `&season=is.null`;
  }

  const r = await fetch(q, { headers: sbHeaders(serviceKey) });
  if (!r.ok) {
    const body = await r.text().catch(() => '');
    throw new Error(`vip_sources ${r.status}: ${body.slice(0, 180)}`);
  }
  const rows = await r.json();
  return Array.isArray(rows) ? rows : [];
}

async function handler(req, res) {
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Método não permitido', sources: [] });
    return;
  }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'Servidor sem SUPABASE_SERVICE_ROLE_KEY', sources: [] });
    return;
  }

  const tmdbId = String(req.query.tmdb_id || req.query.tmdb || '').trim();
  const mediaType = String(req.query.type || req.query.media_type || 'movie').trim().toLowerCase();
  const season = req.query.season != null && req.query.season !== '' ? parseInt(req.query.season, 10) : null;
  const episode =
    req.query.episode != null && req.query.episode !== '' ? parseInt(req.query.episode, 10) : null;

  if (!tmdbId) {
    res.status(400).json({ error: 'tmdb_id obrigatório', sources: [] });
    return;
  }
  if (mediaType !== 'movie' && mediaType !== 'tv') {
    res.status(400).json({ error: 'type deve ser movie ou tv', sources: [] });
    return;
  }

  const hard = isMediaAuthHard();
  const access = await resolveVipAccess(req, serviceKey);
  // resolveVipAccess usa REQUIRE_VIP_LIVE_TV para o flag hard interno;
  // aqui usamos REQUIRE_AUTH_MEDIA separado.
  const loggedIn = access.source === 'jwt' || access.source === 'userId' || access.source === 'deviceId';

  const vipConfig = await loadVipTitleConfig(serviceKey, tmdbId, mediaType);
  const needsVip = titleRequiresVip(vipConfig, mediaType === 'tv' ? episode : null);

  // Sempre respeita vip_titles do painel (lock total ou grátis até EP N).
  // Soft (REQUIRE_AUTH_MEDIA off) só dispensa login em título NÃO-VIP.
  if (needsVip && !access.isVip) {
    console.warn(
      `[media-sources] BLOCK vip_lock tmdb=${tmdbId} ep=${episode} user=${access.userId || '-'}`,
    );
    res.status(403).json({
      error: 'VIP necessário para este título/episódio.',
      code: 'VIP_REQUIRED',
      sources: [],
      vipConfig: vipConfig
        ? {
            vip_lock: !!vipConfig.vip_lock,
            vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null,
          }
        : null,
      requiresVip: true,
      isVip: false,
    });
    return;
  }

  if (hard) {
    if (!loggedIn) {
      console.warn(`[media-sources] BLOCK no-auth tmdb=${tmdbId} type=${mediaType}`);
      res.status(401).json({
        error: 'Login necessário para assistir.',
        code: 'AUTH_REQUIRED',
        sources: [],
      });
      return;
    }
    console.log(
      `[media-sources] OK hard tmdb=${tmdbId} type=${mediaType} source=${access.source} vip=${access.isVip}`,
    );
  } else {
    console.log(
      `[media-sources] soft-pass tmdb=${tmdbId} type=${mediaType} source=${access.source} (REQUIRE_AUTH_MEDIA off)`,
    );
  }

  try {
    let sources = await loadSources(serviceKey, tmdbId, mediaType, season, episode);
    // Mesma prioridade visual do app: MegaEmbed VIP no topo
    sources = sources.slice().sort((a, b) => {
      const aVip = a.source_label === 'MegaEmbed VIP' ? 1 : 0;
      const bVip = b.source_label === 'MegaEmbed VIP' ? 1 : 0;
      return bVip - aVip;
    });

    res.status(200).json({
      sources,
      vipConfig: vipConfig
        ? {
            vip_lock: !!vipConfig.vip_lock,
            vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null,
          }
        : null,
      requiresVip: needsVip,
      isVip: access.isVip,
    });
  } catch (err) {
    console.error('[media-sources]', err);
    res.status(500).json({ error: err.message || 'Erro ao carregar fontes', sources: [] });
  }
}

module.exports = handler;
