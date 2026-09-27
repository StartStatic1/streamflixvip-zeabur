#!/usr/bin/env python3
"""Repara stremio-addons apos apply_source_meta_fix quebrar.
Garante detectOrigin + detectSize existem; se faltar, injeta.
Valida sintaxe com node -c. Preserva detectAudio v4 e x100."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "lib" / "stremio-addons.js"
CACHE = ROOT / "lib" / "stremio-addons.cache.js"

DETECT_ORIGIN_SIZE = r'''
function detectSize(text, stream) {
  const blob = String(text || '');
  const m = blob.match(/(\d+(?:[.,]\d+)?)\s*(GiB|GB|MiB|MB)\b/i);
  if (m) return m[1].replace(',', '.') + ' ' + m[2].toUpperCase().replace('GIB', 'GB').replace('MIB', 'MB');
  const bytes = stream && stream.behaviorHints && stream.behaviorHints.videoSize;
  const n = Number(bytes);
  if (Number.isFinite(n) && n > 0) {
    if (n >= 1073741824) return (n / 1073741824).toFixed(1) + ' GB';
    if (n >= 1048576) return Math.round(n / 1048576) + ' MB';
  }
  return null;
}

function detectOrigin(stream, addonName) {
  const host = (typeof shortAddonName === 'function' ? shortAddonName(addonName) : String(addonName || '')).trim();
  const hostLow = host.toLowerCase();
  const hints = (stream && stream.behaviorHints) || {};
  const stripNoise = (typeof stripNoise === 'function')
    ? stripNoise
    : (s) => String(s || '').replace(/\s+/g, ' ').trim();
  const rawBits = [
    stream && stream.name,
    stream && stream.title,
    hints.bingeGroup,
    stream && stream.bingeGroup,
    stream && stream.description,
  ].map(stripNoise).filter(Boolean);

  function tidy(s) {
    let x = stripNoise(s);
    x = x.split(/[\n\r]/)[0];
    x = x.replace(/[\u00b7\u2022|]+/g, ' ');
    x = x.replace(/\b(4k|uhd|2160p|1080p|720p|480p|360p|fhd|hd|sd|web-?dl|blu-?ray|bluray|dublado|dublada|legendado|legendada|dual|audio|pt-?br|subs?|dub|hevc|x264|x265|h\.?264|h\.?265)\b/ig, ' ');
    x = x.replace(/\b(server|servidor)\s*\d+\b/ig, ' ');
    x = x.replace(/\bflixhub\b/ig, ' ');
    x = x.replace(/\s+/g, ' ').trim();
    return x;
  }

  for (const bit of rawBits) {
    const first = bit.split(/[\n\u00b7\u2022|,]/)[0];
    const c = tidy(first);
    if (!c) continue;
    if (c.toLowerCase() === hostLow) continue;
    if (c.length < 2 || c.length > 18) continue;
    if (/^\d+$/.test(c)) continue;
    if (/stream|addon|http|https/.test(c.toLowerCase())) continue;
    return c.slice(0, 16);
  }
  return null;
}
'''

def load():
    candidates = []
    if CACHE.exists() and CACHE.stat().st_size > 5000:
        candidates.append(CACHE)
    if MAIN.exists() and MAIN.stat().st_size > 5000 and "temporary loader" not in MAIN.read_text(encoding="utf-8", errors="replace")[:500]:
        candidates.append(MAIN)
    if not candidates:
        raise SystemExit("sem arquivo util (cache/main)")
    # prefer largest
    path = max(candidates, key=lambda p: p.stat().st_size)
    return path, path.read_text(encoding="utf-8", errors="replace")

def ensure_functions(t):
    has_origin = "function detectOrigin" in t
    has_size = "function detectSize" in t
    print("detectOrigin", has_origin, "detectSize", has_size)
    if has_origin and has_size:
        return t, False
    # inject before detectAudio if possible
    m = re.search(r"function detectAudio\s*\(", t)
    if m:
        t = t[:m.start()] + DETECT_ORIGIN_SIZE + "\n" + t[m.start():]
        print("ok injetou detectOrigin+detectSize antes de detectAudio")
        return t, True
    # fallback: append near end before module.exports
    m2 = re.search(r"module\.exports", t)
    if m2:
        t = t[:m2.start()] + DETECT_ORIGIN_SIZE + "\n" + t[m2.start():]
        print("ok injetou antes de module.exports")
        return t, True
    t = t + "\n" + DETECT_ORIGIN_SIZE
    print("ok injetou no final")
    return t, True

def ensure_calls(t):
    if "const origin = detectOrigin" in t:
        print("ok calls origin/size ja existem")
        return t
    t2, n = re.subn(
        r"const a = detectAudio\(raw(?:, addonName)?\);\n",
        "const a = detectAudio(raw, addonName);\n  const origin = detectOrigin(stream, addonName);\n  const size = detectSize(raw, stream);\n",
        t,
        count=1,
    )
    if n:
        print("ok injetou calls")
        return t2
    print("aviso: nao achou linha detectAudio(raw)")
    return t

def ensure_meta_return(t):
    if "meta: { quality:" in t:
        print("ok meta no return")
        return t
    t2, n = re.subn(
        r"(source_label:\s*label,\s*\n?\s*priority:[^\n]+\n)",
        r"\1    meta: { quality: q || null, audio: a || null, origin: (typeof origin !== 'undefined' ? origin : null), size: (typeof size !== 'undefined' ? size : null) },\n",
        t,
        count=1,
    )
    if n:
        print("ok meta injetado no return")
        return t2
    print("aviso meta return")
    return t

def ensure_map(t):
    t2, n = re.subn(
        r"\.map\(\(\{ source_url, source_label, priority \}\) => \(\{ source_url, source_label, priority \}\)\)",
        ".map(({ source_url, source_label, priority, meta }) => ({ source_url, source_label, priority, meta }))",
        t,
        count=1,
    )
    if n:
        print("ok map meta")
        return t2
    if "priority, meta" in t:
        print("ok map ja ok")
    else:
        print("aviso map")
    return t

def node_check(path: Path) -> bool:
    r = subprocess.run(["node", "--check", str(path)], capture_output=True, text=True)
    if r.returncode == 0:
        print("node --check OK")
        return True
    print("node --check FALHOU")
    print(r.stderr[:800] if r.stderr else r.stdout[:800])
    return False

def main():
    path, t = load()
    print("alvo", path, "len", len(t))
    had_x100 = "prio = prio * 100" in t or "p = p * 100" in t
    t, _ = ensure_functions(t)
    t = ensure_calls(t)
    t = ensure_meta_return(t)
    t = ensure_map(t)
    # write BOTH so loader or direct require work
    CACHE.write_text(t, encoding="utf-8")
    # if MAIN is tiny loader, keep loader; else sync
    main_txt = MAIN.read_text(encoding="utf-8", errors="replace") if MAIN.exists() else ""
    if "temporary loader" in main_txt or len(main_txt) < 3000:
        print("MAIN e loader — so atualizei CACHE")
    else:
        MAIN.write_text(t, encoding="utf-8")
        print("MAIN sincronizado")
    ok = node_check(CACHE)
    t2 = CACHE.read_text(encoding="utf-8")
    print("x100", "ok" if ("prio = prio * 100" in t2 or "p = p * 100" in t2 or not had_x100) else "AVISO")
    print("detectOrigin", "function detectOrigin" in t2)
    print("detectSize", "function detectSize" in t2)
    print("detectAudio", "function detectAudio" in t2)
    print("fim", CACHE, CACHE.stat().st_size)
    if not ok:
        sys.exit(1)

if __name__ == "__main__":
    main()
