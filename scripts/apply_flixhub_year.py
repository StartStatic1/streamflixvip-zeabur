#!/usr/bin/env python3
"""FlixHub: so aceita stream se o ano do nome bater com o TMDB."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "lib/stremio-addons.js"
t = p.read_text()

old = """function streamMatchesQuery(stream, queryYear, season, episode) {
  const blob = [stream && stream.name, stream && stream.title, stream && stream.description]
    .filter(Boolean)
    .join(' ');
  const y = yearOfBlob(blob);
  if (queryYear && y && Number(queryYear) !== Number(y)) return false;"""

new = """function streamMatchesQuery(stream, queryYear, season, episode, addon) {
  const blob = [stream && stream.name, stream && stream.title, stream && stream.description]
    .filter(Boolean)
    .join(' ');
  const y = yearOfBlob(blob);
  const isFlix = /flixhub/i.test(String((addon && (addon.name || addon.id || addon.manifestUrl || addon.manifest_url || addon.url)) || '') + ' ' + blob);
  if (isFlix && queryYear) {
    if (!y || Number(queryYear) !== Number(y)) return false;
  }
  if (queryYear && y && Number(queryYear) !== Number(y)) return false;"""

old_call = "if (!streamMatchesQuery(s, queryYear, season, episode)) continue;"
new_call = "if (!streamMatchesQuery(s, queryYear, season, episode, addon)) continue;"

n = 0
if old in t:
    t = t.replace(old, new, 1)
    n += 1
    print("ok fn year flix")
elif "isFlix && queryYear" in t:
    print("ok fn year flix ja")
else:
    print("aviso fn")

if old_call in t:
    t = t.replace(old_call, new_call)
    n += 1
    print("ok call")
elif "streamMatchesQuery(s, queryYear, season, episode, addon)" in t:
    print("ok call ja")
else:
    print("aviso call")

p.write_text(t)
print("fim apply_flixhub_year", n)
