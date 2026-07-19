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
const fs = require('fs');

const app = express();
const PORT = process.env.PORT || 8080;

// ── Detecção do nome real da pasta de arquivos estáticos ──
// O GitHub (e Windows/Mac) não fazem diferença entre maiúsculas e
// minúsculas em nomes de pasta, mas o Linux (usado pelo Zeabur) faz.
// Se a pasta foi commitada como "Public" em vez de "public" (ou
// vice-versa), o caminho fixo quebraria com ENOENT. Aqui detectamos
// qual nome existe de verdade no filesystem do container, na hora que
// o servidor sobe, e usamos esse — funciona com qualquer capitalização.
function resolveStaticDir() {
  const candidates = ['public', 'Public', 'PUBLIC'];
  for (const name of candidates) {
    const fullPath = path.join(__dirname, name);
    if (fs.existsSync(fullPath)) return fullPath;
  }
  // Nenhuma bateu — assume 'public' mesmo (vai dar erro claro se faltar,
  // mais fácil de diagnosticar do que um caminho errado silencioso).
  return path.join(__dirname, 'public');
}
const STATIC_DIR = resolveStaticDir();
console.log('Servindo arquivos estáticos de:', STATIC_DIR);

// ── Middlewares ──
// express.json() popula req.body (equivalente ao que a Vercel já fazia
// automaticamente) — necessário pros endpoints POST (admin-vip, comments,
// redeem-vip, track-login, embed-config no modo telemetria).
app.use(express.json({ limit: '2mb' }));

// ── Handlers importados (mesma lógica dos arquivos da Vercel) ──
const activeAds     = require('./api/active-ads.js');
const adminVip       = require('./api/admin-vip.js');
const appVersion     = require('./api/app-version.js');
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
app.all('/api/app-version',   wrap(appVersion));
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
// STATIC_DIR já resolve automaticamente entre 'public'/'Public' (ver acima).
app.use(express.static(STATIC_DIR, {
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
  res.sendFile(path.join(STATIC_DIR, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`StreamFlixVIP (espelho Zeabur) rodando na porta ${PORT}`);
});

// ── Sincronização IPTV: DESATIVADA aqui, migrada pro GitHub Actions ──
// Rodava a cada 5 minutos dentro deste mesmo processo (ver histórico
// abaixo), mas isso competia por CPU/memória com o site e o proxy de
// vídeo, e ainda duplicava o consumo de cota da TMDB junto com o
// sync-standalone.js que agora roda separado, agendado pelo GitHub
// Actions (.github/workflows/iptv-sync.yml), a cada 30 minutos.
// Se precisar voltar a rodar por aqui por algum motivo, é só descomentar
// a linha abaixo.
// iptvSync.startAutoSync({ intervalMs: 5 * 60 * 1000, timeBudgetMs: 50_000 });

// ── Watchdog de memória ──
// Numa VM de 2GB com stream de vídeo passando pelo Node (stream-proxy.js),
// cada requisição simultânea de vídeo consome um buffer de chunks na RAM.
// Se vários usuários assistem ao mesmo tempo, a memória do processo sobe
// (visto no painel: 72% de uso mesmo com CPU em 6% — típico de memória
// presa em requisições de stream, não de CPU travada). Isso loga o uso
// pra você acompanhar nos Runtime logs e identificar quando está perto
// do limite, ANTES de a Zeabur matar e reiniciar o processo (o que gera
// aquele monte de instâncias em "Starting" que você viu no painel).
setInterval(() => {
  const used = process.memoryUsage();
  const rssMB = Math.round(used.rss / 1024 / 1024);
  if (rssMB > 1200) { // ~60% dos 1967MB disponíveis — alerta cedo
    console.warn(`⚠️ Memória alta: ${rssMB}MB em uso (RSS). Considere reiniciar ou investigar streams presos.`);
  }
}, 30000);
