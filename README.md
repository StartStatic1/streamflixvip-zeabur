# StreamFlixVIP

API + site estático do StreamFlixVIP, rodando no **Hetzner VPS** (Express + PM2).

## Servidor

| Item | Valor |
|------|--------|
| Host | `StreamFlix-server` |
| SSH | `ssh root@65.21.48.50` |
| Pasta | `/root/streamflix` |
| Processo | PM2 (`streamflix` / `streamflix-api`) |
| Porta | `8000` (ou `PORT` no `.env`) |
| Domínio | `streamflixvip.online` |

## Deploy (Termux / SSH)

```bash
ssh root@65.21.48.50
cd /root/streamflix
git pull origin main
npm install --omit=dev   # só se package.json mudou
pm2 restart all
pm2 logs --lines 20
```

## Estrutura

```
server.js       → Express: rotas api/* + estáticos Public/
api/*.js        → endpoints (admin, live-tv, iptv, media-sources, …)
Public/         → index, admin.html, assets
lib/            → helpers (iptv-parser, etc.)
.env            → secrets (não versionado)
```

## Admin

Painel em `/admin.html` (login admin).

- **IPTV** — fontes VOD (filmes/séries)
- **TV ao vivo** — fontes Xtream de canais ao vivo (até 5 fallbacks por prioridade)
