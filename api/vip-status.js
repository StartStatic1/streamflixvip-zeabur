// api/vip-status.js
// Consulta o status VIP de um usuário.
// Cache em memória 2 min por userId — reduz egress Supabase.
//
// Uso: GET /api/vip-status?userId=<uuid>

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

const VIP_CACHE_TTL_MS = Math.max(
  30_000,
  Number(process.env.VIP_STATUS_CACHE_TTL_MS || 2 * 60 * 1000) || 2 * 60 * 1000,
);
/** @type {Map<string, { at: number, body: object }>} */
const vipCache = new Map();
const VIP_CACHE_MAX = 2000;

function cacheGet(userId) {
  const hit = vipCache.get(userId);
  if (!hit) return null;
  if (Date.now() - hit.at > VIP_CACHE_TTL_MS) {
    vipCache.delete(userId);
    return null;
  }
  return hit.body;
}

function cacheSet(userId, body) {
  if (vipCache.size >= VIP_CACHE_MAX) {
    const first = vipCache.keys().next().value;
    if (first) vipCache.delete(first);
  }
  vipCache.set(userId, { at: Date.now(), body });
}

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

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada nas env vars' });
    return;
  }

  const userId = (req.query.userId || '').trim();
  if (!userId) {
    res.status(400).json({ error: 'Informe "userId".' });
    return;
  }

  const force =
    String(req.query.refresh || '').toLowerCase() === '1' ||
    String(req.query.refresh || '').toLowerCase() === 'true';

  if (!force) {
    const cached = cacheGet(userId);
    if (cached) {
      res.setHeader('X-Cache', 'HIT');
      res.status(200).json({ ...cached, cached: true });
      return;
    }
  }

  try {
    const url = `${SUPABASE_URL}/rest/v1/vip_status?user_id=eq.${encodeURIComponent(userId)}&select=expires_at,plan_label`;
    const r = await fetch(url, {
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
      },
    });
    const rows = await r.json();
    const row = Array.isArray(rows) && rows.length ? rows[0] : null;

    let body;
    if (!row) {
      body = { isVip: false, expiresAt: null, planLabel: null };
    } else {
      const isVip = new Date(row.expires_at).getTime() > Date.now();
      body = {
        isVip,
        expiresAt: row.expires_at,
        planLabel: row.plan_label || null,
      };
    }

    cacheSet(userId, body);
    res.setHeader('X-Cache', force ? 'BYPASS' : 'MISS');
    res.status(200).json(body);
  } catch (err) {
    console.error('vip-status error:', err);
    const stale = cacheGet(userId);
    if (stale) {
      res.setHeader('X-Cache', 'STALE');
      res.status(200).json({ ...stale, cached: true, stale: true });
      return;
    }
    res.status(502).json({ error: 'Falha ao consultar status VIP.', detail: String(err) });
  }
};
