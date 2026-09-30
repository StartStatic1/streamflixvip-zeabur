// Cache curto do vip-status, compartilhado no mesmo processo PM2.
// Sempre recalcula isVip a partir de expiresAt (teste de 1h não fica “vivo”).
// redeem / InfinitePay chamam invalidate(userId) na hora do pagamento.

const store = new Map();
const TTL_MS = Math.max(
  5_000,
  Number(process.env.VIP_STATUS_CACHE_TTL_MS || 15_000) || 15_000,
);
const MAX = 2000;

function recompute(body) {
  if (!body) return { isVip: false, expiresAt: null, planLabel: null };
  const exp = body.expiresAt || body.expires_at || null;
  const isVip = !!(exp && new Date(exp).getTime() > Date.now());
  return { ...body, expiresAt: exp, isVip };
}

function get(userId) {
  if (!userId) return null;
  const hit = store.get(String(userId));
  if (!hit) return null;
  if (Date.now() - hit.at > TTL_MS) {
    store.delete(String(userId));
    return null;
  }
  return recompute(hit.body);
}

function set(userId, body) {
  if (!userId) return;
  if (store.size >= MAX) {
    const first = store.keys().next().value;
    if (first) store.delete(first);
  }
  store.set(String(userId), { at: Date.now(), body: recompute(body) });
}

function invalidate(userId) {
  if (!userId) return;
  store.delete(String(userId));
}

module.exports = { get, set, invalidate, recompute, TTL_MS };
