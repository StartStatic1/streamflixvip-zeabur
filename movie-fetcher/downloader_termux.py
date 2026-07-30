#!/usr/bin/env python3
"""
Movie Fetcher - Downloader (versao Termux/celular)

Mesma logica do downloader.py original que roda no VPS, adaptada
para rodar no Termux (Android), que e o unico lugar onde o link de
origem do IPTV nao e bloqueado (o provedor bloqueia IPs de
datacenter/VPS, mas aceita IPs residenciais/moveis).

Diferencas em relacao a versao do VPS:
  - Retry automatico em caso de queda de conexao/timeout (comum em
    rede movel), com espera progressiva entre tentativas.
  - Notificacao no Android (via termux-notification) a cada filme
    concluido ou com erro, para acompanhar sem precisar olhar a tela
    o tempo todo.
  - Log tambem gravado em arquivo local (nao so no terminal), para
    poder conferir depois mesmo se o Termux fechar.
  - Mesma validacao de tamanho minimo do arquivo (evita subir
    "paginas de erro" disfarcadas de video pro R2).

Como usar:
  1. termux-wake-lock          (evita o Android matar o processo)
  2. pip install boto3
  3. Colocar o arquivo .env nesta mesma pasta (mesmas variaveis do
     .env do VPS: R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY,
     R2_ACCOUNT_ID, R2_BUCKET_NAME, R2_PUBLIC_BASE_URL)
  4. export $(grep -v '^#' .env | xargs) && python3 downloader_termux.py

Para parar: Ctrl+C (o download em andamento e cancelado com
seguranca, nao deixa arquivo incompleto no R2).
"""

import os
import sys
import json
import time
import shutil
import subprocess
import urllib.request
import urllib.error
from datetime import datetime
import boto3
from botocore.client import Config

# ---------------------------------------------------------------
# Configuracao (mesmas variaveis de ambiente do .env do VPS)
# ---------------------------------------------------------------
R2_ACCESS_KEY_ID = os.environ["R2_ACCESS_KEY_ID"]
R2_SECRET_ACCESS_KEY = os.environ["R2_SECRET_ACCESS_KEY"]
R2_ACCOUNT_ID = os.environ["R2_ACCOUNT_ID"]
R2_BUCKET_NAME = os.environ["R2_BUCKET_NAME"]
R2_PUBLIC_BASE_URL = os.environ["R2_PUBLIC_BASE_URL"]

CANDIDATES_KEY = "movie-fetcher/candidates.json"
QUEUE_KEY = "movie-fetcher/download_queue.json"
LOG_PREFIX = "[downloader-termux]"

CHECK_INTERVAL = int(os.environ.get("DOWNLOADER_CHECK_INTERVAL", "60"))
MIN_FREE_GB = float(os.environ.get("MIN_FREE_DISK_GB", "1"))  # celular tem menos espaco livre que o VPS
MIN_VALID_FILE_MB = float(os.environ.get("MIN_VALID_FILE_MB", "10"))

# Pasta padrao acessivel no Android via termux-setup-storage
TMP_DIR = os.environ.get(
    "DOWNLOADER_TMP_DIR",
    os.path.expanduser("~/storage/downloads/movie-fetcher-tmp"),
)

MAX_RETRIES = int(os.environ.get("DOWNLOADER_MAX_RETRIES", "4"))
RETRY_BASE_WAIT = 15  # segundos, dobra a cada tentativa (15, 30, 60, 120...)

os.makedirs(TMP_DIR, exist_ok=True)

LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "downloader_termux.log")

s3 = boto3.client(
    "s3",
    endpoint_url=f"https://{R2_ACCOUNT_ID}.r2.cloudflarestorage.com",
    aws_access_key_id=R2_ACCESS_KEY_ID,
    aws_secret_access_key=R2_SECRET_ACCESS_KEY,
    config=Config(signature_version="s3v4"),
    region_name="auto",
)


# ---------------------------------------------------------------
# Utilitarios
# ---------------------------------------------------------------
def log(msg):
    line = f"{LOG_PREFIX} {datetime.now().isoformat()} {msg}"
    print(line, flush=True)
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass  # nao deixa falha de log derrubar o processo


def notify(title, content):
    """Manda uma notificacao Android, se o comando termux-notification existir."""
    try:
        subprocess.run(
            ["termux-notification", "--title", title, "--content", content],
            timeout=5,
            capture_output=True,
        )
    except Exception:
        pass  # termux-api pode nao estar instalado, tudo bem


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
    total, used, free = shutil.disk_usage(TMP_DIR)
    return free / (1024 ** 3)


def update_candidate_status(candidates, movie_id, **fields):
    for c in candidates:
        if c["id"] == movie_id:
            c.update(fields)
            return True
    return False


# ---------------------------------------------------------------
# Download (com retry para lidar com rede movel instavel)
# ---------------------------------------------------------------
PROGRESS_LOG_INTERVAL = float(os.environ.get("DOWNLOADER_PROGRESS_INTERVAL", "5"))  # segundos entre linhas de progresso
PROGRESS_CHUNK_SIZE = 1024 * 256  # 256KB por leitura


def _format_size(num_bytes):
    for unit in ("B", "KB", "MB", "GB"):
        if num_bytes < 1024:
            return f"{num_bytes:.1f}{unit}"
        num_bytes /= 1024
    return f"{num_bytes:.1f}TB"


def download_file_once(url, dest_path):
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

        total_size = resp.getheader("Content-Length")
        total_size = int(total_size) if total_size else None

        downloaded = 0
        start_time = time.time()
        last_log_time = start_time

        with open(dest_path, "wb") as out:
            while True:
                chunk = resp.read(PROGRESS_CHUNK_SIZE)
                if not chunk:
                    break
                out.write(chunk)
                downloaded += len(chunk)

                now = time.time()
                if now - last_log_time >= PROGRESS_LOG_INTERVAL:
                    elapsed = now - start_time
                    speed = downloaded / elapsed if elapsed > 0 else 0
                    if total_size:
                        pct = downloaded / total_size * 100
                        eta_sec = (total_size - downloaded) / speed if speed > 0 else 0
                        eta_min = int(eta_sec // 60)
                        eta_s = int(eta_sec % 60)
                        log(
                            f"progresso: {pct:.1f}% ({_format_size(downloaded)}/{_format_size(total_size)}) "
                            f"- {_format_size(speed)}/s - ETA {eta_min}m{eta_s:02d}s"
                        )
                    else:
                        log(f"progresso: {_format_size(downloaded)} baixados - {_format_size(speed)}/s")
                    last_log_time = now

    size_mb = os.path.getsize(dest_path) / (1024 * 1024)
    if size_mb < MIN_VALID_FILE_MB:
        os.remove(dest_path)
        raise ValueError(
            f"arquivo baixado muito pequeno ({size_mb:.2f}MB, minimo {MIN_VALID_FILE_MB}MB) "
            f"- provavelmente pagina de erro do servidor de origem, nao o video real"
        )


def download_file(url, dest_path):
    """Tenta baixar com retry progressivo (util em rede movel instavel)."""
    last_error = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            if attempt > 1:
                wait = RETRY_BASE_WAIT * (2 ** (attempt - 2))
                log(f"tentativa {attempt}/{MAX_RETRIES} apos espera de {wait}s...")
                time.sleep(wait)
            download_file_once(url, dest_path)
            return  # sucesso
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last_error = e
            log(f"falha de rede na tentativa {attempt}/{MAX_RETRIES}: {e}")
            if os.path.exists(dest_path):
                os.remove(dest_path)
        except ValueError:
            raise  # arquivo pequeno / conteudo invalido: nao adianta tentar de novo, propaga
    raise last_error if last_error else RuntimeError("falha desconhecida no download")


# ---------------------------------------------------------------
# Processamento
# ---------------------------------------------------------------
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
        notify("Movie Fetcher - Disco cheio", f"{movie['title']}: sem espaco livre")
        return False

    try:
        download_file(movie["source_url"], tmp_path)
    except Exception as e:
        log(f"ERRO ao baixar {movie['title']}: {e}")
        update_candidate_status(candidates, movie_id, status="error", error=str(e))
        save_json(CANDIDATES_KEY, candidates)
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
        notify("Movie Fetcher - Erro", f"{movie['title']}: {e}")
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
        notify("Movie Fetcher - Erro upload", f"{movie['title']}: {e}")
        return False

    os.remove(tmp_path)
    r2_url = f"{R2_PUBLIC_BASE_URL}/{r2_key}"

    candidates = load_json(CANDIDATES_KEY, [])
    update_candidate_status(candidates, movie_id, status="done", r2_url=r2_url, downloaded_at=datetime.now().isoformat())
    save_json(CANDIDATES_KEY, candidates)

    log(f"CONCLUIDO: {movie['title']} -> {r2_url}")
    notify("Movie Fetcher - Concluido", f"{movie['title']} ({size_mb:.0f}MB)")
    return True


def main():
    log(f"downloader (Termux) iniciado (verifica fila a cada {CHECK_INTERVAL}s, minimo {MIN_FREE_GB}GB livres)")
    log(f"pasta temporaria: {TMP_DIR}")
    notify("Movie Fetcher", "Downloader iniciado no celular")

    processed = 0
    errors = 0

    try:
        while True:
            queue = load_json(QUEUE_KEY, [])
            if not queue:
                time.sleep(CHECK_INTERVAL)
                continue

            next_item = queue[0]
            movie_id = next_item["id"]

            success = process_one(movie_id)
            processed += 1
            if not success:
                errors += 1

            queue = load_json(QUEUE_KEY, [])
            queue = [q for q in queue if q["id"] != movie_id]
            save_json(QUEUE_KEY, queue)

            if not success:
                log(f"item {movie_id} removido da fila apos falha (verifique status 'error' no candidates.json)")

            log(f"progresso da sessao: {processed} processados, {errors} com erro")
            time.sleep(5)
    except KeyboardInterrupt:
        log(f"interrompido pelo usuario. Total da sessao: {processed} processados, {errors} com erro")
        notify("Movie Fetcher - Parado", f"{processed} processados, {errors} com erro")
        sys.exit(0)


if __name__ == "__main__":
    main()
