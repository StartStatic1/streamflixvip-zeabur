// api/admin-vip.js
// API admin VIP — restaurada (nucleo + IPTV + live TV + fontes + ads basico + avisos)
const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const deviceLock = require('../lib/device-lock');
const codeMeta = require('../lib/code-meta');
const codeWindow = require('../lib/code-window');
const { PLANS, priceForLabel } = require('../lib/plans');

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY nao configurada' }); return; }

  const authHeader = req.headers['authorization'] || '';
  const userToken = authHeader.replace('Bearer ', '').trim();
  if (!userToken) { res.status(401).json({ error: 'Token nao fornecido' }); return; }

  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: serviceKey, Authorization: `Bearer ${userToken}` },
  });
  if (!userRes.ok) { res.status(401).json({ error: 'Token invalido' }); return; }
  const userJson = await userRes.json();
  const userId = userJson && userJson.id;
  if (!userId) { res.status(401).json({ error: 'Token invalido' }); return; }

  const adminRes = await fetch(
    `${SUPABASE_URL}/rest/v1/vip_panel_admins?id=eq.${encodeURIComponent(userId)}&select=id`,
    { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
  );
  const adminRows = await adminRes.json();
  if (!adminRes.ok || !Array.isArray(adminRows) || adminRows.length === 0) {
    res.status(403).json({ error: 'Acesso negado' });
    return;
  }

  let body = req.body;
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch (e) { body = {}; } }
  const action = body && body.action;
  const svcHeaders = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  if (action === 'list') {
    const [codesRes, tvRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/vip_codes?select=*&order=created_at.desc&limit=200`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/tv_activations?select=code,device_label&is_active=eq.true`, { headers: svcHeaders }),
    ]);
    const rows = await codesRes.json();
    const tvRows = await tvRes.json();
    const tvCountByCode = new Map();
    if (Array.isArray(tvRows)) {
      for (const t of tvRows) tvCountByCode.set(t.code, (tvCountByCode.get(t.code) || 0) + 1);
    }
    const rowsWithTv = Array.isArray(rows)
      ? rows.map((c) => ({ ...c, tv_activations_count: tvCountByCode.get(c.code) || 0 }))
      : rows;
    res.status(200).json({ codes: rowsWithTv });
    return;
  }

  if (action === 'create') {
    const { codes } = body;
    if (!Array.isArray(codes) || codes.length === 0) {
      res.status(400).json({ error: 'Informe os codigos' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify(codes),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
  }

  if (action === 'deactivate' || action === 'reactivate') {
    const { code } = body;
    if (!code) { res.status(400).json({ error: 'Informe o code' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({ is_active: action === 'reactivate' }),
    });
    res.status(200).json({ updated: await r.json() });
    return;
  }

  if (action === 'list-users') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,name,first_login_at,last_login_at,last_seen_at,expires_at,plan_label,last_code_used&order=last_login_at.desc&limit=500`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar usuarios', detail: rows }); return; }
    res.status(200).json({ users: rows });
    return;
  }

  if (action === 'activations') {
    const [statusRes, codesRes, tvRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,expires_at,plan_label,last_code_used&order=expires_at.desc&limit=500`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/vip_codes?select=code,used_by,plan_label,is_active`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/tv_activations?select=code,device_id,device_label,expires_at,is_active`, { headers: svcHeaders }),
    ]);
    const statusRows = await statusRes.json();
    const codesRows = await codesRes.json();
    const tvRows = await tvRes.json();
    if (!statusRes.ok) { res.status(502).json({ error: 'Erro vip_status', detail: statusRows }); return; }
    const codesByUser = new Map();
    if (Array.isArray(codesRows)) {
      for (const c of codesRows) if (c.used_by) codesByUser.set(c.used_by, c.code);
    }
    const tvByCode = new Map();
    if (Array.isArray(tvRows)) {
      for (const t of tvRows) {
        if (!t.is_active) continue;
        if (!tvByCode.has(t.code)) tvByCode.set(t.code, []);
        tvByCode.get(t.code).push({ deviceId: t.device_id, deviceLabel: t.device_label, expiresAt: t.expires_at });
      }
    }
    const now = new Date();
    const activations = (Array.isArray(statusRows) ? statusRows : []).map((u) => {
      let origem = 'Desconhecida';
      let codigoRelacionado = null;
      let paymentProvider = null;
      const codeUsed = u.last_code_used ? String(u.last_code_used) : '';
      if (codeUsed.startsWith('PIX-IP-') || codeUsed.startsWith('CARTAO-IP-')) {
        origem = codeUsed.startsWith('CARTAO-IP-') ? 'Cartão InfinitePay' : 'PIX InfinitePay';
        paymentProvider = 'infinitepay';
        codigoRelacionado = codeUsed;
      } else if (codeUsed.startsWith('PIX-MP-')) {
        origem = 'Pagamento Mercado Pago';
        paymentProvider = 'mercadopago';
        codigoRelacionado = codeUsed;
      } else if (codeUsed) {
        origem = 'Codigo: ' + codeUsed;
        codigoRelacionado = codeUsed;
      } else if (codesByUser.has(u.user_id)) {
        codigoRelacionado = codesByUser.get(u.user_id);
        origem = 'Codigo: ' + codigoRelacionado;
      }
      const amountEstimate = priceForLabel(u.plan_label);
      const tvs = codigoRelacionado && !String(codigoRelacionado).startsWith('PIX-') && !String(codigoRelacionado).startsWith('CARTAO-')
        ? (tvByCode.get(codigoRelacionado) || [])
        : [];
      return {
        email: u.email || '(sem e-mail)',
        userId: u.user_id,
        vipAtivo: u.expires_at ? new Date(u.expires_at) > now : false,
        expiresAt: u.expires_at,
        planLabel: u.plan_label,
        origem,
        paymentProvider,
        amountEstimate,
        codigoRelacionado,
        tvsAtivas: tvs.length,
        tvs,
        deviceKey: 'u:' + u.user_id,
        devices: deviceLock.get('u:' + u.user_id),
      };
    });
    res.status(200).json({ activations });
    return;
  }

  // ════ Painel de códigos (admin-codes.html) ════
  if (action === 'codes-panel' || action === 'code-detail' || action === 'delete-codes' || action === 'cleanup-codes') {
    const one = action === 'code-detail' ? String(body.code || '') : '';
    const codeFilter = one ? `&code=eq.${encodeURIComponent(one)}` : '';
    const [codesRes, tvRes, redRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/vip_codes?select=*${codeFilter}&order=created_at.desc&limit=3000`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/tv_activations?select=code,device_id,device_label,expires_at,is_active,activated_at${codeFilter}`, { headers: svcHeaders }),
      one
        ? fetch(`${SUPABASE_URL}/rest/v1/vip_redemptions?select=email,plan_label,duration_hours,redeemed_at,platform,device_id&code=eq.${encodeURIComponent(one)}&order=redeemed_at.desc&limit=50`, { headers: svcHeaders })
        : Promise.resolve(null),
    ]);
    const codes = await codesRes.json();
    if (!codesRes.ok || !Array.isArray(codes)) { res.status(502).json({ error: 'Erro ao listar códigos', detail: codes }); return; }
    const tvRows = tvRes.ok ? await tvRes.json() : [];
    const userIds = [...new Set(codes.map((c) => c.used_by).filter(Boolean))];
    const users = new Map();
    for (let i = 0; i < userIds.length; i += 150) {
      const ids = userIds.slice(i, i + 150).map(encodeURIComponent).join(',');
      const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,name,expires_at,plan_label,last_seen_at&user_id=in.(${ids})`, { headers: svcHeaders });
      const rows = r.ok ? await r.json() : [];
      for (const u of rows) users.set(u.user_id, u);
    }
    const tvByCode = new Map();
    for (const t of Array.isArray(tvRows) ? tvRows : []) {
      if (!tvByCode.has(t.code)) tvByCode.set(t.code, []);
      tvByCode.get(t.code).push(t);
    }
    const metaAll = codeMeta.all();
    const now = Date.now();
    const WEEK = 7 * 24 * 3600 * 1000;
    const out = codes.map((c) => {
      const meta = metaAll[c.code] || {};
      const u = c.used_by ? users.get(c.used_by) : null;
      const tvs = (tvByCode.get(c.code) || []).filter((t) => t.is_active);
      const allTvs = tvByCode.get(c.code) || [];
      // Validade da CONTA/TV (pode vir de outros códigos somados) — usada pra renovação.
      let exp = u && u.expires_at ? new Date(u.expires_at).getTime() : 0;
      for (const t of tvs) exp = Math.max(exp, new Date(t.expires_at).getTime() || 0);
      // Janela do PRÓPRIO código: início = resgate no celular (ou 1ª ativação de TV) + duração.
      const start = codeWindow.firstUse(c, allTvs, meta);
      const hours = Number(c.duration_hours) || 0;
      const codeEnd = start && hours ? start + hours * 3600 * 1000 : 0;
      const isTest = hours <= 24 || /teste/i.test(String(c.plan_label || ''));
      const used = !!c.used_by || allTvs.length > 0;
      let status;
      if (!c.is_active) status = 'desativado';
      else if (!used) status = (meta.soldAt || meta.buyerName || meta.buyerPhone) ? 'vendido_sem_uso' : 'nao_vendido';
      else if (codeEnd && codeEnd > now) status = (!isTest && exp && exp - now < WEEK) ? 'vencendo' : 'ativo';
      else if (isTest || (exp && exp > now)) status = 'concluido';
      else status = 'vencido';
      return {
        code: c.code,
        planLabel: /^\d+h$/.test(String(c.plan_label || '')) ? ((PLANS.find((p) => p.hours === Number(c.duration_hours)) || {}).label || c.plan_label) : c.plan_label,
        durationHours: c.duration_hours,
        price: priceForLabel(c.plan_label) ?? ((PLANS.find((p) => p.hours === Number(c.duration_hours)) || {}).price || null),
        planLabel2: undefined,
        isActive: !!c.is_active,
        createdAt: c.created_at || null,
        usedAt: c.used_at || null,
        status,
        isTest,
        codeStart: start ? new Date(start).toISOString() : null,
        codeEnd: codeEnd ? new Date(codeEnd).toISOString() : null,
        accountExpiresAt: exp ? new Date(exp).toISOString() : null,
        expiresAt: exp ? new Date(exp).toISOString() : null,
        account: u ? { email: u.email || null, name: u.name || null, userId: u.user_id, lastSeen: u.last_seen_at || null } : (c.used_by ? { userId: c.used_by } : null),
        tvCount: tvs.length,
        buyerName: meta.buyerName || '',
        buyerPhone: meta.buyerPhone || '',
        note: meta.note || '',
        batch: meta.batch || '',
        soldAt: meta.soldAt || null,
        devices: deviceLock.get(c.used_by ? 'u:' + c.used_by : 'c:' + c.code),
        ...(() => {
          // Pode apagar? Nunca: vendido, dando VIP agora, ou pago/usado (histórico/receita).
          const sold = !!(meta.soldAt || meta.buyerName || meta.buyerPhone);
          const tvLive = tvs.some((t) => new Date(t.expires_at).getTime() > now);
          if (sold) return { deletable: false, deleteReason: 'Código vendido — não pode apagar (desative se precisar).' };
          if (!used) return { deletable: true, deleteKind: 'nao_usado' };
          if (isTest) {
            if (tvLive || (codeEnd && codeEnd > now)) return { deletable: false, deleteReason: 'Teste ainda em uso.' };
            return { deletable: true, deleteKind: 'teste' };
          }
          return { deletable: false, deleteReason: 'Código usado/pago — fica guardado no histórico e no faturamento.' };
        })(),
      };
    });
    if (action === 'delete-codes' || action === 'cleanup-codes') {
      const want = action === 'cleanup-codes'
        ? out.filter((c) => c.deletable)
        : out.filter((c) => Array.isArray(body.codes) && body.codes.includes(c.code));
      const ok = want.filter((c) => c.deletable);
      const blocked = want.filter((c) => !c.deletable).map((c) => ({ code: c.code, reason: c.deleteReason }));
      if (body.preview === true) {
        res.status(200).json({ preview: true, total: ok.length, naoUsados: ok.filter((c) => c.deleteKind === 'nao_usado').length, testes: ok.filter((c) => c.deleteKind === 'teste').length, codes: ok.map((c) => c.code), blocked });
        return;
      }
      const deleted = [];
      for (let i = 0; i < ok.length; i += 50) {
        const part = ok.slice(i, i + 50);
        const list = part.map((c) => `"${c.code.replace(/"/g, '')}"`).join(',');
        const tests = part.filter((c) => c.deleteKind === 'teste').map((c) => `"${c.code.replace(/"/g, '')}"`).join(',');
        if (tests) await fetch(`${SUPABASE_URL}/rest/v1/tv_activations?code=in.(${encodeURIComponent(tests)})`, { method: 'DELETE', headers: svcHeaders });
        const dr = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=in.(${encodeURIComponent(list)})&used_by=is.null`, { method: 'DELETE', headers: { ...svcHeaders, Prefer: 'return=representation' } });
        let gone = dr.ok ? await dr.json() : [];
        // teste usado por conta: apaga só se ainda é teste (garantia extra no filtro)
        const usedTests = part.filter((c) => c.deleteKind === 'teste' && c.account);
        for (const c of usedTests) {
          const r2 = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(c.code)}&duration_hours=lte.24`, { method: 'DELETE', headers: { ...svcHeaders, Prefer: 'return=representation' } });
          if (r2.ok) gone = gone.concat(await r2.json());
        }
        for (const g of gone) { deleted.push(g.code); codeMeta.remove(g.code); }
      }
      console.log('[admin] apagou códigos', deleted.length, 'por', String(userId).slice(0, 8));
      codeMeta.logAdmin({ at: Date.now(), by: String(userId).slice(0, 8), action: action === 'cleanup-codes' ? 'limpeza' : 'apagar', codes: deleted });
      res.status(200).json({ ok: true, deleted, blocked });
      return;
    }
    if (one) {
      const c = out[0];
      if (!c) { res.status(404).json({ error: 'Código não encontrado' }); return; }
      const reds = redRes && redRes.ok ? await redRes.json() : [];
      const key = c.account?.userId ? 'u:' + c.account.userId : 'c:' + c.code;
      res.status(200).json({
        code: c,
        tvs: (tvByCode.get(c.code) || []).map((t) => ({ label: t.device_label || null, deviceIdTail: String(t.device_id).slice(-6), expiresAt: t.expires_at, activatedAt: t.activated_at, active: !!t.is_active })),
        devices: deviceLock.get(key),
        deviceKey: key,
        redemptions: Array.isArray(reds) ? reds.map((r) => ({ ...r, device_id: r.device_id ? String(r.device_id).slice(-6) : null })) : [],
        history: (codeMeta.get(c.code) || {}).history || [],
      });
      return;
    }
    res.status(200).json({ codes: out, plans: PLANS });
    return;
  }

  if (action === 'code-meta-save') {
    const code = String(body.code || '').trim();
    if (!code) { res.status(400).json({ error: 'Informe o código' }); return; }
    const f = {};
    for (const k of ['buyerName', 'buyerPhone', 'note', 'batch', 'sold']) if (k in body) f[k] = body[k];
    const m = codeMeta.update(code, f, body.sold === true && !(codeMeta.get(code) || {}).soldAt ? 'marcado como vendido' : 'dados do comprador editados');
    res.status(200).json({ ok: true, meta: m });
    return;
  }

  if (action === 'create-batch') {
    const plan = PLANS.find((p) => p.id === body.planId);
    const hours = plan ? plan.hours : Math.max(1, Math.min(87600, parseInt(body.hours, 10) || 0));
    if (!hours) { res.status(400).json({ error: 'Plano inválido' }); return; }
    const label = plan ? plan.label : `${hours}h`;
    const qty = Math.max(1, Math.min(200, parseInt(body.qty, 10) || 1));
    const prefix = String(body.prefix || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 10) || 'VIP';
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const rnd = () => Array.from({ length: 6 }, () => chars[require('crypto').randomInt(chars.length)]).join('');
    const set = new Set();
    while (set.size < qty) set.add(`${prefix}-${rnd()}`);
    const rows = [...set].map((code) => ({ code, duration_hours: hours, plan_label: label }));
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes`, {
      method: 'POST', headers: { ...svcHeaders, Prefer: 'return=representation' }, body: JSON.stringify(rows),
    });
    const created = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar (tente de novo)', detail: created }); return; }
    const batch = String(body.batch || '').trim();
    for (const c of created) {
      codeMeta.update(c.code, {
        batch, buyerName: body.buyerName || '', buyerPhone: body.buyerPhone || '', note: body.note || '',
        sold: !!(body.buyerName || body.buyerPhone),
      }, `criado${batch ? ' no lote ' + batch : ''} (${label})`);
    }
    res.status(200).json({ created: created.map((c) => c.code), planLabel: label });
    return;
  }

  // Um clique: desativa o código, desliga as TVs dele e libera celular/TV da conta.
  if (action === 'deactivate-full') {
    const code = String(body.code || '').trim();
    if (!code) { res.status(400).json({ error: 'Informe o código' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({ is_active: false }),
    });
    const updated = await r.json();
    if (!r.ok || !Array.isArray(updated) || !updated.length) { res.status(502).json({ error: 'Código não encontrado' }); return; }
    const usedBy = updated[0].used_by;
    await fetch(`${SUPABASE_URL}/rest/v1/tv_activations?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ is_active: false }),
    });
    if (usedBy) {
      deviceLock.clear('u:' + usedBy);
    }
    deviceLock.clear('c:' + code);
    codeMeta.logAdmin({ at: Date.now(), by: String(userId).slice(0, 8), action: 'desativar', codes: [code] });
    res.status(200).json({ ok: true, code });
    return;
  }

  // ════ Vendas InfinitePay (cartão + PIX) ════
  if (action === 'vendas-infinitepay') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,name,expires_at,plan_label,last_code_used,updated_at&or=(last_code_used.like.PIX-IP-*,last_code_used.like.CARTAO-IP-*)&order=updated_at.desc&limit=500`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar vendas', detail: rows }); return; }
    const now = new Date();
    const vendas = (Array.isArray(rows) ? rows : []).map((u) => {
      const code = String(u.last_code_used || '');
      const isCard = code.startsWith('CARTAO-IP-');
      const tx = code.replace(/^(PIX|CARTAO)-IP-/, '');
      const amount = priceForLabel(u.plan_label);
      return {
        userId: u.user_id,
        email: u.email || '(sem e-mail)',
        name: u.name || null,
        planLabel: u.plan_label,
        amount,
        method: isCard ? 'Cartão' : 'PIX',
        methodKey: isCard ? 'card' : 'pix',
        tx: tx || null,
        paidAt: u.updated_at || null,
        expiresAt: u.expires_at,
        vipAtivo: u.expires_at ? new Date(u.expires_at) > now : false,
      };
    });
    const totalCard = vendas.filter((v) => v.methodKey === 'card').reduce((a, v) => a + (v.amount || 0), 0);
    const totalPix = vendas.filter((v) => v.methodKey === 'pix').reduce((a, v) => a + (v.amount || 0), 0);
    res.status(200).json({ vendas, totalCard, totalPix, total: totalCard + totalPix });
    return;
  }

  res.status(400).json({ error: 'Ação desconhecida' });
};
