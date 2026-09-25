// lib/stremio-addons.js — temporary loader until full file is restored
const fs = require('fs');
const path = require('path');
const GOOD = 'https://raw.githubusercontent.com/StartStatic1/streamflixvip-zeabur/a0d4671b134a91a978568f74d946f5e37c1f1ecd/lib/stremio-addons.js';
const cache = path.join(__dirname, 'stremio-addons.cache.js');

function loadSync() {
  if (fs.existsSync(cache)) {
    return require(cache);
  }
  const { execFileSync } = require('child_process');
  try {
    execFileSync('curl', ['-fsSL', '-o', cache, GOOD], { stdio: 'pipe' });
    return require(cache);
  } catch (e) {
    console.error('[stremio-addons] failed to restore cache', e && e.message);
    return {
      normalizeManifestUrl: (u) => u,
      baseFromManifestUrl: (u) => String(u || '').replace(/\/manifest\.json$/i, ''),
      loadActiveAddons: async () => [],
      collectAddonSources: async () => [],
      diagnoseAddonSources: async () => [],
      resolveAnimeIds: async () => null,
      probeManifest: async () => ({ name: 'offline' }),
    };
  }
}
module.exports = loadSync();
