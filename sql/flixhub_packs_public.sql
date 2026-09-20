-- FlixHub: link publico estilo UnioFlix (sem token na URL)
ALTER TABLE public.flixhub_packs
  ADD COLUMN IF NOT EXISTS public_slug text;

ALTER TABLE public.flixhub_packs
  ADD COLUMN IF NOT EXISTS public_enabled boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX IF NOT EXISTS flixhub_packs_public_slug_uidx
  ON public.flixhub_packs (public_slug)
  WHERE public_slug IS NOT NULL;
