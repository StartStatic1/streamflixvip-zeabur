#!/usr/bin/env python3
"""FlixHub: nao aceita Motel curto sem ano. Ano + titulo quase exato."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "api" / "flixhub.js"
t = p.read_text()

old = """function scoreOne(itemName, queryTitle, queryYear) {
  const t = norm(itemName);
  const n = norm(queryTitle);
  if (!t || !n) return 0;
  const tTok = tokens(itemName);
  const nTok = tokens(queryTitle);
  if (!tTok.length || !nTok.length) return 0;
  const tSet = new Set(tTok);
  const nSet = new Set(nTok);
  const interN = nTok.filter((w) => tSet.has(w)).length;
  const interT = tTok.filter((w) => nSet.has(w)).length;
  const coverN = interN / nTok.length;
  const coverT = interT / tTok.length;
  const itemYear = yearOf(itemName);
  if (queryYear && itemYear && queryYear !== itemYear) return 0;
  let score = 0;
  if (t === n) score = 100;
  else if (coverN >= 0.99 && nTok.length >= 2) score = 88;
  else if (coverN >= 0.8 && coverT >= 0.7 && nTok.length >= 3) score = 80;
  else if (coverT >= 0.99 && tTok.length >= nTok.length && nTok.length >= 3) score = 78;
  else if (coverN >= 0.75 && nTok.length >= 2 && interN >= 2) score = 72;
  else if (t.includes(n) || n.includes(t)) score = 70;
  else return 0;
  if (queryYear && itemYear && queryYear === itemYear) score += 12;
  if (nTok.length >= 3 && tTok.length <= 2 && coverN < 0.99) return 0;
  if (nTok.length >= 4 && interN < 3) return 0;
  return score;
}"""

new = """function scoreOne(itemName, queryTitle, queryYear) {
  const t = norm(itemName);
  const n = norm(queryTitle);
  if (!t || !n) return 0;
  const tTok = tokens(itemName);
  const nTok = tokens(queryTitle);
  if (!tTok.length || !nTok.length) return 0;
  const GENERIC = new Set(['the','and','motel','hotel','house','night','day','man','girl','last','new','love','dead','dark','city','war','story','filme','movie','part','vol']);
  const tSet = new Set(tTok);
  const nSet = new Set(nTok);
  const interN = nTok.filter((w) => tSet.has(w)).length;
  const interT = tTok.filter((w) => nSet.has(w)).length;
  const coverN = interN / nTok.length;
  const coverT = interT / tTok.length;
  const itemYear = yearOf(itemName);
  if (queryYear && itemYear && Number(queryYear) !== Number(itemYear)) return 0;
  let score = 0;
  if (t === n) score = 100;
  else if (coverN >= 0.99 && nTok.length >= 2) score = 88;
  else if (coverN >= 0.8 && coverT >= 0.7 && nTok.length >= 3) score = 80;
  else if (coverT >= 0.99 && tTok.length >= nTok.length && nTok.length >= 3) score = 78;
  else if (coverN >= 0.75 && nTok.length >= 2 && interN >= 2) score = 72;
  else if (t.includes(n) && nTok.length >= 2 && tTok.length >= nTok.length) score = 70;
  else return 0;
  if (n.includes(t) && tTok.length < nTok.length && score < 88) return 0;
  const shared = nTok.filter((w) => tSet.has(w));
  if (shared.length === 1 && nTok.length >= 2 && GENERIC.has(shared[0])) return 0;
  if (queryYear && itemYear && Number(queryYear) === Number(itemYear)) score += 20;
  if (queryYear && !itemYear && score < 88) return 0;
  if (nTok.length >= 3 && tTok.length <= 2 && coverN < 0.99) return 0;
  if (nTok.length >= 4 && interN < 3) return 0;
  return score;
}"""

if "GENERIC = new Set" in t:
    print("ok scoreOne ja estrito")
elif old in t:
    t = t.replace(old, new, 1)
    print("ok scoreOne estrito")
else:
    print("aviso scoreOne nao achado")

old_pick = """          const sameYear = exact.filter((it) => {
            const y = yearOf(it.name || it.title || '');
            return !y || y === queryYear;
          });
          const preferYear = exact.filter((it) => yearOf(it.name || it.title || '') === queryYear);
          if (preferYear.length) pool = preferYear;
          else if (sameYear.length) pool = sameYear;
          else continue;"""

new_pick = """          const preferYear = exact.filter((it) => yearOf(it.name || it.title || '') === queryYear);
          if (preferYear.length) pool = preferYear;
          else continue;"""

if old_pick in t:
    t = t.replace(old_pick, new_pick, 1)
    print("ok exact so com o mesmo ano")
elif "else continue;" in t and "preferYear.length) pool = preferYear" in t and "sameYear" not in t:
    print("ok exact ja exige ano")
else:
    print("aviso pick exact")

old_th = """      if (score < 70) continue;"""
new_th = """      if (score < 80) continue;"""
if old_th in t:
    t = t.replace(old_th, new_th, 1)
    print("ok limiar 80")
else:
    print("aviso limiar")

t = t.replace("if (best && bestScore >= 70) return best;\n  if (bestLow && bestLowScore >= 70) return bestLow;",
              "if (best && bestScore >= 80) return best;\n  if (bestLow && bestLowScore >= 80) return bestLow;")

p.write_text(t)
print("fim apply_flixhub_strict_match", p.stat().st_size)
