-- ============================================================
-- StreamFlix — fontes de TV ao vivo SEPARADAS do VOD + limpeza
-- Rode UMA VEZ no SQL Editor do Supabase (role postgres).
-- ============================================================

CREATE TABLE IF NOT EXISTS public.live_tv_sources (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  xtream_host text NOT NULL,
  xtream_user text NOT NULL,
  xtream_pass text NOT NULL,
  priority int DEFAULT 10,
  is_active boolean DEFAULT true,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_live_tv_sources_name
  ON public.live_tv_sources (name);

ALTER TABLE public.live_tv_sources ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.enforce_max_sources_per_episode(max_count int DEFAULT 2)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  deleted_count bigint := 0;
BEGIN
  IF max_count IS NULL OR max_count < 1 THEN
    max_count := 2;
  END IF;

  WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
             PARTITION BY tmdb_id, media_type,
                          COALESCE(season, -1),
                          COALESCE(episode, -1)
             ORDER BY priority ASC NULLS LAST, created_at DESC NULLS LAST, id
           ) AS rn
    FROM vip_sources
    WHERE is_active IS DISTINCT FROM false
  ),
  doomed AS (
    SELECT id FROM ranked WHERE rn > max_count
  )
  DELETE FROM vip_sources vs
  USING doomed d
  WHERE vs.id = d.id;

  GET DIAGNOSTICS deleted_count = ROW_COUNT;
  RETURN jsonb_build_object('deleted', deleted_count, 'max_count', max_count);
END;
$$;

GRANT EXECUTE ON FUNCTION public.enforce_max_sources_per_episode(int) TO service_role;
GRANT EXECUTE ON FUNCTION public.enforce_max_sources_per_episode(int) TO postgres;

DELETE FROM public.iptv_unmatched_items
WHERE created_at < now() - interval '14 days';

INSERT INTO public.live_tv_sources (name, xtream_host, xtream_user, xtream_pass, priority, is_active)
SELECT s.name, rtrim(s.xtream_host, '/'), s.xtream_user, s.xtream_pass, COALESCE(s.priority, 10), COALESCE(s.is_active, true)
FROM public.iptv_sources s
WHERE s.xtream_host IS NOT NULL AND s.xtream_user IS NOT NULL AND s.xtream_pass IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM public.live_tv_sources LIMIT 1)
ON CONFLICT (name) DO NOTHING;

SELECT public.enforce_max_sources_per_episode(2);
