// lib/code-window.js — janela de validade do PRÓPRIO código VIP.
// Regra: um código vale "plano" a partir do 1º uso (celular ou TV), uma vez só.
// 1º uso: code-meta.firstUseAt (gravado desde 30/10/2026) > vip_codes.used_at (celular)
//         > deduzido das TVs antigas (expiração - n × duração, sem passar da criação do código).
const HOUR = 3600 * 1000;

/** Deduz o 1º uso a partir das linhas de TV antigas (antes do conserto, cada redigitação somava +duração). */
function deriveFromTv(code, tvRows) {
  const dur = (Number(code.duration_hours) || 0) * HOUR;
  const created = code.created_at ? new Date(code.created_at).getTime() : 0;
  if (!dur) return 0;
  let best = 0;
  for (const t of tvRows || []) {
    const exp = new Date(t.expires_at).getTime();
    const act = new Date(t.activated_at).getTime() || exp;
    if (!exp) continue;
    // entrada mais recente = activated_at; o 1º uso fica entre a criação e ela.
    // n = quantas vezes a duração foi somada (folga de 25h p/ sobra de um código de teste anterior).
    let n = Math.max(1, Math.ceil((exp - act - 25 * HOUR) / dur));
    let start = exp - n * dur;
    while (n > 1 && created && start < created - HOUR) { n--; start = exp - n * dur; }
    if (!best || start < best) best = start;
  }
  return best;
}

function firstUse(code, tvRows, meta) {
  const cands = [];
  if (meta && meta.firstUseAt) cands.push(Number(meta.firstUseAt));
  if (code.used_at) cands.push(new Date(code.used_at).getTime());
  const d = deriveFromTv(code, tvRows);
  if (d) cands.push(d);
  const v = cands.filter((x) => x > 0);
  return v.length ? Math.min(...v) : 0;
}

function codeEnd(code, start) {
  const dur = (Number(code.duration_hours) || 0) * HOUR;
  return start && dur ? start + dur : 0;
}

module.exports = { firstUse, codeEnd, deriveFromTv };
