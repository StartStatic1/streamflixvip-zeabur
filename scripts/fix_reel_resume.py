from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/reels/ReelPlayerScreen.kt')
t = p.read_text()
t = t.replace(
'''        val saved = prefs.getInt(ReelLocalStore.idxKey(storyId), -1)
        val start = if (saved >= 0 && !prefs.getBoolean(ReelLocalStore.doneKey(storyId), false)) saved else 0''',
'''        val saved = ReelLocalStore.savedIndex(prefs, storyId)
        val start = if (saved >= 0 && !ReelLocalStore.isDone(prefs, storyId)) saved else 0''',
)
t = t.replace('prefs.getBoolean(ReelLocalStore.likeKey(storyId), false)', 'ReelLocalStore.isLiked(prefs, storyId)')
t = t.replace(
    'val resume = if (prefs.getBoolean(ReelLocalStore.doneKey(storyId), false)) 0L else prefs.getLong(ReelLocalStore.posKey(storyId, index), 0L)',
    'val resume = if (ReelLocalStore.isDone(prefs, storyId)) 0L else ReelLocalStore.savedPos(prefs, storyId, index)',
)
p.write_text(t)
print('ok')
