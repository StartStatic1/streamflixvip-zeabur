// api/admin-vip.js — carrega 9 chunks
const fs = require('fs');
const path = require('path');
const Module = require('module');
const n = 9;
const code = [];
for (let i = 0; i < n; i++) {
  code.push(fs.readFileSync(path.join(__dirname, 'admin-vip.c' + i + '.js'), 'utf8'));
}
const m = new Module(module.id, module);
m.filename = path.join(__dirname, 'admin-vip.js');
m.paths = Module._nodeModulePaths(__dirname);
m._compile(code.join(''), m.filename);
module.exports = m.exports;
