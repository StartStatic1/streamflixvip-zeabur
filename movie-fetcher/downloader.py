#!/usr/bin/env python3
"""
Movie Fetcher - Downloader
Roda em loop lento (verifica a fila a cada CHECK_INTERVAL segundos).
Para cada pedido na fila (download_queue.json): baixa o video da URL
de origem (IPTV), sobe pro R2 em movies/<id>.mp4, atualiza o status
no candidates.json para "done" com o link publico, remove da fila.

Processa 1 filme por vez (sequencial), com checagem de espaco em
disco antes de cada download, para nunca comprometer o VPS que
tambem hospeda o site principal StreamFlixVIP.
"""

import os
import json
import time
import shutil
import urllib.request
from datetime import datetime
import boto3
from botocore.client import Config

R2_ACCESS_KEY_ID = os.environ["R2_ACCESS_KEY_ID"]
R2_SECRET_ACCESS_KEY = os.environ["R2_SECRET_ACCESS_KEY"]
R2_ACCOUNT_ID = os.environ["R2_ACCOUNT_ID"]
R2_BUCKET_NAME = os.environ["R2_BUCKET_NAME"]
R2_PUBLIC_BASE_URL = os.environ["R2_PUBLIC_BASE_URL"]

CANDIDATES_KEY = "movie-fetcher/candidates.json"
QUEUE_KEY = "movie-fetcher/download_queue.json"
LOG_PREFIX = "[downloader]"

CHECK_INTERVAL = int(os.environ.get("DOWNLOADER_CHECK_INTERVAL", "60"))
MIN_FREE_GB = float(os.environ.get("MIN_FREE_DISK_GB", "5"))
TMP_DIR = os.environ.get("DOWNLOADER_TMP_DIR", "/root/streamflix/movie-fetcher/tmp_downloads")

# Arquivo baixado menor que isso e tratado como erro (provavelmente
# pagina de erro do servidor de origem, nao o video real).
MIN_VALID_FILE_MB = float(os.environ.get("MIN_VALID_FILE_MB", "10"))

os.makedirs(TMP_DIR, exist_ok=True)

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


def load_json(key, default):
    try:
        obj = s3.get_object(Bucket=R2_BUCKET_NAME, Key=key)
        return json.loads(obj["Body"].read().decode("utf-8"))
    except s3.exceptions.NoSuchKey:
        return default
    except Exception as e:
        log(f"aviso: falha ao ler {key} ({e})")
        return default


def save_json(key, data):
    body = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
    s3.put_object(Bucket=R2_BUCKET_NAME, Key=key, Body=body, ContentType="application/json")


def free_disk_gb():
    total, used, free = shutil.disk_usage("/")
    return free / (1024 ** 3)


def update_candidate_status(candidates, movie_id, **fields):
    for c in candidates:
        if c["id"] == movie_id:
            c.update(fields)
            return True
    return False


def download_file(url, dest_path):
    """
    Baixa o arquivo de video da fonte IPTV.

    O painel de origem so entrega o link real (302 -> servidor de
    entrega com token assinado) quando reconhece o User-Agent como
    um player de video legitimo. Com um UA generico (ex: Mozilla/5.0)
    ele responde 200 OK com uma pagina HTML de erro disfarcada de
    video/mp4 (Content-Length pequeno), sem nunca lancar excecao.

    Por isso: usamos User-Agent de VLC, mandamos Range como um player
    real manda, e validamos o tamanho final do arquivo antes de dar
    como sucesso.
    """
    headers = {
        "User-Agent": "VLC/3.0.4 LibVLC/3.0.4",
        "Accept": "*/*",
        "Range": "bytes=0-",
        "Icy-MetaData": "1",
    }
    req = urllib.request.Request(url, headers=headers)

    with urllib.request.urlopen(req, timeout=120) as resp:
        content_type = resp.getheader("Content-Type", "")
        if content_type and "video" not in content_type.lower() and "octet-stream" not in content_type.lower():
            raise ValueError(f"resposta nao parece ser video (Content-Type: {content_type})")

        with open(dest_path, "wb") as out:
            shutil.copyfileobj(resp, out)

    size_mb = os.path.getsize(dest_path) / (1024 * 1024)
    if size_mb < MIN_VALID_FILE_MB:
        os.remove(dest_path)
        raise ValueError(
            f"arquivo baixado muito pequeno ({size_mb:.2f}MB, minimo {MIN_VALID_FILE_MB}MB) "
            f"- provavelmente pagina de erro do servidor de origem, nao o video real"
        )


def process_one(movie_id):
    candidates = load_json(CANDIDATES_KEY, [])
    movie = next((c for c in candidates if c["id"] == movie_id), None)
    if not movie:
        log(f"ERRO: filme {movie_id} nao encontrado nos candidatos, pulando")
        return False

    title_safe = "".join(ch if ch.isalnum() or ch in " -_" else "" for ch in movie["title"]).strip()
    filename = f"{title_safe} ({movie['year']}).mp4".replace(" ", "_")
    tmp_path = os.path.join(TMP_DIR, filename)
    r2_key = f"movies/{filename}"

    log(f"iniciando: {movie['title']} ({movie['year']})")
    update_candidate_status(candidates, movie_id, status="downloading")
    save_json(CANDIDATES_KEY, candidates)

    free_gb = free_disk_gb()
    if free_gb < MIN_FREE_GB:
        log(f"ABORTADO: espaco em disco insuficiente ({free_gb:.1f}GB livres, minimo {MIN_FREE_GB}GB)")
        update_candidate_status(candidates, movie_id, status="error", error="disco cheio")
        save_json(CANDIDATES_KEY, candidates)
        return False

    try:
        download_file(movie["source_url"], tmp_path)
    except Exception as e:
        log(f"ERRO ao baixar {movie['title']}: {e}")
        update_candidate_status(candidates, movie_id, status="error", error=str(e))
        save_json(CANDIDATES_KEY, candidates)
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
        return False

    size_mb = os.path.getsize(tmp_path) / (1024 * 1024)
    log(f"download concluido ({size_mb:.1f}MB), enviando para R2...")

    try:
        s3.upload_file(tmp_path, R2_BUCKET_NAME, r2_key, ExtraArgs={"ContentType": "video/mp4"})
    except Exception as e:
        log(f"ERRO ao subir para R2: {e}")
        update_candidate_status(candidates, movie_id, status="error", error=str(e))
        save_json(CANDIDATES_KEY, candidates)
        os.remove(tmp_path)
        return False

    os.remove(tmp_path)
    r2_url = f"{R2_PUBLIC_BASE_URL}/{r2_key}"

    candidates = load_json(CANDIDATES_KEY, [])
    update_candidate_status(candidates, movie_id, status="done", r2_url=r2_url, downloaded_at=datetime.now().isoformat())
    save_json(CANDIDATES_KEY, candidates)

    log(f"CONCLUIDO: {movie['title']} -> {r2_url}")
    return True


def main():
    log(f"downloader iniciado (verifica fila a cada {CHECK_INTERVAL}s, minimo {MIN_FREE_GB}GB livres)")
    while True:
        queue = load_json(QUEUE_KEY, [])
        if not queue:
            time.sleep(CHECK_INTERVAL)
            continue

        next_item = queue[0]
        movie_id = next_item["id"]

        success = process_one(movie_id)

        queue = load_json(QUEUE_KEY, [])
        queue = [q for q in queue if q["id"] != movie_id]
        save_json(QUEUE_KEY, queue)

        if not success:
            log(f"item {movie_id} removido da fila apos falha (verifique status 'error' no candidates.json)")

        time.sleep(5)


if __name__ == "__main__":
    main()
