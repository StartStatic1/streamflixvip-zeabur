#!/usr/bin/env python3
"""Lista iptv_bridges com use_live e testa Xtream live (VPS)."""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def env_key():
    env = dict(os.environ)
    try:
        import subprocess

        out = subprocess.check_output(["pm2", "env", "0"], text=True, timeout=10, stderr=subprocess.DEVNULL)
        for line in out.splitlines():
            if "SUPABASE" in line and "=" in line:
                k, _, v = line.partition("=")
                env[k.strip()] = v.strip()
    except Exception:
        pass
    url = env.get("SUPABASE_URL") or "https://gkujbjpvphuvrejpvvtz.supabase.co"
    key = (
        env.get("SUPABASE_SERVICE_ROLE_KEY")
        or env.get("SUPABASE_SERVICE_KEY")
        or ""
    )
    if not key:
        try:
            import subprocess

            key = subprocess.check_output(
                ["node", "-e", "console.log(process.env.SUPABASE_SERVICE_ROLE_KEY||'')"],
                cwd=str(ROOT),
                text=True,
                timeout=5,
            ).strip()
        except Exception:
            pass
    return url.rstrip("/"), key


def main():
    url, key = env_key()
    if not key:
        print("ERRO: sem SUPABASE_SERVICE_ROLE_KEY")
        sys.exit(1)
    headers = {"apikey": key, "Authorization": "Bearer " + key}
    q = (
        url
        + "/rest/v1/iptv_bridges?select=id,name,xtream_host,xtream_user,xtream_pass,use_live,is_active,live_cats"
        + "&order=created_at.desc"
    )
    req = urllib.request.Request(q, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as r:
        rows = json.loads(r.read().decode())
    if not isinstance(rows, list):
        print(rows)
        sys.exit(1)

    print(f"Bridges total: {len(rows)}")
    live_on = [b for b in rows if b.get("use_live") and b.get("is_active")]
    print(f"use_live + ativa: {len(live_on)}")
    print("-" * 60)
    ok_n = 0
    for b in live_on:
        name = b.get("name") or "?"
        host = (b.get("xtream_host") or "").rstrip("/")
        user = b.get("xtream_user") or ""
        pw = b.get("xtream_pass") or ""
        cats = b.get("live_cats") or []
        ncat = len(cats) if isinstance(cats, list) else 0
        if not host or not user or not pw:
            print(f"[{name}] SEM CREDENCIAIS (live_cats={ncat})")
            continue
        t0 = time.time()
        try:
            u = (
                host
                + "/player_api.php?"
                + urllib.parse.urlencode(
                    {"username": user, "password": pw, "action": "get_live_streams"}
                )
            )
            rq = urllib.request.Request(
                u, headers={"User-Agent": "IPTVSmarters/1.0", "Accept": "application/json"}
            )
            with urllib.request.urlopen(rq, timeout=14) as resp:
                streams = json.loads(resp.read().decode())
            n = len(streams) if isinstance(streams, list) else 0
            ms = int((time.time() - t0) * 1000)
            if n > 0:
                ok_n += 1
                print(f"[{name}] OK streams={n} live_cats={ncat} {ms}ms  host={host[:40]}")
            else:
                print(f"[{name}] 0 streams live_cats={ncat} {ms}ms")
        except Exception as e:
            ms = int((time.time() - t0) * 1000)
            print(f"[{name}] FALHOU {e} ({ms}ms)")
    print("-" * 60)
    print(f"Bridges live OK: {ok_n}/{len(live_on)}")
    print("TIP: so entram na TV do app se use_live+ativa e Xtream responder.")
    print("Para garantir: copie host/user/pass das OK em Painel > TV ao vivo (live_tv_sources).")


if __name__ == "__main__":
    main()
