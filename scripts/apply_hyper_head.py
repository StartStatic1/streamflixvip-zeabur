#!/usr/bin/env python3
"""HEAD vazio no stream-proxy fazia o Exo falhar no primeiro clique."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "api" / "stream-proxy.js"
t = p.read_text()

old = """  if (req.method === 'HEAD') {
    res.status(200).end();
    return;
  }"""

new = """  if (req.method === 'HEAD') {
    try {
      const hh = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
        Range: 'bytes=0-0',
      };
      const refOverride = String(req.query.referer || req.query.Referer || '').trim();
      const host = target.hostname || '';
      if (refOverride) hh.Referer = refOverride;
      else if (/hakunaymatata\\.com$/i.test(host) || /mzfi\\.me$/i.test(host)) hh.Referer = 'https://mzfi.me/';
      else hh.Referer = target.origin + '/';
      const ac = new AbortController();
      const tm = setTimeout(() => ac.abort(), 8000);
      const up = await fetch(target.toString(), { method: 'GET', headers: hh, signal: ac.signal });
      clearTimeout(tm);
      const ct = up.headers.get('content-type') || 'video/mp4';
      const cr = up.headers.get('content-range');
      const cl = up.headers.get('content-length');
      res.setHeader('Content-Type', ct);
      res.setHeader('Accept-Ranges', 'bytes');
      if (cr) res.setHeader('Content-Range', cr);
      if (cl) res.setHeader('Content-Length', cl);
      res.status(up.status === 206 ? 200 : (up.ok ? 200 : up.status)).end();
      return;
    } catch (_) {
      res.status(200).end();
      return;
    }
  }"""

if "Range: 'bytes=0-0'" in t:
    print("ok head ja")
elif old in t:
    t = t.replace(old, new, 1)
    print("ok head proxy")
else:
    print("aviso head")

if "hakunaymatata.com" in t and "refOverride" in t:
    print("ok get referer")
elif "forwardHeaders['Referer'] = target.origin + '/';" in t:
    t = t.replace(
        "forwardHeaders['Referer'] = target.origin + '/';",
        """const refOverride = String(req.query.referer || req.query.Referer || '').trim();
    const host = target.hostname || '';
    if (refOverride) forwardHeaders['Referer'] = refOverride;
    else if (/hakunaymatata\\.com$/i.test(host) || /mzfi\\.me$/i.test(host)) forwardHeaders['Referer'] = 'https://mzfi.me/';
    else forwardHeaders['Referer'] = target.origin + '/';""",
        1,
    )
    print("ok get referer")

p.write_text(t)
print("fim apply_hyper_head", p.stat().st_size)
