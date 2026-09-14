#!/usr/bin/env python3
from pathlib import Path
p = Path('api/media-sources.js')
t = p.read_text(encoding='utf-8')
quality_first = """      if (ra.q !== rb.q) return ra.q - rb.q;
      if (ra.p !== rb.p) return ra.p - rb.p;
      return 0;"""
priority_first = """      if (ra.p !== rb.p) return ra.p - rb.p;
      return ra.q - rb.q;"""
if priority_first in t and quality_first not in t:
    print('media-sources already panel-priority')
elif quality_first in t:
    p.write_text(t.replace(quality_first, priority_first, 1), encoding='utf-8')
    print('media-sources panel-priority restored')
else:
    raise SystemExit('sort block not found')
