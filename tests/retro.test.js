const test = require('node:test');
const assert = require('node:assert');
const { mergeWatch, cleanTitle } = require('../lib/retro');

test('retrospectiva: junta histórico + progresso sem contar duas vezes e respeita o ano', () => {
  const y0 = Date.UTC(2026, 0, 1, 3), y1 = Date.UTC(2027, 0, 1, 3);
  const history = [
    { tmdb_id: 1, media_type: 'movie', season: 0, episode: 0, title: 'Filme', max_position_seconds: 5000, duration_seconds: 4000, first_seen_at: '2026-03-01T00:00:00Z', last_seen_at: '2026-03-01T00:00:00Z' },
    { tmdb_id: 2, media_type: 'tv', season: 1, episode: 1, title: 'Série', max_position_seconds: 1200, duration_seconds: 2400, first_seen_at: '2025-12-30T00:00:00Z', last_seen_at: '2025-12-31T00:00:00Z' },
  ];
  const progress = [
    { tmdb_id: 1, media_type: 'movie', season: 0, episode: 0, title: 'Filme', position_seconds: 3000, duration_seconds: 4000, updated_at: '2026-03-02T00:00:00Z' },
    { tmdb_id: 2, media_type: 'tv', season: 1, episode: 2, title: 'Série', position_seconds: 600, duration_seconds: 2400, updated_at: '2026-05-01T00:00:00Z' },
  ];
  const out = mergeWatch(history, progress, y0, y1);
  assert.equal(out.length, 2);
  const movie = out.find((x) => x.tmdbId === 1);
  assert.equal(movie.seconds, 4000, 'limitado pela duração e sem somar duas vezes');
  assert.ok(out.find((x) => x.tmdbId === 2 && x.episode === 2));
  assert.ok(!out.find((x) => x.tmdbId === 2 && x.episode === 1), 'fora do ano');
});

test('retrospectiva: nome da série sem o episódio grudado', () => {
  assert.equal(cleanTitle('Reacher — S4E3 — S4E5 — S4E6'), 'Reacher');
  assert.equal(cleanTitle('Tô de Graça — S8E28'), 'Tô de Graça');
  assert.equal(cleanTitle("X-Men '97"), "X-Men '97");
  assert.equal(cleanTitle('86 EIGHTY-SIX'), '86 EIGHTY-SIX');
});
