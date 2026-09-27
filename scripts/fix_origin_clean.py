#!/usr/bin/env python3
"""Limpa detectOrigin: nao mostrar -s1790..., binge ids, lixo tecnico.
FlixHub/Xtream → origin null (so qualidade/audio se houver).
Preserva detectAudio e x100."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "lib" / "stremio-addons.js"
CACHE = ROOT / "lib" / "stremio-addons.cache.js"

NEW_ORIGIN = r'''function detectOrigin(stream, addonName) {
  const name = String(addonName || '').toLowerCase();
  // Fontes xtream/painel/flixhub: origin nao ajuda (vira ID feio)
  if (/flixhub|xtream|cineduo|goldvip|sander|svent|bridge|m3u/.test(name)) return null;

  const hints = (stream && stream.behaviorHints) || {};
  function sn(s) { return String(s || '').replace(/\s+/g, ' ').trim(); }

  const rawBits = [
    stream && stream.name,
    stream && stream.title,
    stream && stream.description,
  ].map(sn).filter(Boolean);

  // bingeGroup costuma ser ID tecnico — so usa se parecer nome legivel
  const bg = sn(hints.bingeGroup || stream && stream.bingeGroup);
  if (bg && !/^-?s?\d+$/i.test(bg) && !/^\d+$/.test(bg) && bg.length >= 3 && bg.length <= 20) {
    rawBits.push(bg);
  }

  function isJunk(c) {
    if (!c || c.length < 2 || c.length > 22) return true;
    if (/^\d+$/.test(c)) return true;
    if (/^-?s\d+/i.test(c)) return true;           // -s1790108136706
    if (/^s\d{8,}/i.test(c)) return true;
    if (/stream|addon|http|https|null|undefined/i.test(c)) return true;
    if (/^[\[\(].*[\]\)]$/.test(c) && c.length > 18) return true;
    return false;
  }

  function tidy(s) {
    let x = sn(s);
    x = x.split(/[\n\r]/)[0];
    x = x.replace(/[\u00b7\u2022|]+/g, ' ');
    // tira qualidade/audio/codec
    x = x.replace(/\b(4k|uhd|2160p|1080p|720p|480p|360p|fhd|hd|sd|web-?dl|blu-?ray|bluray|dublado|dublada|legendado|legendada|dual|audio|pt-?br|subs?|dub|hevc|x264|x265|h\.?264|h\.?265|aac|ac3|dts)\b/ig, ' ');
    x = x.replace(/\b(server|servidor)\s*\d+\b/ig, ' ');
    x = x.replace(/\bflixhub\b/ig, ' ');
    // tira ids -s123...
    x = x.replace(/-?s\d{6,}/gi, ' ');
    x = x.replace(/\s+/g, ' ').trim();
    return x;
  }

  const hostLow = name.replace(/^.*\//, '').trim();

  for (const bit of rawBits) {
    const first = bit.split(/[\n\u00b7\u2022|,]/)[0];
    let c = tidy(first);
    if (isJunk(c)) continue;
    if (c.toLowerCase() === hostLow) continue;
    // limpa prefixos tipo [TB+] 
    c = c.replace(/^\[[^\]]+\]\s*/g, '').trim();
    if (isJunk(c)) continue;
    // origem curta e legivel
    if (c.length >= 2 && c.length <= 18) return c.slice(0, 16);
  }
  return null;
}'''

def load():
    if CACHE.exists() and CACHE.stat().st_size > 5000:
        return CACHE, CACHE.read_text(encoding="utf-8", errors="replace")
    t = MAIN.read_text(encoding="utf-8", errors="replace")
    if len(t) > 5000 and "temporary loader" not in t[:500]:
        return MAIN, t
    raise SystemExit("sem arquivo util")

def replace_fn(t, name, new_body):
    m = re.search(rf"function {name}\s*\([^)]*\)\s*\{{", t)
    if not m:
        print(f"aviso: {name} nao encontrado")
        return t, False
    start = m.start()
    depth = 0
    j = m.end() - 1
    while j < len(t):
        if t[j] == "{":
            depth += 1
        elif t[j] == "}":
            depth -= 1
            if depth == 0:
                j += 1
                break
        j += 1
    t = t[:start] + new_body + t[j:]
    print(f"ok {name} substituido")
    return t, True

def main():
    path, t = load()
    print("alvo", path, "len", len(t))
    t, ok = replace_fn(t, "detectOrigin", NEW_ORIGIN)
    if not ok:
        # inject before detectAudio
        m = re.search(r"function detectAudio\s*\(", t)
        if m:
            t = t[:m.start()] + NEW_ORIGIN + "\n" + t[m.start():]
            print("ok detectOrigin injetado")
        else:
            print("FALHOU injetar detectOrigin")
            sys.exit(1)
    CACHE.write_text(t, encoding="utf-8")
    main_txt = MAIN.read_text(encoding="utf-8", errors="replace") if MAIN.exists() else ""
    if "temporary loader" not in main_txt and len(main_txt) > 5000:
        MAIN.write_text(t, encoding="utf-8")
        print("MAIN sync")
    else:
        print("so CACHE")
    r = subprocess.run(["node", "--check", str(CACHE)], capture_output=True, text=True)
    if r.returncode != 0:
        print("node --check FALHOU", (r.stderr or "")[:600])
        sys.exit(1)
    print("node --check OK")
    print("x100", "ok" if ("prio = prio * 100" in t or "p = p * 100" in t) else "?")
    print("fim", CACHE.stat().st_size)

if __name__ == "__main__":
    main()
