// lib/client-gate.js
//
// Proteção no SERVIDOR contra APK mod/antigo.
// Atualização forçada no client NÃO alcança MOD — o corte tem que ser aqui.
//
// Headers esperados do app oficial:
//   Authorization: Bearer <jwt Supabase>
//   X-App-Version: 12.0.1          (versionName)
//   X-App-Version-Code: 120001     (versionCode numérico, opcional)
//
// Env:
//   MIN_APP_VERSION=12.0.1         → abaixo disso = 403 (sem fontes)
//   MIN_APP_VERSION_CODE=120001    → alternativa numérica (vence se ambos setados)
//   REQUIRE_AUTH_MEDIA=1           → media-sources exige login
//   REQUIRE_JWT_MEDIA=1            → só JWT real (ignora X-User-Id forjado)
//   REQUIRE_VIP_LIVE_TV=1          → live-tv só VIP (já existia em vip-gate)
//   REQUIRE_JWT_LIVE_TV=1          → live-tv só com JWT (não aceita userId solto)

function parseVersionName(v) {
  const s = String(v || '').trim();
  if (!s) return null;
  const parts = s.replace(/^v/i, '').split(/[.+\-]/).map((p) => parseInt(p, 10));
  if (!parts.length || parts.some((n) => !Number.isFinite(n))) return null;
  while (parts.length < 3) parts.push(0);
  return parts.slice(0, 4);
}

function compareVersionName(a, b) {
  const pa = parseVersionName(a);
  const pb = parseVersionName(b);
  if (!pa || !pb) return 0;
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = pa[i] || 0;
    const y = pb[i] || 0;
    if (x !== y) return x - y;
  }
  return 0;
}

function extractAppVersion(req) {
  const name =
    (req.headers?.['x-app-version'] || req.headers?.['X-App-Version'] || '').toString().trim() ||
    (req.query?.appVersion || '').toString().trim();
  const codeRaw =
    (req.headers?.['x-app-version-code'] || req.headers?.['X-App-Version-Code'] || '').toString().trim() ||
    (req.query?.appVersionCode || '').toString().trim();
  const code = codeRaw ? parseInt(codeRaw, 10) : null;
  return {
    versionName: name || null,
    versionCode: Number.isFinite(code) ? code : null,
  };
}

function isEnvOn(name) {
  const v = String(process.env[name] || '').trim().toLowerCase();
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

/**
 * @returns {{ ok: true } | { ok: false, status: number, body: object }}
 */
function checkMinAppVersion(req) {
  const minCode = process.env.MIN_APP_VERSION_CODE
    ? parseInt(String(process.env.MIN_APP_VERSION_CODE).trim(), 10)
    : null;
  const minName = String(process.env.MIN_APP_VERSION || '').trim();

  if (!minName && !(Number.isFinite(minCode) && minCode > 0)) {
    return { ok: true };
  }

  const { versionName, versionCode } = extractAppVersion(req);

  // Sem header = cliente antigo / MOD que não manda versão → bloqueia se min estiver setado
  if (!versionName && versionCode == null) {
    return {
      ok: false,
      status: 403,
      body: {
        error: 'Atualize o aplicativo para continuar.',
        code: 'APP_UPDATE_REQUIRED',
        minVersion: minName || null,
        minVersionCode: Number.isFinite(minCode) ? minCode : null,
        sources: [],
        categories: [],
        channels: [],
      },
    };
  }

  if (Number.isFinite(minCode) && minCode > 0) {
    if (versionCode == null || versionCode < minCode) {
      return {
        ok: false,
        status: 403,
        body: {
          error: 'Atualize o aplicativo para continuar.',
          code: 'APP_UPDATE_REQUIRED',
          minVersion: minName || null,
          minVersionCode: minCode,
          clientVersionCode: versionCode,
          sources: [],
          categories: [],
          channels: [],
        },
      };
    }
    return { ok: true };
  }

  if (minName) {
    if (!versionName || compareVersionName(versionName, minName) < 0) {
      return {
        ok: false,
        status: 403,
        body: {
          error: 'Atualize o aplicativo para continuar.',
          code: 'APP_UPDATE_REQUIRED',
          minVersion: minName,
          clientVersion: versionName,
          sources: [],
          categories: [],
          channels: [],
        },
      };
    }
  }

  return { ok: true };
}

/**
 * Bloqueia e responde se versão mínima não for atendida.
 * @returns {boolean} true se já respondeu (caller deve return)
 */
function enforceMinAppVersion(req, res) {
  const result = checkMinAppVersion(req);
  if (result.ok) return false;
  console.warn(
    `[client-gate] BLOCK version client=${req.headers?.['x-app-version'] || '-'} code=${req.headers?.['x-app-version-code'] || '-'} min=${process.env.MIN_APP_VERSION || process.env.MIN_APP_VERSION_CODE || '-'}`,
  );
  res.status(result.status).json(result.body);
  return true;
}

module.exports = {
  parseVersionName,
  compareVersionName,
  extractAppVersion,
  checkMinAppVersion,
  enforceMinAppVersion,
  isEnvOn,
};
