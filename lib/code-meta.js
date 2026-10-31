// lib/code-meta.js — anotações do painel por código VIP (comprador, WhatsApp, nota, lote, histórico).
// Arquivo JSON no servidor (sem mudar o Supabase): ~/streamflix-data/code-meta.json (CODE_META_FILE).
const fs = require('fs');
const os = require('os');
const path = require('path');

function file() { return process.env.CODE_META_FILE || path.join(os.homedir(), 'streamflix-data', 'code-meta.json'); }
let db = null;
function load() {
  if (db) return db;
  try { db = JSON.parse(fs.readFileSync(file(), 'utf8')); } catch (_) { db = null; }
  if (!db || typeof db !== 'object' || !db.codes) db = { codes: {} };
  return db;
}
function save() {
  try {
    fs.mkdirSync(path.dirname(file()), { recursive: true });
    const tmp = file() + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(db));
    fs.renameSync(tmp, file());
  } catch (e) { console.warn('[code-meta] write error:', e.message); }
}
const clean = (v, n) => (v == null ? '' : String(v)).replace(/[<>]/g, '').trim().slice(0, n);

function get(code) { return load().codes[code] || null; }
function all() { return load().codes; }

/** Atualiza campos e registra no histórico. */
function update(code, fields, what) {
  const d = load();
  const cur = d.codes[code] || { history: [] };
  if ('buyerName' in fields) cur.buyerName = clean(fields.buyerName, 80);
  if ('buyerPhone' in fields) cur.buyerPhone = clean(fields.buyerPhone, 30).replace(/[^\d+]/g, '');
  if ('note' in fields) cur.note = clean(fields.note, 500);
  if ('batch' in fields) cur.batch = clean(fields.batch, 60);
  if ('firstUseAt' in fields && fields.firstUseAt && !cur.firstUseAt) cur.firstUseAt = Number(fields.firstUseAt);
  if ('sold' in fields) cur.soldAt = fields.sold ? (cur.soldAt || Date.now()) : null;
  cur.updatedAt = Date.now();
  cur.history = (cur.history || []).concat([{ at: Date.now(), what: clean(what || 'editado', 120) }]).slice(-50);
  d.codes[code] = cur;
  save();
  return cur;
}

function remove(code) { const d = load(); if (d.codes[code]) { delete d.codes[code]; save(); } }
/** Registro de ações do admin (apagar/limpar) — últimas 500. */
function logAdmin(entry) { const d = load(); d.adminLog = (d.adminLog || []).concat([entry]).slice(-500); save(); }

module.exports = { get, all, update, remove, logAdmin };
