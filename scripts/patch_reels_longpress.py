from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/ui/reels/ReelsScreen.kt')
t = p.read_text()
old = '''                                        ReelsFilter.Todas -> {
                                            if (ReelLocalStore.isLiked(prefs, story.id)) {
                                                ReelLocalStore.setLiked(prefs, story.id, false)
                                            }
                                            if (ReelLocalStore.isInProgress(prefs, story.id)) {
                                                ReelLocalStore.clearProgress(prefs, story.id)
                                            }
                                        }'''
new = '''                                        ReelsFilter.Todas -> { }'''
if old in t:
    t = t.replace(old, new, 1)
    p.write_text(t)
    print('longpress ok')
else:
    print('trecho nao achado ou ja patch')
