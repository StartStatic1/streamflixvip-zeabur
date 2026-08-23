// lib/partner-auth.js
// Valida API key de parceiro (header Authorization: Bearer sf_live_... ou X-API-Key)
const crypto = require('crypto');

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

// Rate limit em memória por partner_id (reinicia com o PM2 — ok para 1 VPS)
const rateBuckets = new Map();

function hashKey(rawKey) {
  return crypto.createHash('sha256').update(String(rawKey), 'utf8').digest('hex');
}

function extractApiKey(req) {
  const h = req.headers || {};
  const x = h['x-api-key'] || h['X-API-Key'];
  if (x && String(x).trim()) return String(x).trim();
  const auth = h.authorization || h.Authorization || '';
  const m = String(auth).match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : '';
}

function checkRateLimit(partnerId, limitPerMin) {
  const now = Date.now();
  const windowMs = 60 * 1000;
  let b = rateBuckets.get(partnerId);
  if (!b || now - b.windowStart > windowMs) {
    b = { windowStart: now, count: 0 };
    rateBuckets.set(partnerId, b);
  }
  b.count += 1;
  if (b.count > limitPerMin) {
    return { ok: false, retryAfterSec: Math.ceil((windowMs - (now - b.windowStart)) / 1000) };
  }
  return { ok: true };
}

async function resolvePartner(req, serviceKey) {
  const rawKey = extractApiKey(req);
  if (!rawKey || !rawKey.startsWith('sf_')) {
    return { ok: false, status: 401, error: 'API key ausente ou inválida. Use Authorization: Bearer sf_live_...' };
  }

  const keyHash = hashKey(rawKey);
  // Busca por hash (único caminho seguro)
  const url =
    `${SUPABASE_URL}/rest/v1/api_partners?key_hash=eq.${encodeURIComponent(keyHash)}` +
    `&select=id,name,is_active,scopes,rate_limit_per_min,request_count&limit=1`;

  const r = await fetch(url, {
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
    },
  });

  if (!r.ok) {
    return { ok: false, status: 500, error: 'Falha ao validar API key' };
  }

  const rows = await r.json();
  const partner = Array.isArray(rows) && rows[0] ? rows[0] : null;
  if (!partner) {
    return { ok: false, status: 401, error: 'API key inválida' };
  }
  if (!partner.is_active) {
    return { ok: false, status: 403, error: 'Parceiro desativado' };
  }

  const limit = Number(partner.rate_limit_per_min) || 60;
  const rl = checkRateLimit(partner.id, limit);
  if (!rl.ok) {
    return {
      ok: false,
      status: 429,
      error: 'Rate limit excedido',
      retryAfterSec: rl.retryAfterSec,
    };
  }

  // Atualiza last_used + contador (fire-and-forget)
  fetch(`${SUPABASE_URL}/rest/v1/api_partners?id=eq.${encodeURIComponent(partner.id)}`, {
    method: 'PATCH',
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      last_used_at: new Date().toISOString(),
      request_count: (Number(partner.request_count) || 0) + 1,
      updated_at: new Date().toISOString(),
    }),
  }).catch(() => {});

  return {
    ok: true,
    partner: {
      id: partner.id,
      name: partner.name,
      scopes: Array.isArray(partner.scopes) ? partner.scopes : ['sources'],
    },
  };
}

function hasScope(partner, scope) {
  const scopes = partner.scopes || [];
  if (scopes.includes('*') || scopes.includes('all')) return true;
  return scopes.includes(scope);
}

function generateApiKey() {
  const secret = crypto.randomBytes(24).toString('base64url');
  const raw = `sf_live_${secret}`;
  const prefix = raw.slice(0, 16);
  return { raw, prefix, hash: hashKey(raw) };
}

module.exports = {
  resolvePartner,
  hasScope,
  generateApiKey,
  hashKey,
};
