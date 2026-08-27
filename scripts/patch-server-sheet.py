#!/usr/bin/env python3
"""Aplica melhorias no sheet de servidores do DetailScreen.kt."""
from pathlib import Path

p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
t = p.read_text(encoding='utf-8')

# Helpers de add-on (insere antes de FREE_SERVER_SLOTS se ainda nao existir)
if 'fun isAddonSource' not in t:
    marker = 'private const val FREE_SERVER_SLOTS = 1'
    helpers = '''private fun isAddonSource(label: String?): Boolean {
    val l = label.orEmpty()
    return l.startsWith("Fenix", ignoreCase = true) ||
        l.startsWith("Frost", ignoreCase = true) ||
        l.startsWith("King", ignoreCase = true) ||
        l.startsWith("BsCine", ignoreCase = true) ||
        l.startsWith("PopPlay", ignoreCase = true) ||
        l.startsWith("Addon", ignoreCase = true)
}

private fun audioHint(label: String?): String? {
    val l = label.orEmpty()
    return when {
        l.contains("Dublado", ignoreCase = true) -> "Dublado"
        l.contains("Legendado", ignoreCase = true) -> "Legendado"
        else -> null
    }
}

/** So o 1o IPTV liberado pro free. Add-ons sao sempre VIP. */
private const val FREE_SERVER_SLOTS = 1'''
    if marker not in t:
        raise SystemExit('FREE_SERVER_SLOTS marker not found')
    t = t.replace(marker, helpers, 1)
    print('helpers ok')
else:
    print('helpers already present')

# Callers: lock add-on for free
old_lock = 'val lockedForFree = !isVip && index >= FREE_SERVER_SLOTS'
new_lock = '''val isAddon = isAddonSource(source.source_label)
                                val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)'''
if old_lock in t:
    t = t.replace(old_lock, new_lock)
    print('lock callers ok', t.count(new_lock))
else:
    print('lock callers skip')

old_rec = 'isRecommended = index == 0,'
new_rec = 'isRecommended = index == 0 && !isAddonSource(source.source_label),'
# only replace inside SourceRow calls where lockedForFree was just above - safer global if unique enough
if 'isRecommended = index == 0 && !isAddon' not in t:
    # replace carefully: after lockedForFree lines
    t = t.replace(
        'isRecommended = index == 0,\n                                    isLockedForFree = lockedForFree,',
        'isRecommended = index == 0 && !isAddon,\n                                    isLockedForFree = lockedForFree,',
    )
    t = t.replace(
        'isRecommended = index == 0,\n                            isLockedForFree = lockedForFree,',
        'isRecommended = index == 0 && !isAddon,\n                            isLockedForFree = lockedForFree,',
    )
    print('recommended ok')

# Headers
t = t.replace(
    '"IPTV primeiro \u00b7 add-ons como extra"',
    '"IPTV estavel \u00b7 Add-ons so VIP"',
)
t = t.replace(
    '"IPTV primeiro · add-ons como extra"',
    '"IPTV estavel · Add-ons so VIP"',
)
t = t.replace(
    'fontSize = 16.sp,\n                            fontWeight = FontWeight.Bold,\n                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),',
    'fontSize = 20.sp,\n                            fontWeight = FontWeight.ExtraBold,\n                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 2.dp),',
)

p.write_text(t, encoding='utf-8')
print('wrote', p, 'bytes', p.stat().st_size)
