#!/usr/bin/env python3
import base64, gzip
from pathlib import Path
parts = [Path(f'scripts/livetv_b64_{i}.txt').read_text().strip() for i in range(4)]
b64 = ''.join(parts)
out = Path('android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvScreen.kt')
out.write_bytes(gzip.decompress(base64.b64decode(b64)))
text = out.read_text()
assert text.startswith('package '), 'bad package'
assert 'SideBg' in text, 'missing SideBg'
assert 'SEE_FILE' not in text, 'still corrupted'
print('LiveTvScreen restored', out.stat().st_size)
print('OK')
