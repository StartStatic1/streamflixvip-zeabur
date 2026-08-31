-- Historias curtas verticais (micro-drama)
-- Rode no SQL Editor do Supabase uma vez.

CREATE TABLE IF NOT EXISTS public.reel_stories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title text NOT NULL,
  subtitle text,
  poster_url text,
  genre text,
  language text DEFAULT 'pt-BR',
  is_active boolean NOT NULL DEFAULT true,
  vip_only boolean NOT NULL DEFAULT true,
  use_addons boolean NOT NULL DEFAULT true,
  sort_order integer NOT NULL DEFAULT 0,
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.reel_episodes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  story_id uuid NOT NULL REFERENCES public.reel_stories(id) ON DELETE CASCADE,
  episode integer NOT NULL,
  title text,
  video_url text NOT NULL DEFAULT '',
  duration_seconds integer,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (story_id, episode)
);

CREATE INDEX IF NOT EXISTS reel_stories_active_idx
  ON public.reel_stories (is_active, sort_order DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS reel_episodes_story_idx
  ON public.reel_episodes (story_id, episode);

ALTER TABLE public.reel_stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_episodes ENABLE ROW LEVEL SECURITY;

-- App e painel usam service role na API. Sem policy publica de escrita.
