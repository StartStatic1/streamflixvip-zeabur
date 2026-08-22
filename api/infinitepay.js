// api/infinitepay.js
// InfinitePay Checkout — PIX/cartão via link + webhook → VIP
//
// POST /api/infinitepay/create-link  { userId, amount, planLabel?, durationHours? }
// POST /api/infinitepay/webhook      (chamado pela InfinitePay quando paga)

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  const path = req.url || '';
  if (path.includes('/create-link')) return createLink(req, res);
  if (path.includes('/webhook')) return handleWebhook(req, res);
  res.status(404).json({ error: 'Rota não encontrada' });
};

/**
 * POST /api/infinitepay/create-link
 * Body: { userId, amount: 19.90, planLabel?: "VIP 30 Dias", durationHours?: 720 }
 */
async function createLink(req, res) {
  if (req.method !== 'POST') return res.status(405).end();

  const handle = (process.env.INFINITEPAY_HANDLE || 'streamflixvip').replace(/^\$/, '');
  const { userId, amount, planLabel, durationHours } = req.body || {};
  if (!userId || amount == null) {
    return res.status(400).json({ error: 'userId e amount são obrigatórios' });
  }

  const hours = Number(durationHours) > 0 ? Number(durationHours) : 720;
  const label = planLabel || 'VIP';
  const cents = Math.round(Number(amount) * 100);
  if (!(cents > 0)) return res.status(400).json({ error: 'amount inválido' });

  // order_nsu carrega dados para o webhook (sem depender de metadata externa)
  const orderNsu = ['vip', userId, String(hours), encodeURIComponent(label), Date.now()].join('__');

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

    // order_nsu = vip__userId__hours__labelEnc__ts
    const parts = orderNsu.split('__');
    if (parts[0] !== 'vip' || parts.length < 4) {
      // responde 200 para não ficar em loop; ignora pedido estranho
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

    console.log(`[infinitepay] VIP ok user=${userId} until=${newExpiry.toISOString()}`);
    return res.status(200).json({ success: true, message: null });
  } catch (e) {
    console.error('[infinitepay] webhook', e);
    return res.status(400).json({ success: false, message: e.message || 'erro' });
  }
}
