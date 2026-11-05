// lib/xtream-fetch.js — Xtream player_api com contorno de IP de datacenter.
// Ordem: direto → relay Cloudflare (lista) → proxy HTTP/SOCKS (opcional).
// Nunca faz proxy de vídeo; só player_api.php / get.php / xmltv.php.
'use strict';

const { execFile } = require('child_process');
const { promisify } = require('util');
const execFileAsync = promisify(execFile);

const DEFAULT_UAS = [
  'okhttp/4.12.0',
  'Dalvik/2.1.0 (Linux; U; Android 13; SM-S911B Build/TP1A.220624.014)',
  'VLC/3.0.20 LibVLC/3.0.20',
  'IPTVSmarters/1.0',
  'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36',
];

function relayUrl() {
  return String(process.env.XTREAM_LIST_RELAY_URL || process.env.HUGO_RELAY_URL || '').trim().replace(/\/+$/, '');
}
function relayKey() {
  return String(process.env.XTREAM_LIST_RELAY_KEY || process.env.HUGO_RELAY_KEY || '').trim();
}
function egressProxy() {
  return String(process.env.XTREAM_EGRESS_PROXY || '').trim();
}

function normHost(h) {
  let u = String(h || '').trim();
  if (!u) return '';
  if (!/^https?:\/\//i.test(u)) u = 'http://' + u;
  return u.replace(/\/+$/, '');
}

function buildPlayerApiUrl(host, user, pass, action, params) {
  const url = new URL(normHost(host) + '/player_api.php');
  url.searchParams.set('username', user);
  url.searchParams.set('password', pass);
  if (action) url.searchParams.set('action', action);
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
  });
  return url;
}

function ipBlockedError(extra) {
  const err = new Error(
    'Xtream HTTP 403 — painel bloqueou IP de datacenter (Hetzner/CF). ' +
      'No celular costuma passar. Use o atalho "neste aparelho" no Bridge, ' +
      'ou configure XTREAM_EGRESS_PROXY (proxy residencial) no servidor.',
  );
  err.status = 403;
  err.code = 'IP_BLOCKED';
  err.ip_blocked = true;
  if (extra) err.detail = extra;
  return err;
}

async function fetchDirect(targetUrl, ua, timeoutMs) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const r = await fetch(targetUrl, {
      signal: ac.signal,
      redirect: 'follow',
      headers: {
        'User-Agent': ua,
        Accept: 'application/json,text/plain,*/*',
        'Accept-Language': 'pt-BR,pt;q=0.9,en;q=0.8',
      },
    });
    const text = await r.text();
    return { status: r.status, text, via: 'direct' };
  } finally {
    clearTimeout(t);
  }
}

async function fetchViaRelay(targetUrl, ua, timeoutMs) {
  const base = relayUrl();
  const key = relayKey();
  if (!base || !key) return null;
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const r = await fetch(base + (base.endsWith('/') ? '' : '/'), {
      signal: ac.signal,
      redirect: 'follow',
      headers: {
        'x-hugo-relay': key,
        'x-hugo-target': Buffer.from(String(targetUrl), 'utf8').toString('base64'),
        'x-hugo-ua': ua,
        Accept: '*/*',
      },
    });
    const text = await r.text();
    const upstream = Number(r.headers.get('x-hugo-upstream') || r.status) || r.status;
    return { status: upstream, text, via: 'relay' };
  } finally {
    clearTimeout(t);
  }
}

async function fetchViaCurlProxy(targetUrl, ua, timeoutMs) {
  const proxy = egressProxy();
  if (!proxy) return null;
  const args = [
    '-sS',
    '-L',
    '--max-time',
    String(Math.max(5, Math.ceil(timeoutMs / 1000))),
    '-A',
    ua,
    '-H',
    'Accept: application/json,text/plain,*/*',
    '-w',
    '\n__SF_HTTP__:%{http_code}',
  ];
  if (/^socks5h?:\/\//i.test(proxy)) {
    args.push('--socks5-hostname', proxy.replace(/^socks5h?:\/\//i, ''));
  } else {
    args.push('-x', proxy);
  }
  args.push(String(targetUrl));
  try {
    const { stdout } = await execFileAsync('curl', args, {
      maxBuffer: 12 * 1024 * 1024,
      timeout: timeoutMs + 2000,
    });
    const marker = '\n__SF_HTTP__:';
    const idx = stdout.lastIndexOf(marker);
    let text = stdout;
    let status = 0;
    if (idx >= 0) {
      text = stdout.slice(0, idx);
      status = Number(stdout.slice(idx + marker.length).trim()) || 0;
    }
    return { status, text, via: 'proxy' };
  } catch (e) {
    const err = new Error('proxy egress falhou: ' + (e && e.message ? e.message : e));
    err.status = 502;
    throw err;
  }
}

function parseJsonOrThrow(text, status, via) {
  if (status === 403) throw ipBlockedError(via);
  if (!status || status >= 400) {
    const err = new Error('Xtream HTTP ' + status + (via && via !== 'direct' ? ' via ' + via : ''));
    err.status = status;
    throw err;
  }
  try {
    return JSON.parse(text);
  } catch (_) {
    throw new Error('Xtream respondeu sem JSON' + (via && via !== 'direct' ? ' via ' + via : ''));
  }
}

/**
 * Busca player_api com fallback de egress.
 * @returns {Promise<any>} JSON
 */
async function xtreamFetch(host, user, pass, action, params, opts) {
  const timeoutMs = (opts && opts.timeoutMs) || 20000;
  const uas = (opts && opts.uas) || DEFAULT_UAS;
  const targetUrl = buildPlayerApiUrl(host, user, pass, action, params).toString();

  let last = null;
  let saw403 = false;

  for (const ua of uas) {
    try {
      const direct = await fetchDirect(targetUrl, ua, timeoutMs);
      if (direct.status === 403) {
        saw403 = true;
        last = ipBlockedError('direct');
        break; // UA não resolve ban de IP
      }
      return parseJsonOrThrow(direct.text, direct.status, 'direct');
    } catch (e) {
      last = e;
      if (e && e.code === 'IP_BLOCKED') {
        saw403 = true;
        break;
      }
      if (e && e.status && e.status !== 401 && e.status !== 406) break;
    }
  }

  if (saw403 || (last && last.status === 403)) {
    // 1) Cloudflare list relay
    try {
      const viaRelay = await fetchViaRelay(targetUrl, uas[0], timeoutMs);
      if (viaRelay) {
        if (viaRelay.status === 403) {
          // CF também barrado — tenta proxy
        } else {
          return parseJsonOrThrow(viaRelay.text, viaRelay.status, 'relay');
        }
      }
    } catch (e) {
      if (!(e && e.code === 'IP_BLOCKED')) last = e;
    }

    // 2) Proxy residencial / ISP (XTREAM_EGRESS_PROXY)
    try {
      const viaProxy = await fetchViaCurlProxy(targetUrl, uas[0], timeoutMs);
      if (viaProxy) {
        return parseJsonOrThrow(viaProxy.text, viaProxy.status, 'proxy');
      }
    } catch (e) {
      last = e;
    }

    throw ipBlockedError(
      'direct+relay' + (egressProxy() ? '+proxy' : '') + ' blocked',
    );
  }

  throw last || new Error('Falha ao ler o servidor Xtream');
}

function relayConfigured() {
  return !!(relayUrl() && relayKey());
}

function proxyConfigured() {
  return !!egressProxy();
}

module.exports = {
  xtreamFetch,
  normHost,
  buildPlayerApiUrl,
  relayConfigured,
  proxyConfigured,
  ipBlockedError,
  DEFAULT_UAS,
};
