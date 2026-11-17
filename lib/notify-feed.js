// lib/notify-feed.js — monta o "feed" que o app do celular consulta de tempos em
// tempos (WorkManager) para mostrar notificação de verdade no aparelho:
//  - broadcasts: avisos do painel marcados "enviar como notificação" (push=true)
//  - episodes: último episódio lançado das séries que o aparelho segue/assiste
// Sem Firebase: o celular pergunta, o servidor responde. O app guarda o que já
// avisou, então aqui só filtramos por data (since) e pela janela de lançamento.

const TMDB_BASE = 'https://api.themoviedb.org/3';
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const TV_TTL_MS = 6 * 60 * 60 * 1000; // série: 6 h de cache (TMDB muda pouco)
const FEED_TTL_MS = 2 * 60 * 1000; // avisos: 2 min
const MAX_SERIES = 60;
const EPISODE_WINDOW_DAYS = 4; // só avisa episódio que saiu nos últimos 4 dias

const tvCache = new Map();
let feedCache = { at: 0, rows: null };

function parseIds(raw) {
  return [...new Set(String(raw || '')
    .split(',')
    .map((s) => Number(String(s).trim()))
    .filter((n) => Number.isInteger(n) && n > 0 && n < 10_000_000))].slice(0, MAX_SERIES);
}

async function fetchJson(url, opts = {}, timeoutMs = 8000) {
  const c = new AbortController();
  const t = setTimeout(() => c.abort(), timeoutMs);
  try {
    const r = await fetch(url, { ...opts, signal: c.signal });
    if (!r.ok) return null;
    return await r.json();
  } catch (_) {
    return null;
  } finally {
    clearTimeout(t);
  }
}

async function lastEpisode(tmdbId, { fetcher = fetchJson, now = Date.now() } = {}) {
  const hit = tvCache.get(tmdbId);
  if (hit && now - hit.at < TV_TTL_MS) return hit.value;
  const key = process.env.TMDB_API_KEY;
  if (!key) return null;
  const j = await fetcher(`${TMDB_BASE}/tv/${tmdbId}?api_key=${encodeURIComponent(key)}&language=pt-BR`);
  let value = null;
  if (j && j.last_episode_to_air) {
    const e = j.last_episode_to_air;
    value = {
      tmdbId,
      name: j.name || j.original_name || '',
      posterPath: j.poster_path || null,
      season: Number(e.season_number) || 0,
      episode: Number(e.episode_number) || 0,
      episodeName: e.name || '',
      airDate: e.air_date || null,
    };
  }
  tvCache.set(tmdbId, { at: now, value });
  if (tvCache.size > 5000) tvCache.clear();
  return value;
}

function isRecent(airDate, now = Date.now()) {
  if (!airDate) return false;
  const t = Date.parse(`${airDate}T12:00:00Z`);
  if (!Number.isFinite(t)) return false;
  const age = now - t;
  // aceita até 1 dia "no futuro" (fuso horário) e até N dias atrás
  return age > -24 * 3600 * 1000 && age <= EPISODE_WINDOW_DAYS * 24 * 3600 * 1000;
}

function mapBroadcast(a) {
  return {
    id: String(a.id),
    type: a.type || 'info',
    title: String(a.title || ''),
    body: String(a.body || ''),
    createdAt: a.created_at || null,
    linkTmdbId: a.link_tmdb_id != null ? Number(a.link_tmdb_id) : null,
    linkMediaType: a.link_media_type || null,
  };
}

async function pushBroadcasts({ fetcher = fetchJson, now = Date.now() } = {}) {
  if (feedCache.rows && now - feedCache.at < FEED_TTL_MS) return feedCache.rows;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_ANON_KEY;
  if (!key) return [];
  const since = new Date(now - 14 * 24 * 3600 * 1000).toISOString();
  const rows = await fetcher(
    `${SUPABASE_URL}/rest/v1/app_announcements?select=id,type,title,body,link_tmdb_id,link_media_type,created_at` +
      `&active=eq.true&push=eq.true&created_at=gte.${encodeURIComponent(since)}&order=created_at.desc&limit=20`,
    { headers: { apikey: key, Authorization: `Bearer ${key}` } },
  );
  const list = Array.isArray(rows) ? rows.filter((a) => a && a.title && a.body).map(mapBroadcast) : [];
  feedCache = { at: now, rows: list };
  return list;
}

async function build(query = {}, deps = {}) {
  const now = deps.now || Date.now();
  const sinceMs = Number(query.since) || 0;
  const ids = parseIds(query.tv);
  const all = await pushBroadcasts({ ...deps, now });
  // primeira consulta do aparelho (since=0): só o mais recente das últimas 48 h,
  // para não despejar avisos antigos de uma vez
  const broadcasts = sinceMs > 0
    ? all.filter((a) => Date.parse(a.createdAt || 0) > sinceMs).slice(0, 3)
    : all.filter((a) => now - Date.parse(a.createdAt || 0) < 48 * 3600 * 1000).slice(0, 1);
  const episodes = [];
  // poucas por vez para não estourar a TMDB
  for (let i = 0; i < ids.length; i += 6) {
    const chunk = await Promise.all(ids.slice(i, i + 6).map((id) => lastEpisode(id, { ...deps, now })));
    for (const e of chunk) if (e && e.season > 0 && e.episode > 0 && isRecent(e.airDate, now)) episodes.push(e);
  }
  return { now, broadcasts, episodes };
}

function _reset() { tvCache.clear(); feedCache = { at: 0, rows: null }; }

module.exports = { build, parseIds, isRecent, lastEpisode, _reset };
