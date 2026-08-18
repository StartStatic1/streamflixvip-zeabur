// /api/tmdb-image
// Proxy de capas/backdrops da TMDB (image.tmdb.org).
// Motivo: em algumas redes BR image.tmdb.org falha/bloqueia;
// o app passa a pedir pelo nosso dominio e o VPS busca a imagem.

const ALLOWED_SIZES = new Set([
  'w92', 'w154', 'w185', 'w342', 'w500', 'w780', 'w1280', 'original',
]);

// So paths de poster/backdrop da TMDB (ex: /abc123.jpg)
const PATH_RE = /^\/[a-zA-Z0-9_./-]+\.(jpg|jpeg|png|webp)$/i;

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const q = req.query || {};
  let size = String(q.size || 'w342').trim();
  let path = String(q.path || '').trim();

  try {
    path = decodeURIComponent(path);
  } catch (_) {}

  if (!path.startsWith('/')) path = '/' + path;
  if (!ALLOWED_SIZES.has(size)) size = 'w342';
  if (!PATH_RE.test(path)) {
    res.status(400).json({ error: 'path invalido' });
    return;
  }

  const upstream = `https://image.tmdb.org/t/p/${size}${path}`;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 12000);

  try {
    const r = await fetch(upstream, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'StreamFlixVIP-ImageProxy/1.0',
        Accept: 'image/*,*/*',
      },
    });
    clearTimeout(timeoutId);

    if (!r.ok) {
      res.status(r.status).json({ error: 'TMDB image ' + r.status });
      return;
    }

    const ctype = r.headers.get('content-type') || 'image/jpeg';
    const buf = Buffer.from(await r.arrayBuffer());

    res.setHeader('Content-Type', ctype);
    res.setHeader('Cache-Control', 'public, max-age=86400, s-maxage=604800, stale-while-revalidate=86400');
    res.setHeader('Content-Length', String(buf.length));
    res.status(200).send(buf);
  } catch (err) {
    clearTimeout(timeoutId);
    console.error('[tmdb-image]', err.message || err);
    res.status(502).json({ error: 'Falha ao buscar imagem TMDB', detail: String(err.message || err) });
  }
};
