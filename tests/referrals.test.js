// Indique e ganhe: regras de uso do código e prêmio único, com um Supabase falso em memória.
const test = require('node:test');
const assert = require('node:assert');
const os = require('os');
const path = require('path');
const fs = require('fs');

process.env.SALES_FILE = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'sales-')), 'sales.json');
process.env.SUPABASE_SERVICE_ROLE_KEY = 'test-service';

const db = { referral_codes: [], referrals: [], vip_status: [], tv_activations: [], vip_codes: [] };
const pk = { referral_codes: ['user_id', 'code'], referrals: ['referred_user_id'], vip_status: ['user_id'] };
const users = {}; // token -> user

function filt(url) {
  const out = [];
  for (const [k, v] of url.searchParams) {
    if (['select', 'order', 'limit', 'on_conflict'].includes(k)) continue;
    const m = /^(eq|is)\.(.*)$/.exec(v);
    if (m) out.push([k, m[2]]);
  }
  return (r) => out.every(([k, v]) => String(r[k]) === v);
}
global.fetch = async (u, opts = {}) => {
  const url = new URL(u);
  const json = (status, body) => ({ ok: status < 300, status, json: async () => body, text: async () => JSON.stringify(body) });
  if (url.pathname === '/auth/v1/user') {
    const t = String(opts.headers.Authorization).replace('Bearer ', '');
    return users[t] ? json(200, users[t]) : json(401, {});
  }
  const table = url.pathname.replace('/rest/v1/', '');
  const rows = db[table];
  const method = opts.method || 'GET';
  const f = filt(url);
  if (method === 'GET') return json(200, rows.filter(f));
  const body = JSON.parse(opts.body);
  if (method === 'POST') {
    const merge = String(opts.headers.Prefer || '').includes('merge-duplicates');
    const keys = pk[table] || [];
    const dup = rows.find((r) => keys.some((k) => r[k] === body[k]));
    if (dup) { if (merge) { Object.assign(dup, body); return json(201, [dup]); } return json(409, { code: '23505' }); }
    const row = { status: table === 'referrals' ? 'pending' : undefined, ...body };
    rows.push(row);
    return json(201, [row]);
  }
  if (method === 'PATCH') {
    const hit = rows.filter(f);
    hit.forEach((r) => Object.assign(r, body));
    return json(200, hit);
  }
  return json(405, {});
};

const ref = require('../lib/referrals');
const sales = require('../lib/sales');

const A = { id: 'aaaaaaaa-0000-0000-0000-000000000001', email: 'a@x', created_at: new Date(Date.now() - 400 * 86400e3).toISOString() };
const B = { id: 'bbbbbbbb-0000-0000-0000-000000000002', email: 'b@x', created_at: new Date().toISOString() };
const C = { id: 'cccccccc-0000-0000-0000-000000000003', email: 'c@x', created_at: new Date().toISOString() };
users.tokenA_xxxxxxxxxxxxxxxxxxxx = A;
users.tokenB_xxxxxxxxxxxxxxxxxxxx = B;
const req = (t) => ({ headers: { authorization: 'Bearer ' + t } });

test('código pessoal é estável e no formato certo', async () => {
  const c1 = await ref.getOrCreateCode(A.id);
  const c2 = await ref.getOrCreateCode(A.id);
  assert.equal(c1, c2);
  assert.match(c1, /^[A-HJKMNP-Z2-9]{6}$/);
});

test('autenticação pelo JWT do app', async () => {
  assert.equal((await ref.userFromRequest(req('tokenA_xxxxxxxxxxxxxxxxxxxx'))).id, A.id);
  assert.equal(await ref.userFromRequest(req('invalido_xxxxxxxxxxxxxxxxxxxx')), null);
  assert.equal(await ref.userFromRequest({ headers: {} }), null);
});

test('regras de uso: próprio código, conta antiga, código inexistente, uma vez só', async () => {
  const codeA = await ref.getOrCreateCode(A.id);
  const codeB = await ref.getOrCreateCode(B.id);
  let r = await ref.applyCode({ id: B.id, createdAt: B.created_at }, codeB);
  assert.equal(r.ok, false, 'próprio código');
  r = await ref.applyCode({ id: A.id, createdAt: A.created_at }, codeB);
  assert.equal(r.ok, false, 'conta antiga');
  r = await ref.applyCode({ id: B.id, createdAt: B.created_at }, 'ZZZZZZ');
  assert.equal(r.ok, false, 'inexistente');
  r = await ref.applyCode({ id: B.id, createdAt: B.created_at }, codeA.toLowerCase());
  assert.equal(r.ok, true, r.message);
  r = await ref.applyCode({ id: B.id, createdAt: B.created_at }, codeA);
  assert.equal(r.ok, false, 'segunda vez');
  const s = await ref.summary(A.id);
  assert.equal(s.invited, 1);
  assert.equal(s.paid, 0);
});

test('prêmio: só depois do pagamento, uma vez só, +REWARD_DAYS para quem indicou', async () => {
  sales.record({ id: 'mp:1', status: 'aprovado', kind: 'vip', userId: B.id });
  const r1 = await ref.onVipPaid({ userId: B.id, saleId: 'mp:1' });
  assert.equal(r1.rewarded, true);
  const vs = db.vip_status.find((v) => v.user_id === A.id);
  const days = (new Date(vs.expires_at) - Date.now()) / 86400e3;
  assert.ok(Math.abs(days - ref.REWARD_DAYS) < 0.01, 'dias ' + days);
  // webhook repetido / 2ª compra não premia de novo
  sales.record({ id: 'mp:2', status: 'aprovado', kind: 'vip', userId: B.id });
  const r2 = await ref.onVipPaid({ userId: B.id, saleId: 'mp:2' });
  assert.equal(r2.rewarded, false);
  const s = await ref.summary(A.id);
  assert.equal(s.paid, 1);
  assert.equal(s.daysEarned, ref.REWARD_DAYS);
  // quem paga sem indicação não gera nada
  assert.equal((await ref.onVipPaid({ userId: A.id, saleId: 'mp:3' })).rewarded, false);
});

test('não premia se a compra paga não for a primeira', async () => {
  const codeA = await ref.getOrCreateCode(A.id);
  // C usa o código, mas já tinha uma compra antes (registrada depois de aplicar)
  assert.equal((await ref.applyCode({ id: C.id, createdAt: C.created_at }, codeA)).ok, true);
  sales.record({ id: 'ip:old', status: 'aprovado', kind: 'vip', userId: C.id });
  sales.record({ id: 'ip:new', status: 'aprovado', kind: 'vip', userId: C.id });
  const r = await ref.onVipPaid({ userId: C.id, saleId: 'ip:new' });
  assert.equal(r.rewarded, false);
  assert.equal(db.referrals.find((x) => x.referred_user_id === C.id).status, 'ineligible');
});

test('TV: dono pela ativação (U-<conta> e código VIP)', async () => {
  db.tv_activations.push({ device_id: 'tv1', code: 'U-' + A.id, is_active: true });
  db.tv_activations.push({ device_id: 'tv2', code: 'VIP123', is_active: true });
  db.vip_codes.push({ code: 'VIP123', used_by: B.id });
  assert.equal(await ref.userIdForTvDevice('tv1'), A.id);
  assert.equal(await ref.userIdForTvDevice('tv2'), B.id);
  assert.equal(await ref.userIdForTvDevice('nada'), null);
});
