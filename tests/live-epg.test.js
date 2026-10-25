// node --test tests/
const test = require('node:test');
const assert = require('node:assert');
const { parseXmlTvTime, parseFeed, aliasFromId } = require('../api/live-epg.js')._test;

function stamp(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())} +0000`;
}

test('horario XMLTV respeita o fuso', () => {
  assert.strictEqual(parseXmlTvTime('20261025120000 +0000').toISOString(), '2026-10-25T12:00:00.000Z');
  assert.strictEqual(parseXmlTvTime('20261025120000 -0300').toISOString(), '2026-10-25T15:00:00.000Z');
  assert.strictEqual(parseXmlTvTime('20261025120000').toISOString(), '2026-10-25T15:00:00.000Z');
  assert.strictEqual(parseXmlTvTime('lixo'), null);
});

test('id estilo epgshare vira nome do canal e agora/a seguir corretos', () => {
  assert.strictEqual(aliasFromId('São.Paulo/SP..SBT.HD.br'), 'SBT HD');
  const now = Date.now();
  const at = (min) => stamp(new Date(now + min * 60000));
  const xml = `<tv>
    <channel id="São.Paulo/SP..SBT.HD.br"><display-name>SBT SP</display-name></channel>
    <programme channel="São.Paulo/SP..SBT.HD.br" start="${at(-30)}" stop="${at(30)}"><title>Jornal</title></programme>
    <programme channel="São.Paulo/SP..SBT.HD.br" start="${at(90)}" stop="${at(120)}"><title>Depois</title></programme>
    <programme channel="São.Paulo/SP..SBT.HD.br" start="${at(30)}" stop="${at(90)}"><title>Novela</title></programme>
  </tv>`;
  const rows = parseFeed(xml);
  const sbt = rows.find((r) => r.name === 'SBT HD');
  assert.ok(sbt, 'alias SBT HD presente');
  assert.strictEqual(sbt.now, 'Jornal');
  assert.strictEqual(sbt.next, 'Novela', 'a seguir = o programa mais proximo');
  assert.ok(rows.find((r) => r.name === 'SBT SP'));
});
