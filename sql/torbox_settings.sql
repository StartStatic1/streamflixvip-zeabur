-- TorBox: 1 linha de config. A chave NUNCA vai no manifesto nem no app.
CREATE TABLE IF NOT EXISTS public.torbox_settings (
  id int PRIMARY KEY DEFAULT 1,
  api_key text,
  is_enabled boolean NOT NULL DEFAULT false,
  last_ok_at timestamptz,
  last_error text,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT torbox_settings_single CHECK (id = 1)
);

INSERT INTO public.torbox_settings (id, is_enabled)
VALUES (1, false)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.torbox_settings ENABLE ROW LEVEL SECURITY;
