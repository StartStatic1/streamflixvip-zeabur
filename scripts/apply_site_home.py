#!/usr/bin/env python3
"""Fase Home: hero/filas mais cinema + fix card servidor impar."""
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
/* ═══ FASE HOME + fix servidor impar ═══ */

/* Servidor impar nao estica */
.server-grid {
  display: grid !important;
  grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  gap: 8px !important;
}
@media (min-width: 600px) {
  .server-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
  }
}
.server-btn {
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
}
.server-grid > .server-btn:last-child:nth-child(odd) {
  max-width: calc(50% - 4px) !important;
}
@media (min-width: 600px) {
  .server-grid > .server-btn:last-child:nth-child(3n+1):nth-last-child(1) {
    max-width: calc(33.333% - 6px) !important;
  }
}

#page-home .hero,
#page-home #heroSection {
  border-radius: 0 0 22px 22px !important;
  overflow: hidden !important;
}
@media (min-width: 1024px) {
  #page-home .hero,
  #page-home #heroSection {
    margin: 10px 18px 0 !important;
    border-radius: 20px !important;
    border: 1px solid rgba(46,230,214,0.1);
    max-height: 72vh !important;
    min-height: 420px !important;
  }
}
.hero-badge {
  display: inline-flex !important;
  align-items: center;
  gap: 8px;
  padding: 6px 12px !important;
  border-radius: 999px !important;
  background: rgba(46,230,214,0.14) !important;
  border: 1px solid rgba(46,230,214,0.28) !important;
  color: #a5f3fc !important;
  font-size: 0.72rem !important;
  font-weight: 800 !important;
  letter-spacing: 0.06em !important;
  text-transform: uppercase !important;
}
.hero-title {
  font-family: Fraunces, Georgia, serif !important;
  letter-spacing: -0.03em !important;
  text-shadow: 0 4px 28px rgba(0,0,0,0.55) !important;
  max-width: 16ch !important;
}
.hero-btns .btn-primary {
  border-radius: 999px !important;
  padding: 12px 22px !important;
  font-weight: 800 !important;
  box-shadow: 0 10px 28px rgba(46,230,214,0.25) !important;
}
.hero-btns .btn-secondary,
.hero-btns .btn-trailer {
  border-radius: 999px !important;
  backdrop-filter: blur(10px);
}
.hero-dots { gap: 6px !important; }
.hero-dot {
  width: 7px !important;
  height: 7px !important;
  border-radius: 99px !important;
  opacity: 0.45;
}
.hero-dot.active {
  width: 22px !important;
  opacity: 1 !important;
  background: #2ee6d6 !important;
}

.cat-section {
  padding-top: 28px !important;
  margin-bottom: 28px !important;
}
.cat-section-title {
  font-family: Fraunces, Georgia, serif !important;
  font-size: 1.15rem !important;
  letter-spacing: -0.02em !important;
  margin-bottom: 14px !important;
}
.cat-grid > * {
  border-radius: 14px !important;
  border: 1px solid rgba(46,230,214,0.1) !important;
  overflow: hidden;
  transition: transform .2s ease, border-color .2s ease, box-shadow .2s ease;
}
.cat-grid > *:hover {
  transform: translateY(-3px);
  border-color: rgba(46,230,214,0.35) !important;
  box-shadow: 0 12px 28px rgba(0,0,0,0.35);
}

.row-section { margin-bottom: 28px !important; }
.row-title {
  font-family: Fraunces, Georgia, serif !important;
  font-size: 1.12rem !important;
  letter-spacing: -0.02em !important;
  font-weight: 700 !important;
}
.row-track .card,
.row-track .poster-card {
  border-radius: 14px !important;
  overflow: hidden !important;
  border: 1px solid rgba(255,255,255,0.06);
  transition: transform .22s ease, box-shadow .22s ease, border-color .22s ease !important;
}
.row-track .card:hover,
.row-track .poster-card:hover {
  transform: translateY(-6px) scale(1.02) !important;
  border-color: rgba(46,230,214,0.3) !important;
  box-shadow: 0 16px 36px rgba(0,0,0,0.45) !important;
}
.row-arrow {
  border-radius: 50% !important;
  border: 1px solid rgba(46,230,214,0.2) !important;
  background: rgba(8,12,18,0.85) !important;
  backdrop-filter: blur(8px);
}
.row-arrow:hover {
  border-color: #2ee6d6 !important;
  color: #2ee6d6 !important;
}

#continueSection .row-title { color: #e2e8f0 !important; }
.continue-card {
  border-radius: 14px !important;
  overflow: hidden !important;
  border: 1px solid rgba(46,230,214,0.1) !important;
}
.continue-progress-fill {
  background: linear-gradient(90deg, #0ea5e9, #2ee6d6) !important;
}

@media (min-width: 1024px) {
  #page-home .rows-wrap,
  #page-home .cat-section {
    padding-left: 8px;
    padding-right: 18px;
  }
}
"""

if "FASE HOME + fix servidor" not in t:
    if "</style>" not in t:
        raise SystemExit("sem </style>")
    t = t.replace("</style>", CSS + "\n</style>", 1)
    print("home css ok")
else:
    print("home css ja")

idx.write_text(t, encoding="utf-8")
print("OK", idx.stat().st_size)
