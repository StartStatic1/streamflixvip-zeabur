#!/usr/bin/env python3
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
p = root / "lib" / "stremio-addons.js"
t = p.read_text()
if "temporary loader" in t or len(t) < 3000:
    cache = root / "lib" / "stremio-addons.cache.js"
    t = cache.read_text() if cache.exists() else t

new = """function wrapProxyHeaders(url, stream) {
  if (!url) return url;
  const hints = stream && stream.behaviorHints && stream.behaviorHints.proxyHeaders;
  const reqH = hints && hints.request;
  let ref = reqH && (reqH.Referer || reqH.referer);
  try {
    const host = new URL(url).hostname || '';
    if (/hakunaymatata\\.com$/i.test(host) || /mzfi\\.me$/i.test(host)) {
      ref = ref || 'https://mzfi.me/';
    }
  } catch (_) {}
  if (!ref) return url;
  try {
    const u = new URL(url);
    const r = new URL(ref);
    if (u.hostname === r.hostname || u.hostname.endsWith('.' + r.hostname)) return url;
  } catch (_) {}
  if (/stream-proxy\\?/.test(url)) return url;
  const base = String(process.env.PUBLIC_BASE_URL || 'https://streamflixvip.online').replace(/\\/+$/, '');
  return base + '/api/stream-proxy?url=' + encodeURIComponent(url) + '&referer=' + encodeURIComponent(ref);
}"""

if "ref = ref ||" in t and "hakunaymatata" in t:
    print("ok wrap forte ja")
else:
    t2, n = re.subn(r"function wrapProxyHeaders\(url, stream\) \{[\s\S]*?\n\}", new, t, count=1)
    if n:
        t = t2
        print("ok wrap forte")
    else:
        print("aviso wrap")

p.write_text(t)
(root / "lib" / "stremio-addons.cache.js").write_text(t)
print("fim apply_hyper_force", p.stat().st_size)
