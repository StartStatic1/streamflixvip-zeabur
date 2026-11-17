// lib/retro.js — "Sua Retrospectiva": números do ano a partir do que o app realmente registrou.
// Fontes: watch_history (histórico que não some, desde 11/2026) + watch_progress (continuar assistindo)
// + favorites (Minha Lista) + title_comments. Tempo = soma da posição máxima salva em cada filme/episódio
// (é um mínimo: o app só salva o progresso de tempos em tempos).
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const TMDB_BASE = 'https://api.themoviedb.org/3';

function hdrs(extra) {
  const k = process.env.SUPABASE_SERVICE_ROLE_KEY || '';
  return { apikey: k, Authorization: `Bearer ${k}`, ...(extra || {}) };
}
async function rows(path) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { headers: hdrs() });
  if (!r.ok) return null;
  const j = await r.json();
  return Array.isArray(j) ? j : null;
}

const genreCache = new Map(); // "movie:123" -> ['Ação', ...]
async function genresOf(mediaType, tmdbId) {
  const key = `${mediaType}:${tmdbId}`;
  if (genreCache.has(key)) return genreCache.get(key);
  const apiKey = process.env.TMDB_API_KEY;
  if (!apiKey) return [];
  try {
    const t = mediaType === 'tv' ? 'tv' : 'movie';
    const r = await fetch(`${TMDB_BASE}/${t}/${encodeURIComponent(tmdbId)}?api_key=${encodeURIComponent(apiKey)}&language=pt-BR`,
      { signal: AbortSignal.timeout(6000) });
    if (!r.ok) return [];
    const j = await r.json();
    const g = Array.isArray(j.genres) ? j.genres.map((x) => x && x.name).filter(Boolean) : [];
    genreCache.set(key, g);
    if (genreCache.size > 20000) genreCache.clear();
    return g;
  } catch (_) {
    return [];
  }
}

/** "Reacher — S4E3 — S4E5" → "Reacher" (o app grava o episódio junto do nome). */
function cleanTitle(t) {
  if (!t) return t;
  const c = String(t).replace(/(\s+[—–-]\s*[ST]\d+\s*E\d+)+\s*$/i, '').trim();
  return c || String(t);
}

/** Junta histórico + progresso (chave = título/temporada/episódio), sem contar duas vezes. */
function mergeWatch(history, progress, yearStart, yearEnd) {
  const m = new Map();
  const inYear = (iso) => { const t = new Date(iso).getTime(); return t >= yearStart && t < yearEnd; };
  for (const h of history || []) {
    if (!inYear(h.last_seen_at)) continue;
    const k = `${h.tmdb_id}|${h.media_type}|${h.season}|${h.episode}`;
    m.set(k, {
      tmdbId: h.tmdb_id, mediaType: h.media_type, season: h.season || 0, episode: h.episode || 0,
      title: cleanTitle(h.title) || null, poster: h.poster_path || null,
      seconds: Number(h.max_position_seconds) || 0, duration: Number(h.duration_seconds) || 0,
      first: h.first_seen_at, last: h.last_seen_at,
    });
  }
  for (const p of progress || []) {
    if (!inYear(p.updated_at)) continue;
    const k = `${p.tmdb_id}|${p.media_type}|${p.season}|${p.episode}`;
    const cur = m.get(k);
    const sec = Number(p.position_seconds) || 0;
    if (cur) {
      cur.seconds = Math.max(cur.seconds, sec);
      if (!cur.title && p.title) cur.title = cleanTitle(p.title);
      if (!cur.poster && p.poster_path) cur.poster = p.poster_path;
    } else {
      m.set(k, {
        tmdbId: p.tmdb_id, mediaType: p.media_type, season: p.season || 0, episode: p.episode || 0,
        title: cleanTitle(p.title) || null, poster: p.poster_path || null, seconds: sec,
        duration: Number(p.duration_seconds) || 0, first: p.updated_at, last: p.updated_at,
      });
    }
  }
  // posição nunca passa da duração conhecida
  for (const v of m.values()) if (v.duration > 0) v.seconds = Math.min(v.seconds, v.duration);
  return [...m.values()];
}

async function build(userId, year) {
  const y = Number(year) || new Date().getFullYear();
  const yearStart = Date.UTC(y, 0, 1, 3); // 00:00 de 1/jan em Brasília
  const yearEnd = Date.UTC(y + 1, 0, 1, 3);
  const uid = encodeURIComponent(userId);
  const from = encodeURIComponent(new Date(yearStart).toISOString());
  const to = encodeURIComponent(new Date(yearEnd).toISOString());

  const [history, progress, favs, comments] = await Promise.all([
    rows(`watch_history?user_id=eq.${uid}&select=tmdb_id,media_type,season,episode,title,poster_path,max_position_seconds,duration_seconds,first_seen_at,last_seen_at&limit=5000`),
    rows(`watch_progress?user_id=eq.${uid}&select=tmdb_id,media_type,season,episode,title,poster_path,position_seconds,duration_seconds,updated_at&limit=5000`),
    rows(`favorites?user_id=eq.${uid}&created_at=gte.${from}&created_at=lt.${to}&select=tmdb_id,media_type,title,poster_path,created_at&order=created_at.desc&limit=500`),
    rows(`title_comments?user_id=eq.${uid}&created_at=gte.${from}&created_at=lt.${to}&is_hidden=is.false&select=id&limit=5000`),
  ]);

  const items = mergeWatch(history, progress, yearStart, yearEnd);

  // por título (filme inteiro ou série inteira)
  const byTitle = new Map();
  for (const it of items) {
    const k = `${it.mediaType}:${it.tmdbId}`;
    const t = byTitle.get(k) || { tmdbId: it.tmdbId, mediaType: it.mediaType, title: it.title, poster: it.poster, seconds: 0, episodes: new Set() };
    t.seconds += it.seconds;
    if (!t.title && it.title) t.title = it.title;
    if (!t.poster && it.poster) t.poster = it.poster;
    if (it.mediaType === 'tv' && it.episode > 0) t.episodes.add(`${it.season}x${it.episode}`);
    byTitle.set(k, t);
  }
  const titles = [...byTitle.values()].map((t) => ({ ...t, episodes: t.episodes.size }));
  titles.sort((a, b) => b.seconds - a.seconds);

  const totalSeconds = titles.reduce((a, t) => a + t.seconds, 0);
  const movies = titles.filter((t) => t.mediaType !== 'tv');
  const series = titles.filter((t) => t.mediaType === 'tv');
  const episodes = series.reduce((a, t) => a + t.episodes, 0);

  // gêneros dos 25 títulos mais vistos (peso = tempo)
  const gScore = new Map();
  const top = titles.slice(0, 25);
  const gl = await Promise.all(top.map((t) => genresOf(t.mediaType, t.tmdbId)));
  top.forEach((t, i) => {
    for (const g of gl[i]) gScore.set(g, (gScore.get(g) || 0) + Math.max(t.seconds, 60));
  });
  const topGenres = [...gScore.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3).map(([name]) => name);

  const pick = (t) => ({ tmdbId: t.tmdbId, mediaType: t.mediaType, title: t.title || 'Sem título', posterPath: t.poster || null,
    minutes: Math.round(t.seconds / 60), episodes: t.episodes || 0 });

  const topSeries = [...series].sort((a, b) => (b.episodes - a.episodes) || (b.seconds - a.seconds)).slice(0, 3).map(pick);
  const firstSeen = items.reduce((min, it) => {
    const t = new Date(it.first || it.last).getTime();
    return t && t < min ? t : min;
  }, Infinity);

  return {
    year: y,
    hasData: titles.length > 0 || (favs && favs.length > 0) || (comments && comments.length > 0),
    titlesWatched: titles.length,
    moviesWatched: movies.length,
    seriesWatched: series.length,
    episodesWatched: episodes,
    // minutos registrados (mínimo). O app mostra horas só se passar de 1h.
    minutesWatched: Math.round(totalSeconds / 60),
    topGenres,
    topSeries,
    topTitles: titles.slice(0, 5).map(pick),
    favoritesAdded: favs ? favs.length : 0,
    favoriteTitles: (favs || []).slice(0, 3).map((f) => ({ tmdbId: f.tmdb_id, mediaType: f.media_type, title: f.title || 'Sem título', posterPath: f.poster_path || null })),
    comments: comments ? comments.length : 0,
    dataSince: Number.isFinite(firstSeen) ? new Date(firstSeen).toISOString() : null,
    note: 'Baseado no que ficou salvo no app (continuar assistindo, Minha Lista e comentários). O tempo é um mínimo.',
  };
}

module.exports = { build, mergeWatch, cleanTitle };
