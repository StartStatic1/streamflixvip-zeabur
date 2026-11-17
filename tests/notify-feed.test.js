const test = require('node:test');
const assert = require('node:assert');
const feed = require('../lib/notify-feed');

const NOW = Date.UTC(2026, 10, 17, 12);
const day = (d) => new Date(NOW - d * 86400000).toISOString().slice(0, 10);

test('notify-feed: ids válidos, sem repetição e com limite', () => {
  assert.deepEqual(feed.parseIds('1,2,2,abc,-3,0, 4'), [1, 2, 4]);
  assert.equal(feed.parseIds(Array.from({ length: 100 }, (_, i) => i + 1).join(',')).length, 60);
});

test('notify-feed: janela de lançamento', () => {
  assert.ok(feed.isRecent(day(0), NOW));
  assert.ok(feed.isRecent(day(3), NOW));
  assert.ok(!feed.isRecent(day(10), NOW));
  assert.ok(!feed.isRecent(null, NOW));
});

test('notify-feed: episódio recente entra, antigo não; avisos filtrados por since', async () => {
  feed._reset();
  process.env.TMDB_API_KEY = 'x';
  process.env.SUPABASE_SERVICE_ROLE_KEY = 'y';
  const fetcher = async (url) => {
    if (url.includes('/tv/10')) return { name: 'Nova', last_episode_to_air: { season_number: 2, episode_number: 5, name: 'Ep', air_date: day(1) } };
    if (url.includes('/tv/20')) return { name: 'Velha', last_episode_to_air: { season_number: 1, episode_number: 8, air_date: day(30) } };
    if (url.includes('app_announcements')) {
      return [
        { id: 'b', title: 'Promo', body: 'x', created_at: new Date(NOW - 3600e3).toISOString() },
        { id: 'a', title: 'Velho', body: 'y', created_at: new Date(NOW - 5 * 86400e3).toISOString() },
      ];
    }
    return null;
  };
  const out = await feed.build({ tv: '10,20', since: String(NOW - 2 * 86400e3) }, { fetcher, now: NOW });
  assert.deepEqual(out.episodes.map((e) => e.tmdbId), [10]);
  assert.deepEqual(out.broadcasts.map((b) => b.id), ['b']);
  const first = await feed.build({ since: '0' }, { fetcher, now: NOW });
  assert.deepEqual(first.broadcasts.map((b) => b.id), ['b'], 'primeira vez: só o mais recente das últimas 48 h');
});
