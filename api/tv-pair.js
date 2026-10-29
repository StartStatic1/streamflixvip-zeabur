// api/tv-pair.js
// Conectar a TV lendo um QR code com o celular já logado (sem digitar código).
// NÃO precisa de migration: o pareamento fica só na memória do servidor (10 min) e,
// ao confirmar, grava em tv_activations (tabela já existente) usando o último código
// VIP que a própria conta resgatou (respeita o limite de TVs por código).
//
//   POST { action:"start",   deviceId }                 -> { pairId, pin, url, expiresIn }
//   POST { action:"poll",    pairId, deviceId }         -> { status: "waiting"|"done"|"expired" }
//   POST { action:"confirm", pairId | pin } + Authorization: Bearer <token do app>
//                                                         -> { success, expiresAt } | { error }
const crypto = require('crypto');

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const MAX_TV_DEVICES_PER_CODE = 2;
const TTL_MS = 10 * 60 * 1000;
const PUBLIC_URL = process.env.PUBLIC_SITE_URL || 'https://www.streamflixvip.online';

const pairs = new Map(); // pairId -> { deviceId, pin, created, status }
const byPin = new Map(); // pin -> pairId
const hits = new Map(); // ip -> [timestamps]

function gc() {
  const now = Date.now();
  for (const [id, p] of pairs) {
    if (now - p.created > TTL_MS) { pairs.delete(id); byPin.delete(p.pin); }
  }
}

function limited(ip, max) {
  const now = Date.now();
  const arr = (hits.get(ip) || []).filter((t) => now - t < 60000);
  arr.push(now);
  hits.set(ip, arr);
  if (hits.size > 5000) hits.clear();
  return arr.length > max;
}

function newPin() {
  for (let i = 0; i < 20; i++) {
    const pin = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
    if (!byPin.has(pin)) return pin;
  }
  return null;
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  gc();
  let body = req.body;
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch (_) { body = {}; } }
  body = body || {};
  const action = String(body.action || '');
  const ip = String(req.headers['x-forwarded-for'] || req.socket?.remoteAddress || '').split(',')[0].trim();

  if (action === 'start') {
    if (limited(ip, 20)) { res.status(429).json({ error: 'Muitas tentativas' }); return; }
    const deviceId = String(body.deviceId || '').trim().slice(0, 120);
    if (!deviceId) { res.status(400).json({ error: 'deviceId obrigatório' }); return; }
    const pin = newPin();
    if (!pin) { res.status(503).json({ error: 'Tente de novo' }); return; }
    const pairId = crypto.randomBytes(16).toString('hex');
    pairs.set(pairId, { deviceId, pin, created: Date.now(), status: 'waiting' });
    byPin.set(pin, pairId);
    res.status(200).json({ pairId, pin, url: `${PUBLIC_URL}/tv?p=${pairId}`, expiresIn: TTL_MS / 1000 });
    return;
  }

  if (action === 'poll') {
    const p = pairs.get(String(body.pairId || ''));
    if (!p || p.deviceId !== String(body.deviceId || '').trim()) { res.status(200).json({ status: 'expired' }); return; }
    res.status(200).json({ status: p.status });
    return;
  }

  if (action === 'confirm') {
    if (limited(ip, 15)) { res.status(429).json({ error: 'Muitas tentativas. Aguarde 1 minuto.' }); return; }
    const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
    if (!serviceKey) { res.status(500).json({ error: 'Servidor sem configuração' }); return; }
    const pairId = body.pairId ? String(body.pairId) : byPin.get(String(body.pin || '').replace(/\D/g, ''));
    const p = pairId ? pairs.get(pairId) : null;
    if (!p || p.status !== 'waiting') { res.status(404).json({ error: 'Código da TV expirado. Abra a tela de ativação na TV de novo.' }); return; }

    const token = String(req.headers.authorization || '').replace(/^Bearer\s+/i, '');
    if (!token) { res.status(401).json({ error: 'Entre na sua conta no app primeiro.' }); return; }
    const headers = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, 'Content-Type': 'application/json' };
    try {
      const ur = await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { apikey: serviceKey, Authorization: `Bearer ${token}` } });
      const user = ur.ok ? await ur.json() : null;
      if (!user || !user.id) { res.status(401).json({ error: 'Sessão expirada. Entre de novo no app.' }); return; }

      const vr = await fetch(`${SUPABASE_URL}/rest/v1/vip_status?user_id=eq.${encodeURIComponent(user.id)}&select=expires_at,plan_label`, { headers });
      const vrows = await vr.json();
      const vip = Array.isArray(vrows) && vrows[0];
      if (!vip || !(new Date(vip.expires_at).getTime() > Date.now())) {
        res.status(403).json({ error: 'Sua conta não tem VIP ativo.' }); return;
      }
      const cr = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?used_by=eq.${encodeURIComponent(user.id)}&select=code&order=used_at.desc.nullslast&limit=1`, { headers });
      const crows = await cr.json();
      const code = Array.isArray(crows) && crows[0] && crows[0].code;
      if (!code) { res.status(409).json({ error: 'Não achamos o código VIP da sua conta. Use o código na TV.' }); return; }

      const or = await fetch(`${SUPABASE_URL}/rest/v1/tv_activations?code=eq.${encodeURIComponent(code)}&is_active=eq.true&device_id=neq.${encodeURIComponent(p.deviceId)}&select=device_id,expires_at`, { headers });
      const others = (await or.json() || []).filter((r) => new Date(r.expires_at).getTime() > Date.now());
      if (others.length >= MAX_TV_DEVICES_PER_CODE) {
        res.status(409).json({ error: `Sua conta já está em ${MAX_TV_DEVICES_PER_CODE} TVs.` }); return;
      }
      const up = await fetch(`${SUPABASE_URL}/rest/v1/tv_activations?on_conflict=device_id`, {
        method: 'POST',
        headers: { ...headers, Prefer: 'resolution=merge-duplicates,return=minimal' },
        body: JSON.stringify({
          device_id: p.deviceId, code, plan_label: vip.plan_label || null,
          expires_at: vip.expires_at, activated_at: new Date().toISOString(), is_active: true,
          device_label: 'QR',
        }),
      });
      if (!up.ok) { console.error('tv-pair upsert', up.status); res.status(502).json({ error: 'Falha ao ativar. Tente de novo.' }); return; }
      p.status = 'done';
      res.status(200).json({ success: true, expiresAt: vip.expires_at });
    } catch (err) {
      console.error('tv-pair error:', err && err.message);
      res.status(500).json({ error: 'Erro interno. Tente de novo.' });
    }
    return;
  }

  res.status(400).json({ error: 'Ação inválida' });
};
