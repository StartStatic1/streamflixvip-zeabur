-- Avisos do app (filme novo, manutenção, promo)
-- Rode UMA vez no SQL Editor do Supabase.

create table if not exists app_announcements (
  id uuid primary key default gen_random_uuid(),
  type text not null default 'info',
  title text not null,
  body text not null,
  active boolean not null default true,
  link_tmdb_id integer,
  link_media_type text,
  created_at timestamptz not null default now()
);

alter table app_announcements enable row level security;

drop policy if exists "announcements_public_read" on app_announcements;
create policy "announcements_public_read"
  on app_announcements for select
  using (active = true);

-- Escrita: apenas service_role (painel admin via SUPABASE_SERVICE_ROLE_KEY)
