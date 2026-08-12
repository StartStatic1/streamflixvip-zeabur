-- Rodar no Supabase → SQL Editor (uma vez)
create table if not exists public.live_tv_manual_channels (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  logo text,
  group_title text default 'Manuais',
  stream_url text not null,
  priority int default 1,
  is_active boolean default true,
  created_at timestamptz default now()
);

create index if not exists live_tv_manual_channels_active_idx
  on public.live_tv_manual_channels (is_active, priority);

-- service role já ignora RLS; se usar anon, habilite policies depois
alter table public.live_tv_manual_channels enable row level security;
