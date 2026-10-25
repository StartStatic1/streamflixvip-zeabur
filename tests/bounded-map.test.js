const test = require('node:test');
const assert = require('node:assert');
const { setBounded } = require('../lib/bounded-map');

test('setBounded limita so o prefixo e remove os mais antigos', () => {
  const m = new Map([['lista:1', 'grande']]);
  for (let i = 0; i < 10; i += 1) setBounded(m, `imdb:${i}`, i, 'imdb:', 3);
  assert.deepStrictEqual([...m.keys()], ['lista:1', 'imdb:7', 'imdb:8', 'imdb:9']);
  setBounded(m, 'imdb:7', 'novo', 'imdb:', 3); // reusar chave move para o fim
  assert.deepStrictEqual([...m.keys()], ['lista:1', 'imdb:8', 'imdb:9', 'imdb:7']);
});
