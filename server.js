// server.js
//
// Servidor Express do StreamFlixVIP (Hetzner VPS).
// Cada arquivo em api/*.js exporta `async function handler(req, res)`
// e e registrado como rota Express abaixo.
//
// Deploy: /root/streamflix no servidor StreamFlix-server (PM2).
// Env: carrega .env da pasta do projeto (PORT, keys, gates VIP, etc.).

try {
  require('dotenv').config();
} catch (_) {}

const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express();
const PORT = process.env.PORT || 8080;

function resolveStaticDir() {
  // Preferir Public/ (pasta oficial do site + APK do Actions).
  // Nunca deixar public/ minúscula “roubar” o static se as duas existirem.
  const candidates = ['Public', 'PUBLIC', 'public'];
  for (const name of candidates) {
    const fullPath = path.join(__dirname, name);
    if (fs.existsSync(fullPath)) return fullPath;
  }
  return path.join(__dirname, 'Public');
}
const STATIC_DIR = resolveStaticDir();
console.log('Servindo arquivos estaticos de:', STATIC_DIR);
console.log(
  'VIP gate Live TV:',
  process.env.REQUIRE_VIP_LIVE_TV === '1' ||
    String(process.env.REQUIRE_VIP_LIVE_TV || '').toLowerCase() === 'true'
    ? 'HARD (bloqueia nao-VIP)'
    : 'SOFT (so loga)',
);
console.log(
  'Auth gate filmes/series:',
  process.env.REQUIRE_AUTH_MEDIA === '1' ||
    String(process.env.REQUIRE_AUTH_MEDIA || '').toLowerCase() === 'true'
    ? 'HARD (exige login; vip_lock exige VIP)'
    : 'SOFT (so loga)',
);

app.use(express.json({ limit: '2mb' }));

const activeAds = require('./api/active-ads.js');
const adminVip = require('./api/admin-vip.js');
const adminPartners = require('./api/admin-partners.js');
const partnerApi = require('./api/partner.js');
const appVersion = require('./api/app-version.js');
const announcements = require('./api/announcements.js');
const comments = require('./api/comments.js');
const embedConfig = require('./api/embed-config.js');
const heartbeat = require('./api/heartbeat.js');
const iptvSync = require('./api/iptv-sync.js');
const omdbRating = require('./api/omdb-rating.js');
const redeemVip = require('./api/redeem-vip.js');
const streamProxy = require('./api/stream-proxy.js');
const subtitles = require('./api/subtitles.js');
const tmdb = require('./api/tmdb.js');
const tmdbImage = require('./api/tmdb-image.js');
const trackLogin = require('./api/track-login.js');
const vipStatus = require('./api/vip-status.js');
const mercadopago = require('./api/mercadopago.js');
const infinitepay = require('./api/infinitepay.js');
const activateTv = require('./api/activate-tv.js');
const tvStatus = require('./api/tv-status.js');
const r2Presign = require('./api/r2-presign.js');
const liveTv = require('./api/live-tv.js');
const mediaSources = require('./api/media-sources.js');

const wrap = (handler) => (req, res) => {
  Promise.resolve(handler(req, res)).catch((err) => {
    console.error('Erro nao tratado na rota:', err);
    if (!res.headersSent) res.status(500).json({ error: 'Erro interno do servidor' });
  });
};

app.all('/api/active-ads', wrap(activeAds));
app.all('/api/admin-vip', wrap(adminVip));
app.all('/api/admin-partners', wrap(adminPartners));
app.all('/api/partner', wrap(partnerApi));
app.all('/api/app-version', wrap(appVersion));
app.all('/api/announcements', wrap(announcements));
app.all('/api/comments', wrap(comments));
app.all('/api/embed-config', wrap(embedConfig));
app.all('/api/heartbeat', wrap(heartbeat));
app.all('/api/iptv-sync', wrap(iptvSync));
app.all('/api/omdb-rating', wrap(omdbRating));
app.all('/api/redeem-vip', wrap(redeemVip));
app.all('/api/stream-proxy', wrap(streamProxy));
app.all('/api/subtitles', wrap(subtitles));
app.all('/api/tmdb', wrap(tmdb));
app.all('/api/tmdb-image', wrap(tmdbImage));
app.all('/api/track-login', wrap(trackLogin));
app.all('/api/vip-status', wrap(vipStatus));
app.all('/api/mercadopago/*', wrap(mercadopago));
app.all('/api/infinitepay/*', wrap(infinitepay));
app.all('/api/activate-tv', wrap(activateTv));
app.all('/api/tv-status', wrap(tvStatus));
app.all('/api/r2-presign', wrap(r2Presign));
app.all('/api/live-tv', wrap(liveTv));
app.all('/api/media-sources', wrap(mediaSources));

app.use(
  express.static(STATIC_DIR, {
    extensions: ['html'],
  }),
);

app.get('*', (req, res) => {
  if (req.path.startsWith('/api/')) {
    res.status(404).json({ error: 'Rota de API nao encontrada' });
    return;
  }
  res.sendFile(path.join(STATIC_DIR, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`StreamFlixVIP rodando na porta ${PORT}`);
});

setInterval(() => {
  const used = process.memoryUsage();
  const rssMB = Math.round(used.rss / 1024 / 1024);
  if (rssMB > 1200) {
    console.warn(
      `Memoria alta: ${rssMB}MB em uso (RSS). Considere reiniciar ou investigar streams presos.`,
    );
  }
}, 30000);
