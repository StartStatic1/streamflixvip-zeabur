#!/usr/bin/env python3
"""Spinner no lugar do erro e pre-carrega Fontes na abertura."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "android/app/src/main/java/com/streamflixvip/app/ui/player/PlayerScreen.kt"
t = p.read_text()
n = 0

a = "                    if (isRecovering && retryAttempt in 1..2) {"
b = "                    if (isRecovering) {"
if a in t:
    t = t.replace(a, b, 1)
    n += 1
    print("ok spinner")
elif b in t:
    print("ok spinner ja")
else:
    print("aviso spinner")

pre = """        } catch (_: Exception) {
            alternateSources = emptyList()
        }
    }

    LaunchedEffect(errorMessage) {"""
pre2 = """        } catch (_: Exception) {
            alternateSources = emptyList()
        }
    }

    LaunchedEffect(url, tmdbId) {
        try { loadAlternateSources() } catch (_: Exception) {}
    }

    LaunchedEffect(errorMessage) {"""

if "LaunchedEffect(url, tmdbId)" in t:
    print("ok preload ja")
elif pre in t:
    t = t.replace(pre, pre2, 1)
    n += 1
    print("ok preload")
else:
    print("aviso preload")

p.write_text(t)
print("fim apply_hyper_fast_switch", n)
