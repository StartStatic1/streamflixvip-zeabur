#!/usr/bin/env python3
"""
Movie Fetcher - Request API
API minima e isolada, rodando numa porta propria (5060), que recebe
pedidos de download vindos do painel HTML e os grava numa fila no R2.
Nao interfere com streamflix-api (5000) nem com o site principal (8000).
"""

import os
import json
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS
import boto3
from botocore.client import Config

R2_ACCESS_KEY_ID = os.environ["R2_ACCESS_KEY_ID"]
R2_SECRET_ACCESS_KEY = os.environ["R2_SECRET_ACCESS_KEY"]
R2_ACCOUNT_ID = os.environ["R2_ACCOUNT_ID"]
R2_BUCKET_NAME = os.environ["R2_BUCKET_NAME"]

CANDIDATES_KEY = "movie-fetcher/candidates.json"
QUEUE_KEY = "movie-fetcher/download_queue.json"
PORT = int(os.environ.get("REQUEST_API_PORT", "5060"))

app = Flask(__name__)
CORS(app)

s3 = boto3.client(
    "s3",
    endpoint_url=f"https://{R2_ACCOUNT_ID}.r2.cloudflarestorage.com",
    aws_access_key_id=R2_ACCESS_KEY_ID,
    aws_secret_access_key=R2_SECRET_ACCESS_KEY,
    config=Config(signature_version="s3v4"),
    region_name="auto",
)


def load_json(key, default):
    try:
        obj = s3.get_object(Bucket=R2_BUCKET_NAME, Key=key)
        return json.loads(obj["Body"].read().decode("utf-8"))
    except s3.exceptions.NoSuchKey:
        return default
    except Exception:
        return default


def save_json(key, data):
    body = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
    s3.put_object(Bucket=R2_BUCKET_NAME, Key=key, Body=body, ContentType="application/json")


@app.route("/api/request-download", methods=["POST"])
def request_download():
    data = request.get_json(force=True, silent=True) or {}
    movie_id = data.get("id")
    if not movie_id:
        return jsonify({"error": "campo 'id' obrigatorio"}), 400

    candidates = load_json(CANDIDATES_KEY, [])
    movie = next((c for c in candidates if c["id"] == movie_id), None)
    if not movie:
        return jsonify({"error": "filme nao encontrado nos candidatos"}), 404

    queue = load_json(QUEUE_KEY, [])
    if any(q["id"] == movie_id for q in queue):
        return jsonify({"status": "ja_estava_na_fila"}), 200

    queue.append({
        "id": movie_id,
        "requested_at": datetime.now().isoformat(),
    })
    save_json(QUEUE_KEY, queue)

    for c in candidates:
        if c["id"] == movie_id and c["status"] == "pending":
            c["status"] = "queued"
    save_json(CANDIDATES_KEY, candidates)

    return jsonify({"status": "adicionado_a_fila", "queue_size": len(queue)}), 200


@app.route("/api/queue-status", methods=["GET"])
def queue_status():
    queue = load_json(QUEUE_KEY, [])
    return jsonify({"queue_size": len(queue), "queue": queue})


@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "service": "movie-fetcher-request-api"})


if __name__ == "__main__":
    print(f"Movie Fetcher Request API rodando na porta {PORT}")
    app.run(host="0.0.0.0", port=PORT)
