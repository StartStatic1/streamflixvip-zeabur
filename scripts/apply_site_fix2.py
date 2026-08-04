#!/usr/bin/env python3
"""Fase 1.5: corrige nav bugada + esconde restos do layout antigo."""
from pathlib import Path
from datetime import datetime
import shutil

idx = Path("/root/streamflix/Public/index.html")
if not idx.exists():
    idx = Path("/root/streamflix/public/index.html")
if not idx.exists():
    raise SystemExit("index.html nao encontrado")

bak = idx.with_suffix(".html.bak-" + datetime.now().strftime("%Y%m%d%H%M%S"))
shutil.copy2(idx, bak)
print("backup", bak)

t = idx.read_text(encoding="utf-8", errors="replace")

FIX = r"""
/* FIX NAV 2026 — sobrescreve conflitos do layout antigo */
@media (max-width: 1023px) {
  .sf-bottom-nav {
    display: flex !important;
    position: fixed !important;
    left: 0 !important;
    right: 0 !important;
    bottom: 0 !important;
    top: auto !important;
    height: 62px !important;
    z-index: 9999 !important;
    background: rgba(6,10,16,0.96) !important;
    backdrop-filter: blur(18px);
    border-top: 1px solid rgba(46,230,214,0.12) !important;
    margin: 0 !important;
  }
  nav#mainNav .brand-animated,
  nav#mainNav .b-stream,
  nav#mainNav .b-flix,
  nav#mainNav .b-vip {
    display: none !important;
  }
  nav#mainNav .logo {
    display: flex !important;
  }
  nav#mainNav .nav-tab {
    display: none !important;
  }
  .hamburger-btn, #hamburgerBtn,
  .drawer, .drawer-overlay, #sideDrawer, #drawerOverlay {
    display: none !important;
    visibility: hidden !important;
    pointer-events: none !important;
  }
  body {
    padding-bottom: 70px !important;
  }
  footer .footer-links {
    display: none !important;
  }
}
@media (min-width: 1024px) {
  body.shell-ready, body {
    padding-left: 232px !important;
  }
  .sf-sidebar {
    display: flex !important;
    position: fixed !important;
    left: 0 !important;
    top: 0 !important;
    bottom: 0 !important;
    width: 232px !important;
    z-index: 200 !important;
  }
  .sf-bottom-nav {
    display: none !important;
  }
  nav#mainNav {
    left: 232px !important;
    width: calc(100% - 232px) !important;
  }
  nav#mainNav .logo,
  nav#mainNav .brand-animated {
    display: none !important;
  }
  .hamburger-btn, #hamburgerBtn,
  .drawer, .drawer-overlay, #sideDrawer, #drawerOverlay {
    display: none !important;
  }
}
"""

if "/* FIX NAV 2026" not in t:
    if "</style>" not in t:
        raise SystemExit("sem </style>")
    t = t.replace("</style>", FIX + "\n</style>", 1)
    print("css fix ok")
else:
    print("css fix ja presente")

if 'class="shell-ready"' not in t and "<body>" in t:
    t = t.replace("<body>", '<body class="shell-ready">', 1)
    print("body class ok")
elif "<body " in t and "shell-ready" not in t.split("<body")[1][:80]:
    t = t.replace("<body ", '<body class="shell-ready" ', 1)
    print("body class attr ok")
else:
    print("body class skip")

idx.write_text(t, encoding="utf-8")
print("OK", idx.stat().st_size)
print("bottom-nav", t.count("sf-bottom-nav"), "sidebar", t.count("sf-sidebar"))
