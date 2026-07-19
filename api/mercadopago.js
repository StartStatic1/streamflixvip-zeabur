// api/mercadopago.js
const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  const path = req.url || '';
  if (path.includes('/create-pix')) {
    return createPix(req, res);
  } else if (path.includes('/webhook')) {
    return handleWebhook(req, res);
  }
  res.status(404).json({ error: 'Rota não encontrada' });
};

async function createPix(req, res) {
  if (req.method !== 'POST') return res.status(405).end();
  const accessToken = process.env.MP_ACCESS_TOKEN;
  if (!accessToken) return res.status(500).json({ error: 'MP_ACCESS_TOKEN não configurado' });

  const { userId, amount, planLabel, durationHours } = req.body;
  if (!userId || !amount) return res.status(400).json({ error: 'userId e amount são obrigatórios' });

  try {
    const response = await fetch('https://api.mercadopago.com/v1/payments', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
        'X-Idempotency-Key': `pix-${userId}-${Date.now()}`
      },
      body: JSON.stringify({
        transaction_amount: Number(amount),
        description: `StreamFlixVIP: ${planLabel || 'Plano VIP'}`,
        payment_method_id: 'pix',
        payer: {
          email: `${userId}@streamflixvip.online`,
          first_name: 'Usuario',
          last_name: 'StreamFlixVIP'
        },
        external_reference: userId,
        metadata: {
          user_id: userId,
          plan_label: planLabel || 'VIP',
          duration_hours: durationHours || 720
        },
        notification_url: "https://streamflixvip.online/api/mercadopago/webhook"
      })
    });

    const data = await response.json();
    if (!response.ok) return res.status(502).json({ error: 'Erro ao gerar PIX', details: data });

    res.status(200).json({
      paymentId: data.id,
      qrCode: data.point_of_interaction.transaction_data.qr_code,
      qrCodeBase64: data.point_of_interaction.transaction_data.qr_code_base64,
      status: data.status
    });
  } catch (err) {
    res.status(500).json({ error: 'Falha interna ao gerar pagamento' });
  }
}

async function handleWebhook(req, res) {
  const accessToken = process.env.MP_ACCESS_TOKEN;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  const paymentId = req.query['data.id'] || req.body?.data?.id;
  const type = req.query.type || req.body?.type;

  if (type !== 'payment' || !paymentId) return res.status(200).send('OK');

  try {
    const mpRes = await fetch(`https://api.mercadopago.com/v1/payments/${paymentId}`, {
      headers: { 'Authorization': `Bearer ${accessToken}` }
    });
    const payment = await mpRes.json();

    if (payment.status !== 'approved') return res.status(200).send('Aguardando');

    const userId = payment.external_reference;
    const durationHours = payment.metadata?.duration_hours || 720;
    const planLabel = payment.metadata?.plan_label || 'VIP Automático';

    const headers = {
      'apikey': serviceKey,
      'Authorization': `Bearer ${serviceKey}`,
      'Content-Type': 'application/json',
    };

    const now = new Date();
    const statusUrl = `${SUPABASE_URL}/rest/v1/vip_status?user_id=eq.${encodeURIComponent(userId)}&select=*`;
    const statusRes = await fetch(statusUrl, { headers });
    const statusRows = await statusRes.json();
    const currentStatus = Array.isArray(statusRows) && statusRows.length ? statusRows[0] : null;

    const currentExpiry = currentStatus?.expires_at ? new Date(currentStatus.expires_at) : null;
    const baseTime = (currentExpiry && currentExpiry > now) ? currentExpiry : now;
    const newExpiry = new Date(baseTime.getTime() + durationHours * 60 * 60 * 1000);

    await fetch(`${SUPABASE_URL}/rest/v1/vip_status`, {
      method: 'POST',
      headers: { ...headers, 'Prefer': 'resolution=merge-duplicates' },
      body: JSON.stringify({
        user_id: userId,
        expires_at: newExpiry.toISOString(),
        plan_label: planLabel,
        last_code_used: `PIX-MP-${paymentId}`,
        updated_at: now.toISOString(),
      }),
    });

    res.status(200).send('OK');
  } catch (err) {
    res.status(500).send('Error');
  }
}
