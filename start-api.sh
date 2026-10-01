#!/bin/bash
python3 /root/streamflix/scripts/patch_live_tv_bridge.py || true
export REQUIRE_VIP_MEDIA=1
export REQUIRE_VIP_LIVE_TV=1
unset MIN_APP_VERSION
unset MIN_APP_VERSION_CODE
unset REQUIRE_JWT_MEDIA
set -e
cd /root/streamflix

if [ ! -f .env ]; then
  echo "ERRO: .env nao encontrado"
  exit 1
fi

set -a
source .env
set +a

if [ -z "$PORT" ]; then
  PORT=8000
fi
export PORT

pm2 delete streamflix-api 2>/dev/null || true
pm2 start server.js --name streamflix-api --update-env
pm2 save
sleep 2

CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 8 http://127.0.0.1:$PORT/api/app-version || echo 000)

if [ "$CODE" = "200" ]; then
  echo "OK API na porta $PORT (http $CODE)"
else
  echo "FALHA API porta $PORT (http $CODE)"
  pm2 logs streamflix-api --err --lines 20 --nostream || true
  exit 1
fi
