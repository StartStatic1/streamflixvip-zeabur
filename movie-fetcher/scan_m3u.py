#!/usr/bin/env python3
"""
Movie Fetcher - Scan M3U (v3 - final)
Le a M3U local, filtra filmes das categorias relevantes (exclui
Lancamentos/Cinema/Oscar/4K/Natalino/Religiosos/Documentarios/etc),
consulta o TMDB para achar o ano real de cada filme, filtra 1975-1995,
deduplica priorizando versao dublada sobre legendada, e salva a lista
de candidatos no R2 (candidates.json). NAO baixa nenhum video.
"""

import os
import re
import json
import time
import urllib.request
import urllib.parse
import boto3
from datetime import datetime
from botocore.client import Config

M3U_LOCAL_PATH = os.environ.get("M3U_LOCAL_PATH", "")
M3U_URL = os.environ.get("M3U_URL", "")
TMDB_API_KEY = os.environ["TMDB_API_KEY"]

R2_ACCESS_KEY_ID = os.environ["R2_ACCESS_KEY_ID"]
R2_SECRET_ACCESS_KEY = os.environ["R2_SECRET_ACCESS_KEY"]
R2_ACCOUNT_ID = os.environ["R2_ACCOUNT_ID"]
R2_BUCKET_NAME = os.environ["R2_BUCKET_NAME"]

YEAR_MIN = int(os.environ.get("YEAR_MIN", "1975"))
YEAR_MAX = int(os.environ.get("YEAR_MAX", "1995"))

CANDIDATES_KEY = "movie-fetcher/candidates.json"
PROGRESS_KEY = "movie-fetcher/scan_progress.json"
LOG_PREFIX = "[scan_m3u]"

# Categorias que entram no escopo (dublado/geral + legendado)
INCLUDED_GROUPS = {
    "Filmes | Terror",
    "Filmes | Drama",
    "Filmes | Ficcao",
    "Filmes | Fantasia",
    "Filmes | Comedia",
    "Filmes | Acao",
    "Filmes | Nacionais",
    "Filmes | Legendados",
    "Filmes | Suspense",
    "Filmes | Crime",
    "Filmes | Romance",
    "Filmes | Faroeste",
    "Filmes | Guerra",
    "Filmes | Animacao",
    "Filmes | Aventura",
    "Filmes | Família",
    "Filmes | Infantis",
}
LEGENDADOS_GROUP = "Filmes | Legendados"

s3 = boto3.client(
    "s3",
    endpoint_url=f"https://{R2_ACCOUNT_ID}.r2.cloudflarestorage.com",
    aws_access_key_id=R2_ACCESS_KEY_ID,
    aws_secret_access_key=R2_SECRET_ACCESS_KEY,
    config=Config(signature_version="s3v4"),
    region_name="auto",
)


def log(msg):
    print(f"{LOG_PREFIX} {datetime.now().isoformat()} {msg}", flush=True)


def load_json_from_r2(key, default):
    try:
        obj = s3.get_object(Bucket=R2_BUCKET_NAME, Key=key)
        return json.loads(obj["Body"].read().decode("utf-8"))
    except s3.exceptions.NoSuchKey:
        return default
    except Exception as e:
        log(f"aviso: falha ao ler {key} do R2 ({e}), usando default")
        return default


def save_json_to_r2(key, data):
    body = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
    s3.put_object(Bucket=R2_BUC
