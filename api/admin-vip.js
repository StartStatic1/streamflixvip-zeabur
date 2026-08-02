// api/admin-vip.js — carrega partes p0..p2
const fs = require('fs');
const path = require('path');
const Module = require('module');
const code = [0, 1, 2]
  .map((i) => fs.readFileSync(path.join(__dirname, 'admin-vip.p' + i + '.js'), 'utf8'))
  .join('');
const m = new Module(module.id, module);
m.filename = path.join(__dirname, 'admin-vip.js');
m.paths = Module._nodeModulePaths(__dirname);
m._compile(code, m.filename);
module.exports = m.exports;
