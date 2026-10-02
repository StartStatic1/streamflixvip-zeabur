#!/usr/bin/env python3
"""VIP: requiresVip na resposta NAO significa sem fonte.
So code VIP_REQUIRED / AUTH_REQUIRED zera a lista.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailViewModel.kt"
t = p.read_text(encoding="utf-8")

old = '''                            val res = NetworkModule.mediaSourcesApi.getMovieSources(tmdbId)
                            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED" || res.requiresVip) {
                                requiredByApi = true
                                fromApiConfig = res.vipConfig
                                sources = emptyList()
                            } else if (res.sources.isNotEmpty()) {
                                sources = res.sources
                                fromApiConfig = res.vipConfig
                            } else {
                                // API vazia: tenta repo (respeita VIP no fallback)
                                sources = try {
                                    repository.getSourcesForMovie(tmdbId)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }'''

new = '''                            val res = NetworkModule.mediaSourcesApi.getMovieSources(tmdbId)
                            // requiresVip=true = titulo exige VIP, NAO acesso negado.
                            // VIP recebe fontes + requiresVip; so code VIP_REQUIRED/AUTH limpa lista.
                            if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED") {
                                requiredByApi = true
                                fromApiConfig = res.vipConfig
                                sources = emptyList()
                            } else if (res.sources.isNotEmpty()) {
                                sources = res.sources
                                fromApiConfig = res.vipConfig
                                if (res.requiresVip == true) requiredByApi = true
                            } else {
                                sources = try {
                                    repository.getSourcesForMovie(tmdbId)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                fromApiConfig = res.vipConfig
                                if (res.requiresVip == true) requiredByApi = true
                            }'''

if "// requiresVip=true = titulo exige VIP" in t or "NAO acesso negado" in t:
    print("already fixed")
elif old in t:
    t = t.replace(old, new, 1)
    p.write_text(t, encoding="utf-8")
    print("ok fixed VIP sources")
else:
    # fallback: remove only the requiresVip part from the if
    bad = 'if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED" || res.requiresVip)'
    good = 'if (res.code == "VIP_REQUIRED" || res.code == "AUTH_REQUIRED")'
    if bad in t:
        t = t.replace(bad, good)
        p.write_text(t, encoding="utf-8")
        print("ok fixed (simple replace)")
    else:
        print("block not found")
        raise SystemExit(1)
"""
