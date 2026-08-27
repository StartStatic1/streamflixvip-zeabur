-- Add-ons estilo Stremio/Nuvio
-- Rode no Supabase SQL Editor uma vez.

CREATE TABLE IF NOT EXISTS public.stremio_addons (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  -- URL do manifest.json (ex: https://catalog.nuvio.tv/manifest.json)
  manifest_url text NOT NULL,
  -- Base resolvida a partir do manifest (sem /manifest.json)
  base_url text,
  is_active boolean NOT NULL DEFAULT true,
  -- prioridade relativa entre add-ons (maior = primeiro)
  priority integer NOT NULL DEFAULT 0,
  notes text,
  last_ok_at timestamptz,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS stremio_addons_manifest_uidx
  ON public.stremio_addons (manifest_url);
CREATE INDEX IF NOT EXISTS stremio_addons_active_idx
  ON public.stremio_addons (is_active, priority DESC);

ALTER TABLE public.stremio_addons ENABLE ROW LEVEL SECURITY;
