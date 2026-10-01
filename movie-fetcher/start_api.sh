#!/bin/bash
set -u
cd /root/streamflix/movie-fetcher || exit 1
if [ -f .env ]; then
  set -a
  source .env
  set +a
fi
exec python3 request_api.py
