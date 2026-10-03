#!/usr/bin/env python3
"""Scan live_tv_sources no VPS (usa env SUPABASE_*). Sem JWT admin."""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_env():
    env = dict(os.environ)
    for name in (".env", ".env.local", "ecosystem.config.js"):
        p = ROOT / name
        if not p.exists():
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "SUPABASE" in line and ("=" in line or ":" in line):
                # .env KEY=VAL
                if "=" in line and not line.strip().startswith("//"):
                    k, _, v = line.partition("=")
                    k = k.strip().replace("process.env.", "")
                    v = v.strip().strip("'\"",).rstrip(",")
                    if k and v and k not in env:
                        env[k] = v
    return env


def main():
    env = load_env()
    # pm2 env
    try:
        import subprocess

        out = subprocess.check_output(
            ["pm2", "env", "0"], stderr=subprocess.DEVNULL, text=True, timeout=10
        )
        for line in out.splitlines():
            if "SUPABASE" in line and "=" in line:
                k, _, v = line.partition("=")
                env[k.strip()] = v.strip()
    except Exception:
        pass

    url = (
        env.get("SUPABASE_URL")
        or env.get("NEXT_PUBLIC_SUPABASE_URL")
        or "https://gkujbjpvphuvrejpvvtz.supabase.co"
    ).rstrip("/")
    key = (
        env.get("SUPABASE_SERVICE_ROLE_KEY")
        or env.get("SUPABASE_SERVICE_KEY")
        or env.get("SUPABASE_KEY")
        or ""
    )
    if not key:
        # try from process via node
        try:
            import subprocess

            js = "console.log(process.env.SUPABASE_SERVICE_ROLE_KEY||process.env.SUPABASE_SERVICE_KEY||'')"
            key = subprocess.check_output(
                ["node", "-e", js], cwd=str(ROOT), text=True, timeout=5
            ).strip()
        except Exception:
            key = ""

    if not key:
        print("ERRO: SUPABASE_SERVICE_ROLE_KEY nao encontrada no env do VPS")
        print("Tente: pm2 show streamflix-api | grep -i supabase")
        sys.exit(1)

    headers = {
        "apikey": key,
        "Authorization": "Bearer " + key,
        "Content-Type": "application/json",
    }
    q = (
        url
        + "/rest/v1/live_tv_sources?select=id,name,xtream_host,xtream_user,xtream_pass,priority,is_active"
        + "&order=priority.asc.nullslast"
    )
    req = urllib.request.Request(q, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            rows = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        print("ERRO Supabase", e.code, e.read()[:300].decode(errors="ignore"))
        sys.exit(1)

    if not isinstance(rows, list):
        print("Resposta inesperada", rows)
        sys.exit(1)

    print(f"Fontes em live_tv_sources: {len(rows)}")
    print("-" * 60)
    alive = 0
    for s in rows:
        name = s.get("name") or "?"
        active = s.get("is_active")
        host = (s.get("xtream_host") or "").rstrip("/")
        user = s.get("xtream_user") or ""
        pw = s.get("xtream_pass") or ""
        pri = s.get("priority")
        status = {
            "name": name,
            "priority": pri,
            "active": active,
            "host": host[:50],
            "ok": False,
            "cats": 0,
            "streams": 0,
            "error": None,
            "ms": 0,
        }
        if not active:
            status["error"] = "inativa"
            print(f"[{name}] INATIVA priority={pri}")
            continue
        if not host or not user or not pw:
            status["error"] = "sem credenciais"
            print(f"[{name}] SEM CREDENCIAIS")
            continue

        def api(action):
            u = (
                host
                + "/player_api.php?"
                + urllib.parse.urlencode(
                    {"username": user, "password": pw, "action": action}
                )
            )
            rq = urllib.request.Request(
                u,
                headers={
                    "User-Agent": "IPTVSmarters/1.0",
                    "Accept": "application/json",
                },
            )
            with urllib.request.urlopen(rq, timeout=14) as resp:
                return json.loads(resp.read().decode())

        t0 = time.time()
        try:
            cats = api("get_live_categories")
            streams = api("get_live_streams")
            status["ms"] = int((time.time() - t0) * 1000)
            status["cats"] = len(cats) if isinstance(cats, list) else 0
            status["streams"] = len(streams) if isinstance(streams, list) else 0
            status["ok"] = status["streams"] > 0
            if status["ok"]:
                alive += 1
                print(
                    f"[{name}] OK  streams={status['streams']} cats={status['cats']} "
                    f"{status['ms']}ms priority={pri}"
                )
            else:
                status["error"] = "0 streams"
                print(f"[{name}] 0 STREAMS {status['ms']}ms (login talvez ok)")
        except Exception as e:
            status["ms"] = int((time.time() - t0) * 1000)
            status["error"] = str(e)[:120]
            print(f"[{name}] FALHOU {status['error']} ({status['ms']}ms)")

    print("-" * 60)
    print(f"VIVAS: {alive} / {len(rows)}")
    if alive == 0:
        print("TIP: Nenhuma Xtream de TV respondeu. Renove painel na aba TV ao vivo.")
    elif alive == 1:
        print("TIP: So 1 fonte viva — sem fallback. Cadastre +1 ou +2 paineis de pe.")
    else:
        print("TIP: OK para fallback. Desative no painel as que falharam.")


if __name__ == "__main__":
    main()
