#!/usr/bin/env python3
"""Corrige escapes ilegais em Regex Kotlin (\\s \\d) e garante layout Hub."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text(encoding="utf-8")

# Linha quebrada atual (um backslash so — invalido em string Kotlin)
# No arquivo: Regex("([A-Za-z0-9]+\s+)?Server\s*\d+"
old_sm = 'Regex("([A-Za-z0-9]+\s+)?Server\s*\d+", RegexOption.IGNORE_CASE)'
new_sm = 'Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE)'

if old_sm in t:
    t = t.replace(old_sm, new_sm)
    print("ok serverMatch regex")
elif new_sm in t:
    print("ok serverMatch ja certo")
else:
    print("aviso serverMatch")
    for ln in t.splitlines():
        if "serverMatch" in ln and "Regex" in ln:
            print("  got:", repr(ln[:100]))

# movieName strip: evita \\p{So} fragil — usa lista simples de simbolos
old_mv = 'desc.substring(0, cutAt).replace(Regex("^[\\p{So}\\s]+"), "").trim().ifBlank { null }'
old_mv2 = 'desc.substring(0, cutAt).replace(Regex("^[\p{So}\s]+"), "").trim().ifBlank { null }'
new_mv = 'desc.substring(0, cutAt).replace(Regex("^[\\u26A1\\uD83D\\uDE80\\uD83C\\uDFAC\\uD83D\\uDCFA\\u26A1\\s]+"), "").trim().ifBlank { null }'
# Simpler: strip non-letter start
new_mv = 'desc.substring(0, cutAt).trim().replace(Regex("^[^A-Za-z0-9\\u00C0-\\u024F]+"), "").ifBlank { null }'

if old_mv2 in t:
    t = t.replace(old_mv2, new_mv)
    print("ok movieName strip")
elif old_mv in t:
    t = t.replace(old_mv, new_mv)
    print("ok movieName strip (dbl)")
elif "replace(Regex(\"^[^A-Za-z0-9" in t:
    print("ok movieName ja")
else:
    print("aviso movieName")
    for ln in t.splitlines():
        if "cutAt" in ln or ("substring(0, cutAt)" in ln):
            print("  got:", repr(ln[:120]))

p.write_text(t, encoding="utf-8")
print("braces", t.count("{"), t.count("}"))
print("fim")
