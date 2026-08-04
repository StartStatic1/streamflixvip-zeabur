#!/usr/bin/env python3
from pathlib import Path
import shutil
from datetime import datetime

idx = Path("/root/streamflix/Public/index.html")
if not idx.exists():
    idx = Path("/root/streamflix/public/index.html")
if not idx.exists():
    raise SystemExit("index.html nao encontrado")

bak = idx.with_suffix(".html.bak-" + datetime.now().strftime("%Y%m%d%H%M%S"))
shutil.copy2(idx, bak)
print("backup", bak)

t = idx.read_text(encoding="utf-8", errors="replace")

CSS = """
/* REDESIGN 2026 shell */
@media(min-width:1024px){
  body.shell-ready{padding-left:232px}
  .sf-sidebar{display:flex!important;position:fixed;left:0;top:0;bottom:0;width:232px;z-index:200;flex-direction:column;background:#0a1018;border-right:1px solid rgba(46,230,214,.08);padding:18px 14px}
  .hamburger-btn,#hamburgerBtn,.drawer,.drawer-overlay{display:none!important}
  .sf-bottom-nav{display:none!important}
  nav#mainNav{left:232px!important;width:calc(100% - 232px)!important}
}
@media(max-width:1023px){
  .sf-sidebar{display:none!important}
  .hamburger-btn,#hamburgerBtn,.drawer,.drawer-overlay{display:none!important}
  body{padding-bottom:62px}
  .sf-bottom-nav{display:flex!important}
}
.sf-sidebar .sf-side-brand{display:flex;align-items:center;gap:10px;padding:6px 10px 20px;cursor:pointer}
.sf-sidebar .logo-icon{width:36px;height:36px;border-radius:10px;background:linear-gradient(135deg,#0ea5e9,#2ee6d6);display:grid;place-items:center;font-weight:800;color:#041018}
.sf-side-name{font-family:Fraunces,serif;font-weight:700;font-size:1.05rem;color:#f0f7fa}
.sf-side-name em{font-style:normal;color:#2ee6d6}
.sf-side-nav{display:flex;flex-direction:column;gap:4px;flex:1}
.sf-side-nav button{display:flex;align-items:center;gap:12px;padding:11px 12px;border-radius:12px;color:#7a8b9a;font-weight:600;background:0;border:0;width:100%;cursor:pointer;font-family:inherit;text-align:left}
.sf-side-nav button.active{background:rgba(46,230,214,.14);color:#fff;box-shadow:inset 3px 0 0 #2ee6d6}
.sf-side-nav button:hover{background:rgba(46,230,214,.08);color:#fff}
.sf-bottom-nav{position:fixed;left:0;right:0;bottom:0;height:62px;background:rgba(6,10,16,.94);border-top:1px solid rgba(46,230,214,.08);z-index:180;justify-content:space-around;align-items:center}
.sf-bottom-nav button{flex:1;height:100%;border:0;background:0;color:#7a8b9a;font-size:.65rem;font-weight:600;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;cursor:pointer;font-family:inherit}
.sf-bottom-nav button.active{color:#2ee6d6}
#page-home .hero{min-height:min(72vh,640px)}
@media(min-width:1024px){#page-home .hero{margin:12px 20px;border-radius:22px;border:1px solid rgba(46,230,214,.08)}}
#page-home .hero-title{font-family:Fraunces,serif;font-size:clamp(1.8rem,4.5vw,3.2rem);font-weight:700}
#page-home .btn-primary{background:linear-gradient(135deg,#0ea5e9,#2ee6d6);color:#041018;border:0;font-weight:800;border-radius:999px;padding:12px 22px}
.card:hover{transform:translateY(-4px) scale(1.02);box-shadow:0 16px 40px rgba(0,0,0,.45)}
footer{border-top:1px solid rgba(46,230,214,.08);background:#060a10}
"""

if "REDESIGN 2026 shell" not in t:
    t = t.replace("</style>", CSS + "\n</style>", 1)
    print("css ok")
else:
    print("css ja presente")

sb = "\n".join([
    '<aside class="sf-sidebar" id="sfSidebar">',
    '<div class="sf-side-brand" onclick="navigate(\'home\')"><div class="logo-icon">S</div><div class="sf-side-name">Stream<em>Flix</em>VIP</div></div>',
    '<nav class="sf-side-nav">',
    '<button type="button" class="active" data-nav="home" onclick="navigate(\'home\');setSideNav(\'home\')">Inicio</button>',
    '<button type="button" data-nav="movies" onclick="navigate(\'all\',\'movie\');setSideNav(\'movies\')">Filmes</button>',
    '<button type="button" data-nav="series" onclick="navigate(\'all\',\'tv\');setSideNav(\'series\')">Series</button>',
    '<button type="button" data-nav="anime" onclick="navigate(\'all\',\'anime\');setSideNav(\'anime\')">Animes</button>',
    '<button type="button" data-nav="watchlist" onclick="navigate(\'watchlist\');setSideNav(\'watchlist\')">Minha Lista</button>',
    '<button type="button" onclick="typeof openVipModal===\'function\'&&openVipModal()">Ativar VIP</button>',
    '</nav></aside>',
])

if 'id="sfSidebar"' not in t and '<nav id="mainNav">' in t:
    t = t.replace('<nav id="mainNav">', sb + '\n<nav id="mainNav" class="sf-topbar">', 1)
    print("sidebar ok")
else:
    print("sidebar skip")

bn = "\n".join([
    '<nav class="sf-bottom-nav" id="sfBottomNav">',
    '<button type="button" class="active" data-nav="home" onclick="navigate(\'home\');setSideNav(\'home\')">Inicio</button>',
    '<button type="button" data-nav="movies" onclick="navigate(\'all\',\'movie\');setSideNav(\'movies\')">Filmes</button>',
    '<button type="button" data-nav="series" onclick="navigate(\'all\',\'tv\');setSideNav(\'series\')">Series</button>',
    '<button type="button" data-nav="watchlist" onclick="navigate(\'watchlist\');setSideNav(\'watchlist\')">Lista</button>',
    '<button type="button" onclick="typeof openAuthModal===\'function\'&&openAuthModal()">Eu</button>',
    '</nav>',
])

if 'id="sfBottomNav"' not in t and '<footer>' in t:
    t = t.replace('<footer>', bn + '\n<footer>', 1)
    print("bottom ok")
else:
    print("bottom skip")

js = (
    "function setSideNav(key){"
    "document.querySelectorAll('.sf-side-nav [data-nav],.sf-bottom-nav [data-nav]')"
    ".forEach(function(el){el.classList.toggle('active',el.getAttribute('data-nav')===key);});}"
    "document.addEventListener('DOMContentLoaded',function(){document.body.classList.add('shell-ready');});"
    "window.toggleDrawer=function(){};"
)

if "function setSideNav" not in t and "function navigate(" in t:
    t = t.replace("function navigate(", js + "\nfunction navigate(", 1)
    print("js ok")
else:
    print("js skip")

idx.write_text(t, encoding="utf-8")
print("OK", idx.stat().st_size)
