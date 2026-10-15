#!/usr/bin/env python3
"""Separa packs StreamHub / FlixHub / qualquer outro pack do painel.

Problema:
1) pickStreamDisplayName forçava prefixo 'FlixHub' em qualquer 'Server N'
2) addonGroupOf e ServerInfoCard so conheciam 'FlixHub' → tudo virava um grupo só

Solução:
- Backend: usa o nome do pack (stream.name = brand) como prefixo, nao hardcode FlixHub
- App: StreamHub na lista known + head do card usa a marca real
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
n = 0

# ---------- 1) lib/stremio-addons.js ----------
addons = root / "lib/stremio-addons.js"
if addons.exists():
    t = addons.read_text(encoding="utf-8")
    old = '''function pickStreamDisplayName(stream) {
  const title = stripNoise(stream && stream.title);
  const name = stripNoise(stream && stream.name);
  const blob = stripNoise([name, title, stream && stream.description].filter(Boolean).join(' '));
  const m = blob.match(/((?:flixhub\\s+)?(?:server|servidor)\\s*\\d+)/i);
  if (m) {
    let s = m[1].replace(/\\s+/g, ' ').trim();
    if (/^server\\s*\\d+/i.test(s)) s = 'FlixHub ' + s.replace(/^server/i, 'Server');
    else if (/^servidor\\s*\\d+/i.test(s)) s = 'FlixHub Server ' + (s.match(/\\d+/) || [''])[0];
    else s = s.replace(/flixhub/ig, 'FlixHub').replace(/\\bserver\\b/ig, 'Server');
    return s;
  }
  if (looksNumberedServer(name)) return name;
  return name || title;
}'''
    # Normalize escaping for actual file content
    old = old.replace('\\\\', '\\')
    new = '''function pickStreamDisplayName(stream) {
  const title = stripNoise(stream && stream.title);
  const name = stripNoise(stream && stream.name);
  const blob = stripNoise([name, title, stream && stream.description].filter(Boolean).join(' '));
  // Prefixo do pack (FlixHub, StreamHub, etc.) — vem do stream.name no flixhub.js
  const brand = (name && name.length >= 2 && name.length <= 24 && !/server|servidor/i.test(name))
    ? name.replace(/\\s+/g, ' ').trim()
    : null;
  // Captura "SmartHelp Server 2", "FlixHub Server 2", ou so "Server 2"
  const mFull = blob.match(/([A-Za-z0-9][A-Za-z0-9._-]{0,24}\\s+)?(?:server|servidor)\\s*\\d+/i);
  if (mFull) {
    let s = mFull[0].replace(/\\s+/g, ' ').trim();
    s = s.replace(/\\bservidor\\b/ig, 'Server').replace(/\\bserver\\b/ig, 'Server');
    // Se veio so "Server N", usa a marca do pack
    if (/^Server\\s*\\d+$/i.test(s)) {
      const prefix = brand || 'FlixHub';
      s = prefix + ' Server ' + (s.match(/\\d+/) || [''])[0];
    }
    return s.slice(0, 48);
  }
  if (looksNumberedServer(name)) return name;
  return name || title;
}'''
    new = new.replace('\\\\', '\\')
    if old in t:
        t = t.replace(old, new, 1)
        addons.write_text(t, encoding="utf-8")
        print("ok pickStreamDisplayName (brand do pack)")
        n += 1
    elif "brand do pack" in t or "const brand = (name && name.length" in t:
        print("ok pickStreamDisplayName ja aplicado")
    else:
        # try looser match
        import re
        pat = re.compile(
            r"function pickStreamDisplayName\(stream\) \{[\s\S]*?\n\}\n\nfunction streamToSourceDetailed",
            re.M,
        )
        m = pat.search(t)
        if m:
            t = t[: m.start()] + new + "\n\nfunction streamToSourceDetailed" + t[m.end() :]
            # fix: streamToSourceDetailed signature might have been eaten
            t = t.replace(
                "function streamToSourceDetailedfunction streamToSourceDetailed",
                "function streamToSourceDetailed",
            )
            addons.write_text(t, encoding="utf-8")
            print("ok pickStreamDisplayName (regex)")
            n += 1
        else:
            print("aviso: pickStreamDisplayName nao encontrado")
else:
    print("aviso: stremio-addons.js ausente")

# ---------- 2) DetailScreen.kt — addonGroupOf + ServerInfoCard ----------
kt = root / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
if kt.exists():
    t = kt.read_text(encoding="utf-8")

    old_known = 'val known = listOf("FlixHub", "Goldvip"'
    new_known = 'val known = listOf("StreamHub", "FlixHub", "Goldvip"'
    if "StreamHub" not in t[t.find("addonGroupOf") : t.find("addonGroupOf") + 400] if "addonGroupOf" in t else True:
        if old_known in t:
            t = t.replace(old_known, new_known, 1)
            print("ok StreamHub em known")
            n += 1
        else:
            print("aviso: known list nao encontrada")
    else:
        print("ok StreamHub ja em known")

    old_card = '''    val isFlix = label.contains("FlixHub", ignoreCase = true) || desc.contains("FlixHub", ignoreCase = true)
    val isJackett = label.contains("Jackettio", ignoreCase = true) || desc.contains("Jackettio", ignoreCase = true)
    val head = if (isFlix) "FlixHub" else label.split("·", "•", "|").first().trim().ifBlank { source.displayName }
    val serverMatch = if (isFlix) Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE).find(desc) else null
    val serverName = serverMatch?.value?.trim()
    val movieName = if (isFlix && serverMatch != null) {
        desc.substring(0, serverMatch.range.first).replace(Regex("^[⚡🚀🎬📺\\s]+"), "").trim().ifBlank { null }
    } else null
    // Filename: pega ate a primeira bandeira ou TorBox ou tamanho
    val filename = if (!isFlix) {'''

    new_card = '''    val isStreamHub = label.contains("StreamHub", ignoreCase = true) || desc.contains("StreamHub", ignoreCase = true)
    val isFlix = label.contains("FlixHub", ignoreCase = true) || desc.contains("FlixHub", ignoreCase = true)
    val isHubPack = isStreamHub || isFlix || Regex("[A-Za-z0-9]+\\s+Server\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(label + " " + desc)
    val isJackett = label.contains("Jackettio", ignoreCase = true) || desc.contains("Jackettio", ignoreCase = true)
    val head = when {
        isStreamHub -> "StreamHub"
        isFlix -> "FlixHub"
        isHubPack -> label.split("·", "•", "|", " ").first().trim().ifBlank { "Hub" }
        else -> label.split("·", "•", "|").first().trim().ifBlank { source.displayName }
    }
    val serverMatch = if (isHubPack) Regex("([A-Za-z0-9]+\\s+)?Server\\s*\\d+", RegexOption.IGNORE_CASE).find(desc + " " + label) else null
    val serverName = serverMatch?.value?.trim()
    val movieName = if (isHubPack && serverMatch != null) {
        desc.substring(0, minOf(serverMatch.range.first, desc.length)).replace(Regex("^[⚡🚀🎬📺\\s]+"), "").trim().ifBlank { null }
    } else null
    // Filename: pega ate a primeira bandeira ou TorBox ou tamanho
    val filename = if (!isHubPack) {'''

    if "isStreamHub" in t and "isHubPack" in t:
        print("ok ServerInfoCard ja separado")
    elif old_card in t:
        t = t.replace(old_card, new_card, 1)
        # Replace remaining isFlix references in the same function that should be isHubPack for filename branch only — already done
        print("ok ServerInfoCard pack-aware")
        n += 1
    else:
        print("aviso: bloco ServerInfoCard nao encontrado exato")
        # partial: just isFlix head
        if 'val head = if (isFlix) "FlixHub" else' in t:
            t = t.replace(
                'val isFlix = label.contains("FlixHub", ignoreCase = true) || desc.contains("FlixHub", ignoreCase = true)\n    val isJackett',
                'val isStreamHub = label.contains("StreamHub", ignoreCase = true) || desc.contains("StreamHub", ignoreCase = true)\n    val isFlix = label.contains("FlixHub", ignoreCase = true) || desc.contains("FlixHub", ignoreCase = true)\n    val isHubPack = isStreamHub || isFlix\n    val isJackett',
                1,
            )
            t = t.replace(
                'val head = if (isFlix) "FlixHub" else label.split("·", "•", "|").first().trim().ifBlank { source.displayName }',
                'val head = when { isStreamHub -> "StreamHub"; isFlix -> "FlixHub"; else -> label.split("·", "•", "|").first().trim().ifBlank { source.displayName } }',
                1,
            )
            print("ok ServerInfoCard partial")
            n += 1

    kt.write_text(t, encoding="utf-8")
else:
    print("aviso: DetailScreen.kt ausente")

print("total patches:", n)
print("fim apply_streamhub_separate")
