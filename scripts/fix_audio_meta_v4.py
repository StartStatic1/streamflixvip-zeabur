#!/usr/bin/env python3
"""detectAudio v4: Aniscrap/anime scrapers — Dublado só com PT explícito.
Inglês → EN. Sem marcador → null (sem chip).
Preserva x100."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "lib" / "stremio-addons.js"
CACHE = ROOT / "lib" / "stremio-addons.cache.js"

NEW_DETECT = r'''function detectAudio(text, addonName) {
  const t = String(text || '').toLowerCase();
  const name = String(addonName || '').toLowerCase();

  // Português BR explícito
  const ptDub = /dublad[oa]s?|dual\s*[aá]udio|pt-?br\s*dub|\bdub\s*pt|\bpt\s*dub|áudio\s*pt|audio\s*pt-?br|\bpt-?br\b.*\bdub|\bdub\b.*\bpt-?br/.test(t);
  const leg = /legendad[oa]s?|soft\s*subs?|hard\s*subs?|pt-?br\s*subs?|\bsubtitled\b|\bsubtitles?\b/.test(t)
    || (/\bsubs?\b|\blegs?\b/.test(t) && !/\bdub/.test(t));
  const enDub = /\bdubbed\b|\beng(?:lish)?\s*dub\b|\bdub\s*eng\b|\ben\s*dub\b|\baudio\s*:?\s*eng|\beng(?:lish)?\s*audio/.test(t);

  // Addons de anime/scrape: nunca confiar em "dub" solto
  const isAnimeScrap = /aniscrap|ani[\s._-]?scrap|animesub|anime[\s._-]?sub|animetsu|consumet|enime/.test(name);

  if (isAnimeScrap) {
    if (ptDub) return 'Dublado';
    if (leg) return 'Legendado';
    if (enDub) return 'EN';
    // texto genérico "dub" sem PT → trata como EN, não Dublado
    if (/\bdub\b|\bdubbed\b/.test(t)) return 'EN';
    return null;
  }

  if (ptDub && !leg) return 'Dublado';
  if (leg && !ptDub) return 'Legendado';
  if (ptDub && leg) return 'Dublado';
  if (enDub && !ptDub) return 'EN';
  if (/anime\s*sub|animesub|subs?\s*br|legend/.test(name) && !ptDub) return 'Legendado';
  if (/\b(original|multi\s*audio|truehd|atmos)\b/.test(t) && !ptDub) return 'Original';
  return null;
}'''

def load_target():
    t = MAIN.read_text(encoding="utf-8", errors="replace")
    if "temporary loader" in t or len(t) < 3000:
        if CACHE.exists() and CACHE.stat().st_size > 5000:
            print("usando cache", CACHE.stat().st_size)
            return CACHE, CACHE.read_text(encoding="utf-8", errors="replace")
        raise SystemExit("stremio-addons muito pequeno e sem cache")
    return MAIN, t

def replace_detect_audio(t):
    m = re.search(r"function detectAudio\s*\([^)]*\)\s*\{", t)
    if not m:
        print("aviso: detectAudio nao encontrado")
        return t
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
    old = t[start:j]
    t = t[:start] + NEW_DETECT + t[j:]
    print("ok detectAudio v4", len(old), "->", len(NEW_DETECT))
    return t

def main():
    path, t = load_target()
    print("alvo", path, "len", len(t))
    had_x100 = "prio = prio * 100" in t or "p = p * 100" in t
    t = replace_detect_audio(t)
    if "detectAudio(raw)" in t and "detectAudio(raw, addonName)" not in t:
        t = t.replace("detectAudio(raw)", "detectAudio(raw, addonName)", 1)
        print("ok chamada 2-arg")
    path.write_text(t, encoding="utf-8")
    if path == CACHE:
        print("cache atualizado")
    else:
        CACHE.write_text(t, encoding="utf-8")
        print("cache sincronizado")
    t2 = path.read_text(encoding="utf-8")
    print("x100", "ok" if ("prio = prio * 100" in t2 or "p = p * 100" in t2 or not had_x100) else "AVISO")
    print("aniscrap rule", "isAnimeScrap" in t2)
    print("fim", path, len(t2))

if __name__ == "__main__":
    main()
