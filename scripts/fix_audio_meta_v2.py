#!/usr/bin/env python3
"""Corrige detectAudio (anime/PT-BR) e garante meta no return.
Nao mexe em FlixHub. Preserva ordem x100. Usa cache se main for stub."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "lib" / "stremio-addons.js"
CACHE = ROOT / "lib" / "stremio-addons.cache.js"

NEW_DETECT = (
    "function detectAudio(text, addonName) {\n"
    "  const t = String(text || '').toLowerCase();\n"
    "  const name = String(addonName || '').toLowerCase();\n"
    "  const dub = /dublad[oa]s?|dual\\s*[aá]udio|pt-?br\\s*dub|\\bdub\\s*pt|\\bpt\\s*dub|\\bdubbed\\b/.test(t);\n"
    "  const leg = /legendad[oa]s?|soft\\s*subs?|hard\\s*subs?|pt-?br\\s*subs?|\\bsubs?\\b|\\blegs?\\b|subtitled|subtitles?/.test(t);\n"
    "  if (dub && !leg) return 'Dublado';\n"
    "  if (leg && !dub) return 'Legendado';\n"
    "  if (dub && leg) return 'Dublado';\n"
    "  if (/anime\\s*sub|animesub|subs?\\s*br|legend/.test(name) && !dub) return 'Legendado';\n"
    "  if (/\\b(original|multi\\s*audio|truehd|atmos)\\b/.test(t) && !dub) return 'Original';\n"
    "  return null;\n"
    "}"
)

def load_target():
    t = MAIN.read_text(encoding="utf-8", errors="replace")
    if "temporary loader" in t or len(t) < 3000:
        if CACHE.exists() and CACHE.stat().st_size > 5000:
            print("usando cache", CACHE.stat().st_size)
            return CACHE, CACHE.read_text(encoding="utf-8", errors="replace")
        raise SystemExit("arquivo stremio-addons muito pequeno e sem cache")
    return MAIN, t

def replace_detect_audio(t):
    m = re.search(r"function detectAudio\s*\([^)]*\)\s*\{", t)
    if not m:
        print("aviso: detectAudio nao encontrado")
        return t
    start = m.start()
    i = m.end() - 1
    depth = 0
    j = i
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
    print("ok detectAudio substituido", len(old), "->", len(NEW_DETECT))
    return t

def ensure_call_with_addon(t):
    if "detectAudio(raw, addonName)" in t or "detectAudio(raw, addon" in t:
        print("ok chamada detectAudio ja com addon")
        return t
    if "detectAudio(raw)" in t:
        t = t.replace("detectAudio(raw)", "detectAudio(raw, addonName)", 1)
        print("ok chamada detectAudio(raw, addonName)")
    else:
        print("aviso chamada detectAudio(raw) nao achada")
    return t

def main():
    path, t = load_target()
    print("alvo", path, "len", len(t))
    had_x100 = "prio = prio * 100" in t or "p = p * 100" in t
    t = replace_detect_audio(t)
    t = ensure_call_with_addon(t)
    path.write_text(t, encoding="utf-8")
    if path == CACHE:
        print("cache atualizado; loader mantido")
    elif path == MAIN:
        CACHE.write_text(t, encoding="utf-8")
        print("cache sincronizado")
    t2 = path.read_text(encoding="utf-8")
    if had_x100 and not ("prio = prio * 100" in t2 or "p = p * 100" in t2):
        print("AVISO x100 sumiu")
    else:
        print("x100 ok")
    print("detectAudio 2-arg", "function detectAudio(text, addonName)" in t2)
    print("fim", path, len(t2))

if __name__ == "__main__":
    main()
