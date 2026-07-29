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
    s3.put_object(Bucket=R2_BUCKET_NAME, Key=key, Body=body, ContentType="application/json")


def load_m3u_content():
    if M3U_LOCAL_PATH:
        log(f"lendo M3U local de {M3U_LOCAL_PATH}")
        with open(M3U_LOCAL_PATH, "r", encoding="utf-8", errors="ignore") as f:
            return f.read()
    if M3U_URL:
        log(f"baixando M3U de {M3U_URL[:40]}...")
        req = urllib.request.Request(M3U_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.read().decode("utf-8", errors="ignore")
    raise RuntimeError("Defina M3U_LOCAL_PATH ou M3U_URL no .env")


EXTINF_RE = re.compile(r'#EXTINF:-?\d+ (?P<attrs>.*?),(?P<display>.*)')
GROUP_RE = re.compile(r'group-title="([^"]*)"')
YEAR_RE = re.compile(r"\((\d{4})\)")


def parse_m3u_movies(content):
    lines = content.splitlines()
    entries = []
    pending = None
    for raw_line in lines:
        line = raw_line.rstrip("\r\n")
        if line.startswith("#EXTINF"):
            m = EXTINF_RE.match(line)
            if not m:
                pending = None
                continue
            attrs = m.group("attrs")
            display = m.group("display").strip()
            group_m = GROUP_RE.search(attrs)
            group = group_m.group(1) if group_m else ""
            pending = {"display": display, "group": group}
        elif line.startswith("http") and pending:
            if "/movie/" in line:
                pending["url"] = line
                entries.append(pending)
            pending = None
    return entries


def extract_year_hint(title):
    m = YEAR_RE.search(title)
    return int(m.group(1)) if m else None


def clean_title(title):
    title = YEAR_RE.sub("", title)
    title = re.sub(r"\[.*?\]", "", title)
    title = re.sub(r"\s+", " ", title)
    return title.strip(" -")


def tmdb_search_movie(title, year_hint=None):
    params = {"api_key": TMDB_API_KEY, "query": title, "language": "pt-BR"}
    if year_hint:
        params["year"] = year_hint
    url = "https://api.themoviedb.org/3/search/movie?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            results = data.get("results") or []
            if results:
                return results[0]
        # se buscou com ano e nao achou, tenta de novo sem ano
        if year_hint:
            params.pop("year")
            url = "https://api.themoviedb.org/3/search/movie?" + urllib.parse.urlencode(params)
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=15) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                results = data.get("results") or []
                return results[0] if results else None
        return None
    except Exception as e:
        log(f"aviso: TMDB falhou para '{title}' ({e})")
        return None


def main():
    log(f"iniciando scan v3 (filtro {YEAR_MIN}-{YEAR_MAX})")

    content = load_m3u_content()
    all_entries = parse_m3u_movies(content)
    log(f"M3U tem {len(all_entries)} entradas de filme (URLs com /movie/) no total")

    scoped = [e for e in all_entries if e["group"] in INCLUDED_GROUPS]
    log(f"{len(scoped)} entradas dentro do escopo de categorias definido")

    candidates = load_json_from_r2(CANDIDATES_KEY, [])
    known_urls = {c["source_url"] for c in candidates}
    # indice por (titulo_tmdb, ano_tmdb) pra deduplicar priorizando dublado
    by_title_year = {}
    for c in candidates:
        by_title_year[(c["title"], c["year"])] = c

    log(f"ja existem {len(candidates)} candidatos salvos anteriormente")

    progress = load_json_from_r2(PROGRESS_KEY, {"processed_urls": []})
    processed_urls = set(progress.get("processed_urls", []))
    log(f"{len(processed_urls)} URLs ja foram processadas em execucoes anteriores (serao puladas)")

    new_count = 0
    updated_count = 0
    checked = 0
    save_every = 25

    for entry in scoped:
        url = entry["url"]
        if url in processed_urls:
            continue

        checked += 1
        title = clean_title(entry["display"])
        year_hint = extract_year_hint(entry["display"])

        movie = tmdb_search_movie(title, year_hint)
        time.sleep(0.25)

        processed_urls.add(url)

        if not movie:
            if checked % 50 == 0:
                log(f"progresso: {checked}/{len(scoped)} verificados, {new_count} candidatos novos ate agora")
            continue

        release = movie.get("release_date") or ""
        if len(release) < 4:
            continue
        tmdb_year = int(release[:4])

        if not (YEAR_MIN <= tmdb_year <= YEAR_MAX):
            continue

        tmdb_title = movie.get("title") or title
        dedup_key = (tmdb_title, str(tmdb_year))

        candidate = {
            "id": f"tmdb-{movie['id']}",
            "tmdb_id": movie["id"],
            "title": tmdb_title,
            "year": str(tmdb_year),
            "overview": movie.get("overview", ""),
            "poster_url": (
                f"https://image.tmdb.org/t/p/w500{movie['poster_path']}"
                if movie.get("poster_path") else None
            ),
            "vote_average": movie.get("vote_average", 0),
            "source_url": url,
            "source_display_name": entry["display"],
            "source_group": entry["group"],
            "status": "pending",
            "added_at": datetime.now().isoformat(),
        }

        existing = by_title_year.get(dedup_key)
        if existing is None:
            candidates.append(candidate)
            by_title_year[dedup_key] = candidate
            known_urls.add(url)
            new_count += 1
            log(f"novo candidato [{entry['group']}]: {tmdb_title} ({tmdb_year})")
        else:
            # ja existe um candidato com mesmo titulo/ano.
            # troca so se o existente for Legendados e o novo NAO for (prioriza dublado)
            if existing["source_group"] == LEGENDADOS_GROUP and entry["group"] != LEGENDADOS_GROUP:
                idx = candidates.index(existing)
                candidates[idx] = candidate
                by_title_year[dedup_key] = candidate
                updated_count += 1
                log(f"atualizado p/ versao dublada [{entry['group']}]: {tmdb_title} ({tmdb_year})")
            # senao, mantem o que ja tem (nao faz nada)

        if checked % save_every == 0:
            save_json_to_r2(CANDIDATES_KEY, candidates)
            save_json_to_r2(PROGRESS_KEY, {"processed_urls": list(processed_urls)})
            log(f"checkpoint salvo: {checked}/{len(scoped)} processados, {new_count} novos, {updated_count} atualizados")

    save_json_to_r2(CANDIDATES_KEY, candidates)
    save_json_to_r2(PROGRESS_KEY, {"processed_urls": list(processed_urls)})
    log(f"scan concluido: {checked} verificados no TMDB, {new_count} novos candidatos, "
        f"{updated_count} atualizados p/ dublado, {len(candidates)} total salvos")


if __name__ == "__main__":
    main()
