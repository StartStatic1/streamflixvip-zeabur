#!/usr/bin/env python3
import base64, gzip
from pathlib import Path
B64 = open('scripts/ps_gz.b64').read().strip()
raw = gzip.decompress(base64.b64decode(B64))
out = Path('android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt')
out.write_bytes(raw)
text = raw.decode()
assert 'series_prefs' in text and 'reapplyTrackPreferences' in text
print('OK', out, len(raw))
