// lib/vip-gate.js
//
// Validação de VIP no SERVIDOR (não no client).
// Usado por endpoints sensíveis (ex: /api/live-tv) para que APK crackeado
// que só força isVip=true localmente NÃO receba URLs de stream.
//
// Aceita (em ordem de preferência):
//   1) Authorization: Bearer <access_token Supabase>  → user real + vip_status
//   2) ?userId=<uuid> ou header X-User-Id            → vip_status (mais fraco;
//      útil na transição até o app enviar JWT sempre)
//   3) ?deviceId=... ou header X-Device-Id           → tv_activations (Android TV)
//
// Modo de operação (env):
//   REQUIRE_VIP_LIVE_TV=1  → hard gate: sem VIP válido → 403
//   (ausente / 0)          → soft: libera tudo, só loga (NÃO quebra app atual)
//
// Depois de publicar app que manda Authorization + forceUpdate, ative o hard
// no VPS: export REQUIRE_VIP_LIVE_TV=1 (ou no .env / systemd / pm2).

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

function isVipGateHard() {
  const v = String(process.env.REQUIRE_VIP_LIVE_TV || '').trim().toLowerCase();
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

function extractBearer(req) {
  const h = req.headers?.authorization || req.headers?.Authorization || '';
  const m = String(h).match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : '';
}

function extractUserId(req) {
  const q = (req.query?.userId || '').toString().trim();
  if (q) return q;
  const h = (req.headers?.['x-user-id'] || '').toString().trim();
  return h || '';
}

function extractDeviceId(req) {
  const q = (req.query?.deviceId || '').toString().trim();
  if (q) return q;
  const h = (req.headers?.['x-device-id'] || '').toString().trim();
  return h || '';
}

/**
 * Valida access_token do Supabase Auth.
 * @returns {{ userId: string, email: string|null } | null}
 */
async function validateSupabaseJwt(accessToken) {
  if (!accessToken) return null;
  try {
    const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        apikey: process.env.SUPABASE_ANON_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY || '',
      },
    });
    if (!r.ok) return null;
    const user = await r.json();
    const id = user?.id;
    if (!id) return null;
    return { userId: String(id), email: user.email || null };
  } catch (e) {
    console.warn('[vip-gate] JWT validate error:', e.message);
    return null;
  }
}

/**
 * Consulta vip_status no Supabase (service role).
 * @returns {{ isVip: boolean, expiresAt: string|null, planLabel: string|null }}
 */
async function checkVipStatus(serviceKey, userId) {
  const empty = { isVip: false, expiresAt: null, planLabel: null };
  if (!userId || !serviceKey) return empty;
  try {
    const url =
      `${SUPABASE_URL}/rest/v1/vip_status?user_id=eq.${encodeURIComponent(userId)}` +
      `&select=expires_at,plan_label`;
    const r = await fetch(url, {
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
      },
    });
    if (!r.ok) return empty;
    const rows = await r.json();
    const row = Array.isArray(rows) && rows.length ? rows[0] : null;
    if (!row?.expires_at) return empty;
    const isVip = new Date(row.expires_at).getTime() > Date.now();
    return {
      isVip,
      expiresAt: row.expires_at,
      planLabel: row.plan_label || null,
    };
  } catch (e) {
    console.warn('[vip-gate] vip_status error:', e.message);
    return empty;
  }
}

/**
 * Consulta tv_activations (Android TV por device_id).
 */
async function checkTvActivation(serviceKey, deviceId) {
  const empty = { isVip: false, expiresAt: null, planLabel: null };
  if (!deviceId || !serviceKey) return empty;
  try {
    const url =
      `${SUPABASE_URL}/rest/v1/tv_activations?device_id=eq.${encodeURIComponent(deviceId)}` +
      `&select=is_active,expires_at,plan_label`;
    const r = await fetch(url, {
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
      },
    });
    if (!r.ok) return empty;
    const rows = await r.json();
    const row = Array.isArray(rows) && rows.length ? rows[0] : null;
    if (!row || !row.is_active || !row.expires_at) return empty;
    const isVip = new Date(row.expires_at).getTime() > Date.now();
    return {
      isVip,
      expiresAt: row.expires_at,
      planLabel: row.plan_label || null,
    };
  } catch (e) {
    console.warn('[vip-gate] tv_activations error:', e.message);
    return empty;
  }
}

/**
 * Resolve se a requisição tem VIP válido.
 *
 * @returns {Promise<{
 *   isVip: boolean,
 *   source: 'jwt'|'userId'|'deviceId'|'none',
 *   userId: string|null,
 *   deviceId: string|null,
 *   expiresAt: string|null,
 *   planLabel: string|null,
 *   hard: boolean,
 * }>}
 */
async function resolveVipAccess(req, serviceKey) {
  const hard = isVipGateHard();
  const token = extractBearer(req);
  const userIdQ = extractUserId(req);
  const deviceId = extractDeviceId(req);

  // 1) JWT (melhor)
  if (token) {
    const auth = await validateSupabaseJwt(token);
    if (auth?.userId) {
      const st = await checkVipStatus(serviceKey, auth.userId);
      return {
        isVip: st.isVip,
        source: 'jwt',
        userId: auth.userId,
        deviceId: null,
        expiresAt: st.expiresAt,
        planLabel: st.planLabel,
        hard,
      };
    }
  }

  // 2) userId explícito (transição / clients antigos que só mandam id)
  if (userIdQ) {
    const st = await checkVipStatus(serviceKey, userIdQ);
    return {
      isVip: st.isVip,
      source: 'userId',
      userId: userIdQ,
      deviceId: null,
      expiresAt: st.expiresAt,
      planLabel: st.planLabel,
      hard,
    };
  }

  // 3) Android TV por device
  if (deviceId) {
    const st = await checkTvActivation(serviceKey, deviceId);
    return {
      isVip: st.isVip,
      source: 'deviceId',
      userId: null,
      deviceId,
      expiresAt: st.expiresAt,
      planLabel: st.planLabel,
      hard,
    };
  }

  return {
    isVip: false,
    source: 'none',
    userId: null,
    deviceId: null,
    expiresAt: null,
    planLabel: null,
    hard,
  };
}

/**
 * Se hard gate ativo e não-VIP, responde 403 e retorna true (caller deve return).
 * Se soft (default), nunca bloqueia — só loga.
 *
 * @returns {Promise<boolean>} true se a resposta já foi enviada (bloqueado)
 */
async function enforceVipOrReject(req, res, serviceKey, { feature = 'live-tv' } = {}) {
  const access = await resolveVipAccess(req, serviceKey);

  if (access.isVip) {
    console.log(
      `[vip-gate] OK feature=${feature} source=${access.source} user=${access.userId || '-'} device=${access.deviceId || '-'}`,
    );
    return false;
  }

  if (!access.hard) {
    // Soft: app atual (sem token) continua funcionando.
    console.log(
      `[vip-gate] soft-pass feature=${feature} source=${access.source} (REQUIRE_VIP_LIVE_TV off)`,
    );
    return false;
  }

  console.warn(
    `[vip-gate] BLOCK feature=${feature} source=${access.source} user=${access.userId || '-'} device=${access.deviceId || '-'}`,
  );

  res.status(403).json({
    error: 'VIP necessário para acessar este recurso.',
    code: 'VIP_REQUIRED',
    feature,
    categories: [],
    channels: [],
    sourcesUsed: 0,
  });
  return true;
}

module.exports = {
  isVipGateHard,
  resolveVipAccess,
  enforceVipOrReject,
  validateSupabaseJwt,
  checkVipStatus,
  checkTvActivation,
};
