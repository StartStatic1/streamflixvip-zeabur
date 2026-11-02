// lib/sales.js — registro de vendas (cartão/PIX) no servidor: ~/streamflix-data/sales.json (SALES_FILE).
// Também serve de trava contra aviso de pagamento repetido (mesmo id = já processado).
const fs = require('fs');
const os = require('os');
const path = require('path');

function file() { return process.env.SALES_FILE || path.join(os.homedir(), 'streamflix-data', 'sales.json'); }
let db = null;
function load() {
  if (db) return db;
  try { db = JSON.parse(fs.readFileSync(file(), 'utf8')); } catch (_) { db = null; }
  if (!db || !Array.isArray(db.sales)) db = { sales: [] };
  return db;
}
function save() {
  try {
    fs.mkdirSync(path.dirname(file()), { recursive: true });
    const tmp = file() + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(db));
    fs.renameSync(tmp, file());
  } catch (e) { console.warn('[sales] write error:', e.message); }
}
function find(id) { return load().sales.find((s) => s.id === id) || null; }
/** Já foi aprovado/creditado? (idempotência) */
function processed(id) { const s = find(id); return !!(s && s.status === 'aprovado'); }
function record(sale) {
  const d = load();
  const i = d.sales.findIndex((s) => s.id === sale.id);
  const row = { ...(i >= 0 ? d.sales[i] : {}), ...sale, updatedAt: Date.now() };
  if (!row.at) row.at = Date.now();
  if (i >= 0) d.sales[i] = row; else d.sales.push(row);
  if (d.sales.length > 20000) d.sales = d.sales.slice(-20000);
  save();
  return row;
}
function all() { return load().sales.slice(); }
function methodOf(capture) {
  const c = String(capture || '').toLowerCase();
  if (c.includes('credit') || c.includes('debit') || c.includes('card')) return 'cartao';
  if (c.includes('pix') || c.includes('bank_transfer')) return 'pix';
  return c || 'desconhecido';
}
module.exports = { find, processed, record, all, methodOf };
