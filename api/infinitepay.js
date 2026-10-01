// api/infinitepay.js
// InfinitePay Checkout — PIX/cartão via link + webhook → VIP
//
// POST /api/infinitepay/create-link  { userId, amount, planLabel?, durationHours? }
// POST /api/infinitepay/webhook      (chamado pela InfinitePay quando paga)

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const vipMem = require('../lib/vip-status-cache');

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  const path = req.url || '';
  if (path.includes('/ticket-status')) return ticketStatus(req, res);
  if (path.includes('/create-link')) return createLink(req, res);
  if (path.includes('/webhook')) return handleWebhook(req, res);
  res.status(404).json({ error: 'Rota não encontrada' });
};

/**
 * POST /api/infinitepay/create-link
 * Body: { userId, amount: 19.90, planLabel?: "VIP 30 Dias", durationHours?: 720 }
 */

async function ticketStatus(req, res) {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) return res.status(500).json({ active: false, error: 'config' });
  const userId = String((req.query && (req.query.userId || req.query.user_id)) || '').trim();
  const tmdbId = String((req.query && (req.query.tmdbId || req.query.tmdb_id)) || '').trim();
  if (!userId || !tmdbId) return res.status(400).json({ active: false, error: 'userId e tmdbId' });
  const url = SUPABASE_URL + '/rest/v1/movie_tickets?user_id=eq.' + encodeURIComponent(userId)
    + '&tmdb_id=eq.' + encodeURIComponent(tmdbId)
    + '&expires_at=gt.' + encodeURIComponent(new Date().toISOString())
    + '&select=expires_at,paid_at&order=expires_at.desc&limit=1';
  const r = await fetch(url, { headers: { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey } });
  if (!r.ok) return res.status(200).json({ active: false });
  const rows = await r.json();
  const row = Array.isArray(rows) && rows[0];
  if (!row) return res.status(200).json({ active: false, tmdbId });
  return res.status(200).json({ active: true, tmdbId, expiresAt: row.expires_at });
}

async function createLink(req, res) {
  if (req.method !== 'POST') return res.status(405).end();

  const handle = (process.env.INFINITEPAY_HANDLE || 'streamflixvip').replace(/^\$/, '');
  const { userId, amount, planLabel, durationHours, type, tmdbId, mediaType } = req.body || {};
  if (!userId || amount == null) {
    return res.status(400).json({ error: 'userId e amount são obrigatórios' });
  }

  const kind = String(type || 'vip').toLowerCase() === 'ticket' ? 'ticket' : 'vip';
  if (kind === 'ticket' && !tmdbId) {
    return res.status(400).json({ error: 'tmdbId obrigatório para ingresso' });
  }

  const hours = Number(durationHours) > 0 ? Number(durationHours) : (kind === 'ticket' ? 24 : 720);
  const label = planLabel || (kind === 'ticket' ? 'Ingresso' : 'VIP');
  const cents = Math.round(Number(amount) * 100);
  if (!(cents > 0)) return res.status(400).json({ error: 'amount inválido' });

  const orderNsu = kind === 'ticket'
    ? ['ticket', userId, String(tmdbId), String(hours), String(mediaType || 'movie'), Date.now()].join('__')
    : ['vip', userId, String(hours), encodeURIComponent(label), Date.now()].join('__');

  const payload = {
    handle,
    order_nsu: orderNsu,
    redirect_url: process.env.INFINITEPAY_REDIRECT_URL || 'https://www.streamflixvip.online/',
    webhook_url: process.env.INFINITEPAY_WEBHOOK_URL || 'https://www.streamflixvip.online/api/infinitepay/webhook',
    items: [
      {
        quantity: 1,
        price: cents,
        description: `StreamFlix ${label}`.slice(0, 120),
      },
    ],
  };

  try {
    const r = await fetch('https://api.checkout.infinitepay.io/links', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) {
      console.error('[infinitepay] create-link fail', r.status, data);
      return res.status(502).json({ error: 'Falha ao criar link InfinitePay', detail: data });
    }
    const url = data.url || data.checkout_url;
    if (!url) {
      return res.status(502).json({ error: 'Resposta sem url', detail: data });
    }
    console.log('[infinitepay] link ok', orderNsu);
    return res.status(200).json({
      url,
      order_nsu: orderNsu,
      amount_cents: cents,
      handle,
    });
  } catch (e) {
    console.error('[infinitepay] create-link', e);
    return res.status(500).json({ error: e.message || 'Erro' });
  }
}

/**
 * POST /api/infinitepay/webhook
 * Body típico: order_nsu, amount, paid_amount, capture_method, transaction_nsu, ...
 */

async function grantTicket(serviceKey, parts, body, res) {
  const userId = parts[1];
  const tmdbId = parts[2];
  const hours = Number(parts[3]) || 24;
  const mediaType = parts[4] === 'tv' ? 'tv' : 'movie';
  if (!userId || !tmdbId) return res.status(200).json({ success: true, message: 'ticket ignored' });
  const headers = { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey, 'Content-Type': 'application/json' };
  const now = new Date();
  const expires = new Date(now.getTime() + hours * 60 * 60 * 1000);
  const tx = body.transaction_nsu || body.invoice_slug || 'ip';
  const row = { user_id: userId, tmdb_id: String(tmdbId), media_type: mediaType, expires_at: expires.toISOString(), paid_at: now.toISOString(), provider_tx: String(tx).slice(0, 80), amount_cents: Number(body.paid_amount || body.amount || 0) || null };
  const r = await fetch(SUPABASE_URL + '/rest/v1/movie_tickets', { method: 'POST', headers: { ...headers, Prefer: 'return=minimal' }, body: JSON.stringify(row) });
  if (!r.ok) { const txt = await r.text(); console.error('[infinitepay] ticket upsert', r.status, txt.slice(0, 200)); return res.status(500).json({ success: false, message: 'ticket table' }); }
  try { require('../lib/vip-status-cache').invalidate(userId); } catch (_) {}
  console.log('[infinitepay] TICKET ok user=' + userId + ' tmdb=' + tmdbId);
  return res.status(200).json({ success: true, message: 'ticket' });
}

async function handleWebhook(req, res) {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    console.error('[infinitepay] sem SERVICE_ROLE');
    return res.status(500).json({ success: false, message: 'config' });
  }

  try {
    const body = req.body || {};
    const orderNsu = String(body.order_nsu || '');
    console.log('[infinitepay] webhook', JSON.stringify(body).slice(0, 400));

    const parts = orderNsu.split('__');
    if (parts[0] === 'ticket') {
      return grantTicket(serviceKey, parts, body, res);
    }
    if (parts[0] !== 'vip' || parts.length < 4) {
      return res.status(200).json({ success: true, message: 'ignored' });
    }
    const userId = parts[1];
    const durationHours = Number(parts[2]) || 720;
    let planLabel = 'VIP InfinitePay';
    try {
      planLabel = decodeURIComponent(parts[3]) || planLabel;
    } catch (_) {}

    if (!userId) {
      return res.status(400).json({ success: false, message: 'sem userId' });
    }

    const headers = {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      'Content-Type': 'application/json',
    };

    const now = new Date();
    const statusUrl = `${SUPABASE_URL}/rest/v1/vip_status?user_id=eq.${encodeURIComponent(userId)}&select=*`;
    const statusRes = await fetch(statusUrl, { headers });
    const statusRows = await statusRes.json();
    const current = Array.isArray(statusRows) && statusRows.length ? statusRows[0] : null;
    const currentExpiry = current?.expires_at ? new Date(current.expires_at) : null;
    const base = currentExpiry && currentExpiry > now ? currentExpiry : now;
    const newExpiry = new Date(base.getTime() + durationHours * 60 * 60 * 1000);
    const tx = body.transaction_nsu || body.invoice_slug || 'ip';

    // Puxa e-mail/nome do Auth (app mobile nao chama track-login)
    let email = current?.email || null;
    let name = current?.name || null;
    if (!email) {
      try {
        const authRes = await fetch(
          `\( {SUPABASE_URL}/auth/v1/admin/users/ \){encodeURIComponent(userId)}`,
          { headers: { ...headers, apikey: serviceKey } },
        );
        if (authRes.ok) {
          const authUser = await authRes.json();
          email = authUser?.email || authUser?.user?.email || null;
          const meta = authUser?.user_metadata || authUser?.user?.user_metadata || {};
          name = name || meta.full_name || meta.name || meta.display_name || null;
        }
      } catch (e) {
        console.error('[infinitepay] auth lookup', e.message || e);
      }
    }

    const upsertBody = {
      user_id: userId,
      expires_at: newExpiry.toISOString(),
      plan_label: planLabel,
      last_code_used: `PIX-IP-${tx}`.slice(0, 80),
      updated_at: now.toISOString(),
    };
    if (email) upsertBody.email = email;
    if (name) upsertBody.name = name;
    if (!current?.first_login_at) upsertBody.first_login_at = now.toISOString();

    await fetch(`${SUPABASE_URL}/rest/v1/vip_status`, {
      method: 'POST',
      headers: { ...headers, Prefer: 'resolution=merge-duplicates' },
      body: JSON.stringify(upsertBody),
    });

    try { vipMem.invalidate(userId); } catch (_) {}
    console.log(`[infinitepay] VIP ok user=${userId} until=${newExpiry.toISOString()}`);
    return res.status(200).json({ success: true, message: null });
  } catch (e) {
    console.error('[infinitepay] webhook', e);
    return res.status(400).json({ success: false, message: e.message || 'erro' });
  }
}
