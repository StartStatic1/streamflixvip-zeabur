#!/usr/bin/env python3
"""ServerSourceCard: host sem qualidade no titulo; selos 720p/1080p/Dublado."""
from pathlib import Path

p = Path("android/app/src/main/java/com/streamflixvip/app/ui/detail/ServerPickerUi.kt")
t = p.read_text()

# 1) isAddonSourceLabel — nomes do painel (StreamFlix.Fenix, FrostStream, IPTV Bridge)
OLD_ADDON = '''internal fun isAddonSourceLabel(label: String?): Boolean {
    val l = label.orEmpty()
    return l.startsWith("Fenix", ignoreCase = true) ||
        l.startsWith("Frost", ignoreCase = true) ||
        l.startsWith("King", ignoreCase = true) ||
        l.startsWith("BsCine", ignoreCase = true) ||
        l.startsWith("PopPlay", ignoreCase = true) ||
        l.startsWith("Addon", ignoreCase = true) ||
        l.startsWith("Nuvio", ignoreCase = true)
}'''

NEW_ADDON = '''internal fun isAddonSourceLabel(label: String?): Boolean {
    val l = label.orEmpty()
    // IPTV nativo StreamFlix.* (Svent, maxcine, etc.) NAO conta como add-on
    val host = l.split("·", "•", "|").firstOrNull()?.trim().orEmpty()
    if (host.equals("StreamFlix.Svent", ignoreCase = true)) return false
    if (host.equals("StreamFlix.maxcine", ignoreCase = true)) return false
    if (host.equals("StreamFlix.dflix", ignoreCase = true)) return false
    if (host.startsWith("StreamFlix.", ignoreCase = true)) return true // Fenix etc no painel
    return host.startsWith("Fenix", ignoreCase = true) ||
        host.startsWith("Frost", ignoreCase = true) ||
        host.startsWith("King", ignoreCase = true) ||
        host.startsWith("BsCine", ignoreCase = true) ||
        host.startsWith("PopPlay", ignoreCase = true) ||
        host.startsWith("Addon", ignoreCase = true) ||
        host.startsWith("Nuvio", ignoreCase = true) ||
        host.startsWith("IPTV Bridge", ignoreCase = true) ||
        host.startsWith("FrostStream", ignoreCase = true) ||
        host.startsWith("HdHub", ignoreCase = true) ||
        host.startsWith("Comet", ignoreCase = true)
}

/** So o nome do servidor (sem 720p / Dublado no titulo). */
private fun hostTitleFromLabel(label: String?): String {
    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return "Servidor"
    val host = raw.split("·", "•").firstOrNull()?.trim().orEmpty()
    return host.ifBlank { raw }.take(28)
}'''

if OLD_ADDON in t:
    t = t.replace(OLD_ADDON, NEW_ADDON, 1)
    print("isAddon OK")
elif "hostTitleFromLabel" in t:
    print("hostTitle already")
else:
    print("WARN isAddon pattern")

# 2) Replace the title Text(source.displayName...) block to use hostTitle + chips
OLD_TITLE = '''            Column(modifier = Modifier.weight(1f)) {
                Text(
                    source.displayName,
                    fontSize = 14.sp,
                    fontWeight = if (isLockedForFree) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isLockedForFree) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                when {
                    isLockedForFree -> Text(
                        "PREMIUM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = GoldVip,
                    )
                    isRecommended -> Text(
                        "RECOMENDADO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = PurpleRec,
                    )
                    audio != null -> Text(
                        audio,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            badge?.let {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLockedForFree) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        badgeBg
                    },
                ) {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLockedForFree) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        } else {
                            badgeFg
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }'''

NEW_TITLE = '''            Column(modifier = Modifier.weight(1f)) {
                Text(
                    hostTitleFromLabel(source.source_label),
                    fontSize = 14.sp,
                    fontWeight = if (isLockedForFree) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isLockedForFree) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // Selos compactos (qualidade + audio + status)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLockedForFree) {
                        SeloChip("PREMIUM", GoldVip, GoldVip.copy(alpha = 0.2f))
                    } else if (isRecommended) {
                        SeloChip("RECOMENDADO", PurpleRec, PurpleRec.copy(alpha = 0.2f))
                    }
                    if (!isLockedForFree && badge != null) {
                        if (isRecommended) Spacer(Modifier.width(6.dp))
                        SeloChip(badge, badgeFg, badgeBg)
                    }
                    if (!isLockedForFree && audio != null) {
                        Spacer(Modifier.width(6.dp))
                        val audioColor = if (audio == "Dublado") Color(0xFF34D399) else Color(0xFF60A5FA)
                        SeloChip(audio, audioColor, audioColor.copy(alpha = 0.18f))
                    }
                }
            }'''

if OLD_TITLE in t:
    t = t.replace(OLD_TITLE, NEW_TITLE, 1)
    print("title+selos OK")
elif "SeloChip" in t:
    print("selos already")
else:
    raise SystemExit("title block not found")

# 3) Add SeloChip composable if missing
if "fun SeloChip" not in t:
    anchor = "@Composable\nfun ServerSourceCard("
    chip = '''@Composable
private fun SeloChip(text: String, fg: Color, bg: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
    ) {
        Text(
            text.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

'''
    if anchor not in t:
        raise SystemExit("ServerSourceCard anchor missing")
    t = t.replace(anchor, chip + anchor, 1)
    print("SeloChip added")

p.write_text(t)
assert "hostTitleFromLabel" in t
assert "SeloChip" in t
print("done")
