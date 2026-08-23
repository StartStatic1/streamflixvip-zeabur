-- API de parceiros StreamFlixVIP
-- Rode no Supabase SQL Editor uma vez.

CREATE TABLE IF NOT EXISTS public.api_partners (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  contact_email text,
  notes text,
  -- Prefixo legível: sf_live_xxxxx (só o prefixo fica visível; o segredo é hasheado)
  key_prefix text NOT NULL,
  key_hash text NOT NULL,
  is_active boolean NOT NULL DEFAULT true,
  -- Escopos: sources (fontes filme/série), livetv (canais ao vivo), catalog (lista tmdb_id com fonte)
  scopes text[] NOT NULL DEFAULT ARRAY['sources']::text[],
  rate_limit_per_min integer NOT NULL DEFAULT 60,
  request_count bigint NOT NULL DEFAULT 0,
  last_used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS api_partners_key_prefix_uidx ON public.api_partners (key_prefix);
CREATE INDEX IF NOT EXISTS api_partners_active_idx ON public.api_partners (is_active);

-- Log simples de uso (opcional, para auditoria)
CREATE TABLE IF NOT EXISTS public.api_partner_usage (
  id bigserial PRIMARY KEY,
  partner_id uuid REFERENCES public.api_partners(id) ON DELETE CASCADE,
  path text,
  status_code integer,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS api_partner_usage_partner_idx
  ON public.api_partner_usage (partner_id, created_at DESC);

-- Service role da API Node acessa tudo; sem políticas abertas para anon.
ALTER TABLE public.api_partners ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.api_partner_usage ENABLE ROW LEVEL SECURITY;
