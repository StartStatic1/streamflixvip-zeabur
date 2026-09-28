#!/usr/bin/env python3
"""Hyper/Stravo: proxy com Referer do addon + host da CDN liberado."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]

p = root / "api" / "stream-proxy.js"
t = p.read_text()
if "hakunaymatata.com" not in t:
    t = t.replace(
        "const EXTRA_ALLOWED_HOSTS = [",
        "const EXTRA_ALLOWED_HOSTS = [\n  'hakunaymatata.com',\n  'bcdnxw.hakunaymatata.com',\n  'mzfi.me',",
        1,
    )
    print("ok hosts hyper")
else:
    print("ok hosts ja")

old = "    forwardHeaders['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';\n    forwardHeaders['Referer'] = target.origin + '/';"
new = """    forwardHeaders['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';
    const refOverride = String(req.query.referer || req.query.Referer || '').trim();
    forwardHeaders['Referer'] = refOverride || (target.origin + '/');
    if (refOverride) {
      try { forwardHeaders['Origin'] = new URL(refOverride).origin; } catch (_) {}
    }"""
if "refOverride" not in t:
    if old in t:
        t = t.replace(old, new, 1)
        print("ok referer query")
    else:
        print("aviso referer")
else:
    print("ok referer ja")
p.write_text(t)

a = root / "lib" / "stremio-addons.js"
at = a.read_text()
if "temporary loader" in at or len(at) < 3000:
    cache = root / "lib" / "stremio-addons.cache.js"
    if cache.exists() and cache.stat().st_size > 3000:
        at = cache.read_text()
        print("ok leu cache addons")
    else:
        raise SystemExit("stremio-addons stub")

WRAP = """
function wrapProxyHeaders(url, stream) {
  const hints = stream && stream.behaviorHints && stream.behaviorHints.proxyHeaders;
  const reqH = hints && hints.request;
  const ref = reqH && (reqH.Referer || reqH.referer);
  if (!url || !ref) return url;
  try {
    const u = new URL(url);
    const r = new URL(ref);
    if (u.hostname === r.hostname || u.hostname.endsWith('.' + r.hostname)) return url;
  } catch (_) {}
  const base = String(process.env.PUBLIC_BASE_URL || 'https://streamflixvip.online').replace(/\\/+$/, '');
  return base + '/api/stream-proxy?url=' + encodeURIComponent(url) + '&referer=' + encodeURIComponent(ref);
}

"""

if "function wrapProxyHeaders" not in at:
    at = at.replace(
        "function streamToSource(stream, addonName, addonPriority)",
        WRAP + "function streamToSource(stream, addonName, addonPriority)",
        1,
    )
    print("ok wrap fn")
else:
    print("ok wrap ja")

old_u = "  const url = (stream.url || '').trim();\n  if (!isHttpStreamUrl(url)) return { source: null, reason: url ? 'url_not_http_playable' : 'missing_url' };"
new_u = "  const rawUrl = (stream.url || '').trim();\n  const url = wrapProxyHeaders(rawUrl, stream);\n  if (!isHttpStreamUrl(rawUrl)) return { source: null, reason: rawUrl ? 'url_not_http_playable' : 'missing_url' };"
if "wrapProxyHeaders(rawUrl" not in at:
    if old_u in at:
        at = at.replace(old_u, new_u, 1)
        print("ok wrap uso")
    else:
        print("aviso wrap uso")
else:
    print("ok wrap uso ja")

a.write_text(at)
cache = root / "lib" / "stremio-addons.cache.js"
cache.write_text(at)
print("fim apply_hyper_proxy", a.stat().st_size, p.stat().st_size)
