-- Rode no Supabase SQL Editor (uma vez)
create table if not exists public.vip_source_blocks (
  id uuid primary key default gen_random_uuid(),
  tmdb_id integer not null,
  media_type text not null check (media_type in ('movie','tv')),
  season integer null,
  episode integer null,
  source_label text null,
  source_url text null,
  created_at timestamptz not null default now()
);

create index if not exists vip_source_blocks_tmdb_idx
  on public.vip_source_blocks (tmdb_id, media_type);

alter table public.vip_source_blocks enable row level security;
