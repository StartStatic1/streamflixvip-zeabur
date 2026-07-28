#!/usr/bin/env python3
"""
Movie Fetcher - Scan M3U
Varre a lista M3U do IPTV, filtra filmes entre 1975-1995 (exclui séries/TV),
consulta o TMDB para pegar metadados, e salva uma lista de "candidatos"
no Cloudflare R2 (candidates.json). NAO baixa nenhum video nesta etapa.

Isolado por completo do projeto StreamFlixVIP principal.
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

M3U_URL = os.environ["M3U_URL"]
TMDB_API_KEY = os.environ["TMDB_API_KEY"]

R2_ACCESS_KEY_ID = os.environ["R2_ACCESS_KEY_ID"]
R2_SECRET_ACCESS_KEY = os.environ["R2_SECRET_ACCESS_KEY"]
R2_ACCOUNT_ID = os.environ["R2_ACCOUNT_ID"]
R2_BUCKET_NAME = os.environ["R2_BUCKET_NAME"]
R2_PUBLIC_BASE_URL = os.environ["R2_PUBLIC_BASE_URL"]

YEAR_MIN = int(os.environ.get("YEAR_MIN", "1975"))
YEAR_MAX = int(os.environ.get("YEAR_MAX", "1995"))

CANDIDATES_KEY = "movie-fetcher/candidates.json"
LOG_PREFIX = "[scan_m3u]"

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
    s3.put_object(
        Bucket=R2_BUCKET_NAME,
        Key=key,
        Body=body,
        ContentType="application/json",
    )

def fetch_m3u(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8", errors="ignore")

def parse_m3u(content):
    lines = content.splitlines()
    entries = []
    current = {}
    for line in lines:
        line = line.strip()
        if line.startswith("#EXTINF"):
            name = line.split(",", 1)[-1].strip()
            current = {"raw_name": name}
        elif line.startswith("http") and current:
            current["url"] = line
            entries.append(current)
            current = {}
    return entries

def extract_year(title):
    m = re.search(r"\((\d{4})\)", title)
    if m:
        return int(m.group(1))
    return None

def clean_title(title):
    title = re.sub(r"\(\d{4}\)", "", title)
    title = re.sub(
        r"\b(4k|1080p|720p|h264|x264|x265|hevc|dual|dublado|legendado|web-dl|bluray|hdtv)\b",
        "",
        title,
        flags=re.IGNORECASE,
    )
    title = re.sub(r"[._]", " ", title)
    title = re.sub(r"\s+", " ", title)
    return title.strip(" -")

def looks_like_series_or_live(raw_name):
    patterns = [
        r"\bS\d{1,2}E\d{1,2}\b",
        r"\bT\d{1,2}\s*E\d{1,2}\b",
        r"\btemporada\b",
        r"\bepis[oó]dio\b",
        r"\bao vivo\b",
        r"\bcanal\b",
        r"\b24h\b",
    ]
    return any(re.search(p, raw_name, flags=re.IGNORECASE) for p in patterns)

def tmdb_search_movie(title, year=None):
    params = {"api_key": TMDB_API_KEY, "query": title, "language": "pt-BR"}
    if year:
        params["year"] = year
    url = "https://api.themoviedb.org/3/search/movie?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            results = data.get("results") or []
            return results[0] if results else None
    except Exception as e:
        log(f"aviso: TMDB falhou para '{title}' ({e})")
        return None

def main():
    log(f"iniciando scan (filtro {YEAR_MIN}-{YEAR_MAX})")
    log(f"baixando M3U de {M3U_URL[:40]}...")

    try:
        content = fetch_m3u(M3U_URL)
    except Exception as e:
        log(f"ERRO fatal: nao foi possivel baixar a M3U ({e})")
        return

    entries = parse_m3u(content)
    log(f"M3U tem {len(entries)} entradas no total")

    candidates = load_json_from_r2(CANDIDATES_KEY, [])
    known_urls = {c["source_url"] for c in candidates}
    log(f"ja existem {len(candidates)} candidatos salvos anteriormente")

    new_count = 0
    checked = 0

    for entry in entries:
        raw_name = entry.get("raw_name", "")
        url = entry.get("url")
        if not url or url in known_urls:
            continue

        if looks_like_series_or_live(raw_name):
            continue

        year = extract_year(raw_name)
        if year is None or not (YEAR_MIN <= year <= YEAR_MAX):
            continue

        checked += 1
        title = clean_title(raw_name)

        movie = tmdb_search_movie(title, year)
        time.sleep(0.3)

        if not movie:
            log(f"sem match no TMDB: '{title}' ({year})")
            continue

        tmdb_year = (movie.get("release_date") or "0000")[:4]
        candidate = {
            "id": f"tmdb-{movie['id']}",
            "tmdb_id": movie["id"],
            "title": movie.get("title") or title,
            "year": tmdb_year,
            "overview": movie.get("overview", ""),
            "poster_url": (
                f"https://image.tmdb.org/t/p/w500{movie['poster_path']}"
                if movie.get("poster_path")
                else None
            ),
            "vote_average": movie.get("vote_average", 0),
            "source_url": url,
            "source_raw_name": raw_name,
            "status": "pending",
            "added_at": datetime.now().isoformat(),
        }
        candidates.append(candidate)
        known_urls.add(url)
        new_count += 1
        log(f"novo candidato: {candidate['title']} ({candidate['year']})")

    save_json_to_r2(CANDIDATES_KEY, candidates)
    log(f"scan concluido: {checked} verificados no TMDB, {new_count} novos candidatos, {len(candidates)} total salvos")

if __name__ == "__main__":
    main()
