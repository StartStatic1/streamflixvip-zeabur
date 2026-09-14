#!/usr/bin/env python3
from pathlib import Path
p = Path('api/media-sources.js')
t = p.read_text(encoding='utf-8')
old = """      if (ra.p !== rb.p) return ra.p - rb.p;
      return ra.q - rb.q;"""
new = """      if (ra.q !== rb.q) return ra.q - rb.q;
      if (ra.p !== rb.p) return ra.p - rb.p;
      return 0;"""
if new in t:
    print('media-sources already quality-first')
elif old in t:
    p.write_text(t.replace(old, new, 1), encoding='utf-8')
    print('media-sources quality-first ok')
else:
    raise SystemExit('sort block not found')
