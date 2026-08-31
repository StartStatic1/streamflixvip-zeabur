#!/usr/bin/env python3
from pathlib import Path
p = Path('android/app/src/main/java/com/streamflixvip/app/MainActivity.kt')
t = p.read_text()
old = 'val showTopBar = currentRoute in listOf("home", "explore", "reels", "livetv", "profile", "mylist", "genres")'
new = 'val showTopBar = currentRoute in listOf("home", "explore", "livetv", "profile", "mylist", "genres")'
if old in t:
    p.write_text(t.replace(old, new, 1))
    print('topbar ok')
else:
    print('topbar skip', 'reels' in t[t.find('showTopBar'):t.find('showTopBar')+180] if 'showTopBar' in t else 'no')
g = Path('android/app/build.gradle.kts')
gt = g.read_text()
for a,b in [('versionCode = 110901','versionCode = 110902'),('versionName = "11.9.1"','versionName = "11.9.2"'),('versionCode = 110900','versionCode = 110902'),('versionName = "11.9.0"','versionName = "11.9.2"')]:
    gt = gt.replace(a,b)
g.write_text(gt)
print('version patched')
