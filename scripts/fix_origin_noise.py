#!/usr/bin/env python3
"""detectOrigin: bloqueia AnimeSub+ TB, TB+, Aniscraper e ruido torbox."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "lib" / "stremio-addons.cache.js"
MAIN = ROOT / "lib" / "stremio-addons.js"

def load():
    if not CACHE.exists() or CACHE.stat().st_size < 5000:
        raise SystemExit("cache ausente")
    return CACHE.read_text(encoding="utf-8", errors="replace")

def main():
    t = load()
    # reforca filtro de nomes lixo no final do loop detectOrigin
    junk = (
        "if (/^(aniscrap|aniscraper|animsub|animesub|nyaa|tordb|torrents?)$/i.test(c)) continue;"
    )
    better = (
        "if (/^(aniscrap|aniscraper|animsub|animesub|nyaa|tordb|torrents?)$/i.test(c)) continue;\n"
        "      if (/animesub|animsub|aniscrap|\\btb\\+?\\b|torbox|\\[tb/i.test(c)) continue;"
    )
    if "animesub|animsub|aniscrap|\\btb" in t or "animesub|animsub|aniscrap|\\btb" in t:
        print("filtro ruido ja presente")
    elif junk in t:
        t = t.replace(junk, better, 1)
        print("ok filtro ruido TB/AnimeSub")
    else:
        # injeta antes do return c.slice
        needle = "return c.slice(0, 16);"
        if needle in t and "torbox" not in t[t.find("function detectOrigin"):t.find("function detectOrigin")+2500]:
            t = t.replace(
                needle,
                "if (/animesub|animsub|aniscrap|\\btb\\+?\\b|torbox|\\[tb/i.test(c)) continue;\n"
                "      return c.slice(0, 16);",
                1,
            )
            print("ok injetou filtro antes return")
        else:
            print("aviso: nao achou ponto")
    CACHE.write_text(t, encoding="utf-8")
    mt = MAIN.read_text(encoding="utf-8", errors="replace") if MAIN.exists() else ""
    if len(mt) > 5000 and "temporary loader" not in mt[:500]:
        MAIN.write_text(t, encoding="utf-8")
        print("MAIN sync")
    r = subprocess.run(["node", "--check", str(CACHE)], capture_output=True, text=True)
    if r.returncode != 0:
        print("node FALHOU", (r.stderr or "")[:400])
        sys.exit(1)
    print("node --check OK", CACHE.stat().st_size)

if __name__ == "__main__":
    main()
