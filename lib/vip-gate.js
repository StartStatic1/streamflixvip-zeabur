// lib/vip-gate.js
//
// Validação de VIP no SERVIDOR (não no client).
// Usado por endpoints sensíveis (ex: /api/live-tv, /api/media-sources).
//
// Aceita (em ordem):
//   1) Authorization: Bearer <access_token Supabase>  → user real + vip_status
//   2) ?userId= / X-User-Id  → só se REQUIRE_JWT_* não estiver ligado
//   3) ?deviceId= / X-Device-Id → tv_activations (Android TV)
//
// Env:
//   REQUIRE_VIP_LIVE_TV=1     → hard: live-tv exige VIP
//   REQUIRE_JWT_LIVE_TV=1     → live-tv exige JWT (não aceita X-User-Id forjado)
//   REQUIRE_JWT_MEDIA=1       → media-sources exige JWT real

const SUPABASE_URL =
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co';

function isVipGateHard() {
  const v = String(process.env.REQUIRE_VIP_LIVE_TV || '').trim().toLowerCase();
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

function isJwtRequired(feature) {
  if (feature === 'live-tv') {
    const v = String(process.env.REQUIRE_JWT_LIVE_TV || '').trim().toLowerCase();
    return v === '1' || v === 'true' || v === 'yes' || v === 'on';
  }
  if (feature === 'media-sources' || feature === 'media') {
    const v = String(process.env.REQUIRE_JWT_MEDIA || '').trim().toLowerCase();
    return v === '1' || v === 'true' || v === 'yes' || v === 'on';
  }
  return false;
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
 * @param {object} [opts]
 * @param {string} [opts.feature]  live-tv | media-sources
 * @param {boolean} [opts.requireJwt] força só JWT nesta chamada
 */
async function resolveVipAccess(req, serviceKey, opts = {}) {
  const feature = opts.feature || 'generic';
  const hard = isVipGateHard();
  const jwtOnly = opts.requireJwt === true || isJwtRequired(feature);
  const token = extractBearer(req);
  const userIdQ = extractUserId(req);
  const deviceId = extractDeviceId(req);

  // 1) JWT (melhor — e único se jwtOnly)
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
        jwtOnly,
      };
    }
  }

  // 2) userId solto — BLOQUEADO se jwtOnly (MOD forja UUID)
  if (userIdQ && !jwtOnly) {
    const st = await checkVipStatus(serviceKey, userIdQ);
    return {
      isVip: st.isVip,
      source: 'userId',
      userId: userIdQ,
      deviceId: null,
      expiresAt: st.expiresAt,
      planLabel: st.planLabel,
      hard,
      jwtOnly,
    };
  }

  // 3) Android TV por device (não é o caso do MOD mobile)
  if (deviceId && !jwtOnly) {
    const st = await checkTvActivation(serviceKey, deviceId);
    return {
      isVip: st.isVip,
      source: 'deviceId',
      userId: null,
      deviceId,
      expiresAt: st.expiresAt,
      planLabel: st.planLabel,
      hard,
      jwtOnly,
    };
  }

  // userId foi enviado mas jwtOnly está on → trata como não autenticado
  if (userIdQ && jwtOnly) {
    console.warn(`[vip-gate] reject forged userId (jwtOnly) feature=${feature} userId=${userIdQ.slice(0, 8)}…`);
  }

  return {
    isVip: false,
    source: 'none',
    userId: null,
    deviceId: null,
    expiresAt: null,
    planLabel: null,
    hard,
    jwtOnly,
  };
}

async function enforceVipOrReject(req, res, serviceKey, { feature = 'live-tv' } = {}) {
  const access = await resolveVipAccess(req, serviceKey, { feature });

  if (access.isVip) {
    console.log(
      `[vip-gate] OK feature=${feature} source=${access.source} user=${access.userId || '-'} device=${access.deviceId || '-'}`,
    );
    return false;
  }

  if (!access.hard) {
    console.log(
      `[vip-gate] soft-pass feature=${feature} source=${access.source} (REQUIRE_VIP_LIVE_TV off)`,
    );
    return false;
  }

  console.warn(
    `[vip-gate] BLOCK feature=${feature} source=${access.source} user=${access.userId || '-'} device=${access.deviceId || '-'} jwtOnly=${access.jwtOnly}`,
  );

  res.status(403).json({
    error: 'VIP necessário para acessar este recurso.',
    code: 'VIP_REQUIRED',
    feature,
    categories: [],
    channels: [],
    sourcesUsed: 0,
    sources: [],
  });
  return true;
}

module.exports = {
  isVipGateHard,
  isJwtRequired,
  resolveVipAccess,
  enforceVipOrReject,
  validateSupabaseJwt,
  checkVipStatus,
  checkTvActivation,
};
