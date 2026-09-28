#!/usr/bin/env python3
"""HEAD sem ir na CDN — evita gastar token; devolve tipo p/ o Exo."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
p = root / "api" / "stream-proxy.js"
t = p.read_text()

safe = """  if (req.method === 'HEAD') {
    res.setHeader('Content-Type', 'video/mp4');
    res.setHeader('Accept-Ranges', 'bytes');
    res.status(200).end();
    return;
  }"""

if "Content-Type', 'video/mp4'" in t and "req.method === 'HEAD'" in t and "bytes=0-0" not in t:
    print("ok head safe ja")
else:
    t2, n = re.subn(
        r"  if \(req\.method === 'HEAD'\) \{[\s\S]*?return;\n    \}\n  \}",
        safe,
        t,
        count=1,
    )
    if n:
        t = t2
        print("ok head safe")
    elif "if (req.method === 'HEAD') {\n    res.status(200).end();" in t:
        t = t.replace(
            "  if (req.method === 'HEAD') {\n    res.status(200).end();\n    return;\n  }",
            safe,
            1,
        )
        print("ok head safe vazio")
    else:
        print("aviso head")

p.write_text(t)
print("fim apply_hyper_head_safe", p.stat().st_size)
