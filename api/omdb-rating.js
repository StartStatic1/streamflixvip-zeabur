// api/omdb-rating.js
// Proxy serverless para a OMDb API — busca nota do IMDb e do Rotten Tomatoes
// por título + ano (a TMDB não tem essas notas).
//
// Motivo de existir: a OMDb exige API key na URL; fazer a chamada
// direto do client exporia a key no bundle. Aqui ela fica só no servidor.
//
// Uso no front-end:
//   /api/omdb-rating?title=Duna&year=2021&imdb_id=tt1160419
//   (imdb_id é opcional mas, se disponível via TMDB external_ids, é bem
//   mais preciso que buscar por título+ano)
//
// Configuração necessária na Vercel:
//   Settings > Environment Variables > OMDB_API_KEY = <sua key gratuita>
//   Pegue a key grátis (1.000 req/dia) em: https://www.omdbapi.com/apikey.aspx
//
// Resposta normalizada (sempre este formato, mesmo se algum campo faltar):
//   { imdbRating: "8.7" | null, imdbVotes: "1.2M" | null,
//     rottenTomatoes: "94%" | null, metascore: "78" | null, found: true }

const OMDB_BASE = 'https://www.omdbapi.com/';

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

  const apiKey = process.env.OMDB_API_KEY;
  if (!apiKey) {
    // Não é um erro fatal para o front-end: apenas devolvemos "não encontrado"
    // para que a UI caia de volta silenciosamente para a nota TMDB, em vez
    // de quebrar a página inteira enquanto a key não é configurada.
    res.setHeader('Cache-Control', 's-maxage=60');
    res.status(200).json({
      found: false,
      configured: false,
      imdbRating: null, imdbVotes: null, rottenTomatoes: null, metascore: null,
    });
    return;
  }

  const { title, year, imdb_id } = req.query;
  if (!imdb_id && !title) {
    res.status(400).json({ error: 'Informe "imdb_id" ou "title" (+ "year" opcional).' });
    return;
  }

  try {
    const url = new URL(OMDB_BASE);
    url.searchParams.set('apikey', apiKey);
    if (imdb_id) {
      url.searchParams.set('i', imdb_id);
    } else {
      url.searchParams.set('t', title);
      if (year) url.searchParams.set('y', year);
    }

    // Timeout de 8s: mesmo padrão usado em tmdb.js e stream-proxy.js — sem
    // isso, uma instabilidade de rede de saída deixa a nota de IMDb/RT
    // pendurada em vez de simplesmente cair pro fallback "não encontrado".
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 8000);
    let omdbRes;
    try {
      omdbRes = await fetch(url.toString(), { signal: controller.signal });
    } finally {
      clearTimeout(timeoutId);
    }
    const data = await omdbRes.json();

    if (data.Response === 'False') {
      res.setHeader('Cache-Control', 's-maxage=21600, stale-while-revalidate=86400');
      res.status(200).json({
        found: false, configured: true,
        imdbRating: null, imdbVotes: null, rottenTomatoes: null, metascore: null,
      });
      return;
    }

    const rtEntry = (data.Ratings || []).find(r => r.Source === 'Rotten Tomatoes');

    // Cacheia por 6h (CDN da Vercel) — notas não mudam de minuto a minuto
    res.setHeader('Cache-Control', 's-maxage=21600, stale-while-revalidate=86400');
    res.status(200).json({
      found: true,
      configured: true,
      imdbRating: data.imdbRating !== 'N/A' ? data.imdbRating : null,
      imdbVotes: data.imdbVotes !== 'N/A' ? data.imdbVotes : null,
      rottenTomatoes: rtEntry ? rtEntry.Value : null,
      metascore: data.Metascore !== 'N/A' ? data.Metascore : null,
    });
  } catch (err) {
    console.error('OMDb proxy error:', err);
    // Também não derruba a UI: devolve "não encontrado" com 200.
    res.setHeader('Cache-Control', 's-maxage=30');
    res.status(200).json({
      found: false, configured: true,
      imdbRating: null, imdbVotes: null, rottenTomatoes: null, metascore: null,
    });
  }
};
