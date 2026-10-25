const test = require('node:test');
const assert = require('node:assert');
const zlib = require('zlib');
const { decodeSubtitleBuffer, toVtt } = require('../lib/subtitle-text');

test('BOM UTF-8 e latin1', () => {
  assert.strictEqual(decodeSubtitleBuffer(Buffer.from('\uFEFFOlá', 'utf8')), 'Olá');
  assert.strictEqual(decodeSubtitleBuffer(Buffer.from([0x4f, 0x6c, 0xe1])), 'Olá');
});
test('UTF-16 LE com BOM e gzip', () => {
  const u16 = Buffer.concat([Buffer.from([0xff, 0xfe]), Buffer.from('Ação', 'utf16le')]);
  assert.strictEqual(decodeSubtitleBuffer(u16), 'Ação');
  assert.strictEqual(decodeSubtitleBuffer(zlib.gzipSync(Buffer.from('ok'))), 'ok');
});
test('SRT -> VTT com horas curtas', () => {
  const v = toVtt('1\r\n0:00:01,5 --> 0:00:02,000\r\n<i>Oi</i>\r\n\r\n2\r\n00:03,000 --> 00:04,000\r\nTchau\r\n');
  assert.match(v, /^WEBVTT/);
  assert.match(v, /00:00:01\.500 --> 00:00:02\.000\n<i>Oi<\/i>/);
  assert.match(v, /00:00:03\.000 --> 00:00:04\.000\nTchau/);
  assert.ok(!/^1$/m.test(v));
});
test('ASS -> VTT com {\\an8}', () => {
  const ass = '[Script Info]\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n' +
    'Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\\an8}{\\i1}Topo, aqui{\\i0}\\NLinha 2\n';
  const v = toVtt(ass);
  assert.match(v, /00:00:01\.000 --> 00:00:02\.500\n\{\\an8\}Topo, aqui\nLinha 2/);
});
