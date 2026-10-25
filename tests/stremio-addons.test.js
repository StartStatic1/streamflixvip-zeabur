// node --test tests/
// Testa a coleta de add-ons com um servidor falso (sem rede externa).
const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');

const hits = [];
let addons = [];
const server = http.createServer((req, res) => {
  hits.push(req.url);
  const send = (code, body) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };
  if (req.url.startsWith('/rest/v1/stremio_addons')) return send(200, addons);
  const m = req.url.match(/^\/(\w+)\/(manifest\.json|stream\/.*)$/);
  if (!m) return send(404, {});
  const [, name, rest] = m;
  if (rest === 'manifest.json') {
    if (name === 'subs') return send(200, { id: 'subs', resources: ['subtitles'], types: ['movie'] });
    if (name === 'ttonly') return send(200, { id: 'tt', resources: [{ name: 'stream', types: ['movie'], idPrefixes: ['tt'] }], types: ['movie'] });
    if (name === 'dead') return; // nunca responde
    return send(200, { id: name, resources: ['stream'], types: ['movie', 'series'] });
  }
  if (name === 'dead') return; // nunca responde (timeout)
  if (name === 'slow') {
    return setTimeout(() => send(200, { streams: [{ name: 'Slow', title: 'Slow 1080p', url: 'https://cdn.example.com/slow.mp4' }] }), 1500);
  }
  if (name === 'ordered') {
    // so o 2o id (tmdb:) tem resultado
    if (rest.includes('tmdb%3A')) return send(200, { streams: [{ name: 'Ord', title: 'Ord 720p', url: 'https://cdn.example.com/ord.mp4' }] });
    return send(200, { streams: [] });
  }
  if (name === 'ttonly') return send(200, { streams: [{ name: 'TT', title: 'TT 1080p', url: 'https://cdn.example.com/tt.mp4' }] });
  return send(404, {});
});

let lib;
let base;
test.before(async () => {
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  base = 'http://127.0.0.1:' + server.address().port;
  process.env.SUPABASE_URL = base;
  delete process.env.TMDB_API_KEY;
  lib = require('../lib/stremio-addons');
});
test.after(() => { server.closeAllConnections && server.closeAllConnections(); server.close(); });

function mk(name, priority) {
  return { id: name, name, manifest_url: base + '/' + name + '/manifest.json', base_url: base + '/' + name, priority };
}

test('ignora add-on de legenda, respeita idPrefixes e a ordem dos IDs', async () => {
  addons = [mk('subs', 1), mk('ttonly', 2), mk('ordered', 3)];
  hits.length = 0;
  // sem TMDB: so ha ids tmdb:, entao "ttonly" nao deve ser consultado
  const out = await lib.collectAddonSources('k', 101, 'movie', null, null, 5000);
  assert.deepStrictEqual(out.map((s) => s.source_url), ['https://cdn.example.com/ord.mp4']);
  assert.ok(!hits.some((h) => h.startsWith('/subs/stream')), 'nao pede stream ao add-on de legenda');
  assert.ok(!hits.some((h) => h.startsWith('/ttonly/stream')), 'nao manda id tmdb: para add-on so-tt');
});

test('cache: 2o pedido nao vai nos add-ons', async () => {
  hits.length = 0;
  const out = await lib.collectAddonSources('k', 101, 'movie', null, null, 5000);
  assert.strictEqual(out.length, 1);
  assert.strictEqual(hits.filter((h) => h.includes('/stream/')).length, 0);
});

test('prazo: devolve o que chegou e completa o cache depois', async () => {
  addons = [mk('ordered', 1), mk('slow', 2)];
  lib.clearAddonCaches();
  const out = await lib.collectAddonSources('k', 202, 'movie', null, null, 600);
  assert.ok(out.length <= 1, 'slow ainda nao chegou');
  await new Promise((r) => setTimeout(r, 1500));
  const later = await lib.collectAddonSources('k', 202, 'movie', null, null, 600);
  assert.ok(later.some((s) => s.source_url.includes('slow.mp4')), 'slow entrou no cache');
});

test('progresso: parcial no prazo curto, completo depois (mesma coleta)', async () => {
  addons = [mk('ordered', 1), mk('slow', 2)];
  lib.clearAddonCaches();
  hits.length = 0;
  const first = await lib.collectAddonSourcesProgress('k', 505, 'movie', null, null, 600);
  assert.strictEqual(first.complete, false);
  assert.ok(!first.sources.some((s) => s.source_url.includes('slow.mp4')));
  const second = await lib.collectAddonSourcesProgress('k', 505, 'movie', null, null, 3000);
  assert.strictEqual(second.complete, true);
  assert.ok(second.sources.some((s) => s.source_url.includes('slow.mp4')));
  assert.strictEqual(hits.filter((h) => h.startsWith('/slow/stream')).length, 1, 'nao refaz a coleta');
  const cached = await lib.collectAddonSourcesProgress('k', 505, 'movie', null, null, 10);
  assert.strictEqual(cached.complete, true);
});

test('disjuntor: add-on que nao responde fica pausado', async () => {
  lib.clearAddonCaches();
  addons = [mk('dead', 1)];
  for (let i = 0; i < 3; i++) {
    await lib.collectAddonSources('k', 300 + i, 'movie', null, null, 6000);
  }
  hits.length = 0;
  const out = await lib.collectAddonSources('k', 399, 'movie', null, null, 6000);
  assert.strictEqual(out.length, 0);
  assert.strictEqual(hits.filter((h) => h.startsWith('/dead/stream')).length, 0, 'pausado: nao chama de novo');
});

