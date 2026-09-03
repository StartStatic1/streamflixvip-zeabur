-- Token das pontes (igual API parceiro). Rode uma vez.

ALTER TABLE public.iptv_bridges
  ADD COLUMN IF NOT EXISTS access_token text;

UPDATE public.iptv_bridges
SET access_token = encode(gen_random_bytes(24), 'hex')
WHERE access_token IS NULL OR access_token = '';
