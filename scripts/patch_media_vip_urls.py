from pathlib import Path

p = Path('api/media-sources.js')
t = p.read_text()
needle = '''    sources = sources.slice().sort((a, b) => {'''
block = '''    const requireVipMedia = String(process.env.REQUIRE_VIP_MEDIA || '1').trim().toLowerCase();
    const vipMediaOn = requireVipMedia === '1' || requireVipMedia === 'true' || requireVipMedia === 'on';
    const freeLimit = Math.max(0, parseInt(String(process.env.FREE_MEDIA_HOSTS || '2'), 10) || 0);
    if (!access.isVip) {
      if (vipMediaOn) {
        sources = sources.map((s) => ({ ...s, source_url: '', vip_only: true }));
        console.log('[media-sources] strip urls free/mod tmdb=' + tmdbId + ' n=' + sources.length);
      } else if (freeLimit >= 0) {
        sources = sources.slice(0, freeLimit);
        console.log('[media-sources] free cap=' + freeLimit + ' tmdb=' + tmdbId);
      }
    }

    sources = sources.slice().sort((a, b) => {'''
if needle in t and 'REQUIRE_VIP_MEDIA' not in t.split('if (!access.isVip)')[-1][:800]:
    t = t.replace(needle, block, 1)
    p.write_text(t)
    print('media-sources urls gated')
else:
    if 'REQUIRE_VIP_MEDIA' in t:
        print('ja tinha REQUIRE_VIP_MEDIA')
    else:
        print('needle nao achado')

nm = Path('android/app/src/main/java/com/streamflixvip/app/network/NetworkModule.kt')
nt = nm.read_text()
if 'X-App-Version' not in nt:
    old = '''        if (!userId.isNullOrBlank()) {
            builder.header("X-User-Id", userId)
        }
        chain.proceed(builder.build())'''
    new = '''        if (!userId.isNullOrBlank()) {
            builder.header("X-User-Id", userId)
        }
        builder.header("X-App-Version", BuildConfig.VERSION_NAME)
        builder.header("X-App-Version-Code", BuildConfig.VERSION_CODE.toString())
        chain.proceed(builder.build())'''
    if old in nt:
        nt = nt.replace(old, new, 1)
        nm.write_text(nt)
        print('network headers ok')
    else:
        print('interceptor nao achado')
else:
    print('headers ja existem')
