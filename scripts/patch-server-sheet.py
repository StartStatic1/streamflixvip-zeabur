#!/usr/bin/env python3
"""Aplica sheet de servidores: ServerSourceCard + add-ons so VIP."""
from pathlib import Path
import re

p = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
t = p.read_text(encoding='utf-8')

# 1) Lock add-ons for free users
old_lock = 'val lockedForFree = !isVip && index >= FREE_SERVER_SLOTS'
new_lock = (
    'val isAddon = isAddonSourceLabel(source.source_label)\n'
    '                                val lockedForFree = !isVip && (index >= FREE_SERVER_SLOTS || isAddon)'
)
if old_lock in t:
    t = t.replace(old_lock, new_lock)
    print('lock callers ok')
else:
    print('lock callers skip')

# 2) Recommended only for non-addon first item
t = t.replace(
    'isRecommended = index == 0,\n                                    isLockedForFree = lockedForFree,',
    'isRecommended = index == 0 && !isAddon,\n                                    isLockedForFree = lockedForFree,',
)
t = t.replace(
    'isRecommended = index == 0,\n                            isLockedForFree = lockedForFree,',
    'isRecommended = index == 0 && !isAddon,\n                            isLockedForFree = lockedForFree,',
)

# 3) Titles
t = t.replace(
    '"Escolha o servidor",\n                            fontSize = 16.sp,\n                            fontWeight = FontWeight.Bold,\n                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),',
    '"Escolha o servidor",\n                            fontSize = 20.sp,\n                            fontWeight = FontWeight.ExtraBold,\n                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),',
)

# 4) SourceRow -> ServerSourceCard wrapper
wrapper = '''@Composable
private fun SourceRow(
    source: VipSource,
    isRecommended: Boolean,
    isLockedForFree: Boolean,
    onClick: () -> Unit,
    onLockedClick: () -> Unit,
) {
    ServerSourceCard(
        source = source,
        isRecommended = isRecommended,
        isLockedForFree = isLockedForFree,
        onClick = onClick,
        onLockedClick = onLockedClick,
    )
}'''

pat = re.compile(
    r'@Composable\nprivate fun SourceRow\(\n    source: VipSource,\n    isRecommended: Boolean,\n    isLockedForFree: Boolean,\n    onClick: \(\) -> Unit,\n    onLockedClick: \(\) -> Unit,\n\) \{.*?\n\}',
    re.DOTALL,
)
m = pat.search(t)
if m and 'ServerSourceCard' not in m.group(0):
    t = pat.sub(wrapper, t, count=1)
    print('SourceRow wrapper ok')
elif 'ServerSourceCard' in t:
    print('SourceRow already wired')
else:
    raise SystemExit('SourceRow not found')

p.write_text(t, encoding='utf-8')
print('OK', p, p.stat().st_size)
