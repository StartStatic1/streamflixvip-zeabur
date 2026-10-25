// lib/subtitle-text.js
// Normaliza arquivos de legenda baixados: gzip, codificacao (UTF-8, BOM, UTF-16,
// latin1/windows-1252), ASS/SSA -> cues, SRT -> WebVTT.
const zlib = require('zlib');

function decodeSubtitleBuffer(buf) {
  let b = Buffer.isBuffer(buf) ? buf : Buffer.from(buf || []);
  // gzip (alguns servidores mandam .srt.gz sem avisar)
  if (b.length > 2 && b[0] === 0x1f && b[1] === 0x8b) {
    try { b = zlib.gunzipSync(b); } catch (_) {}
  }
  if (b.length >= 2 && b[0] === 0xff && b[1] === 0xfe) return new TextDecoder('utf-16le').decode(b.subarray(2));
  if (b.length >= 2 && b[0] === 0xfe && b[1] === 0xff) return new TextDecoder('utf-16be').decode(b.subarray(2));
  if (b.length >= 3 && b[0] === 0xef && b[1] === 0xbb && b[2] === 0xbf) b = b.subarray(3);
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(b);
  } catch (_) {
    return new TextDecoder('windows-1252').decode(b);
  }
}

function msToVtt(ms) {
  const v = Math.max(0, Math.round(ms));
  const h = Math.floor(v / 3600000);
  const m = Math.floor((v % 3600000) / 60000);
  const s = Math.floor((v % 60000) / 1000);
  const r = v % 1000;
  const p = (n, l) => String(n).padStart(l, '0');
  return `${p(h, 2)}:${p(m, 2)}:${p(s, 2)}.${p(r, 3)}`;
}

// "0:01:02.34" (ASS, centesimos) -> ms
function assTimeToMs(t) {
  const m = String(t || '').trim().match(/^(\d+):(\d{1,2}):(\d{1,2})(?:[.,](\d{1,3}))?$/);
  if (!m) return null;
  const frac = m[4] ? Number(m[4].padEnd(3, '0').slice(0, 3)) : 0;
  return Number(m[1]) * 3600000 + Number(m[2]) * 60000 + Number(m[3]) * 1000 + frac;
}

function isAss(text) {
  return /^\s*\[script info\]/im.test(text) || /^\s*Dialogue:\s*/m.test(text) && /\[events\]/i.test(text);
}

// ASS/SSA -> WebVTT. Mantem {\an8} (posicao no topo) para o app tratar.
function assToVtt(text) {
  const lines = String(text).split(/\r?\n/);
  let fmt = null;
  const cues = [];
  for (const line of lines) {
    const f = line.match(/^\s*Format:\s*(.+)$/i);
    if (f && /text/i.test(f[1])) { fmt = f[1].split(',').map((x) => x.trim().toLowerCase()); continue; }
    const d = line.match(/^\s*Dialogue:\s*(.+)$/i);
    if (!d) continue;
    const cols = fmt || ['layer', 'start', 'end', 'style', 'name', 'marginl', 'marginr', 'marginv', 'effect', 'text'];
    const parts = d[1].split(',');
    const head = parts.slice(0, cols.length - 1);
    const body = parts.slice(cols.length - 1).join(',');
    const start = assTimeToMs(head[cols.indexOf('start')]);
    const end = assTimeToMs(head[cols.indexOf('end')]);
    if (start == null || end == null || end <= start) continue;
    const top = /\{[^}]*\\an[789][^}]*\}/.test(body);
    let t = body.replace(/\\N/gi, '\n').replace(/\\h/g, ' ').replace(/\{[^}]*\}/g, '').trim();
    if (!t) continue;
    if (top) t = '{\\an8}' + t;
    cues.push({ start, end, t });
  }
  cues.sort((a, b) => a.start - b.start);
  return 'WEBVTT\n\n' + cues.map((c) => `${msToVtt(c.start)} --> ${msToVtt(c.end)}\n${c.t}\n`).join('\n');
}

function toVtt(input) {
  let t = String(input || '').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
  if (/^\s*WEBVTT/i.test(t)) return t.replace(/^\s+/, '');
  if (isAss(t)) return assToVtt(t);
  // SRT: tira o numero do bloco, troca virgula por ponto, completa horas ("1:02:03" e "02:03,456")
  t = t.replace(/^\s*\d+\s*\n(?=\s*\d{1,2}:\d{2})/gm, '');
  const fixTs = (line) => line.replace(/(\d{1,2}:)?(\d{1,2}):(\d{2})[,.](\d{1,3})/g, (m0, h, mm, ss, ms) => {
    const hh = h ? h.slice(0, -1) : '0';
    return `${hh.padStart(2, '0')}:${mm.padStart(2, '0')}:${ss}.${ms.padEnd(3, '0')}`;
  });
  t = t.split('\n').map((line) => (line.includes('-->') ? fixTs(line) : line)).join('\n');
  return 'WEBVTT\n\n' + t.trim() + '\n';
}

module.exports = { decodeSubtitleBuffer, toVtt, assToVtt };
