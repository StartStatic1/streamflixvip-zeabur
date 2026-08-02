// api/announcements.js
//
// Lista de avisos do app (filme novo, manutenção, promo).
// Edite announcements.json na raiz e faça push — o app busca ao abrir o Perfil.
//
// GET /api/announcements

const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'announcements.json');

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  try {
    const raw = fs.readFileSync(FILE, 'utf-8');
    const data = JSON.parse(raw);
    const list = Array.isArray(data.announcements) ? data.announcements : [];
    const active = list
      .filter((a) => a && a.active !== false && a.title && a.body)
      .map((a) => ({
        id: String(a.id || a.title),
        type: a.type || 'info',
        title: String(a.title),
        body: String(a.body),
        createdAt: a.createdAt || null,
        linkTmdbId: a.linkTmdbId || null,
        linkMediaType: a.linkMediaType || null,
      }));
    res.status(200).json({ announcements: active });
  } catch (err) {
    console.error('Erro ao ler announcements.json:', err);
    res.status(200).json({ announcements: [] });
  }
};
