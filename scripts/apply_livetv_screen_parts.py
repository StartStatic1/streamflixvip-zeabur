#!/usr/bin/env python3
import base64, gzip
from pathlib import Path
p1 = Path('scripts/livetv_screen_b64_part1.txt').read_text().strip()
p2 = Path('scripts/livetv_screen_b64_part2.txt').read_text().strip()
out = Path('android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvScreen.kt')
out.write_bytes(gzip.decompress(base64.b64decode(p1 + p2)))
print('LiveTvScreen restored', out.stat().st_size)
text = out.read_text()
assert text.startswith('package '), 'bad package'
assert 'SideBg' in text, 'missing SideBg'
assert 'SEE_FILE' not in text, 'still corrupted'
print('OK')
