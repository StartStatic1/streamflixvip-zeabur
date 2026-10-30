// api/device-lock.js
// Celular: "esta conta VIP está ativa neste aparelho?" e "Usar neste aparelho".
//   POST { action: "check" | "claim" }
//   Headers: Authorization: Bearer <token>, X-Device-Id, X-Device-Label (opcional)
// Resposta: { ok:true } | { ok:false, code:"DEVICE_IN_USE"|"SWAP_LIMIT", otherLabel, swapsLeft, maxSwaps, nextSwapAt, message }
// Erro de rede/servidor NUNCA deve tirar o VIP no app: o app só bloqueia com ok:false explícito.
const deviceLock = require('../lib/device-lock');
const { validateSupabaseJwt, checkVipStatus } = require('../lib/vip-gate');

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Device-Id, X-Device-Label, X-Client');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  let body = req.body;
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch (_) { body = {}; } }
  body = body || {};
  const action = String(body.action || 'check');
  const deviceId = String(req.headers['x-device-id'] || body.deviceId || '').trim().slice(0, 120);
  const label = String(req.headers['x-device-label'] || body.deviceLabel || '').slice(0, 60);
  const token = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  if (!deviceId || !token) { res.status(400).json({ error: 'Faltam dados.' }); return; }

  const auth = await validateSupabaseJwt(token);
  if (!auth?.userId) { res.status(401).json({ error: 'Sessão expirada.' }); return; }
  const st = await checkVipStatus(serviceKey, auth.userId);
  if (!st.isVip) { res.status(200).json({ ok: true, isVip: false }); return; }

  const key = 'u:' + auth.userId;
  const r = action === 'claim'
    ? deviceLock.claim(key, 'phone', deviceId, label)
    : deviceLock.check(key, 'phone', deviceId, label);
  if (r.ok) { res.status(200).json({ ok: true, isVip: true, swapped: !!r.swapped }); return; }
  const message = r.code === 'SWAP_LIMIT'
    ? `Você já trocou de celular ${r.maxSwaps} vezes nos últimos 30 dias. Fale com o suporte para liberar.`
    : deviceLock.MSG.phone;
  res.status(200).json({ ...r, isVip: true, message });
};
