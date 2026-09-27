#!/usr/bin/env python3
"""detectOrigin: nao devolver nome igual ao addon (Aniscraper quando ja e Aniscrap)."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "lib" / "stremio-addons.cache.js"
MAIN = ROOT / "lib" / "stremio-addons.js"

def load():
    if CACHE.exists() and CACHE.stat().st_size > 5000:
        return CACHE, CACHE.read_text(encoding="utf-8", errors="replace")
    raise SystemExit("cache ausente")

def main():
    path, t = load()
    # Apos return c.slice, filtrar se parecer o nome do addon
    needle = "if (c.length >= 2 && c.length <= 18) return c.slice(0, 16);"
    if "sameAsAddon" in t:
        print("ja tem filtro sameAsAddon")
    elif needle in t:
        repl = (
            "if (c.length >= 2 && c.length <= 18) {\n"
            "      // nao repetir nome do addon (Aniscraper / Aniscrap / Animsub)\n"
            "      const cl = c.toLowerCase().replace(/[^a-z0-9]/g, '');\n"
            "      const nl = hostLow.replace(/[^a-z0-9]/g, '');\n"
            "      if (cl && nl && (cl === nl || cl.includes(nl) || nl.includes(cl))) continue;\n"
            "      if (/^(aniscrap|aniscraper|animsub|animesub|nyaa|tordb|torrents?)$/i.test(c)) continue;\n"
            "      return c.slice(0, 16);\n"
            "    }"
        )
        t = t.replace(needle, repl, 1)
        print("ok filtro origem redundante")
    else:
        print("aviso: ponto de injecao nao achado")
        sys.exit(1)
    CACHE.write_text(t, encoding="utf-8")
    mt = MAIN.read_text(encoding="utf-8", errors="replace") if MAIN.exists() else ""
    if len(mt) > 5000 and "temporary loader" not in mt[:500]:
        MAIN.write_text(t, encoding="utf-8")
        print("MAIN sync")
    r = subprocess.run(["node", "--check", str(CACHE)], capture_output=True, text=True)
    if r.returncode != 0:
        print("node FALHOU", (r.stderr or "")[:500])
        sys.exit(1)
    print("node --check OK")
    print("fim", CACHE.stat().st_size)

if __name__ == "__main__":
    main()
