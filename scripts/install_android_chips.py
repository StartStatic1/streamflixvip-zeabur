#!/usr/bin/env python3
"""Escreve ServerPickerUi.kt e SupabaseApi.kt com suporte a chips."""
import base64, gzip, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]

def load(prefix):
    parts = []
    for i in range(20):
        f = pathlib.Path(__file__).parent / f"{prefix}_p{i}.b64"
        if not f.exists():
            break
        parts.append(f.read_text().strip())
    if not parts:
        raise SystemExit(f"missing {prefix}_p*.b64")
    s = "".join(parts)
    s += "=" * ((4 - len(s) % 4) % 4)
    return gzip.decompress(base64.b64decode(s))

picker = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/ServerPickerUi.kt"
supabase = ROOT / "android/app/src/main/java/com/streamflixvip/app/network/SupabaseApi.kt"
picker.write_bytes(load("kt_picker"))
supabase.write_bytes(load("kt_supabase"))
print("ok", picker, picker.stat().st_size)
print("ok", supabase, supabase.stat().st_size)
assert b"chipColors" in picker.read_bytes()
assert b"SourceMeta" in supabase.read_bytes()
print("android chips files written")
