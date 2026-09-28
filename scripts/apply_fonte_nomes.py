#!/usr/bin/env python3
"""Fontes no player usam nome do painel (Goldvip, Hyper, Guindex)."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
t = p.read_text()

old_load = """            alternateSources = resp.sources.filter { it.isDirectPlayable }
                .flatMap { it.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL) }
                .distinct().filter { it.isNotBlank() && it != activeUrl }
            alternateIndex = 0"""

new_load = """            val labeled = mutableListOf<Pair<String, String>>()
            for (src in resp.sources.filter { it.isDirectPlayable }) {
                val nome = src.displayName.ifBlank { \"Servidor\" }
                for (u in src.candidatePlaybackUrls(BuildConfig.API_BASE_URL, NetworkModule.ZEABUR_BASE_URL)) {
                    if (u.isNotBlank() && u != activeUrl && labeled.none { it.first == u }) {
                        labeled += u to nome
                    }
                }
            }
            alternateSources = labeled.map { it.first }
            alternateLabels = labeled.associate { it.first to it.second }
            alternateIndex = 0"""

# fix escaped quotes in new_load for actual python file
new_load = new_load.replace('\\"', '"')

old_ui = """                                alternateSources.forEachIndexed { i, src ->
                                    val label = try {
                                        val host = java.net.URI(src).host ?: \"Fonte ${i + 1}\"
                                        host.removePrefix(\"www.\").take(28)
                                    } catch (_: Exception) {
                                        \"Fonte ${i + 1}\"
                                    }
                                    SubmenuItem(\"${i + 1}. $label\", false) {
                                        alternateIndex = i + 1
                                        reloadWithUrl(src)
                                        settingsPanel = SettingsPanel.NONE
                                        Toast.makeText(context, \"Fonte ${i + 1}\", Toast.LENGTH_SHORT).show()
                                    }
                                }""".replace('\\"', '"')

new_ui = """                                alternateSources.forEachIndexed { i, src ->
                                    val label = alternateLabels[src] ?: prettyFonteLabel(src, i)
                                    SubmenuItem(\"${i + 1}. $label\", false) {
                                        alternateIndex = i + 1
                                        reloadWithUrl(src)
                                        settingsPanel = SettingsPanel.NONE
                                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                    }
                                }""".replace('\\"', '"')

helper = '''\nprivate fun prettyFonteLabel(url: String, index: Int): String {\n    val host = try { java.net.URI(url).host.orEmpty().lowercase() } catch (_: Exception) { \"\" }\n    return when {\n        host.contains(\"streamflixvip\") || host.contains(\"hakunaymatata\") -> \"Hyper\"\n        host.contains(\"goldvip\") || host.contains(\"sventank\") || host.contains(\"cineduo\") || host.contains(\"kraps\") -> \"Goldvip\"\n        host.contains(\"guindex\") -> \"Guindex\"\n        host.contains(\"flixhub\") -> \"FlixHub\"\n        host.isNotBlank() -> host.removePrefix(\"www.\").substringBefore(\".\").replaceFirstChar { it.uppercase() }\n        else -> \"Fonte ${index + 1}\"\n    }\n}\n\n'''.replace('\\n', '\n').replace('\\"', '"')

ok = 0
if "var alternateLabels" not in t:
    t = t.replace(
        "    var alternateSources by remember { mutableStateOf(emptyList<String>()) }\n",
        "    var alternateSources by remember { mutableStateOf(emptyList<String>()) }\n    var alternateLabels by remember { mutableStateOf(mapOf<String, String>()) }\n",
        1,
    )
    print("ok var labels")
    ok += 1
else:
    print("ok var labels ja")

if old_load in t:
    t = t.replace(old_load, new_load, 1)
    print("ok load labels")
    ok += 1
elif "alternateLabels = labeled" in t:
    print("ok load labels ja")
else:
    print("aviso load")

if old_ui in t:
    t = t.replace(old_ui, new_ui, 1)
    print("ok ui labels")
    ok += 1
elif "prettyFonteLabel" in t and "alternateLabels[src]" in t:
    print("ok ui labels ja")
else:
    print("aviso ui")

if "fun prettyFonteLabel" not in t:
    needle = "private enum class SettingsPanel"
    if needle in t:
        t = t.replace(needle, helper + needle, 1)
        print("ok helper")
        ok += 1
    else:
        print("aviso helper")
else:
    print("ok helper ja")

p.write_text(t)
print("fim apply_fonte_nomes", ok)
