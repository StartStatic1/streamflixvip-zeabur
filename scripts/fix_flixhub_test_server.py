#!/usr/bin/env python3
"""Restaura api/admin-flixhub.js e api/flixhub.js e melhora teste de servidor."""
from pathlib import Path
import urllib.request
import sys

ROOT = Path(__file__).resolve().parents[1]
admin_path = ROOT / "api/admin-flixhub.js"
fh_path = ROOT / "api/flixhub.js"

def fetch(url):
    return urllib.request.urlopen(url, timeout=40).read().decode("utf-8", errors="replace")

def load_good(path_suffix, markers):
    commits = [
        "48550bf",
        "da7ff9d",
        "6c2a9cf",
        "dc340bc",
        "a50cd4a",
    ]
    for c in commits:
        url = f"https://raw.githubusercontent.com/StartStatic1/streamflixvip-zeabur/{c}/{path_suffix}"
        try:
            t = fetch(url)
            if "PLACEHOLDER" in t or len(t) < 500:
                continue
            if all(m in t for m in markers):
                print("restored", path_suffix, "from", c, "len", len(t))
                return t
        except Exception as e:
            print("skip", c, e)
    return None

admin = admin_path.read_text(encoding="utf-8") if admin_path.exists() else ""
if "PLACEHOLDER" in admin or "test-server" not in admin or len(admin) < 500:
    admin = load_good("api/admin-flixhub.js", ["test-server", "module.exports"])
    if not admin:
        print("ERROR: cannot restore admin-flixhub.js"); sys.exit(1)

fh = fh_path.read_text(encoding="utf-8") if fh_path.exists() else ""
if "PLACEHOLDER" in fh or "xtreamOnce" not in fh or len(fh) < 500:
    fh = load_good("api/flixhub.js", ["xtreamOnce", "module.exports"])
    if not fh:
        print("ERROR: cannot restore flixhub.js"); sys.exit(1)

# Patch test-server block
idx = admin.find("  if (action === 'test-server')")
idx2 = admin.find("  if (action === 'list')")
if idx < 0 or idx2 < 0:
    print("ERROR anchors", idx, idx2); sys.exit(1)

NEW = r'''  if (action === 'test-server') {
    const hostRaw = String(body.host || '').replace(/\/+$/, '');
    const userXt = String(body.user || '');
    const pass = String(body.pass || '');
    if (!hostRaw || !userXt || !pass) {
      res.status(400).json({ ok: false, error: 'host, user e senha obrigatorios' });
      return;
    }
    const UAS = [
      'IPTVSmartersPro/1.0',
      'IPTVSmartersPlayer/1.1.0',
      'TiviMate/4.7.0',
      'XCIPTV/1.0',
      'okhttp/4.12.0',
      'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36',
      'VLC/3.0.20 LibVLC/3.0.20',
    ];
    function baseVariants(h) {
      const out = [];
      const seen = new Set();
      const add = (x) => {
        const k = String(x || '').replace(/\/+$/, '');
        if (k && !seen.has(k)) { seen.add(k); out.push(k); }
      };
      add(h);
      try {
        const u = new URL(h.includes('://') ? h : 'http://' + h);
        const ports = u.port ? [u.port] : ['', '8080', '25461', '8888', '80'];
        for (const p of ports) {
          const nu = new URL(u.toString());
          if (p) nu.port = p; else nu.port = '';
          add(nu.origin);
        }
        if (u.protocol === 'http:') {
          const https = new URL(u.toString());
          https.protocol = 'https:';
          add(https.origin);
        }
      } catch (_) {}
      return out;
    }
    const bases = baseVariants(hostRaw);
    let lastErr = null;
    let sawHtml = false;
    for (const base of bases) {
      for (const ua of UAS) {
        try {
          const urlInfo = new URL(base + '/player_api.php');
          urlInfo.searchParams.set('username', userXt);
          urlInfo.searchParams.set('password', pass);
          const ac = new AbortController();
          const timer = setTimeout(() => ac.abort(), 14000);
          let r = await fetch(urlInfo.toString(), {
            signal: ac.signal,
            redirect: 'follow',
            headers: {
              'User-Agent': ua,
              Accept: 'application/json,text/plain,*/*',
              'Accept-Language': 'pt-BR,pt;q=0.9',
            },
          });
          clearTimeout(timer);
          let text = await r.text();
          if (!r.ok) {
            lastErr = 'HTTP ' + r.status + ' em ' + base;
            if (r.status === 403) {
              res.status(200).json({
                ok: false,
                error: 'HTTP 403 — painel bloqueou o IP do VPS. No celular funciona; no servidor nao. Peca para liberar o IP do VPS ou use outro host.',
                status: 403,
                hostTried: base,
              });
              return;
            }
            continue;
          }
          const trimmed = (text || '').trim();
          if (/^<!DOCTYPE|^<html|Welcome to nginx|cloudflare/i.test(trimmed)) {
            sawHtml = true;
            lastErr = 'HTML/nginx em ' + base + ' (nao e API Xtream neste endereco/porta)';
            continue;
          }
          let data;
          try { data = JSON.parse(text); }
          catch (_) {
            lastErr = 'Resposta sem JSON em ' + base + ': ' + trimmed.slice(0, 60);
            continue;
          }
          let vodCount = 0;
          let sample = [];
          try {
            const urlVod = new URL(base + '/player_api.php');
            urlVod.searchParams.set('username', userXt);
            urlVod.searchParams.set('password', pass);
            urlVod.searchParams.set('action', 'get_vod_streams');
            const ac2 = new AbortController();
            const t2 = setTimeout(() => ac2.abort(), 18000);
            const r2 = await fetch(urlVod.toString(), {
              signal: ac2.signal,
              headers: { 'User-Agent': ua, Accept: 'application/json,text/plain,*/*' },
            });
            clearTimeout(t2);
            const tVod = await r2.text();
            if (r2.ok) {
              try {
                const arr = JSON.parse(tVod);
                if (Array.isArray(arr)) {
                  vodCount = arr.length;
                  sample = arr.slice(0, 3).map((x) => x.name || x.title || '?');
                }
              } catch (_) {}
            }
          } catch (_) {}
          const userInfo = data && data.user_info ? data.user_info : data;
          res.status(200).json({
            ok: true,
            vodCount,
            sample,
            ua,
            hostUsed: base,
            auth: !!(userInfo && (userInfo.auth === 1 || userInfo.status === 'Active' || userInfo.username || data)),
          });
          return;
        } catch (e) {
          lastErr = String(e && e.message ? e.message : e);
        }
      }
    }
    for (const base of bases.slice(0, 3)) {
      try {
        const g = new URL(base + '/get.php');
        g.searchParams.set('username', userXt);
        g.searchParams.set('password', pass);
        g.searchParams.set('type', 'm3u_plus');
        g.searchParams.set('output', 'ts');
        const ac = new AbortController();
        const timer = setTimeout(() => ac.abort(), 12000);
        const r = await fetch(g.toString(), {
          signal: ac.signal,
          headers: { 'User-Agent': 'IPTVSmartersPro/1.0', Accept: '*/*' },
        });
        clearTimeout(timer);
        const text = await r.text();
        if (r.ok && /#EXTM3U/i.test(text)) {
          res.status(200).json({
            ok: true,
            vodCount: 0,
            sample: ['get.php OK (#EXTM3U)'],
            hostUsed: base,
            note: 'player_api nao devolveu JSON, mas get.php respondeu M3U. Pode salvar; se o addon nao listar, o host bloqueia player_api no VPS.',
          });
          return;
        }
      } catch (_) {}
    }
    let msg = lastErr || 'Falha ao testar';
    if (sawHtml) {
      msg = 'Host devolve pagina HTML/nginx em vez de API Xtream. Confira URL e PORTA do painel (ex: :8080 ou :25461). No leitor M3U pode funcionar com outro endereco.';
    }
    res.status(200).json({ ok: false, error: msg });
    return;
  }

'''

if "baseVariants" not in admin:
    admin = admin[:idx] + NEW + admin[idx2:]
    print("admin test-server replaced")
else:
    print("admin already has baseVariants")

old_once = """    try {
      return JSON.parse(text);
    } catch (_) {
      throw new Error('Resposta sem JSON: ' + text.slice(0, 80));
    }"""
new_once = """    try {
      return JSON.parse(text);
    } catch (_) {
      const head = String(text || '').trim().slice(0, 80);
      if (/^<!DOCTYPE|^<html|Welcome to nginx|cloudflare/i.test(head)) {
        throw new Error('HTML/nginx em vez de JSON (host/porta errados ou API bloqueada): ' + head);
      }
      throw new Error('Resposta sem JSON: ' + head);
    }"""
if old_once in fh:
    fh = fh.replace(old_once, new_once, 1)
    print("flixhub error msg improved")
elif "HTML/nginx em vez de JSON" in fh:
    print("flixhub already improved")
else:
    print("WARN flixhub once block not found")

admin_path.write_text(admin, encoding="utf-8")
fh_path.write_text(fh, encoding="utf-8")
print("ok", admin_path, len(admin))
print("ok", fh_path, len(fh))
assert "PLACEHOLDER" not in admin and "PLACEHOLDER" not in fh
assert "test-server" in admin and "xtreamOnce" in fh
print("done")
