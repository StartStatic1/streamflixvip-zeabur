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
const MAX_TV_DEVICES_PER_CODE = 1; // 1 TV por assinante (vaga controlada em lib/device-lock.js)
const deviceLock = require('../lib/device-lock');
const { tvKeyForCode } = require('../lib/vip-gate');

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
  const replace = body?.replace === true;

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

    // 3) 1 TV por assinante. A vaga é do assinante (conta dona do código, ou o
    //    próprio código). Outra TV usando? Só troca com "Usar nesta TV" (replace),
    //    que gasta 1 das trocas do mês. Renovação na mesma TV não conta.
    const key = await tvKeyForCode(serviceKey, code);
    const lk = replace
      ? deviceLock.claim(key, 'tv', deviceId, deviceLabel)
      : deviceLock.check(key, 'tv', deviceId, deviceLabel);
    if (!lk.ok) {
      res.status(409).json({
        code: lk.code === 'SWAP_LIMIT' ? 'SWAP_LIMIT' : 'TV_IN_USE',
        error: lk.code === 'SWAP_LIMIT'
          ? `Limite de ${lk.maxSwaps} trocas de TV em 30 dias atingido. Fale com o suporte.`
          : `Este VIP já está em uso em outra TV${lk.otherLabel ? ' (' + lk.otherLabel + ')' : ''}. Você pode usar nesta TV (a outra será desconectada).`,
        swapsLeft: lk.swapsLeft,
        maxSwaps: lk.maxSwaps,
      });
      return;
    }

    const currentExpiry = existing?.expires_at ? new Date(existing.expires_at) : null;
    const baseTime = (currentExpiry && currentExpiry > now) ? currentExpiry : now;
    const newExpiry = new Date(baseTime.getTime() + durationHours * 60 * 60 * 1000);

    // 4) Upsert por device_id (uma linha por aparelho — reativar/renovar
    //    com um código novo simplesmente atualiza a mesma linha).
    const upsertRes = await fetch(`${SUPABASE_URL}/rest/v1/tv_activations?on_conflict=device_id`, {
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
      res.status(502).json({ error: `Falha ao ativar (detalhe técnico): ${errText.slice(0, 200)}` });
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
