#!/usr/bin/env python3
"""Aplica aba Especiais (season 0) no DetailScreen — T1-T4 intactas, Especiais no fim."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt")
if not p.exists():
    raise SystemExit(f"Arquivo nao encontrado: {p}")
text = p.read_text(encoding="utf-8")
if "seasonDisplayName" in text and "Int.MAX_VALUE" in text:
    print("Ja esta com Especiais. Nada a fazer.")
    raise SystemExit(0)

old1 = (
    "            val seasons = details.seasons.orEmpty().filter { it.season_number > 0 } // ignora \"specials\" (temporada 0)\n"
    "            val currentSeason = seasons.firstOrNull { it.season_number == state.expandedSeason }"
)
new1 = (
    "            // T1–Tn primeiro; season 0 = Especiais no fim\n"
    "            val seasons = details.seasons.orEmpty()\n"
    "                .filter { it.season_number >= 0 }\n"
    "                .sortedBy { if (it.season_number == 0) Int.MAX_VALUE else it.season_number }\n"
    "            val currentSeason = seasons.firstOrNull { it.season_number == state.expandedSeason }"
)
if old1 not in text:
    raise SystemExit("Bloco de seasons nao encontrado")
text = text.replace(old1, new1, 1)

old_h = '                    currentSeason?.name ?: "Temporada",'
if old_h not in text:
    raise SystemExit("Header name nao encontrado")
text = text.replace(old_h, '                    seasonDisplayName(currentSeason),', 1)

old_p = (
    "                                    Text(\n"
    "                                        season.name,\n"
    "                                        fontSize = 14.sp,\n"
    "                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,\n"
    "                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,\n"
    "                                    )"
)
new_p = (
    "                                    Text(\n"
    "                                        seasonDisplayName(season),\n"
    "                                        fontSize = 14.sp,\n"
    "                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,\n"
    "                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,\n"
    "                                    )"
)
if old_p not in text:
    raise SystemExit("Picker name nao encontrado")
text = text.replace(old_p, new_p, 1)

helper = (
    "\n"
    "/** Nome amigavel: season 0 = Especiais (OVAs); demais = nome TMDB ou Temporada N */\n"
    "private fun seasonDisplayName(season: TmdbSeason?): String {\n"
    "    if (season == null) return \"Temporada\"\n"
    "    if (season.season_number == 0) return \"Especiais\"\n"
    "    val n = season.name?.takeIf { it.isNotBlank() }\n"
    "    if (n != null && !n.equals(\"Specials\", ignoreCase = true) && !n.equals(\"Especiais\", ignoreCase = true)) return n\n"
    "    return \"Temporada ${season.season_number}\"\n"
    "}\n"
    "\n"
)
marker = "@Composable\nprivate fun SeasonPickerHeader("
if "fun seasonDisplayName" not in text:
    if marker not in text:
        raise SystemExit("SeasonPickerHeader nao encontrado")
    text = text.replace(marker, helper + marker, 1)

p.write_text(text, encoding="utf-8")
print("OK Especiais aplicado.", p, "bytes=", p.stat().st_size)
