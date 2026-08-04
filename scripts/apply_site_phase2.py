#!/usr/bin/env python3
"""Fase 2: aperta detalhes — player, servidores e card de informacoes."""
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
/* ═══ FASE 2 — Detalhes / Assistir mais compacto ═══ */
.detail-body {
  display: grid !important;
  gap: 18px !important;
  padding: 16px 16px 32px !important;
  max-width: 1100px;
  margin: 0 auto;
}
@media (min-width: 900px) {
  .detail-body {
    grid-template-columns: 1fr 280px !important;
    align-items: start;
    padding: 20px 24px 48px !important;
    gap: 22px !important;
  }
  .detail-side { position: sticky; top: calc(var(--nav-h, 64px) + 16px); }
}
.detail-main { min-width: 0; }
.player-wrap {
  border-radius: 16px !important;
  overflow: hidden !important;
  border: 1px solid rgba(46,230,214,0.14) !important;
  background: #000 !important;
  box-shadow: 0 12px 40px rgba(0,0,0,0.45) !important;
  margin-bottom: 14px !important;
}
.player-media {
  aspect-ratio: 16/9 !important;
  background: #000 !important;
}
.server-label {
  font-size: 0.68rem !important;
  letter-spacing: 0.1em !important;
  color: #7a8b9a !important;
  margin: 4px 0 10px !important;
}
.server-grid {
  display: grid !important;
  grid-template-columns: 1fr 1fr !important;
  gap: 8px !important;
  margin-bottom: 18px !important;
}
@media (min-width: 600px) {
  .server-grid { grid-template-columns: 1fr 1fr 1fr !important; }
}
.server-btn {
  display: flex !important;
  align-items: center !important;
  gap: 10px !important;
  padding: 11px 12px !important;
  border-radius: 12px !important;
  border: 1px solid rgba(46,230,214,0.12) !important;
  background: rgba(14,22,32,0.9) !important;
  margin: 0 !important;
  min-height: 52px !important;
}
.server-btn.active {
  border-color: #2ee6d6 !important;
  background: rgba(46,230,214,0.12) !important;
  box-shadow: 0 0 0 1px rgba(46,230,214,0.25), 0 8px 24px rgba(46,230,214,0.12) !important;
}
.server-btn:hover {
  border-color: rgba(46,230,214,0.35) !important;
  transform: translateY(-1px);
}
.detail-info-card {
  border-radius: 14px !important;
  border: 1px solid rgba(46,230,214,0.12) !important;
  background: rgba(10,16,24,0.92) !important;
  padding: 14px 16px !important;
  margin: 0 !important;
}
.detail-info-row {
  padding: 9px 0 !important;
  border-bottom: 1px solid rgba(255,255,255,0.05) !important;
  font-size: 0.82rem !important;
}
.detail-info-row:last-child {
  border-bottom: 0 !important;
  padding-bottom: 0 !important;
}
.detail-desc, .detail-main > section, .detail-main > div {
  margin-bottom: 16px;
}
.detail-title-block h1 {
  font-family: Fraunces, serif !important;
  letter-spacing: -0.02em !important;
}
.vip-gate-box {
  border-radius: 14px !important;
  border: 1px solid rgba(46,230,214,0.2) !important;
  background: linear-gradient(160deg, rgba(14,165,233,0.12), rgba(10,16,24,0.95)) !important;
  padding: 18px 16px !important;
  margin-bottom: 14px !important;
}
.detail-banner {
  border-radius: 0 0 20px 20px !important;
  overflow: hidden;
}
@media (min-width: 1024px) {
  .detail-banner {
    margin: 8px 16px 0;
    border-radius: 18px !important;
    border: 1px solid rgba(46,230,214,0.1);
  }
}
.detail-main textarea,
.detail-main input[type="text"] {
  border-radius: 12px !important;
}
"""

if "FASE 2 — Detalhes" not in t:
    if "</style>" not in t:
        raise SystemExit("sem </style>")
    t = t.replace("</style>", CSS + "\n</style>", 1)
    print("phase2 css ok")
else:
    print("phase2 ja presente")

idx.write_text(t, encoding="utf-8")
print("OK", idx.stat().st_size)
