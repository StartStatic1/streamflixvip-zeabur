-- FlixHub: 1 pack = 1 add-on Nuvio com N servidores Xtream
CREATE TABLE IF NOT EXISTS public.flixhub_packs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL DEFAULT 'FlixHub',
  access_token text NOT NULL,
  is_active boolean NOT NULL DEFAULT true,
  servers jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS flixhub_packs_active_idx ON public.flixhub_packs (is_active);

ALTER TABLE public.flixhub_packs ENABLE ROW LEVEL SECURITY;
