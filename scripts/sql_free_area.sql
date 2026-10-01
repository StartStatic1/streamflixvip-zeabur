-- Area Free: rode no SQL Editor do Supabase (uma vez)
alter table public.vip_titles
  add column if not exists is_free boolean not null default false;

create index if not exists vip_titles_is_free_idx
  on public.vip_titles (is_free)
  where is_free = true;
