const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'crash-'));
process.env.CRASH_LOG_FILE = path.join(tmp, 'c.json');
const store = require('../lib/crash-store');

test('scrub remove senha, token e caminho de URL', () => {
  const s = store.scrub('falha http://user:pw@host.tv:8080/live/u/p/1.ts?token=abc Bearer abcdefghijklmnop senha=xyz');
  assert.ok(!s.includes('pw@'));
  assert.ok(!s.includes('/live/u/p'));
  assert.ok(!s.includes('abcdefghijklmnop'));
  assert.ok(!s.includes('xyz'));
});

test('sanitizeReport exige stack/message e corta campos', () => {
  assert.strictEqual(store.sanitizeReport({ model: 'TX9' }), null);
  const r = store.sanitizeReport({ model: 'TX9', stack: 'java.lang.IllegalArgumentException: Key 1 was already used\n at a.b(C.kt:1)', screen: 'x'.repeat(500), password: 'nao' }, '1.2.3.4');
  assert.strictEqual(r.model, 'TX9');
  assert.ok(r.screen.length <= 121);
  assert.strictEqual(r.password, undefined);
  assert.match(r.signature, /^[0-9a-f]{12}$/);
});

test('append grava, limita e summarize agrupa', async () => {
  const a = store.sanitizeReport({ stack: 'E: x\n at f(A.kt:1)', model: 'TX9', appVersion: '3.0.4' });
  await store.append([a, { ...a }]);
  const all = store.load();
  assert.strictEqual(all.length, 2);
  const g = store.summarize(all);
  assert.strictEqual(g.length, 1);
  assert.strictEqual(g[0].count, 2);
  await store.clear();
  assert.strictEqual(store.load().length, 0);
  fs.rmSync(tmp, { recursive: true, force: true });
});
