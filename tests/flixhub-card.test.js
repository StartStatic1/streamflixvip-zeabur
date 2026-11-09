// node --test tests/flixhub-card.test.js
const test = require('node:test');
const assert = require('node:assert');
const {
  streamToSourceDetailed,
  pickStreamDisplayName,
  hubCardDescription,
  hubMetaDescription,
} = require('../lib/stremio-addons');

// Simula o parser do ServerInfoCard no APK atual (sem hifen no prefixo).
function parseCardLegacy(label, desc) {
  const d = String(desc || '').replace(/\n/g, ' ').replace(/\r/g, ' ');
  const l = String(label || '');
  const isFlix = /flixhub/i.test(l + ' ' + d);
  const isStream = /streamhub/i.test(l + ' ' + d);
  const isHub = isFlix || isStream;
  const m = isHub ? /([A-Za-z0-9]+\s+)?Server\s*[0-9]+/i.exec(d + ' ' + l) : null;
  let movieName = null;
  if (isHub && m) {
    const cutAt = Math.min(m.index, d.length);
    movieName = d.slice(0, cutAt).trim().replace(/^[^A-Za-z0-9\u00C0-\u024F]+/, '') || null;
  }
  return { head: isFlix ? 'FlixHub' : isStream ? 'StreamHub' : null, serverName: m ? m[0].trim() : null, movieName };
}

test('hubCardDescription remove hifen pra parser legado do app', () => {
  assert.strictEqual(hubCardDescription('tutor-ins Server 4'), 'tutorins Server 4');
  assert.strictEqual(hubCardDescription('CineTela Server 2'), 'CineTela Server 2');
});

test('hubMetaDescription: titulo + servidor sanitizado', () => {
  const stream = {
    name: 'FlixHub',
    title: '🎬 Batman O Cavaleiro das Trevas [L]\n⚡ tutor-ins Server 4\n🎯 FULL HD 1080p',
  };
  assert.strictEqual(
    hubMetaDescription(stream, 'tutor-ins Server 4'),
    'Batman O Cavaleiro das Trevas [L] tutorins Server 4',
  );
});

test('pickStreamDisplayName pega tutor-ins Server 4 inteiro', () => {
  const stream = {
    name: 'FlixHub',
    title: '🎬 Batman O Cavaleiro das Trevas [L]\n⚡ tutor-ins Server 4\n🎯 FULL HD 1080p',
  };
  assert.strictEqual(pickStreamDisplayName(stream), 'tutor-ins Server 4');
});

test('card FlixHub: titulo separado + servidor + [L]=Legendado', () => {
  const { source } = streamToSourceDetailed({
    name: 'FlixHub',
    title: '🎬 Batman O Cavaleiro das Trevas [L]\n⚡ tutor-ins Server 4\n🎯 FULL HD 1080p',
    url: 'http://tutor-ins.online/movie/u/p/1.mp4',
  }, 'FlixHub', 0);
  assert.ok(source);
  assert.match(source.source_label, /tutor-ins Server 4/i);
  assert.strictEqual(source.meta.quality, '1080p');
  assert.strictEqual(source.meta.audio, 'Legendado');
  assert.strictEqual(source.meta.description, 'Batman O Cavaleiro das Trevas [L] tutorins Server 4');
  const card = parseCardLegacy(source.source_label, source.meta.description);
  assert.strictEqual(card.head, 'FlixHub');
  assert.strictEqual(card.movieName, 'Batman O Cavaleiro das Trevas [L]');
  assert.strictEqual(card.serverName, 'tutorins Server 4');
});

test('card FlixHub: [D]=Dublado e CineTela/4Lives limpos', () => {
  const { source: dub } = streamToSourceDetailed({
    name: 'FlixHub',
    title: '🎬 Inception [D]\n⚡ CineTela Server 2\n🎯 FULL HD 1080p',
    url: 'http://example.com/movie/u/p/1.mp4',
  }, 'FlixHub', 0);
  assert.strictEqual(dub.meta.audio, 'Dublado');
  const cardDub = parseCardLegacy(dub.source_label, dub.meta.description);
  assert.strictEqual(cardDub.movieName, 'Inception [D]');
  assert.strictEqual(cardDub.serverName, 'CineTela Server 2');

  for (const name of ['4Lives Server 1', 'nosworld Server 5']) {
    const { source } = streamToSourceDetailed({
      name: 'FlixHub',
      title: '🎬 Interstellar (2014)\n⚡ ' + name + '\n🎯 FULL HD 1080p',
      url: 'http://example.com/movie/u/p/1.mp4',
    }, 'FlixHub', 0);
    const card = parseCardLegacy(source.source_label, source.meta.description);
    assert.strictEqual(card.serverName, name);
    assert.strictEqual(card.movieName, 'Interstellar (2014)');
  }
});
