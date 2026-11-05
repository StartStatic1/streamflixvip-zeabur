# Xtream list relay (Cloudflare Worker)

Mesmo Worker do Hugo (`hugo-list.xfdapx.workers.dev`): só `player_api.php` / `get.php` / `xmltv.php`.

Usado pelo Streamflix quando o painel Xtream devolve **403** ao IP do VPS Hetzner.

- Env no VPS: `XTREAM_LIST_RELAY_URL` + `XTREAM_LIST_RELAY_KEY` (pode reutilizar `HUGO_RELAY_*`).
- Se o painel também bloquear Cloudflare (ex.: Draxix), configure `XTREAM_EGRESS_PROXY` (proxy residencial) ou use o atalho “neste aparelho” no Bridge.
