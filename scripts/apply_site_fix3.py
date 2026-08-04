#!/usr/bin/env python3
"""Remove hamburguer de vez + Limpar assistidos + acesso facil a Lista."""
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

CSS = r"""
/* FIX3 — sem hamburguer + acoes lista/assistidos */
.hamburger-btn,
#hamburgerBtn,
.drawer,
.drawer-overlay,
#sideDrawer,
#drawerOverlay {
  display: none !important;
  visibility: hidden !important;
  pointer-events: none !important;
  width: 0 !important;
  height: 0 !important;
  overflow: hidden !important;
  opacity: 0 !important;
}
.sf-clear-btn {
  border: 1px solid rgba(244,63,94,0.35);
  background: rgba(244,63,94,0.1);
  color: #fb7185;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 0.72rem;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
}
.sf-clear-btn:hover {
  background: rgba(244,63,94,0.22);
  color: #fff;
}
.row-header {
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
#page-watchlist .all-page-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
"""

if "FIX3 — sem hamburguer" not in t:
    t = t.replace("</style>", CSS + "\n</style>", 1)
    print("css ok")
else:
    print("css ja")

t = t.replace(
    'onclick="toggleDrawer()"',
    'onclick="return false"',
)
print("toggleDrawer clicks neutered")

JS_CLEAR = r"""
  function clearAllContinue() {
    try {
      if (!getContinueList().length) { showToast('Nada para limpar'); return; }
      if (!confirm('Limpar todos os titulos de Continuar Assistindo?')) return;
      localStorage.setItem('sfv_continue', '[]');
      if (typeof _currentUser !== 'undefined' && _currentUser && typeof syncToCloud === 'function') {
        try { syncToCloud(); } catch(e) {}
      }
      if (typeof renderContinueRow === 'function') renderContinueRow();
      showToast('Historico de assistidos limpo');
    } catch(e) { console.error(e); }
  }
  function clearAllWatchlist() {
    try {
      const list = typeof getWatchlist === 'function' ? getWatchlist() : [];
      if (!list.length) { showToast('Lista vazia'); return; }
      if (!confirm('Remover todos os titulos da Minha Lista?')) return;
      if (typeof saveWatchlist === 'function') saveWatchlist([]);
      else localStorage.setItem('sfv_watchlist', '[]');
      if (typeof renderWatchlist === 'function') renderWatchlist();
      showToast('Minha Lista limpa');
    } catch(e) { console.error(e); }
  }
"""

if "function clearAllContinue" not in t:
    if "function removeContinue(" in t:
        t = t.replace(
            "function removeContinue(",
            JS_CLEAR + "\n  function removeContinue(",
            1,
        )
        print("js clear ok")
    else:
        print("WARN removeContinue not found")
else:
    print("js clear ja")

OLD_CONT = """        <div class=\"row-header\">
          <div class=\"row-title\">Continuar Assistindo</div>
        </div>"""
NEW_CONT = """        <div class=\"row-header\">
          <div class=\"row-title\">Continuar Assistindo</div>
          <button type=\"button\" class=\"sf-clear-btn\" onclick=\"clearAllContinue()\">Limpar assistidos</button>
        </div>"""

if "Limpar assistidos" not in t and OLD_CONT in t:
    t = t.replace(OLD_CONT, NEW_CONT, 1)
    print("continue header ok")
elif "Limpar assistidos" in t:
    print("continue header ja")
else:
    if 'row-title">Continuar Assistindo</div>' in t and "Limpar assistidos" not in t:
        t = t.replace(
            'row-title">Continuar Assistindo</div>',
            'row-title">Continuar Assistindo</div>\n          <button type="button" class="sf-clear-btn" onclick="clearAllContinue()">Limpar assistidos</button>',
            1,
        )
        print("continue header soft ok")
    else:
        print("WARN continue header pattern")

if "clearAllWatchlist()" not in t and "<h2>Minha Lista</h2>" in t:
    t = t.replace(
        "<h2>Minha Lista</h2>",
        '<h2>Minha Lista</h2>\n    <button type="button" class="sf-clear-btn" onclick="clearAllWatchlist()" style="float:right;margin-top:4px">Limpar lista</button>',
        1,
    )
    print("watchlist header ok")
else:
    print("watchlist header skip")

if "window.toggleDrawer=function(){}" not in t:
    if "function navigate(" in t:
        t = t.replace(
            "function navigate(",
            "window.toggleDrawer=function(){};window.closeDrawer=function(){};\n  function navigate(",
            1,
        )
        print("drawer noop ok")
else:
    print("drawer noop ja")

idx.write_text(t, encoding="utf-8")
print("OK", idx.stat().st_size)
