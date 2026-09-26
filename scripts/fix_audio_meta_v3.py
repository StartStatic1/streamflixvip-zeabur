#!/usr/bin/env python3
"""detectAudio v3: Dublado = só português BR.
Inglês (dubbed/eng dub) → EN, não Dublado.
Animesub sem marcador PT → Legendado.
Preserva x100 e meta. Não mexe FlixHub."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "lib" / "stremio-addons.js"
CACHE = ROOT / "lib" / "stremio-addons.cache.js"

NEW_DETECT = (
    "function detectAudio(text, addonName) {\n"
    "  const t = String(text || '').toLowerCase();\n"
    "  const name = String(addonName || '').toLowerCase();\n"
    "  // Dublado = APENAS português BR (nunca inglês 'dubbed')\n"
    "  const ptDub = /dublad[oa]s?|dual\\s*[aá]udio|pt-?br\\s*dub|\\bdub\\s*pt|\\bpt\\s*dub|áudio\\s*pt|audio\\s*pt-?br/.test(t);\n"
    "  const leg = /legendad[oa]s?|soft\\s*subs?|hard\\s*subs?|pt-?br\\s*subs?|\\bsubs?\\b|\\blegs?\\b|subtitled|subtitles?/.test(t);\n"
    "  const enDub = /\\bdubbed\\b|\\beng(?:lish)?\\s*dub\\b|\\bdub\\s*eng\\b|\\ben\\s*dub\\b|\\baudio\\s*:?\\s*eng/.test(t);\n"
    "  if (ptDub && !leg) return 'Dublado';\n"
    "  if (leg && !ptDub) return 'Legendado';\n"
    "  if (ptDub && leg) return 'Dublado';\n"
    "  if (enDub && !ptDub) return 'EN';\n"
    "  if (/anime\\s*sub|animesub|subs?\\s*br|legend/.test(name) && !ptDub) return 'Legendado';\n"
    "  if (/aniscrap|ani.?scrap/.test(name) && !ptDub && !leg) return null;\n"
    "  if (/\\b(original|multi\\s*audio|truehd|atmos)\\b/.test(t) && !ptDub) return 'Original';\n"
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
    print("ok detectAudio v3", len(old), "->", len(NEW_DETECT))
    return t

def ensure_call_with_addon(t):
    if "detectAudio(raw, addonName)" in t or "detectAudio(raw, addon" in t:
        print("ok chamada ja com addonName")
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
    else:
        CACHE.write_text(t, encoding="utf-8")
        print("cache sincronizado")
    t2 = path.read_text(encoding="utf-8")
    print("x100 ok" if had_x100 and ("prio = prio * 100" in t2 or "p = p * 100" in t2) else ("x100 ok" if not had_x100 else "AVISO x100"))
    print("sem dubbed-como-Dublado", "\\bdubbed\\b" not in t2.split("function detectAudio")[1][:800] if "function detectAudio" in t2 else False)
    print("tem EN", "return 'EN'" in t2)
    print("fim", path, len(t2))

if __name__ == "__main__":
    main()
