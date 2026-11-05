-- Aviso "pode exigir VPN" por fonte (pontes Xtream e TV ao vivo).
-- Ausente / false = comportamento antigo. Rode uma vez no SQL Editor.

ALTER TABLE public.iptv_bridges
  ADD COLUMN IF NOT EXISTS requires_vpn boolean NOT NULL DEFAULT false;

ALTER TABLE public.live_tv_sources
  ADD COLUMN IF NOT EXISTS requires_vpn boolean NOT NULL DEFAULT false;
