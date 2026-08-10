#!/usr/bin/env python3
from pathlib import Path

p = Path("api/media-sources.js")
t = p.read_text()

old = """  if (hard) {
    if (!loggedIn) {
      console.warn(`[media-sources] BLOCK no-auth tmdb=${tmdbId} type=${mediaType}`);
      res.status(401).json({
        error: 'Login necessário para assistir.',
        code: 'AUTH_REQUIRED',
        sources: [],
      });
      return;
    }
    if (needsVip && !access.isVip) {
      console.warn(
        `[media-sources] BLOCK vip_lock tmdb=${tmdbId} user=${access.userId || '-'}`,
      );
      res.status(403).json({
        error: 'VIP necessário para este título.',
        code: 'VIP_REQUIRED',
        sources: [],
      });
      return;
    }
    console.log(
      `[media-sources] OK hard tmdb=${tmdbId} type=${mediaType} source=${access.source} vip=${access.isVip}`,
    );
  } else {
    console.log(
      `[media-sources] soft-pass tmdb=${tmdbId} type=${mediaType} source=${access.source} (REQUIRE_AUTH_MEDIA off)`,
    );
  }"""

new = """  // Sempre respeita vip_titles do painel (lock total ou grátis até EP N).
  // Soft (REQUIRE_AUTH_MEDIA off) só dispensa login em título NÃO-VIP.
  if (needsVip && !access.isVip) {
    console.warn(
      `[media-sources] BLOCK vip_lock tmdb=${tmdbId} ep=${episode} user=${access.userId || '-'}`,
    );
    res.status(403).json({
      error: 'VIP necessário para este título/episódio.',
      code: 'VIP_REQUIRED',
      sources: [],
      vipConfig: vipConfig
        ? {
            vip_lock: !!vipConfig.vip_lock,
            vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null,
          }
        : null,
      requiresVip: true,
      isVip: false,
    });
    return;
  }

  if (hard) {
    if (!loggedIn) {
      console.warn(`[media-sources] BLOCK no-auth tmdb=${tmdbId} type=${mediaType}`);
      res.status(401).json({
        error: 'Login necessário para assistir.',
        code: 'AUTH_REQUIRED',
        sources: [],
      });
      return;
    }
    console.log(
      `[media-sources] OK hard tmdb=${tmdbId} type=${mediaType} source=${access.source} vip=${access.isVip}`,
    );
  } else {
    console.log(
      `[media-sources] soft-pass tmdb=${tmdbId} type=${mediaType} source=${access.source} (REQUIRE_AUTH_MEDIA off)`,
    );
  }"""

if old not in t:
    if "BLOCK vip_lock tmdb=${tmdbId} ep=${episode}" in t:
        print("already patched")
    else:
        raise SystemExit("block not found")
else:
    t = t.replace(old, new, 1)
    p.write_text(t)
    print("patched media-sources.js")

print("DONE")
