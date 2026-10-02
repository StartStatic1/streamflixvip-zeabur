#!/usr/bin/env python3
"""Nao mostra cadeado VIP enquanto a fonte free ainda carrega."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()
old = """            if (state.movieIsLocked(isVip) || (!isVip && state.movieSources.isEmpty() && state.vipConfig?.vip_lock == true)) {"""
new = """            val waitingSources = state.isLoadingMovieSources && state.movieSources.isEmpty()
            val freeTitle = state.vipConfig?.is_free == true
            if (!waitingSources && !freeTitle && (state.movieIsLocked(isVip) || (!isVip && state.movieSources.isEmpty() && state.vipConfig?.vip_lock == true))) {"""
if "waitingSources && !freeTitle" in t:
    print("ok lock ja")
elif old in t:
    t = t.replace(old, new, 1)
    p.write_text(t)
    print("ok lock wait")
else:
    print("aviso lock")
print("fim patch_free_lock_ui")
