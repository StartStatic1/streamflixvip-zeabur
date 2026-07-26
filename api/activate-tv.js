// api/activate-tv.js
// Ativação de código VIP num aparelho de TV — roda no servidor (Vercel).
//
// Por que é um endpoint separado do redeem-vip.js: no mobile, um código é
// de uso único e fica preso a uma conta (userId) via Supabase Auth. Na TV
// não tem login de e-mail/senha — só o código. Se essa rota reaproveitasse
// a mesma trava de "usado" do redeem-vip, o MESMO código que já ativou o
// VIP no celular não serviria mais pra TV (erro "já utilizado").
//
// Aqui a validade do código (`vip_codes.duration_hours`, `is_active`) é
// checada, mas o "uso" não é marcado na própria vip_codes — é registrado
// numa tabela separada (tv_activations), uma linha por aparelho de TV.
// Isso deixa o mesmo código servir pra ativar a conta mobile E até
// MAX_TV_DEVICES_PER_CODE aparelhos de TV, sem interferir um no outro.
//
// Uso no front-end (TV):
//   POST /api/activate-tv   body: { code: "SFV-30D-XXXX", deviceId: "<android_id>" }
//
// Configuração necessária na Vercel (mesma variável do redeem-vip.js):
//   SUPABASE_SERVICE_ROLE_KEY
//
// Migração SQL necessária no Supabase (rodar uma vez, ver migrations/tv_activations.sql):
//   cria a tabela tv_activations e a coluna vip_redemptions.platform

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const MAX_TV_DEVICES_PER_CODE = 2; // quantos aparelhos de TV o mesmo código pode ativar

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada nas env vars da Vercel' });
    return;
  }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (e) { body = {}; }
  }
  const code = (body?.code || '').trim();
  const deviceId = (body?.deviceId || '').trim();
  const deviceLabel = (body?.deviceLabel || '').trim() || null;

  if (!code || !deviceId) {
    res.status(400).json({ error: 'Informe "code" e "deviceId".' });
    return;
  }

  const headers = {
    'apikey': serviceKey,
    'Authorization': `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  try {
    // 1) Busca o código — mesma tabela do mobile, mas SEM checar used_by
    //    (esse campo é do fluxo de conta mobile e não deve bloquear a TV).
    const lookupUrl = `${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}&select=*`;
    const lookupRes = await fetch(lookupUrl, { headers });
    const rows = await lookupRes.json();

    if (!lookupRes.ok || !Array.isArray(rows) || rows.length === 0) {
      res.status(404).json({ error: 'Código inválido.' });
      return;
    }

    const vipCode = rows[0];

    if (!vipCode.is_active) {
      res.status(410).json({ error: 'Este código foi desativado.' });
      return;
    }

    const durationHours = Number(vipCode.duration_hours) || 0;
    if (durationHours <= 0) {
      res.status(400).json({ error: 'Código com duração inválida.' });
      return;
    }

    const now = new Date();

    // 2) Esse aparelho já tem ativação registrada? Se sim, é uma renovação
    //    (não conta pro limite de aparelhos por código) — estende a partir
    //    do maior entre "agora" e a expiração atual, igual ao mobile.
    const existingUrl = `${SUPABASE_URL}/rest/v1/tv_activations?device_id=eq.${encodeURIComponent(deviceId)}&select=*`;
    const existingRes = await fetch(existingUrl, { headers });
    const existingRows = await existingRes.json();
    const existing = Array.isArray(existingRows) && existingRows.length ? existingRows[0] : null;

    // 3) Limite de aparelhos por código — conta outros devices ativos com
    //    esse mesmo código, sem contar o próprio (renovação não deve travar).
    const othersUrl = `${SUPABASE_URL}/rest/v1/tv_activations?code=eq.${encodeURIComponent(code)}&is_active=eq.true&device_id=neq.${encodeURIComponent(deviceId)}&select=device_id`;
    const othersRes = await fetch(othersUrl, { headers });
    const othersRows = await othersRes.json();
    const othersCount = Array.isArray(othersRows) ? othersRows.length : 0;

    if (othersCount >= MAX_TV_DEVICES_PER_CODE) {
      res.status(409).json({ error: `Este código já ativou o número máximo de TVs permitido (${MAX_TV_DEVICES_PER_CODE}).` });
      return;
    }

    const currentExpiry = existing?.expires_at ? new Date(existing.expires_at) : null;
    const baseTime = (currentExpiry && currentExpiry > now) ? currentExpiry : now;
    const newExpiry = new Date(baseTime.getTime() + durationHours * 60 * 60 * 1000);

    // 4) Upsert por device_id (uma linha por aparelho — reativar/renovar
    //    com um código novo simplesmente atualiza a mesma linha).
    const upsertRes = await fetch(`${SUPABASE_URL}/rest/v1/tv_activations`, {
      method: 'POST',
      headers: { ...headers, 'Prefer': 'resolution=merge-duplicates,return=representation' },
      body: JSON.stringify({
        device_id: deviceId,
        code,
        plan_label: vipCode.plan_label || null,
        expires_at: newExpiry.toISOString(),
        activated_at: now.toISOString(),
        is_active: true,
        device_label: deviceLabel || existing?.device_label || null,
      }),
    });

    if (!upsertRes.ok) {
      const errText = await upsertRes.text();
      console.error('tv_activations upsert error:', errText);
      res.status(502).json({ error: 'Código validado, mas falhou ao ativar a TV. Tente novamente.' });
      return;
    }

    // 5) Log no histórico (mesma tabela que o mobile usa), marcado como TV
    //    — não bloqueia a resposta se falhar, é só telemetria pro painel.
    try {
      await fetch(`${SUPABASE_URL}/rest/v1/vip_redemptions`, {
        method: 'POST',
        headers: { ...headers, 'Prefer': 'return=minimal' },
        body: JSON.stringify({
          user_id: null,
          email: null,
          code,
          plan_label: vipCode.plan_label || null,
          duration_hours: durationHours,
          redeemed_at: now.toISOString(),
          platform: 'tv',
          device_id: deviceId,
        }),
      });
    } catch (logErr) {
      console.warn('vip_redemptions (tv) log error (non-fatal):', logErr);
    }

    res.status(200).json({
      success: true,
      expiresAt: newExpiry.toISOString(),
      planLabel: vipCode.plan_label || null,
    });
  } catch (err) {
    console.error('activate-tv error:', err);
    res.status(500).json({ error: 'Erro interno ao ativar. Tente novamente.' });
  }
};
