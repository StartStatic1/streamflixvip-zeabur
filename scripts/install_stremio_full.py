#!/usr/bin/env python3
"""Instala lib/stremio-addons.js completo (meta chips + ordem painel x100 + Server N)."""
from pathlib import Path
import base64

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "lib" / "stremio-addons.js"

def main():
    chunks = []
    for i in range(4):
        p = ROOT / "scripts" / f"stremio_full_p{i}.b64"
        if not p.exists():
            raise SystemExit(f"missing {p}")
        chunks.append(p.read_text().strip())
    data = base64.b64decode("".join(chunks))
    text = data.decode("utf-8")
    if "buildStreamMeta" not in text or "prio = prio * 100" not in text:
        raise SystemExit("decoded file invalid")
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_text(text, encoding="utf-8")
    print("ok", DEST, len(text))
    print("meta ok + panel order x100")

if __name__ == "__main__":
    main()
