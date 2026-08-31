from pathlib import Path

p = Path('api/media-sources.js')
t = p.read_text()
old = '''  (Array.isArray(rows) ? rows : []).forEach((row) => {
    if (row.episode != null) set.add(Number(row.episode));
  });
  return [...set].filter((n) => Number.isFinite(n)).sort((a, b) => a - b);
}'''
new = '''  (Array.isArray(rows) ? rows : []).forEach((row) => {
    if (row.episode != null) set.add(Number(row.episode));
  });
  try {
    const addons = await loadActiveAddons(serviceKey);
    if (addons && addons.length) {
      const apiKey = process.env.TMDB_API_KEY;
      if (apiKey) {
        const u = 'https://api.themoviedb.org/3/tv/' + tmdbId + '/season/' + season +
          '?api_key=' + encodeURIComponent(apiKey);
        const tr = await fetch(u);
        if (tr.ok) {
          const data = await tr.json();
          const today = new Date().toISOString().slice(0, 10);
          (data.episodes || []).forEach((e) => {
            const n = Number(e.episode_number);
            const air = e.air_date || '';
            if (Number.isFinite(n) && (!air || air <= today)) set.add(n);
          });
        }
      } else {
        for (let i = 1; i <= 24; i++) set.add(i);
      }
    }
  } catch (e) {
    console.warn('[media-sources] addon episodes', e && e.message);
  }
  return [...set].filter((n) => Number.isFinite(n)).sort((a, b) => a - b);
}'''
if old not in t:
    raise SystemExit('bloco loadEpisodes nao encontrado')
p.write_text(t.replace(old, new, 1))
print('api ok')

d = Path('android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt')
if d.exists():
    kt = d.read_text()
    bad = '!hasSource -> {}'
    good = '!hasSource -> onSelectEpisode(state.expandedSeason ?: 1, ep.episode_number, title, posterPath)'
    if bad in kt:
        d.write_text(kt.replace(bad, good, 1))
        print('click ok')
    else:
        print('click skip')
