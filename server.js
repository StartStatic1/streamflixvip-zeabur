// server.js
//
// Servidor Express que hospeda o StreamFlixVIP no Zeabur, em paralelo com
// a instância que já roda na Vercel (mesmo Supabase, mesmo catálogo).
//
// Por que existe: a Vercel (plano Hobby) tem limite de 12 Serverless
// Functions por deploy e limites de "fair use" que já causaram bloqueio
// do time inteiro. Rodar uma cópia no Zeabur (que cobra por recurso de
// container, não por quantidade de arquivos/execuções) dá redundância:
// se um cair, o outro continua no ar.
//
// Como funciona: cada arquivo em api/*.js já exporta uma função no
// formato `async function handler(req, res)` (padrão Vercel Serverless
// Function). Esse mesmo formato é compatível com Express quase sem
// alteração — só precisamos "plugar" cada handler numa rota, chamando-o
// com o (req, res) do Express diretamente.

const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 8080;

// ── Middlewares ──
// express.json() popula req.body (equivalente ao que a Vercel já fazia
// automaticamente) — necessário pros endpoints POST (admin-vip, comments,
// redeem-vip, track-login, embed-config no modo telemetria).
app.use(express.json({ limit: '2mb' }));

// ── Handlers importados (mesma lógica dos arquivos da Vercel) ──
const activeAds     = require('./api/active-ads.js');
const adminVip       = require('./api/admin-vip.js');
const comments       = require('./api/comments.js');
const embedConfig    = require('./api/embed-config.js');
const iptvSync       = require('./api/iptv-sync.js');
const omdbRating     = require('./api/omdb-rating.js');
const redeemVip      = require('./api/redeem-vip.js');
const streamProxy    = require('./api/stream-proxy.js');
const subtitles      = require('./api/subtitles.js');
const tmdb           = require('./api/tmdb.js');
const trackLogin     = require('./api/track-login.js');
const vipStatus      = require('./api/vip-status.js');

// ── Rotas de API ──
// Cada rota aceita todos os métodos (o próprio handler já checa
// req.method internamente e responde 405 quando necessário — mesmo
// comportamento que tinham na Vercel, preservado aqui).
const wrap = (handler) => (req, res) => {
  Promise.resolve(handler(req, res)).catch((err) => {
    console.error('Erro não tratado na rota:', err);
    if (!res.headersSent) res.status(500).json({ error: 'Erro interno do servidor' });
  });
};

app.all('/api/active-ads',    wrap(activeAds));
app.all('/api/admin-vip',     wrap(adminVip));
app.all('/api/comments',      wrap(comments));
app.all('/api/embed-config',  wrap(embedConfig));
app.all('/api/iptv-sync',     wrap(iptvSync));
app.all('/api/omdb-rating',   wrap(omdbRating));
app.all('/api/redeem-vip',    wrap(redeemVip));
app.all('/api/stream-proxy',  wrap(streamProxy));
app.all('/api/subtitles',     wrap(subtitles));
app.all('/api/tmdb',          wrap(tmdb));
app.all('/api/track-login',   wrap(trackLogin));
app.all('/api/vip-status',    wrap(vipStatus));

// ── Arquivos estáticos (site, admin, embed) ──
// A pasta public/ contém uma cópia do index.html, admin.html, embed/,
// manifest.json, sw.js, etc — tudo que na Vercel ficava solto na raiz.
app.use(express.static(path.join(__dirname, 'public'), {
  extensions: ['html'], // permite acessar /admin em vez de /admin.html, se preciso
}));

// Fallback: qualquer rota não-API que não bata com um arquivo estático
// serve o index.html (comportamento de SPA — o roteamento de tela dentro
// do app é feito por hash/JS no próprio index.html, igual já era na Vercel).
app.get('*', (req, res) => {
  if (req.path.startsWith('/api/')) {
    res.status(404).json({ error: 'Rota de API não encontrada' });
    return;
  }
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`StreamFlixVIP (espelho Zeabur) rodando na porta ${PORT}`);
});
