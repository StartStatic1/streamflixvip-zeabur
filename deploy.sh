#!/bin/bash
set -e
cd /root/streamflix
echo "==> Atualizando do GitHub..."
git fetch origin main
git reset --hard origin/main
echo "==> Arquivos criticos..."
AV=$(wc -c < api/admin-vip.js)
echo "admin-vip.js = $AV bytes"
if [ "$AV" -lt 20000 ]; then
  echo "ERRO: admin-vip.js muito pequeno. Abortando."
  exit 1
fi
node -e "require('./lib/iptv-parser.js'); console.log('parser OK')"
echo "==> Subindo API..."
bash /root/streamflix/start-api.sh
PORT=$(grep '^PORT=' .env | cut -d= -f2 | tr -d ' \r')
if [ -z "$PORT" ]; then PORT=8000; fi
if [ -f /etc/nginx/sites-available/streamflix ]; then
  sed -i "s|proxy_pass http://localhost:[0-9]*;|proxy_pass http://localhost:${PORT};|g" /etc/nginx/sites-available/streamflix
  nginx -t && systemctl reload nginx
  echo "nginx -> $PORT"
fi
PUB=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 https://www.streamflixvip.online/api/app-version || echo 000)
echo "Publico HTTP $PUB"
if [ "$PUB" != "200" ]; then exit 1; fi
echo "DEPLOY OK"
