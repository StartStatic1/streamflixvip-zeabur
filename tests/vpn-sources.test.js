const test = require('node:test');
const assert = require('node:assert');
const { labelNeedsVpn, stampRequiresVpn } = require('../lib/vpn-sources');

test('labelNeedsVpn casa nome da ponte no rótulo do add-on', () => {
  const names = new Set(['Kasshub']);
  assert.strictEqual(labelNeedsVpn('Kasshub', names), true);
  assert.strictEqual(labelNeedsVpn('Kasshub · HD', names), true);
  assert.strictEqual(labelNeedsVpn('Kasshub · 1080p · Dublado', names), true);
  assert.strictEqual(labelNeedsVpn('Draxix', names), false);
  assert.strictEqual(labelNeedsVpn('Ka', names), false);
  assert.strictEqual(labelNeedsVpn('', names), false);
});

test('stampRequiresVpn respeita flag já verdadeira e preenche o resto', () => {
  const names = new Set(['Kasshub']);
  const out = stampRequiresVpn([
    { source_url: 'a', source_label: 'Kasshub · HD' },
    { source_url: 'b', source_label: 'Draxix', requires_vpn: true },
    { source_url: 'c', source_label: 'Sander' },
  ], names);
  assert.strictEqual(out[0].requires_vpn, true);
  assert.strictEqual(out[1].requires_vpn, true);
  assert.strictEqual(out[2].requires_vpn, false);
});
