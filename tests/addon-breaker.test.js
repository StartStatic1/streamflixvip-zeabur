const test = require('node:test');
const assert = require('node:assert');
const { recordAddonHealth, addonPaused } = require('../lib/stremio-addons.js');

test('disjuntor: 3 falhas pausam, pausa cresce e resposta boa zera', () => {
  const addon = { id: 'teste-breaker', name: 'Teste' };
  const realNow = Date.now;
  let now = 1_000_000;
  Date.now = () => now;
  const warn = console.warn; console.warn = () => {};
  try {
    recordAddonHealth(addon, false); recordAddonHealth(addon, false);
    assert.equal(addonPaused(addon), false);
    recordAddonHealth(addon, false);
    assert.equal(addonPaused(addon), true);
    now += 10 * 60 * 1000 + 1; // 1a pausa: 10 min
    assert.equal(addonPaused(addon), false);
    for (let i = 0; i < 3; i++) recordAddonHealth(addon, false);
    now += 10 * 60 * 1000 + 1; // 2a pausa: 20 min
    assert.equal(addonPaused(addon), true);
    now += 10 * 60 * 1000;
    assert.equal(addonPaused(addon), false);
    recordAddonHealth(addon, true); // respondeu: zera
    for (let i = 0; i < 3; i++) recordAddonHealth(addon, false);
    now += 10 * 60 * 1000 + 1; // volta para 10 min
    assert.equal(addonPaused(addon), false);
  } finally {
    Date.now = realNow; console.warn = warn;
  }
});
