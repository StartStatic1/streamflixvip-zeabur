// api/announcements.js
// Avisos ativos para o app Android (Perfil).
// Fonte principal: tabela Supabase app_announcements.
// Fallback: announcements.json (se a tabela ainda não existir).

const fs = require('fs');
const path = require('path');

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const FILE = path.join(__dirname, '..', 'announcements.json');

function mapRow(a) {
  return {
    id: String(a.id),
    type: a.type || 'info',
    title: String(a.title || ''),
    body: String(a.body || ''),
    createdAt: a.created_at || a.createdAt || null,
    linkTmdbId: a.link_tmdb_id != null ? a.link_tmdb_id : (a.linkTmdbId || null),
    linkMediaType: a.link_media_type || a.linkMediaType || null,
  };
}

async function fromSupabase() {
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_ANON_KEY;
  if (!key) return null;
  const url =
    `${SUPABASE_URL}/rest/v1/app_announcements` +
    `?select=id,type,title,body,active,link_tmdb_id,link_media_type,created_at` +
    `&active=eq.true&order=created_at.desc&limit=50`;
  const r = await fetch(url, {
    headers: { apikey: key, Authorization: `Bearer ${key}` },
  });
  if (!r.ok) return null;
  const rows = await r.json();
  if (!Array.isArray(rows)) return null;
  return rows.filter((a) => a && a.title && a.body).map(mapRow);
}

function fromFile() {
  try {
    const raw = fs.readFileSync(FILE, 'utf-8');
    const data = JSON.parse(raw);
    const list = Array.isArray(data.announcements) ? data.announcements : [];
    return list
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
  } catch (_) {
    return [];
  }
}

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
    const fromDb = await fromSupabase();
    const announcements = fromDb != null ? fromDb : fromFile();
    res.status(200).json({ announcements });
  } catch (err) {
    console.error('announcements:', err);
    res.status(200).json({ announcements: fromFile() });
  }
};
