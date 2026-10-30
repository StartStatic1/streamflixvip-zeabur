// lib/device-lock.js
//
// Anti-compartilhamento: cada assinante VIP usa 1 celular + 1 TV ao mesmo tempo.
// Guardado num arquivo JSON no servidor (sem tabela nova no Supabase):
//   ~/streamflix-data/device-locks.json   (troque com DEVICE_LOCK_FILE)
//
// Chave do assinante: "u:<userId>" (conta) ou "c:<codigo>" (TV só com código).
// Vagas: "phone" e "tv". Cada vaga guarda o aparelho ativo.
// - Vaga livre            -> o 1º aparelho que usar vira o ativo (sem gastar troca).
// - Mesmo aparelho        -> ok.
// - Outro aparelho        -> bloqueado (DEVICE_IN_USE) até o usuário tocar "Usar neste aparelho".
// - Ativo parado há 30 d  -> o novo assume sozinho (celular perdido/trocado).
// - Trocas: MAX_SWAPS por vaga a cada 30 dias.
// Qualquer erro aqui = LIBERA (nunca derruba assinante por falha nossa).
// Desligar tudo: DEVICE_LOCK=off no .env e reiniciar.

const fs = require('fs');
const os = require('os');
const path = require('path');

const DAY = 24 * 60 * 60 * 1000;
const WINDOW_MS = 30 * DAY;
const STALE_MS = 30 * DAY;
const MAX_SWAPS = Math.max(1, Number(process.env.DEVICE_LOCK_MAX_SWAPS || 3) || 3);
const SEEN_WRITE_MS = 10 * 60 * 1000;

function enabled() {
  const v = String(process.env.DEVICE_LOCK || 'on').trim().toLowerCase();
  return !(v === 'off' || v === '0' || v === 'false' || v === 'no');
}

function file() {
  return process.env.DEVICE_LOCK_FILE || path.join(os.homedir(), 'streamflix-data', 'device-locks.json');
}

let db = null;
let dirty = false;
let timer = null;

function load() {
  if (db) return db;
  try {
    db = JSON.parse(fs.readFileSync(file(), 'utf8'));
    if (!db || typeof db !== 'object' || !db.keys) db = { keys: {} };
  } catch (_) {
    db = { keys: {} };
  }
  return db;
}

function flush() {
  timer = null;
  if (!dirty || !db) return;
  dirty = false;
  try {
    fs.mkdirSync(path.dirname(file()), { recursive: true });
    const tmp = file() + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(db));
    fs.renameSync(tmp, file());
  } catch (e) {
    console.warn('[device-lock] write error:', e.message);
  }
}

function save(now) {
  dirty = true;
  if (now) { if (timer) clearTimeout(timer); flush(); return; }
  if (!timer) timer = setTimeout(flush, 2000);
}

process.once('exit', () => { try { flush(); } catch (_) {} });

function entry(key) {
  const d = load();
  if (!d.keys[key]) d.keys[key] = {};
  return d.keys[key];
}

function cleanLabel(s) {
  return String(s || '').replace(/[^\w .()+-]/g, '').trim().slice(0, 40) || null;
}

function recentSwaps(e, slot) {
  const now = Date.now();
  const arr = (e['swaps_' + slot] || []).filter((t) => now - t < WINDOW_MS);
  e['swaps_' + slot] = arr;
  return arr;
}

function swapInfo(e, slot) {
  const arr = recentSwaps(e, slot);
  const left = Math.max(0, MAX_SWAPS - arr.length);
  const nextAt = left > 0 ? null : new Date(arr[0] + WINDOW_MS).toISOString();
  return { swapsLeft: left, maxSwaps: MAX_SWAPS, nextSwapAt: nextAt };
}

/**
 * Checa (e registra) o aparelho. Nunca lança.
 * @returns {{ok:true}|{ok:false, code:'DEVICE_IN_USE', slot, otherLabel, swapsLeft, maxSwaps, nextSwapAt}}
 */
function check(key, slot, deviceId, label) {
  try {
    if (!enabled() || !key || !deviceId) return { ok: true };
    const e = entry(key);
    const now = Date.now();
    const cur = e[slot];
    if (!cur || !cur.id) {
      e[slot] = { id: deviceId, label: cleanLabel(label), at: now, seen: now };
      save(true);
      return { ok: true, bound: true };
    }
    if (cur.id === deviceId) {
      if (now - (cur.seen || 0) > SEEN_WRITE_MS) {
        cur.seen = now;
        if (label && !cur.label) cur.label = cleanLabel(label);
        save(false);
      }
      return { ok: true };
    }
    if (now - (cur.seen || cur.at || 0) > STALE_MS) {
      e[slot] = { id: deviceId, label: cleanLabel(label), at: now, seen: now, prev: cur.label || null };
      save(true);
      return { ok: true, bound: true };
    }
    e['blocked_' + slot] = (e['blocked_' + slot] || 0) + 1;
    e['lastBlocked_' + slot] = { label: cleanLabel(label), at: now };
    save(false);
    return { ok: false, code: 'DEVICE_IN_USE', slot, otherLabel: cur.label || null, ...swapInfo(e, slot) };
  } catch (err) {
    console.warn('[device-lock] check error (libera):', err.message);
    return { ok: true };
  }
}

/** "Usar neste aparelho": move a vaga pra este aparelho, gastando 1 troca. */
function claim(key, slot, deviceId, label) {
  try {
    if (!enabled() || !key || !deviceId) return { ok: true };
    const e = entry(key);
    const now = Date.now();
    const cur = e[slot];
    if (!cur || !cur.id || cur.id === deviceId || now - (cur.seen || cur.at || 0) > STALE_MS) {
      return check(key, slot, deviceId, label);
    }
    const info = swapInfo(e, slot);
    if (info.swapsLeft <= 0) {
      return { ok: false, code: 'SWAP_LIMIT', slot, otherLabel: cur.label || null, ...info };
    }
    e['swaps_' + slot].push(now);
    e[slot] = { id: deviceId, label: cleanLabel(label), at: now, seen: now, prev: cur.label || null };
    save(true);
    console.log(`[device-lock] troca ${slot} key=${String(key).slice(0, 10)}…`);
    return { ok: true, swapped: true, ...swapInfo(e, slot) };
  } catch (err) {
    console.warn('[device-lock] claim error (libera):', err.message);
    return { ok: true };
  }
}

/** Admin: libera vaga(s) e zera as trocas. */
function release(key, slot) {
  const e = load().keys[key];
  if (!e) return false;
  const slots = slot ? [slot] : ['phone', 'tv'];
  for (const s of slots) {
    delete e[s];
    delete e['swaps_' + s];
    delete e['blocked_' + s];
    delete e['lastBlocked_' + s];
  }
  save(true);
  return true;
}

function get(key) {
  const e = load().keys[key];
  if (!e) return null;
  const out = {};
  for (const s of ['phone', 'tv']) {
    if (e[s]) out[s] = { label: e[s].label || null, deviceIdTail: String(e[s].id).slice(-6), since: e[s].at, lastSeen: e[s].seen };
    out['blocked_' + s] = e['blocked_' + s] || 0;
    out['swaps_' + s] = recentSwaps(e, s).length;
  }
  return out;
}

function all() {
  const d = load();
  const out = {};
  for (const k of Object.keys(d.keys)) out[k] = get(k);
  return out;
}

const MSG = {
  phone: 'Sua conta VIP está em uso em outro celular.',
  tv: 'Sua conta VIP está em uso em outra TV.',
};

module.exports = { check, claim, release, get, all, enabled, MSG, MAX_SWAPS };
