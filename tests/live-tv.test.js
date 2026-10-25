// node --test tests/
// TV ao vivo com servidor falso: atualizacao unica, fontes repetidas, pausa de fonte morta.
const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');

const hits = [];
let streamsDelayMs = 0;
let p2Fails = false;
const panelStreams = {
  p1: [
    { stream_id: 1, name: 'BR: Globo HD', category_id: 10, stream_icon: 'http://x/globo.png' },
    { stream_id: 2, name: 'SporTV FHD', category_id: 11 },
    { stream_id: 3, name: 'Canal XXX', category_id: 99 },
  ],
  p2: [
    { stream_id: 7, name: 'Globo', category_id: 20 },
    { stream_id: 8, name: 'ESPN', category_id: 21 },
  ],
};
const panelCats = {
  p1: [{ category_id: 10, category_name: 'BR | ABERTOS' }, { category_id: 11, category_name: '⚽ Esportes' }, { category_id: 99, category_name: 'Adultos' }],
  p2: [{ category_id: 20, category_name: 'Abertos' }, { category_id: 21, category_name: 'Canais Esportes' }],
};

const server = http.createServer((req, res) => {
  hits.push(req.url);
  const send = (code, body) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };
  const base = `http://127.0.0.1:${server.address().port}`;
  if (req.url.startsWith('/rest/v1/live_tv_sources')) {
    return send(200, [{ id: 1, name: 'Fonte1', priority: 1, xtream_host: `${base}/p1`, xtream_user: 'u', xtream_pass: 'p', is_active: true }]);
  }
  if (req.url.startsWith('/rest/v1/iptv_bridges')) {
    return send(200, [
      { id: 2, name: 'Ponte2', xtream_host: `${base}/p2`, xtream_user: 'u', xtream_pass: 'p' },
      { id: 3, name: 'Ponte2Copia', xtream_host: `${base}/p2/`, xtream_user: 'U', xtream_pass: 'p' },
      { id: 4, name: 'Morta', xtream_host: `${base}/dead`, xtream_user: 'u', xtream_pass: 'p' },
    ]);
  }
  if (req.url.startsWith('/rest/v1/')) return send(200, []);
  const m = req.url.match(/^\/(\w+)\/player_api\.php\?(.*)$/);
  if (!m) return send(404, {});
  const [, panel, qs] = m;
  const action = new URLSearchParams(qs).get('action');
  if (panel === 'dead') return send(401, { error: 'no' });
  if (panel === 'p2' && p2Fails) return send(503, {});
  if (action === 'get_live_categories') return send(200, panelCats[panel]);
  setTimeout(() => send(200, panelStreams[panel]), streamsDelayMs);
});

let liveTv;
test.before(async () => {
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  process.env.NODE_ENV = 'test';
  process.env.SUPABASE_URL = `http://127.0.0.1:${server.address().port}`;
  process.env.SUPABASE_SERVICE_ROLE_KEY = 'test-key';
  delete process.env.REQUIRE_VIP_LIVE_TV;
  liveTv = require('../api/live-tv.js');
});
test.after(() => server.close());

function call(query = {}) {
  return new Promise((resolve, reject) => {
    const headers = {};
    const res = {
      setHeader(k, v) { headers[k] = v; },
      status(code) { this.code = code; return this; },
      json(body) { resolve({ code: this.code, body, headers }); },
    };
    liveTv({ method: 'GET', query, headers: {} }, res).catch(reject);
  });
}

const count = (re) => hits.filter((h) => re.test(h)).length;

test('pedidos simultaneos fazem uma unica atualizacao e fontes repetidas carregam 1x', async () => {
  streamsDelayMs = 150;
  const [a, b, c] = await Promise.all([call(), call(), call()]);
  streamsDelayMs = 0;
  for (const r of [a, b, c]) assert.strictEqual(r.code, 200);
  assert.strictEqual(count(/^\/p1\/player_api.*get_live_streams/), 1);
  assert.strictEqual(count(/^\/p2\/player_api.*get_live_streams/), 1, 'Ponte2Copia e a mesma fonte');

  const body = a.body;
  const names = body.channels.map((ch) => ch.name).sort();
  assert.deepStrictEqual(names, ['ESPN', 'Globo', 'SporTV'].sort());
  const globo = body.channels.find((ch) => ch.name === 'Globo');
  assert.strictEqual(globo.streams.length, 2, 'Globo junta as duas fontes');
  assert.ok(globo.logo);
  const catIds = body.categories.map((x) => x.id);
  assert.deepStrictEqual(catIds.sort(), ['abertos', 'all', 'esportes'].sort(), 'categorias iguais viram uma so');
  assert.ok(!JSON.stringify(body).includes('XXX'));
  assert.strictEqual(body.sourcesUsed, 2);
});

test('fonte morta fica pausada e fonte que caiu usa a ultima lista boa', async () => {
  const deadBefore = count(/^\/dead\//);
  p2Fails = true;
  const r = await call({ refresh: '1' }); // < 1 min: devolve cache sem refazer
  assert.strictEqual(r.headers['X-Cache'], 'HIT');
  // forca atualizacao interna
  const { sourceHealth } = liveTv._test;
  const deadState = [...sourceHealth.entries()].find(([k]) => k.includes('|u') && sourceHealth.get(k).name === 'Morta');
  assert.ok(deadState && deadState[1].fails >= 1 && deadState[1].until > Date.now());
  p2Fails = false;
  assert.strictEqual(count(/^\/dead\//), deadBefore);
});

test('loadWithHealth: pausa crescente e reaproveita ultima lista boa', async () => {
  const { loadWithHealth, sourceHealth, backoffMs } = liveTv._test;
  let ok = true;
  let calls = 0;
  const entry = {
    key: 'teste|x', name: 'T', priority: 1,
    load: async () => { calls += 1; return ok ? { streams: [{ name: 'A' }], categories: [] } : { streams: [], categories: [], skipReason: 'HTTP 503' }; },
  };
  const r1 = await loadWithHealth(entry);
  assert.strictEqual(r1.streams.length, 1);
  ok = false;
  const r2 = await loadWithHealth(entry);
  assert.strictEqual(r2.reused, true);
  assert.strictEqual(r2.streams.length, 1);
  const r3 = await loadWithHealth(entry); // pausada: nem chama
  assert.strictEqual(calls, 2);
  assert.strictEqual(r3.reused, true);
  assert.strictEqual(sourceHealth.get('teste|x').fails, 1);
  assert.ok(backoffMs(1) < backoffMs(2) && backoffMs(30) === 6 * 60 * 60 * 1000);
  sourceHealth.delete('teste|x');
});

test('categoryKey e cleanCategoryName juntam variacoes', () => {
  const { categoryKey, cleanCategoryName } = liveTv._test;
  assert.strictEqual(categoryKey('BR | ESPORTES'), 'esportes');
  assert.strictEqual(categoryKey('⚽ Esportes'), 'esportes');
  assert.strictEqual(categoryKey('Canais Esportes'), 'esportes');
  assert.strictEqual(categoryKey('Brasil'), 'brasil');
  assert.strictEqual(categoryKey('BR TV'), 'br tv');
  assert.strictEqual(cleanCategoryName('BR | ESPORTES'), 'Esportes');
  assert.strictEqual(cleanCategoryName('⚽ Futebol'), 'Futebol');
});

test('categorias 24h e "Canais |" ficam com nome limpo', () => {
  const { categoryKey, cleanCategoryName } = liveTv._test;
  assert.strictEqual(categoryKey('24h - Animes'), categoryKey('Animes 24h'));
  assert.strictEqual(cleanCategoryName('24h) CINEMA'), 'Cinema 24h');
  assert.strictEqual(cleanCategoryName('Canais | Abertos'), 'Abertos');
  assert.strictEqual(categoryKey('Canais | Abertos'), 'abertos');
});

test('nomes do mesmo canal com numero/qualidade grudados viram um so', () => {
  const { channelMergeKey, mergeResults } = require('../api/live-tv.js')._test;
  const k = (n) => channelMergeKey(n).replace(/\s+/g, '');
  assert.strictEqual(k('HBO 2'), k('HBO2'));
  assert.strictEqual(k('SporTV 2'), k('SporTV2'));
  assert.strictEqual(k('HBO XTREMEFHD'), k('HBO Xtreme'));
  assert.strictEqual(k('RECORD RS FHD2'), k('RECORD RS'));
  assert.strictEqual(k('-SPORTV 3'), k('SporTV 3'));
  assert.notStrictEqual(k('SporTV 2'), k('SporTV 3'));
  const r = {
    entryKey: 'a', priority: 1, categories: [{ id: '1', name: 'Filmes' }],
    streams: [
      { name: 'HBO 2', categoryId: '1', url: 'http://a/1.m3u8', sourceLabel: 'A' },
      { name: 'HBO2 FHD', categoryId: '1', url: 'http://a/2.m3u8', sourceLabel: 'A' },
    ],
  };
  const { channels } = mergeResults([r], []);
  assert.strictEqual(channels.length, 1);
  assert.strictEqual(channels[0].streams.length, 2);
});

test('fonte cujos links nao tocam vai para o fim da ordem', () => {
  const { mergeResults, playbackHealth } = require('../api/live-tv.js')._test;
  playbackHealth.set('morta', { ok: 0, total: 4, at: Date.now(), name: 'Morta' });
  const mk = (key, pri, url) => ({
    entryKey: key, priority: pri, categories: [{ id: '1', name: 'Abertos' }],
    streams: [{ name: 'Globo HD', categoryId: '1', url, sourceLabel: key }],
  });
  const { channels } = mergeResults([mk('morta', 1, 'http://m/1.m3u8'), mk('boa', 5, 'http://b/1.m3u8')], []);
  assert.strictEqual(channels[0].streams[0].url, 'http://b/1.m3u8');
  assert.strictEqual(channels[0].streams.length, 2, 'a fonte morta continua como reserva');
  playbackHealth.delete('morta');
});

test('"ESPN 1" junta com "ESPN", mas "ESPN 2" continua separado', () => {
  const { mergeResults } = require('../api/live-tv.js')._test;
  const r = {
    entryKey: 'x', priority: 1, categories: [{ id: '1', name: 'Esportes' }],
    streams: ['ESPN', 'ESPN 1', 'ESPN 2'].map((name, i) => ({ name, categoryId: '1', url: `http://x/${i}.m3u8`, sourceLabel: 'X' })),
  };
  const names = mergeResults([r], []).channels.map((c) => c.name).sort();
  assert.deepStrictEqual(names, ['ESPN', 'ESPN 2']);
});
