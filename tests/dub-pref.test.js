const test = require('node:test');
const assert = require('node:assert');
const { audioOf, buildSiblingIndex, preferDubbed, tagName } = require('../lib/dub-pref');

const norm = (s) => String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/\b(19|20)\d{2}\b/g, ' ')
  .replace(/\b(dublado|legendado|leg|dub|dual|audio|nacional)\b/g, ' ')
  .replace(/[^a-z0-9]+/g, ' ').trim();

test('dub-pref: áudio pelo nome e pela pasta', () => {
  const cats = new Map([['1', 'F • Ação'], ['2', 'F • Legendados'], ['3', 'Filmes Dublados']]);
  assert.equal(audioOf({ name: 'Batman [L]', category_id: '1' }, cats), 'leg');
  assert.equal(audioOf({ name: 'Batman', category_id: '2' }, cats), 'leg');
  assert.equal(audioOf({ name: 'Batman', category_id: '3' }, cats), 'dub');
  assert.equal(audioOf({ name: 'Batman (Dublado)', category_id: '2' }, cats), 'dub');
  assert.equal(audioOf({ name: 'Batman', category_id: '1' }, cats), 'unknown');
});

test('dub-pref: legendado escolhido vira alternativo e o dublado passa a principal', () => {
  const cats = new Map([['1', 'F • Ação'], ['2', 'F • Legendados']]);
  const leg = { name: 'Batman O Cavaleiro das Trevas [L]', category_id: '2', stream_id: 10 };
  const dub = { name: 'Batman O Cavaleiro das Trevas', category_id: '1', stream_id: 11 };
  const other = { name: 'Batman O Cavaleiro das Trevas (1990)', category_id: '1', stream_id: 12 };
  const idx = buildSiblingIndex([leg, dub], norm);
  const r = preferDubbed(leg, idx, norm, cats);
  assert.equal(r.primary.stream_id, 11);
  assert.equal(r.alt.stream_id, 10);
  assert.equal(r.altAudio, 'leg');
  assert.equal(r.primaryAudio, 'dub', 'sem marca + legendado à parte = dublado');
  // ano diferente não é a mesma obra
  const idx2 = buildSiblingIndex([{ ...leg, name: 'Duna (2021) [L]' }, { ...other, name: 'Duna (1984)' }], norm);
  const r2 = preferDubbed({ ...leg, name: 'Duna (2021) [L]' }, idx2, norm, cats);
  assert.equal(r2.alt, null);
  assert.equal(r2.primaryAudio, 'leg');
});

test('dub-pref: só uma versão = sem alternativo; marca no nome', () => {
  const only = { name: 'Filme X', category_id: '9' };
  const r = preferDubbed(only, buildSiblingIndex([only], norm), norm, new Map());
  assert.equal(r.primary, only);
  assert.equal(r.alt, null);
  assert.equal(tagName('Filme X', 'leg'), 'Filme X [L]');
  assert.equal(tagName('Filme X [L]', 'leg'), 'Filme X [L]');
  assert.equal(tagName('Filme X', 'dub'), 'Filme X [D]');
  assert.equal(tagName('Filme X', 'unknown'), 'Filme X');
  assert.equal(tagName('Filme X (L)', 'leg'), 'Filme X (L) [L]');
});
