#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/reels/ReelPlayerScreen.kt')
t = p.read_text()
if 'ReelLocalStore' in t:
    print('ja ok')
    raise SystemExit(0)
t = t.replace(
    'private fun reelPrefs(context: Context) =\n    context.getSharedPreferences("sfv_reels", Context.MODE_PRIVATE)\n\n',
    '',
)
t = t.replace('val prefs = remember { reelPrefs(context) }', 'val prefs = remember { ReelLocalStore.prefs(context) }')
repls = [
    ('"like_$storyId"', 'ReelLocalStore.likeKey(storyId)'),
    ('"idx_$storyId"', 'ReelLocalStore.idxKey(storyId)'),
    ('"done_$storyId"', 'ReelLocalStore.doneKey(storyId)'),
    ('"pos_${storyId}_$index"', 'ReelLocalStore.posKey(storyId, index)'),
]
for a,b in repls:
    t = t.replace(a,b)
p.write_text(t)
print('player scoped', t.count('ReelLocalStore'))
