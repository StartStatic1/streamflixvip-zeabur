#!/usr/bin/env python3
import runpy
from pathlib import Path
here = Path(__file__).resolve().parent
for name in ("apply_wave1_core_a.py", "apply_wave1_core_b.py", "apply_wave1_panels.py"):
    print(">>", name)
    runpy.run_path(str(here / name), run_name="__main__")
print("fim apply_wave1_all")
