// api/retro.js — GET /api/retro?year=2026 (Authorization: Bearer <JWT do app>) → números da retrospectiva
const { userFromRequest } = require('../lib/referrals');
const retro = require('../lib/retro');

const cache = new Map();
const TTL = 10 * 60 * 1000;

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });
  if (!process.env.SUPABASE_SERVICE_ROLE_KEY) return res.status(500).json({ error: 'config' });

  const user = await userFromRequest(req);
  if (!user) return res.status(401).json({ error: 'Entre na sua conta no app primeiro.' });

  const nowYear = new Date().getFullYear();
  let year = Number((req.query && req.query.year) || nowYear) || nowYear;
  if (year < 2024 || year > nowYear) year = nowYear;

  const key = `${user.id}:${year}`;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < TTL && !(req.query && req.query.refresh)) return res.status(200).json(hit.body);
  const body = await retro.build(user.id, year);
  cache.set(key, { body, at: Date.now() });
  if (cache.size > 3000) cache.clear();
  return res.status(200).json(body);
};
