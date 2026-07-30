#!/usr/bin/env python3
"""
Movie Fetcher - Fix Source URLs

Problema que este script resolve:
  O scan_m3u.py salva a source_url de cada filme no momento do scan.
  Provedores IPTV reorganizam o catalogo com o tempo, e um ID que
  antes apontava para o filme certo pode passar a apontar para outro
  conteudo (ou para nada). O scan_m3u.py original NUNCA atualiza a
  source_url de um filme que ja existe no candidates.json - ele so
  adiciona filmes novos. Isso significa que filmes com URL desatualizada
  ficam presos para sempre com o link errado.

O que este script faz:
  1. Baixa a M3U atual (mesma fonte configurada no .env)
  2. Para cada filme indicado (por padrao, todos com status "error"
     no candidates.json - mas pode alvejar IDs especificos), procura
     de novo na M3U atual uma entrada com titulo compativel
  3. Se achar, ATUALIZA a source_url e reseta o status para "pending"
     (fica pronto para ser baixado de novo, dessa vez com a URL certa)
  4. Se nao achar nada compativel, deixa como esta e avisa no log

Uso:
  # Corrigir todos os filmes com status "error":
  python3 fix_source_urls.py

  # Corrigir apenas IDs especificos (ex: um filme que veio com
  # conteudo errado, mesmo tendo baixado "com sucesso"):
  python3 fix_source_urls.py tmdb-1210789 tmdb-51184

  # Sempre reconfirme antes de aplicar: o script mostra o que vai
  # mudar e pede confirmacao (s/n) antes de salvar no R2.
"""

import os
import re
import sys
import json
import time
import urllib.request
import urllib.parse
import boto3
from datetime import datetime
from botocore.client import Config

M3U_LOCAL_PATH = os.environ.get("M3U_LOCAL_PATH", "")
M3U_URL = os.environ.get("M3U_URL", "")
TMDB_API_KEY = os.environ.get("TMDB_API_KEY", "")

R2_ACCESS_KEY_ID = os.environ["R2_ACCESS_KEY_ID"]
R2_SECRET_ACCESS_KEY = os.environ["R2_SECRET_ACCESS_KEY"]
R2_ACCOUNT_ID = os.environ["R2_ACCOUNT_ID"]
R2_BUCKET_NAME = os.environ["R2_BUCKET_NAME"]

CANDIDATES_KEY = "movie-fetcher/candidates.json"
LOG_PREFIX = "[fix-urls]"

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


def save_json_to_r2(key, data):
    body = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
    s3.put_object(Bucket=R2_BUCKET_NAME, Key=key, Body=body, ContentType="application/json")


def load_m3u_content():
    if M3U_LOCAL_PATH and os.path.exists(M3U_LOCAL_PATH):
        log(f"lendo M3U local de {M3U_LOCAL_PATH}")
        with open(M3U_LOCAL_PATH, "r", encoding="utf-8", errors="ignore") as f:
            return f.read()
    if M3U_URL:
        log("baixando M3U atual da fonte...")
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


def clean_title(title):
    title = YEAR_RE.sub("", title)
    title = re.sub(r"\[.*?\]", "", title)
    title = re.sub(r"\s+", " ", title)
    return title.strip(" -").lower()


def find_best_match(target_title, target_year, entries):
    """Procura na M3U atual uma entrada cujo titulo limpo bate com o alvo.
    Prioriza correspondencia exata de titulo; se houver mais de uma,
    prioriza a que tiver o ano no texto de exibicao."""
    target_clean = clean_title(target_title)
    matches = []
    for e in entries:
        entry_clean = clean_title(e["display"])
        if entry_clean == target_clean or target_clean in entry_clean or entry_clean in target_clean:
            matches.append(e)

    if not matches:
        return None
    if len(matches) == 1:
        return matches[0]

    # mais de um: prioriza o que tem o ano certo mencionado
    for e in matches:
        if target_year and str(target_year) in e["display"]:
            return e
    return matches[0]


def main():
    target_ids = sys.argv[1:]  # IDs passados na linha de comando, se houver

    candidates = load_json_from_r2(CANDIDATES_KEY, [])
    log(f"{len(candidates)} candidatos carregados do R2")

    if target_ids:
        to_fix = [c for c in candidates if c["id"] in target_ids]
        log(f"modo alvo especifico: {len(to_fix)} filme(s) selecionado(s) por ID")
    else:
        to_fix = [c for c in candidates if c.get("status") == "error"]
        log(f"modo automatico: {len(to_fix)} filme(s) com status 'error' encontrados")

    if not to_fix:
        log("nada para corrigir. Encerrando.")
        return

    content = load_m3u_content()
    entries = parse_m3u_movies(content)
    log(f"M3U atual tem {len(entries)} entradas de filme")

    proposals = []
    for movie in to_fix:
        match = find_best_match(movie["title"], movie["year"], entries)
        if match and match["url"] != movie.get("source_url"):
            proposals.append((movie, match))
        elif match:
            log(f"'{movie['title']}' ({movie['year']}): URL na M3U e igual a atual, nada a corrigir")
        else:
            log(f"'{movie['title']}' ({movie['year']}): NENHUMA correspondencia encontrada na M3U atual")

    if not proposals:
        log("nenhuma correcao aplicavel encontrada. Encerrando.")
        return

    print("\n" + "=" * 70)
    print(f"{len(proposals)} correcao(oes) proposta(s):\n")
    for movie, match in proposals:
        print(f"  {movie['title']} ({movie['year']})")
        print(f"    de: {movie.get('source_url', '(vazio)')}")
        print(f"    para: {match['url']}")
        print()
    print("=" * 70)

    resp = input("\nAplicar essas correcoes? Isso vai atualizar a source_url e "
                 "marcar como 'pending' para novo download. (s/n): ").strip().lower()
    if resp != "s":
        log("cancelado pelo usuario, nada foi alterado.")
        return

    updated = 0
    for movie, match in proposals:
        for c in candidates:
            if c["id"] == movie["id"]:
                c["source_url"] = match["url"]
                c["source_display_name"] = match["display"]
                c["source_group"] = match["group"]
                c["status"] = "pending"
                c.pop("error", None)
                c.pop("r2_url", None)
                c.pop("downloaded_at", None)
                updated += 1
                break

    save_json_to_r2(CANDIDATES_KEY, candidates)
    log(f"CONCLUIDO: {updated} filme(s) corrigido(s) e marcado(s) como 'pending' no candidates.json")


if __name__ == "__main__":
    main()
