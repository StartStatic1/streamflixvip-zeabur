// api/app-version.js
// GET /api/app-version           → mobile (app-version.json)
// GET /api/app-version?platform=tv → TV (tv-version.json)

const fs = require('fs');
const path = require('path');

function versionFileFor(platform) {
  if (platform === 'tv') {
    return path.join(__dirname, '..', 'tv-version.json');
  }
  return path.join(__dirname, '..', 'app-version.json');
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const platform = String(req.query.platform || '').toLowerCase();
  const file = versionFileFor(platform);

  try {
    const raw = fs.readFileSync(file, 'utf-8');
    const data = JSON.parse(raw);

    if (
      typeof data.versionCode !== 'number' ||
      typeof data.versionName !== 'string' ||
      typeof data.apkUrl !== 'string'
    ) {
      console.error('version json invalido:', file, data);
      res.status(500).json({ error: 'Configuração de versão inválida no servidor' });
      return;
    }

    res.status(200).json({
      versionCode: data.versionCode,
      versionName: data.versionName,
      apkUrl: data.apkUrl,
      forceUpdate: data.forceUpdate !== false,
      releaseNotes: data.releaseNotes || '',
      platform: platform === 'tv' ? 'tv' : 'mobile',
    });
  } catch (err) {
    console.error('Erro ao ler version json:', file, err.message);
    res.status(500).json({ error: 'Não foi possível verificar a versão' });
  }
};
