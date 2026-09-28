#!/usr/bin/env python3
"""Bridge/Xtream addon: mesma regra de ano+titulo do FlixHub."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "api" / "bridge.js"
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
  else return 0;
  if (queryYear && itemYear && queryYear === itemYear) score += 12;
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
  else if (t.includes(n) && nTok.length >= 2 && tTok.length >= nTok.length) score = 70;
  else return 0;
  if (n.includes(t) && tTok.length < nTok.length && score < 88) return 0;
  const shared = nTok.filter((w) => tSet.has(w));
  if (shared.length === 1 && nTok.length >= 2 && GENERIC.has(shared[0])) return 0;
  if (queryYear && itemYear && Number(queryYear) === Number(itemYear)) score += 20;
  if (queryYear && !itemYear && score < 88) return 0;
  return score;
}"""

if "GENERIC = new Set(['the','and','motel'" in t:
    print("ok bridge ja estrito")
elif old in t:
    t = t.replace(old, new, 1)
    print("ok scoreOne bridge")
else:
    print("aviso scoreOne bridge nao achado")

old_th = "  return bestScore >= 78 ? best : null;"
new_th = "  return bestScore >= 80 ? best : null;"
if old_th in t:
    t = t.replace(old_th, new_th, 1)
    print("ok limiar bridge 80")
elif "bestScore >= 80" in t:
    print("ok limiar ja 80")
else:
    print("aviso limiar bridge")

p.write_text(t)
print("fim apply_bridge_strict_match", p.stat().st_size)
