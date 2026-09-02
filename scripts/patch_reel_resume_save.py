from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/reels/ReelPlayerScreen.kt')
t = p.read_text()
old = '''            exo.setMediaItem(MediaItem.fromUri(url))\n            exo.prepare()\n            val resume = if (ReelLocalStore.isDone(prefs, storyId)) 0L else ReelLocalStore.savedPos(prefs, storyId, index)\n            if (resume > 3_000L) exo.seekTo(resume)\n            exo.playWhenReady = true'''
new = '''            exo.setMediaItem(MediaItem.fromUri(url))\n            exo.prepare()\n            prefs.edit()\n                .putBoolean(ReelLocalStore.doneKey(storyId), false)\n                .putInt(ReelLocalStore.idxKey(storyId), index)\n                .apply()\n            val resume = ReelLocalStore.savedPos(prefs, storyId, index)\n            if (resume > 3_000L) exo.seekTo(resume)\n            exo.playWhenReady = true'''
if old in t:
    t = t.replace(old, new, 1)
old2 = '''            val finished = prefs.getBoolean(ReelLocalStore.doneKey(storyId), false) ||\n                (exo.playbackState == Player.STATE_ENDED && index >= episodes.lastIndex) ||\n                (exo.duration > 0 && index >= episodes.lastIndex && exo.currentPosition > exo.duration * 0.92)'''
new2 = '''            val finished =\n                (exo.playbackState == Player.STATE_ENDED && index >= episodes.lastIndex) ||\n                (exo.duration > 0 && index >= episodes.lastIndex && exo.currentPosition > exo.duration * 0.92)'''
if old2 in t:
    t = t.replace(old2, new2, 1)
old3 = '''    LaunchedEffect(exo) {\n        while (true) {\n            val dur = exo.duration\n            progress = if (dur > 0) (exo.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f\n            delay(400)\n        }\n    }'''
new3 = '''    LaunchedEffect(exo) {\n        while (true) {\n            val dur = exo.duration\n            progress = if (dur > 0) (exo.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f\n            if (dur > 0 && exo.currentPosition > 2_000L && exo.playbackState != Player.STATE_ENDED) {\n                prefs.edit()\n                    .putBoolean(ReelLocalStore.doneKey(storyId), false)\n                    .putInt(ReelLocalStore.idxKey(storyId), index)\n                    .putLong(ReelLocalStore.posKey(storyId, index), exo.currentPosition)\n                    .apply()\n            }\n            delay(800)\n        }\n    }'''
if old3 in t:
    t = t.replace(old3, new3, 1)
p.write_text(t)
print('player resume patched')
