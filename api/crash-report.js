// api/crash-report.js
//
// Relatorios de crash do app Android TV (com.streamflixvip.tv).
//   POST /api/crash-report  { reports: [ {appVersion, versionCode, model, manufacturer,
//                             device, android, sdk, screen, action, thread, kind, message, stack, ts} ] }
//        -> publico, limitado por IP, sem dados sensiveis (lib/crash-store scrub).
//   POST /api/crash-report  { action: 'list' | 'clear' } com Authorization: Bearer <sessao admin>
//        -> so admin (vip_panel_admins). Tela: /admin-crashes.html
const store = require('../lib/crash-store');

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const RATE_WINDOW_MS = 60 * 60 * 1000;
const RATE_MAX = 40;
const rate = new Map();

function clientIp(req) {
  const fwd = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  return fwd || (req.socket && req.socket.remoteAddress) || '';
}

function allow(ip, n) {
  const now = Date.now();
  const e = rate.get(ip);
  if (!e || now - e.start > RATE_WINDOW_MS) {
    rate.set(ip, { start: now, n });
    if (rate.size > 5000) rate.delete(rate.keys().next().value);
    return n <= RATE_MAX;
  }
  e.n += n;
  return e.n <= RATE_MAX;
}

async function isAdmin(req) {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) return false;
  const userToken = String(req.headers['authorization'] || '').replace('Bearer ', '').trim();
  if (!userToken) return false;
  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: serviceKey, Authorization: `Bearer ${userToken}` },
  });
  if (!userRes.ok) return false;
  const user = await userRes.json();
  if (!user || !user.id) return false;
  const adminRes = await fetch(
    `${SUPABASE_URL}/rest/v1/vip_panel_admins?id=eq.${encodeURIComponent(user.id)}&select=id`,
    { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
  );
  const rows = await adminRes.json();
  return adminRes.ok && Array.isArray(rows) && rows.length > 0;
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  let body = req.body;
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch (_) { body = {}; } }
  body = body || {};
  const action = body.action;

  if (action === 'list' || action === 'clear') {
    if (!(await isAdmin(req))) { res.status(403).json({ error: 'Acesso negado' }); return; }
    if (action === 'clear') {
      await store.clear();
      res.status(200).json({ ok: true });
      return;
    }
    const all = store.load();
    const limit = Math.min(Math.max(Number(body.limit) || 100, 1), store.MAX_ENTRIES);
    res.status(200).json({ total: all.length, groups: store.summarize(all), recent: all.slice(-limit).reverse() });
    return;
  }

  const list = Array.isArray(body.reports) ? body.reports : [body];
  const ip = clientIp(req);
  // LOWMEM com pss 0MB = o Android matou o app em segundo plano (normal, não é fechamento visto pelo usuário).
  // Apps 17.0.0/4.0.x ainda mandam; o servidor descarta para o painel mostrar só erros de verdade.
  const isNoise = (r) => r && String(r.kind || '').toLowerCase() === 'lowmem' && /pss 0MB/.test(String(r.stack || ''));
  const reports = list.slice(0, 5).filter((r) => !isNoise(r)).map((r) => store.sanitizeReport(r, ip)).filter(Boolean);
  if (!reports.length && list.length) { res.status(200).json({ ok: true, stored: 0 }); return; }
  if (!reports.length) { res.status(400).json({ error: 'Relatorio vazio' }); return; }
  if (!allow(ip, reports.length)) { res.status(429).json({ error: 'Muitos relatorios' }); return; }
  await store.append(reports);
  for (const r of reports) {
    console.warn(`[tv-crash] ${r.appVersion || '?'} ${r.manufacturer || ''} ${r.model || ''} sdk${r.sdk || '?'} tela=${r.screen || '?'} sig=${r.signature} :: ${(String(r.stack || r.message).split('\n')[0] || '').slice(0, 160)}`);
  }
  res.status(200).json({ ok: true, stored: reports.length });
};
