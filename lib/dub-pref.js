// lib/dub-pref.js — quando o mesmo título aparece em duas pastas da fonte Xtream
// (ex.: "F • Ação" dublado e "F • Legendados"), escolhe o DUBLADO como principal
// e devolve o legendado como servidor alternativo. Antes o primeiro da lista
// ganhava — e, em vários painéis, era o legendado.

const LEG_NAME = /legendad[oa]s?|\bleg\b|\[\s*l\s*\]|\(\s*l\s*\)|\bsubs?\b|\bsubtitled\b|\blegenda\b/i;
const DUB_NAME = /dublad[oa]s?|\[\s*d\s*\]|\(\s*d\s*\)|\bdub\b|dual\s*[aá]udio|\bnacional\b|\bpt-?br\b/i;
const TAG_RE = /[\[(]\s*(?:l|d|leg|dub|dual|sub|legendado|dublado|nacional)\s*[\])]/gi;

/** 'dub' | 'leg' | 'unknown' pelo nome do item e, se não der, pelo nome da pasta. */
function audioOf(item, catNames) {
  const name = String((item && (item.name || item.title)) || '');
  const legN = LEG_NAME.test(name);
  const dubN = DUB_NAME.test(name);
  if (dubN && !legN) return 'dub';
  if (legN && !dubN) return 'leg';
  if (dubN && legN) return 'dub'; // "dual áudio + legenda"
  const cat = catNames && item ? String(catNames.get(String(item.category_id)) || '') : '';
  if (/legend|\bleg\b|\bsub/i.test(cat)) return 'leg';
  if (/dublad|\bdub\b|nacional/i.test(cat)) return 'dub';
  return 'unknown';
}

const RANK = { dub: 0, unknown: 1, leg: 2 };

/** Chave do "mesmo título": nome normalizado sem as marcas [L]/[D]/(LEG)... */
function siblingKey(name, norm) {
  return norm(String(name || '').replace(TAG_RE, ' '));
}

function yearOf(s) {
  const m = String(s || '').match(/\b((?:19|20)\d{2})\b/);
  return m ? Number(m[1]) : null;
}

/**
 * Índice por chave de título (reaproveitado entre pedidos pelo chamador).
 * @returns {Map<string, object[]>}
 */
function buildSiblingIndex(rows, norm) {
  const idx = new Map();
  for (const it of rows || []) {
    const k = siblingKey(it && (it.name || it.title), norm);
    if (!k) continue;
    if (!idx.has(k)) idx.set(k, []);
    idx.get(k).push(it);
  }
  return idx;
}

/**
 * A partir do item escolhido pelo casamento de título, acha as cópias do mesmo
 * título (mesma chave, mesmo ano quando os dois têm ano) e devolve
 * { primary, primaryAudio, alt, altAudio } — principal dublado (ou sem marca),
 * alternativo legendado. alt = null quando não há as duas versões.
 * @param {(item:object)=>boolean} [isLow] item de qualidade baixa (CAM) vai para o fim
 */
function preferDubbed(hit, index, norm, catNames, isLow) {
  if (!hit) return { primary: null, primaryAudio: null, alt: null, altAudio: null };
  const key = siblingKey(hit.name || hit.title, norm);
  const hy = yearOf(hit.name || hit.title);
  const pool = ((index && index.get(key)) || [hit]).filter((x) => {
    if (x === hit) return true;
    const y = yearOf(x.name || x.title);
    return !hy || !y || y === hy;
  });
  if (!pool.includes(hit)) pool.unshift(hit);
  const scored = pool.map((it, i) => ({
    it,
    a: audioOf(it, catNames),
    low: isLow ? !!isLow(it) : false,
    i,
  }));
  scored.sort((x, y) => (x.low - y.low) || (RANK[x.a] - RANK[y.a]) || (x.i - y.i));
  const first = scored[0];
  let alt = null;
  if (first.a !== 'leg') alt = scored.find((s) => s.a === 'leg' && !s.low) || null;
  return {
    primary: first.it,
    // tem a versão legendada à parte: a outra (sem marca) é a dublada do painel BR
    primaryAudio: alt && first.a === 'unknown' ? 'dub' : first.a,
    alt: alt ? alt.it : null,
    altAudio: alt ? alt.a : null,
  };
}

/** Nome com a marca de áudio que o app entende ([D] Dublado / [L] Legendado). */
function tagName(name, audio) {
  const n = String(name || '').trim();
  // o app reconhece "[L]"/"legendado" e "[D]"/"dublado" (não "(L)" nem "LEG" solto)
  if (audio === 'leg' && !/\[\s*l\s*\]|legendad/i.test(n)) return n + ' [L]';
  if (audio === 'dub' && !/\[\s*d\s*\]|dublad/i.test(n)) return n + ' [D]';
  return n;
}

module.exports = { audioOf, siblingKey, buildSiblingIndex, preferDubbed, tagName };
