// lib/referrals.js — "Indique e ganhe".
//
// Cada conta tem um código pessoal (tabela referral_codes). Uma conta NOVA pode usar o código de
// um amigo uma única vez (tabela referrals, chave = conta indicada). Quando a conta indicada faz a
// PRIMEIRA compra VIP paga (Mercado Pago / InfinitePay, pagamento já confirmado no webhook), quem
// indicou ganha REWARD_DAYS dias de VIP — uma vez só por conta indicada (troca atômica pending→rewarded).

const crypto = require('crypto');
const sales = require('./sales');

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

/** Dias de VIP que quem indicou ganha quando o amigo paga o primeiro VIP. */
const REWARD_DAYS = Number(process.env.REFERRAL_REWARD_DAYS || 7) || 7;
/** Só contas criadas há até N dias podem usar um código ("conta nova"). */
const MAX_ACCOUNT_AGE_DAYS = Number(process.env.REFERRAL_MAX_ACCOUNT_AGE_DAYS || 30) || 30;
const SITE_URL = 'https://www.streamflixvip.online';
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; // sem 0/O, 1/I/L
const CODE_LEN = 6;

function serviceKey() { return process.env.SUPABASE_SERVICE_ROLE_KEY || ''; }
function sbHeaders(extra) {
  const k = serviceKey();
  return { apikey: k, Authorization: `Bearer ${k}`, 'Content-Type': 'application/json', ...(extra || {}) };
}
async function sb(path, opts) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, { ...(opts || {}), headers: sbHeaders(opts && opts.headers) });
  const text = await r.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch (_) { body = text; }
  return { ok: r.ok, status: r.status, body };
}

function normalizeCode(c) {
  return String(c || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 12);
}
function randomCode() {
  const bytes = crypto.randomBytes(CODE_LEN);
  let s = '';
  for (let i = 0; i < CODE_LEN; i++) s += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length];
  return s;
}
function linkFor(code) { return `${SITE_URL}/?ref=${encodeURIComponent(code)}`; }
function shareTextFor(code) {
  return [
    '🎬 Vem pro StreamFlix VIP! Filmes, séries e TV ao vivo no celular e na TV.',
    '',
    `Baixe grátis: ${SITE_URL}`,
    '',
    `No app, vá em Perfil › Indique e ganhe e use meu código: ${code}`,
  ].join('\n');
}

// ── Sessão do app (JWT do Supabase) ─────────────────────────────────────────
const userCache = new Map();
async function userFromRequest(req) {
  const token = String((req.headers && req.headers.authorization) || '').replace(/^Bearer\s+/i, '').trim();
  if (!token || token.length < 20) return null;
  const hit = userCache.get(token);
  if (hit && Date.now() - hit.at < 60_000) return hit.user;
  try {
    const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: { apikey: process.env.SUPABASE_ANON_KEY || serviceKey(), Authorization: `Bearer ${token}` },
    });
    if (!r.ok) return null;
    const u = await r.json();
    if (!u || !u.id) return null;
    const user = { id: String(u.id), email: u.email || null, createdAt: u.created_at || null };
    userCache.set(token, { user, at: Date.now() });
    if (userCache.size > 3000) userCache.clear();
    return user;
  } catch (e) {
    console.warn('[referral] auth', e.message || e);
    return null;
  }
}

// ── Código pessoal ──────────────────────────────────────────────────────────
async function getOrCreateCode(userId) {
  const cur = await sb(`referral_codes?user_id=eq.${encodeURIComponent(userId)}&select=code`);
  if (cur.ok && Array.isArray(cur.body) && cur.body[0]) return cur.body[0].code;
  for (let i = 0; i < 6; i++) {
    const code = randomCode();
    const ins = await sb('referral_codes', {
      method: 'POST',
      headers: { Prefer: 'return=representation' },
      body: JSON.stringify({ user_id: userId, code }),
    });
    if (ins.ok && Array.isArray(ins.body) && ins.body[0]) return ins.body[0].code;
    // 409: ou o código já existe (tenta outro) ou a conta ganhou código em paralelo (relê)
    const again = await sb(`referral_codes?user_id=eq.${encodeURIComponent(userId)}&select=code`);
    if (again.ok && Array.isArray(again.body) && again.body[0]) return again.body[0].code;
  }
  throw new Error('não consegui gerar o código');
}

async function ownerOfCode(code) {
  const r = await sb(`referral_codes?code=eq.${encodeURIComponent(code)}&select=user_id`);
  return r.ok && Array.isArray(r.body) && r.body[0] ? String(r.body[0].user_id) : null;
}

async function summary(userId) {
  const code = await getOrCreateCode(userId);
  const [mine, used] = await Promise.all([
    sb(`referrals?referrer_user_id=eq.${encodeURIComponent(userId)}&select=status,reward_days`),
    sb(`referrals?referred_user_id=eq.${encodeURIComponent(userId)}&select=code,status,created_at`),
  ]);
  const rows = mine.ok && Array.isArray(mine.body) ? mine.body : [];
  const usedRow = used.ok && Array.isArray(used.body) ? used.body[0] : null;
  return {
    code,
    link: linkFor(code),
    shareText: shareTextFor(code),
    rewardDays: REWARD_DAYS,
    invited: rows.length,
    paid: rows.filter((r) => r.status === 'rewarded').length,
    daysEarned: rows.reduce((a, r) => a + (r.status === 'rewarded' ? Number(r.reward_days) || 0 : 0), 0),
    usedCode: usedRow ? usedRow.code : null,
  };
}

/** Pagou VIP antes? (log de vendas do servidor + marca de pagamento no vip_status) */
async function hasPaidBefore(userId, exceptSaleId) {
  const paidSale = sales.all().some((s) => s && s.userId === userId && s.status === 'aprovado'
    && (s.kind || 'vip') === 'vip' && s.id !== exceptSaleId);
  if (paidSale) return true;
  if (exceptSaleId) return false; // na hora do prêmio, o vip_status já tem a compra atual
  const vs = await sb(`vip_status?user_id=eq.${encodeURIComponent(userId)}&select=last_code_used`);
  const last = vs.ok && Array.isArray(vs.body) && vs.body[0] ? String(vs.body[0].last_code_used || '') : '';
  return /^PIX-MP-|-IP-/.test(last);
}

const applyHits = new Map();
function tooManyTries(userId) {
  const now = Date.now();
  const arr = (applyHits.get(userId) || []).filter((t) => now - t < 3600_000);
  arr.push(now);
  applyHits.set(userId, arr);
  if (applyHits.size > 5000) applyHits.clear();
  return arr.length > 10;
}

/** Usa o código de um amigo. Devolve { ok, message } (mensagens em pt-BR para o app mostrar). */
async function applyCode(user, rawCode) {
  const code = normalizeCode(rawCode);
  if (code.length < 4) return { ok: false, status: 400, message: 'Digite o código de convite.' };
  if (tooManyTries(user.id)) return { ok: false, status: 429, message: 'Muitas tentativas. Tente de novo mais tarde.' };

  const already = await sb(`referrals?referred_user_id=eq.${encodeURIComponent(user.id)}&select=code`);
  if (already.ok && Array.isArray(already.body) && already.body[0]) {
    return { ok: false, status: 409, message: `Você já usou o código ${already.body[0].code}.` };
  }
  const owner = await ownerOfCode(code);
  if (!owner) return { ok: false, status: 404, message: 'Código não encontrado. Confira e tente de novo.' };
  if (owner === user.id) return { ok: false, status: 400, message: 'Você não pode usar o seu próprio código.' };

  const created = user.createdAt ? new Date(user.createdAt).getTime() : 0;
  if (!created || Date.now() - created > MAX_ACCOUNT_AGE_DAYS * 86400_000) {
    return { ok: false, status: 403, message: `O código de convite é só para contas novas (até ${MAX_ACCOUNT_AGE_DAYS} dias).` };
  }
  if (await hasPaidBefore(user.id, null)) {
    return { ok: false, status: 403, message: 'O código de convite vale só antes da primeira assinatura VIP.' };
  }

  const ins = await sb('referrals', {
    method: 'POST',
    headers: { Prefer: 'return=minimal' },
    body: JSON.stringify({ referred_user_id: user.id, referrer_user_id: owner, code }),
  });
  if (!ins.ok) {
    if (ins.status === 409) return { ok: false, status: 409, message: 'Você já usou um código de convite.' };
    console.error('[referral] insert', ins.status, JSON.stringify(ins.body).slice(0, 200));
    return { ok: false, status: 502, message: 'Não foi possível salvar agora. Tente de novo.' };
  }
  return { ok: true, status: 200, message: `Código ${code} aplicado! Quando você assinar o VIP, seu amigo ganha ${REWARD_DAYS} dias.` };
}

// ── Prêmio (chamado pelos webhooks depois que o VIP pago foi liberado) ──────
async function addVipDays(userId, days, note) {
  const now = new Date();
  const cur = await sb(`vip_status?user_id=eq.${encodeURIComponent(userId)}&select=expires_at,plan_label,email`);
  const row = cur.ok && Array.isArray(cur.body) ? cur.body[0] : null;
  const curExp = row && row.expires_at ? new Date(row.expires_at) : null;
  const base = curExp && curExp > now ? curExp : now;
  const newExp = new Date(base.getTime() + days * 86400_000);
  const body = {
    user_id: userId,
    expires_at: newExp.toISOString(),
    plan_label: curExp && curExp > now && row.plan_label ? row.plan_label : `Indicação (+${days} dias)`,
    last_code_used: String(note || 'INDICACAO').slice(0, 80),
    updated_at: now.toISOString(),
  };
  const up = await sb('vip_status', {
    method: 'POST',
    headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
    body: JSON.stringify(body),
  });
  if (!up.ok) throw new Error('vip_status ' + up.status);
  try { require('./vip-status-cache').invalidate(userId); } catch (_) {}
  return newExp.toISOString();
}

async function onVipPaid({ userId, saleId }) {
  if (!userId || !serviceKey()) return { rewarded: false, reason: 'sem-dados' };
  const id = encodeURIComponent(userId);
  const r = await sb(`referrals?referred_user_id=eq.${id}&status=eq.pending&select=referrer_user_id,code`);
  const ref = r.ok && Array.isArray(r.body) ? r.body[0] : null;
  if (!ref) return { rewarded: false, reason: 'sem-indicacao' };

  if (await hasPaidBefore(userId, saleId)) {
    await sb(`referrals?referred_user_id=eq.${id}&status=eq.pending`, {
      method: 'PATCH', headers: { Prefer: 'return=minimal' },
      body: JSON.stringify({ status: 'ineligible', note: 'nao_e_a_primeira_compra', sale_id: String(saleId || '') }),
    });
    return { rewarded: false, reason: 'nao-primeira-compra' };
  }

  // troca atômica: só um webhook consegue passar de pending para rewarded
  const claim = await sb(`referrals?referred_user_id=eq.${id}&status=eq.pending`, {
    method: 'PATCH', headers: { Prefer: 'return=representation' },
    body: JSON.stringify({ status: 'rewarded', reward_days: REWARD_DAYS, rewarded_at: new Date().toISOString(), sale_id: String(saleId || '') }),
  });
  if (!claim.ok || !Array.isArray(claim.body) || !claim.body[0]) return { rewarded: false, reason: 'ja-premiado' };

  try {
    const until = await addVipDays(ref.referrer_user_id, REWARD_DAYS, `INDICACAO-${String(userId).slice(0, 8)}`);
    console.log(`[referral] +${REWARD_DAYS}d para ${ref.referrer_user_id} (indicou ${userId}) até ${until}`);
    return { rewarded: true, referrer: ref.referrer_user_id, until };
  } catch (e) {
    console.error('[referral] falha ao dar os dias, volta para pending:', e.message || e);
    await sb(`referrals?referred_user_id=eq.${id}&status=eq.rewarded`, {
      method: 'PATCH', headers: { Prefer: 'return=minimal' },
      body: JSON.stringify({ status: 'pending', reward_days: null, rewarded_at: null, note: 'erro_ao_premiar' }),
    });
    return { rewarded: false, reason: 'erro' };
  }
}

/** Não deixa o webhook de pagamento falhar por causa da indicação. */
function onVipPaidSafe(args) {
  Promise.resolve().then(() => onVipPaid(args)).catch((e) => console.error('[referral] onVipPaid', e.message || e));
}

// ── TV: dono da TV ativada (só para mostrar o código) ───────────────────────
async function userIdForTvDevice(deviceId) {
  const d = String(deviceId || '').trim();
  if (!d || d.length > 200) return null;
  const a = await sb(`tv_activations?device_id=eq.${encodeURIComponent(d)}&select=code,is_active`);
  const row = a.ok && Array.isArray(a.body) ? a.body[0] : null;
  if (!row || !row.code) return null;
  if (String(row.code).startsWith('U-')) return String(row.code).slice(2);
  const c = await sb(`vip_codes?code=eq.${encodeURIComponent(row.code)}&select=used_by`);
  const vc = c.ok && Array.isArray(c.body) ? c.body[0] : null;
  return vc && vc.used_by ? String(vc.used_by) : null;
}

module.exports = {
  REWARD_DAYS, MAX_ACCOUNT_AGE_DAYS, SITE_URL,
  normalizeCode, randomCode, linkFor, shareTextFor,
  userFromRequest, getOrCreateCode, summary, applyCode, onVipPaid, onVipPaidSafe, userIdForTvDevice,
};
