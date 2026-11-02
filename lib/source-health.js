// lib/source-health.js — saúde dos servidores (em memória):
// - demoteFailed: servidores que falharam no teste nos últimos 15 min vão para o fim da lista
// - promoteFirstWorking: testa os primeiros N em paralelo (Range pequeno, prazo curto) e sobe o 1º que manda vídeo
const FAIL_TTL_MS = 15 * 60 * 1000;
const failed = new Map(); // chave (host+caminho curto) -> expira em
const okRecent = new Map();

function keyOf(url) {
  try { const u = new URL(url); return u.host + u.pathname.split('/').slice(0, 3).join('/'); } catch (_) { return String(url).slice(0, 80); }
}
// links presos ao IP de quem abre primeiro (AIOStreams/ElfHosted/debrid): o servidor NUNCA abre — vão direto para a TV
const IP_LOCKED = /elfhosted|aiostreams|real-?debrid|alldebrid|premiumize|torbox|debrid-link|easydebrid|offcloud|putio|stremthru|mediafusion|comet\.|torrentio/i;
function isIpLocked(url) { return IP_LOCKED.test(String(url || '')); }
function isProbeable(url) { return /^https?:\/\//i.test(String(url || '')) && !isIpLocked(url); }
function markFailed(url) { failed.set(keyOf(url), Date.now() + FAIL_TTL_MS); if (failed.size > 5000) failed.clear(); }
function markOk(url) { failed.delete(keyOf(url)); okRecent.set(keyOf(url), Date.now()); if (okRecent.size > 5000) okRecent.clear(); }
function isFailed(url) { const e = failed.get(keyOf(url)); return !!(e && e > Date.now()); }

function demoteFailed(sources) {
  const good = []; const bad = [];
  for (const s of sources) (isFailed(s.source_url) ? bad : good).push(s);
  return bad.length ? good.concat(bad) : sources;
}

/** Só erro de verdade (bloqueado / não existe) conta como falha; lento nunca rebaixa. */
function isHardStatus(st) { return st === 401 || st === 403 || st === 404 || st === 410 || st === 451; }

async function probe(url, capMs) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), capMs);
  try {
    const r = await fetch(url, { headers: { Range: 'bytes=0-65535', 'User-Agent': 'Mozilla/5.0' }, redirect: 'follow', signal: ctrl.signal });
    if (r.status !== 200 && r.status !== 206) { try { ctrl.abort(); } catch (_) {} return { ok: false, hard: isHardStatus(r.status) }; }
    const reader = r.body && r.body.getReader ? r.body.getReader() : null;
    if (!reader) return { ok: true };
    const first = await reader.read(); // 1º pedaço de vídeo de verdade
    try { ctrl.abort(); } catch (_) {}
    return { ok: !!(first && first.value && first.value.length > 0), hard: false };
  } catch (_) {
    return { ok: false, hard: false, timeout: true };
  } finally { clearTimeout(t); }
}

/** Testa os primeiros `n` em paralelo; o primeiro que manda bytes vira o 1º da lista. Nunca demora mais que capMs. */
async function promoteFirstWorking(sources, n, capMs) {
  const cand = sources.slice(0, n).map((s, i) => ({ s, i })).filter((x) => isProbeable(x.s.source_url));
  if (!cand.length) return sources;
  let winner = null;
  await new Promise((resolve) => {
    let left = cand.length;
    const done = () => { if (--left === 0) resolve(); };
    const cap = setTimeout(resolve, capMs + 100);
    for (const c of cand) {
      probe(c.s.source_url, capMs).then((r) => {
        if (r.ok) { markOk(c.s.source_url); if (!winner) { winner = c; clearTimeout(cap); resolve(); } }
        else if (r.hard) markFailed(c.s.source_url);
        done();
      });
    }
  });
  let out = sources;
  if (winner && winner.i > 0) out = [winner.s].concat(sources.filter((_, i) => i !== winner.i));
  return demoteFailed(out);
}

module.exports = { isIpLocked, demoteFailed, promoteFirstWorking, markFailed, markOk, isFailed };
