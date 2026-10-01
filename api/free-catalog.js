/**
 * GET /api/free-catalog
 * Lista filmes marcados is_free na Area Free (para faixa da Home).
 * Query: limit (default 50, max 80)
 */
const SUPABASE_URL = process.env.SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || '';
const SUPABASE_SERVICE_KEY =
  process.env.SUPABASE_SERVICE_ROLE_KEY ||
  process.env.SUPABASE_SERVICE_KEY ||
  '';
const TMDB_KEY = process.env.TMDB_API_KEY || process.env.TMDB_KEY || '';

function sbHeaders() {
  return {
    apikey: SUPABASE_SERVICE_KEY,
    Authorization: 'Bearer ' + SUPABASE_SERVICE_KEY,
  };
}

async function handler(req, res) {
  if (req.method === 'OPTIONS') {
    res.status(204).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }
  if (!SUPABASE_URL || !SUPABASE_SERVICE_KEY) {
    res.status(500).json({ error: 'Supabase nao configurado', items: [] });
    return;
  }

  const limit = Math.min(80, Math.max(1, parseInt(String(req.query.limit || '50'), 10) || 50));

  try {
    const url =
      SUPABASE_URL +
      '/rest/v1/vip_titles?is_free=eq.true&media_type=eq.movie&select=tmdb_id&order=tmdb_id.desc&limit=' +
      limit;
    const r = await fetch(url, { headers: sbHeaders() });
    const rows = await r.json();
    if (!r.ok) {
      console.warn('[free-catalog]', r.status, rows);
      res.status(200).json({
        items: [],
        error: 'Coluna is_free ausente? Rode o SQL scripts/sql_free_area.sql no Supabase.',
        needsMigration: true,
      });
      return;
    }
    const ids = (Array.isArray(rows) ? rows : [])
      .map((x) => Number(x.tmdb_id))
      .filter((n) => Number.isFinite(n) && n > 0);

    if (!ids.length) {
      res.status(200).json({ items: [], total: 0 });
      return;
    }

    const items = [];
    if (TMDB_KEY) {
      for (const id of ids.slice(0, limit)) {
        try {
          const tr = await fetch(
            'https://api.themoviedb.org/3/movie/' + id + '?api_key=' + TMDB_KEY + '&language=pt-BR',
          );
          if (!tr.ok) continue;
          const m = await tr.json();
          items.push({
            id: m.id,
            title: m.title || m.original_title,
            poster_path: m.poster_path,
            backdrop_path: m.backdrop_path,
            vote_average: m.vote_average,
            release_date: m.release_date,
            media_type: 'movie',
          });
        } catch (_) {}
      }
    } else {
      for (const id of ids) {
        items.push({ id: id, title: 'Filme ' + id, media_type: 'movie' });
      }
    }

    res.status(200).json({ items: items, total: items.length });
  } catch (e) {
    console.error('[free-catalog]', e);
    res.status(500).json({ error: e.message || 'Erro', items: [] });
  }
}

module.exports = handler;
