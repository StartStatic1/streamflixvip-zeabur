#!/usr/bin/env python3
"""Substitui Regex com \\s/\\d por versao sem escape (compila no Kotlin)."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text(encoding="utf-8")

# Qualquer variante quebrada ou correta da linha serverMatch
import re

pat = re.compile(
    r'val serverMatch = if \(isHubPack\) Regex\(".*?", RegexOption\.IGNORE_CASE\)\.find\(desc \+ " " \+ label\) else null'
)
replacement = (
    'val serverMatch = if (isHubPack) '
    'Regex("([A-Za-z0-9]+[ \\t]+)?Server[ \\t]*[0-9]+", RegexOption.IGNORE_CASE)'
    '.find(desc + " " + label) else null'
)
# In the file we need: [ \t] with a real backslash before t for Kotlin tab? 
# Safer: only space, no tab escape at all:
replacement = (
    'val serverMatch = if (isHubPack) '
    'Regex("([A-Za-z0-9]+ +)?Server *[0-9]+", RegexOption.IGNORE_CASE)'
    '.find(desc + " " + label) else null'
)

m = pat.search(t)
if m:
    t = t[: m.start()] + replacement + t[m.end() :]
    print("ok serverMatch sem escape")
else:
    print("aviso: serverMatch nao encontrado")
    for ln in t.splitlines():
        if "serverMatch" in ln:
            print(repr(ln[:130]))

p.write_text(t, encoding="utf-8")
print("braces", t.count("{"), t.count("}"))
# verify no illegal \s or \d left in that line
for ln in t.splitlines():
    if "serverMatch" in ln and "Regex" in ln:
        print("final:", ln)
        if "\\s" in ln or ("\\d" in ln and "[0-9]" not in ln):
            # file should not have single-char illegal sequences
            pass
        # Check for single backslash-s: in Python string from file, '\\s' means one backslash+s
        if "\\s" in repr(ln) and "\\\\s" not in repr(ln):
            # actually if we used [0-9] and spaces only, fine
            print("repr check", repr(ln[40:90]))
print("fim")
