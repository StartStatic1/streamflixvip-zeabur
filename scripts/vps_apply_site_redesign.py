#!/usr/bin/env python3
"""Aplica redesign shell no Public/index.html do StreamFlixVIP."""
from pathlib import Path
import shutil
from datetime import datetime

ROOT = Path("/root/streamflix")
INDEX = ROOT / "Public" / "index.html"
if not INDEX.exists():
    INDEX = ROOT / "public" / "index.html"
if not INDEX.exists():
    raise SystemExit("index.html nao encontrado")

bak = INDEX.with_suffix(".html.bak-" + datetime.now().strftime("%Y%m%d%H%M%S"))
shutil.copy2(INDEX, bak)
print("backup", bak)

html = INDEX.read_text(encoding="utf-8", errors="replace")

NEW_CSS = r"""
    /* REDESIGN 2026 — Cinema shell */
    :root {
      --sidebar-w: 232px;
      --topbar-h: 64px;
      --bottom-nav-h: 62px;
      --content-max: 1400px;
    }
    @media (min-width: 1024px) {
      body.shell-ready { padding-left: var(--sidebar-w); }
      .sf-sidebar {
        display: flex !important; position: fixed; left: 0; top: 0; bottom: 0;
        width: var(--sidebar-w); z-index: 200; flex-direction: column;
        background: linear-gradient(180deg, #0a1018 0%, #060a10 100%);
        border-right: 1px solid var(--border-soft); padding: 18px 14px 20px;
      }
      nav#mainNav.sf-topbar, nav#mainNav {
        left: var(--sidebar-w) !important; width: calc(100% - var(--sidebar-w)) !important;
      }
      .hamburger-btn, #hamburgerBtn, .drawer, .drawer-overlay { display: none !important; }
      .sf-bottom-nav { display: none !important; }
    }
    @media (max-width: 1023px) {
      .sf-sidebar { display: none !important; }
      .hamburger-btn, #hamburgerBtn, .drawer, .drawer-overlay { display: none !important; }
      body { padding-bottom: calc(var(--bottom-nav-h) + env(safe-area-inset-bottom, 0px)); }
      .sf-bottom-nav { display: flex !important; }
    }
    .sf-sidebar .sf-side-brand {
      display: flex; align-items: center; gap: 10px; padding: 6px 10px 20px; cursor: pointer;
    }
    .sf-sidebar .sf-side-brand .logo-icon {
      width: 36px; height: 36px; border-radius: 10px;
      background: linear-gradient(135deg, var(--accent), var(--accent2));
      display: grid; place-items: center; font-weight: 800; color: #041018;
    }
    .sf-sidebar .sf-side-name {
      font-family: var(--font-display, Fraunces, serif); font-weight: 700; font-size: 1.05rem; color: var(--text);
    }
    .sf-sidebar .sf-side-name em { font-style: normal; color: var(--accent2); }
    .sf-side-nav { display: flex; flex-direction: column; gap: 4px; flex: 1; }
    .sf-side-nav button {
      display: flex; align-items: center; gap: 12px; padding: 11px 12px; border-radius: 12px;
      color: var(--muted); font-weight: 600; font-size: 0.92rem; background: transparent;
      border: 0; cursor: pointer; text-align: left; width: 100%; font-family: inherit;
    }
    .sf-side-nav button:hover { background: rgba(46,230,214,0.08); color: var(--text); }
    .sf-side-nav button.active {
      background: rgba(46,230,214,0.14); color: #fff; box-shadow: inset 3px 0 0 var(--accent2);
    }
    .sf-side-nav svg { width: 20px; height: 20px; flex-shrink: 0; }
    .sf-side-section {
      font-size: 0.65rem; letter-spacing: .12em; text-transform: uppercase;
      color: var(--muted-dim); padding: 16px 12px 6px; font-weight: 700;
    }
    .sf-side-foot { padding-top: 12px; border-top: 1px solid var(--border-soft); font-size: 0.72rem; color: var(--muted-dim); }
    nav#mainNav {
      position: fixed; top: 0; left: 0; right: 0; height: var(--topbar-h); z-index: 150;
      display: flex; align-items: center; gap: 12px; padding: 0 18px;
      background: rgba(5,8,12,0.78); backdrop-filter: blur(16px);
      border-bottom: 1px solid var(--border-soft);
    }
    @media (min-width: 1024px) {
      nav#mainNav .logo { display: none; }
    }
    nav#mainNav .nav-search {
      flex: 1; max-width: 520px; margin: 0 auto; height: 42px; border-radius: 999px;
      background: rgba(14,22,32,0.9); border: 1px solid var(--border); padding: 0 14px;
    }
    .sf-bottom-nav {
      display: none; position: fixed; left: 0; right: 0; bottom: 0; z-index: 180;
      height: calc(var(--bottom-nav-h) + env(safe-area-inset-bottom, 0px));
      padding-bottom: env(safe-area-inset-bottom, 0px);
      background: rgba(6,10,16,0.94); backdrop-filter: blur(18px);
      border-top: 1px solid var(--border-soft);
      justify-content: space-around; align-items: center;
    }
    .sf-bottom-nav button {
      flex: 1; height: 100%; display: flex; flex-direction: column; align-items: center;
      justify-content: center; gap: 3px; border: 0; background: transparent;
      color: var(--muted); font-size: 0.65rem; font-weight: 600; font-family: inherit; cursor: pointer;
    }
    .sf-bottom-nav button.active { color: var(--accent2); }
    .sf-bottom-nav button svg { width: 22px; height: 22px; }
    #page-home .hero { min-height: min(72vh, 640px); border-radius: 0 0 28px 28px; overflow: hidden; }
    @media (min-width: 1024px) {
      #page-home .hero {
        margin: 12px 20px 8px; border-radius: 22px; min-height: min(68vh, 560px);
        border: 1px solid var(--border-soft);
      }
    }
    #page-home .hero-title {
      font-family: var(--font-display, Fraunces, serif);
      font-size: clamp(1.8rem, 4.5vw, 3.2rem); font-weight: 700; line-height: 1.08;
    }
    #page-home .btn-primary {
      background: linear-gradient(135deg, var(--accent), var(--accent2));
      color: #041018; border: 0; font-weight: 800; border-radius: 999px;
      padding: 12px 22px; box-shadow: 0 8px 28px rgba(46,230,214,0.28);
    }
    .card { border-radius: 14px; transition: transform .2s ease, box-shadow .2s ease; }
    .card:hover { transform: translateY(-4px) scale(1.02); box-shadow: 0 16px 40px rgba(0,0,0,0.45); }
    footer {
      border-top: 1px solid var(--border-soft); padding: 28px 20px 24px;
      margin-top: 20px; background: #060a10;
    }
    .footer-copy { text-align: center; font-size: 0.75rem; color: var(--muted-dim); margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border-soft); }
"""

if "REDESIGN 2026 — Cinema shell" not in html:
    if "</style>" not in html:
        raise SystemExit("</style> nao encontrado")
    html = html.replace("</style>", NEW_CSS + "\n</style>", 1)
    print("CSS ok")
else:
    print("CSS ja presente")

SIDEBAR = """
<aside class=\"sf-sidebar\" id=\"sfSidebar\" aria-label=\"Navegacao\">
  <div class=\"sf-side-brand\" onclick=\"navigate('home')\">
    <div class=\"logo-icon\">S</div>
    <div class=\"sf-side-name\">Stream<em>Flix</em>VIP</div>
  </div>
  <nav class=\"sf-side-nav\">
    <button type=\"button\" class=\"active\" data-nav=\"home\" onclick=\"navigate('home');setSideNav('home')\">
      <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M3 10.5L12 3l9 7.5V20a1 1 0 01-1 1h-5v-6H9v6H4a1 1 0 01-1-1v-9.5z\"/></svg>
      Inicio
    </button>
    <button type=\"button\" data-nav=\"movies\" onclick=\"navigate('all','movie');setSideNav('movies')\">
      <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"6\" width=\"20\" height=\"12\" rx=\"2\"/></svg>
      Filmes
    </button>
    <button type=\"button\" data-nav=\"series\" onclick=\"navigate('all','tv');setSideNav('series')\">
      <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"3\" y=\"5\" width=\"18\" height=\"14\" rx=\"2\"/></svg>
      Series
    </button>
    <button type=\"button\" data-nav=\"anime\" onclick=\"navigate('all','anime');setSideNav('anime')\">
      <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"9\"/></svg>
      Animes
    </button>
    <button type=\"button\" data-nav=\"watchlist\" onclick=\"navigate('watchlist');setSideNav('watchlist')\">
      <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M19 21l-7-4-7 4V5a2 2 0 012-2h10a2 2 0 012 2z\"/></svg>
      Minha Lista
    </button>
    <div class=\"sf-side-section\">Conta</div>
    <button type=\"button\" onclick=\"typeof openVipModal==='function'&&openVipModal()\">
      <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M12 2l2.4 7.2H22l-6 4.8 2.4 7.2L12 16.8 5.6 21.2 8 14 2 9.2h7.6z\"/></svg>
      Ativar VIP
    </button>
  </nav>
  <div class=\"sf-side-foot\">© 2026 StreamFlixVIP</div>
</aside>
"""

if 'id="sfSidebar"' not in html:
    if '<nav id="mainNav">' in html:
        html = html.replace(
            '<nav id="mainNav">',
            SIDEBAR + '\n<nav id="mainNav" class="sf-topbar">',
            1,
        )
        print("sidebar ok")
    else:
        print("WARN mainNav nao encontrado")
else:
    print("sidebar ja presente")

BOTTOM = """
<nav class=\"sf-bottom-nav\" id=\"sfBottomNav\" aria-label=\"Mobile\">
  <button type=\"button\" class=\"active\" data-nav=\"home\" onclick=\"navigate('home');setSideNav('home')\">
    <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M3 10.5L12 3l9 7.5V20a1 1 0 01-1 1h-5v-6H9v6H4a1 1 0 01-1-1v-9.5z\"/></svg>
    Inicio
  </button>
  <button type=\"button\" data-nav=\"movies\" onclick=\"navigate('all','movie');setSideNav('movies')\">
    <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"6\" width=\"20\" height=\"12\" rx=\"2\"/></svg>
    Filmes
  </button>
  <button type=\"button\" data-nav=\"series\" onclick=\"navigate('all','tv');setSideNav('series')\">
    <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"3\" y=\"5\" width=\"18\" height=\"14\" rx=\"2\"/></svg>
    Series
  </button>
  <button type=\"button\" data-nav=\"watchlist\" onclick=\"navigate('watchlist');setSideNav('watchlist')\">
    <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M19 21l-7-4-7 4V5a2 2 0 012-2h10a2 2 0 012 2z\"/></svg>
    Lista
  </button>
  <button type=\"button\" onclick=\"typeof openAuthModal==='function'&&openAuthModal()\">
    <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"7\" r=\"4\"/><path d=\"M20 21a8 8 0 10-16 0\"/></svg>
    Eu
  </button>
</nav>
"""

if 'id="sfBottomNav"' not in html:
    if "<footer>" in html:
        html = html.replace("<footer>", BOTTOM + "\n<footer>", 1)
        print("bottom nav ok")
    else:
        print("WARN footer nao encontrado")
else:
    print("bottom ja presente")

JS = r"""
  function setSideNav(key) {
    document.querySelectorAll('.sf-side-nav [data-nav], .sf-bottom-nav [data-nav]').forEach(function(el) {
      el.classList.toggle('active', el.getAttribute('data-nav') === key);
    });
  }
  document.addEventListener('DOMContentLoaded', function() {
    document.body.classList.add('shell-ready');
  });
  window.toggleDrawer = function() {};
"""

if "function setSideNav" not in html:
    if "function navigate(" in html:
        html = html.replace("function navigate(", JS + "\n  function navigate(", 1)
        print("JS ok")
    else:
        print("WARN navigate nao encontrado")
else:
    print("JS ja presente")

INDEX.write_text(html, encoding="utf-8")
print("OK", INDEX, "bytes", INDEX.stat().st_size)
