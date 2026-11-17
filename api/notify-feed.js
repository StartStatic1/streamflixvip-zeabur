// api/notify-feed.js — GET /api/notify-feed?since=<epoch ms>&tv=<tmdbId,tmdbId,...>
// Consultado pelo app do celular a cada ~45 min (WorkManager) para notificações.
const feed = require('../lib/notify-feed');

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });
  try {
    const body = await feed.build(req.query || {});
    return res.status(200).json(body);
  } catch (err) {
    console.error('notify-feed:', err && err.message);
    return res.status(200).json({ now: Date.now(), broadcasts: [], episodes: [] });
  }
};
