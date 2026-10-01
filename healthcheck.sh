#!/bin/bash
LOG=/var/log/streamflix-watch.log
CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 https://www.streamflixvip.online/api/app-version || echo 000)
if [ "$CODE" != "200" ]; then
  echo "$(date -Is) FALHA http=$CODE — reiniciando" >> "$LOG"
  bash /root/streamflix/start-api.sh >> "$LOG" 2>&1 || true
else
  echo "$(date -Is) OK" >> "$LOG"
fi
tail -n 200 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
