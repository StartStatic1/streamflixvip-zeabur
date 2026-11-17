// api/referral.js — "Indique e ganhe"
//   GET  /api/referral                      (Authorization: Bearer <JWT do app>) → código, link, texto e contagem
//   POST /api/referral { action:'apply', code }   (Authorization) → usa o código de um amigo (1x, conta nova)
//   GET  /api/referral?deviceId=<id da TV>  → só o código do dono da TV ativada (somente leitura)
const ref = require('../lib/referrals');

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'OPTIONS') return res.status(200).end();
  if (!process.env.SUPABASE_SERVICE_ROLE_KEY) return res.status(500).json({ error: 'config' });

  const deviceId = String((req.query && req.query.deviceId) || '').trim();
  if (req.method === 'GET' && deviceId && !req.headers.authorization) {
    const uid = await ref.userIdForTvDevice(deviceId);
    if (!uid) return res.status(200).json({ code: null, message: 'Ative esta TV com a sua conta para ver o seu código.' });
    const code = await ref.getOrCreateCode(uid);
    return res.status(200).json({ code, link: ref.linkFor(code), rewardDays: ref.REWARD_DAYS });
  }

  const user = await ref.userFromRequest(req);
  if (!user) return res.status(401).json({ error: 'Entre na sua conta no app primeiro.' });

  if (req.method === 'GET') return res.status(200).json(await ref.summary(user.id));

  if (req.method === 'POST') {
    let body = req.body || {};
    if (typeof body === 'string') { try { body = JSON.parse(body); } catch (_) { body = {}; } }
    if (body.action === 'apply') {
      const r = await ref.applyCode(user, body.code);
      return res.status(r.status).json(r.ok ? { ok: true, message: r.message } : { ok: false, error: r.message });
    }
    return res.status(400).json({ error: 'ação inválida' });
  }
  return res.status(405).json({ error: 'Method not allowed' });
};
