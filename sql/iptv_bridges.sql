-- Aba Bridge (Xtream → add-on). Rode uma vez no SQL Editor.

CREATE TABLE IF NOT EXISTS public.iptv_bridges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  xtream_host text NOT NULL,
  xtream_user text NOT NULL,
  xtream_pass text NOT NULL,
  use_live boolean NOT NULL DEFAULT false,
  use_movies boolean NOT NULL DEFAULT true,
  use_series boolean NOT NULL DEFAULT true,
  live_cats jsonb NOT NULL DEFAULT '[]'::jsonb,
  vod_cats jsonb NOT NULL DEFAULT '[]'::jsonb,
  series_cats jsonb NOT NULL DEFAULT '[]'::jsonb,
  is_active boolean NOT NULL DEFAULT true,
  addon_id uuid,
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS iptv_bridges_active_idx
  ON public.iptv_bridges (is_active);

ALTER TABLE public.iptv_bridges ENABLE ROW LEVEL SECURITY;
