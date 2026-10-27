// lib/crash-store.js
//
// Guarda os relatorios de crash do app TV num JSON fora do repo
// (padrao ~/streamflix-data/tv-crashes.json; troque com CRASH_LOG_FILE).
// Mantem so os ultimos MAX_ENTRIES. Nada de token/senha: tudo passa por
// scrub() antes de gravar.
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');

const MAX_ENTRIES = 500;
const LIMITS = {
  appVersion: 40, versionCode: 12, model: 80, manufacturer: 80, device: 80, brand: 80,
  android: 20, sdk: 6, screen: 120, action: 200, thread: 80, kind: 20, message: 600,
  stack: 12000, ram: 40, abi: 60, deviceId: 64,
};

function storeFile() {
  return process.env.CRASH_LOG_FILE || path.join(os.homedir(), 'streamflix-data', 'tv-crashes.json');
}

// Remove credenciais que possam aparecer em mensagens/URLs de excecao.
function scrub(text) {
  return String(text == null ? '' : text)
    .replace(/(https?:\/\/)[^\s/@:]+:[^\s/@]+@/gi, '$1***:***@')
    .replace(/\b(password|passwd|senha|pass|token|access_token|refresh_token|apikey|api_key|key|auth|username|user)=([^&\s"']+)/gi, '$1=***')
    .replace(/\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{8,}/g, '$1 ***')
    .replace(/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g, '***jwt***')
    // URLs de stream/playlist: guarda so host, sem caminho (caminho Xtream carrega user/senha)
    .replace(/(https?:\/\/[^\s/"']+)\/[^\s"']*/gi, '$1/…');
}

function clip(v, n) {
  const s = scrub(v).replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '');
  return s.length > n ? s.slice(0, n) + '…' : s;
}

function sanitizeReport(raw, ip) {
  if (!raw || typeof raw !== 'object') return null;
  const out = {};
  for (const [k, n] of Object.entries(LIMITS)) {
    if (raw[k] != null && raw[k] !== '') out[k] = clip(raw[k], n);
  }
  if (!out.stack && !out.message) return null;
  const ts = Number(raw.ts);
  out.ts = Number.isFinite(ts) && ts > 1.5e12 && ts < Date.now() + 864e5 ? new Date(ts).toISOString() : new Date().toISOString();
  out.receivedAt = new Date().toISOString();
  // Assinatura: excecao + 3 primeiras linhas "at" do nosso pacote/geral, para agrupar iguais.
  const lines = String(out.stack || out.message).split('\n').map((l) => l.trim());
  const head = (lines[0] || '').replace(/:.*/, '');
  const frames = lines.filter((l) => l.startsWith('at ')).slice(0, 3).join('|');
  out.signature = crypto.createHash('sha1').update(head + '|' + frames).digest('hex').slice(0, 12);
  if (ip) out.ipHash = crypto.createHash('sha1').update('sfv-crash|' + ip).digest('hex').slice(0, 10);
  return out;
}

function load() {
  try {
    const arr = JSON.parse(fs.readFileSync(storeFile(), 'utf8'));
    return Array.isArray(arr) ? arr : [];
  } catch (_) {
    return [];
  }
}

let chain = Promise.resolve();
function append(reports) {
  chain = chain.then(() => {
    const file = storeFile();
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const all = load().concat(reports).slice(-MAX_ENTRIES);
    const tmp = file + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(all));
    fs.renameSync(tmp, file);
    return all.length;
  }).catch((e) => { console.warn('[crash-store] falha ao gravar:', e && e.message); return 0; });
  return chain;
}

function clear() {
  chain = chain.then(() => {
    const file = storeFile();
    if (fs.existsSync(file)) {
      fs.copyFileSync(file, file + '.bak-' + Date.now());
      fs.writeFileSync(file, '[]');
    }
  }).catch(() => {});
  return chain;
}

function summarize(list) {
  const groups = new Map();
  for (const r of list) {
    const g = groups.get(r.signature) || { signature: r.signature, count: 0, first: r.ts, last: r.ts, title: (String(r.stack || r.message).split('\n')[0] || '').slice(0, 200), models: {}, versions: {} };
    g.count += 1;
    if (r.ts < g.first) g.first = r.ts;
    if (r.ts > g.last) g.last = r.ts;
    const m = [r.manufacturer, r.model].filter(Boolean).join(' ') || '?';
    g.models[m] = (g.models[m] || 0) + 1;
    const v = r.appVersion || '?';
    g.versions[v] = (g.versions[v] || 0) + 1;
    groups.set(r.signature, g);
  }
  return [...groups.values()].sort((a, b) => (a.last < b.last ? 1 : -1));
}

module.exports = { scrub, sanitizeReport, append, load, clear, summarize, storeFile, MAX_ENTRIES };
