#!/usr/bin/env python3
"""Fix card servidor impar no mobile + player embed mais limpo."""
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
/* ═══ POLISH — servidor impar + player embed ═══ */

#serverGrid.server-grid,
.server-grid {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 8px !important;
  margin-bottom: 16px !important;
}
#serverGrid .server-btn,
.server-grid .server-btn {
  flex: 0 0 calc(50% - 4px) !important;
  width: calc(50% - 4px) !important;
  max-width: calc(50% - 4px) !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}
@media (min-width: 640px) {
  #serverGrid .server-btn,
  .server-grid .server-btn {
    flex: 0 0 calc(33.333% - 6px) !important;
    width: calc(33.333% - 6px) !important;
    max-width: calc(33.333% - 6px) !important;
  }
}

.player-wrap {
  border-radius: 14px !important;
  overflow: hidden !important;
  background: #000 !important;
  border: 1px solid rgba(46,230,214,0.12) !important;
}
.player-media {
  position: relative !important;
  width: 100% !important;
  aspect-ratio: 16 / 9 !important;
  background: #000 !important;
  overflow: hidden !important;
}
.player-media iframe,
.player-media video {
  position: absolute !important;
  inset: 0 !important;
  width: 100% !important;
  height: 100% !important;
  border: 0 !important;
  object-fit: contain !important;
  background: #000 !important;
}

@media (max-width: 640px) {
  .player-media video::-webkit-media-controls-panel {
    background: linear-gradient(transparent, rgba(0,0,0,0.65));
  }
  .custom-controls-bar {
    padding: 8px 10px !important;
  }
}
"""

if "POLISH — servidor impar" not in t:
    if "</style>" not in t:
        raise SystemExit("sem </style>")
    t = t.replace("</style>", CSS + "\n</style>", 1)
    print("polish css ok")
else:
    print("polish ja")

idx.write_text(t, encoding="utf-8")
print("OK", idx.stat().st_size)
