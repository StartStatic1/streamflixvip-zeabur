#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
s = (ROOT / 'server.js').read_text(encoding='utf-8')
if 'free-catalog' in s:
    print('server.js already has free-catalog')
else:
    s = s.replace(
        "const mediaSources = require('./api/media-sources.js');",
        "const mediaSources = require('./api/media-sources.js');\nconst freeCatalog = require('./api/free-catalog.js');",
    )
    s = s.replace(
        "app.all('/api/media-sources', wrap(mediaSources));",
        "app.all('/api/media-sources', wrap(mediaSources));\napp.all('/api/free-catalog', wrap(freeCatalog));",
    )
    (ROOT / 'server.js').write_text(s, encoding='utf-8')
    print('server.js patched')
