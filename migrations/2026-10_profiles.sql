-- =====================================================================
-- 2026-10 · Perfis ("Quem está assistindo?")
-- AINDA NÃO APLICADA — aguarda aprovação.
-- O app já funciona sem esta tabela (perfis ficam só no aparelho);
-- depois de aplicada, os perfis passam a acompanhar a conta em outros celulares.
-- Só CRIA coisas novas: não altera nem apaga nenhuma tabela existente.
-- =====================================================================
begin;

create table if not exists public.user_profiles (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  -- lista de perfis: [{id, name, avatar, kids, pin(sha-256)}], no máx. 5
  profiles   jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now(),
  constraint user_profiles_max5 check (jsonb_typeof(profiles) = 'array' and jsonb_array_length(profiles) <= 5)
);

alter table public.user_profiles enable row level security;

drop policy if exists user_profiles_owner on public.user_profiles;
create policy user_profiles_owner on public.user_profiles
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create or replace function public.touch_user_profiles() returns trigger
language plpgsql as $$ begin new.updated_at := now(); return new; end $$;

drop trigger if exists trg_touch_user_profiles on public.user_profiles;
create trigger trg_touch_user_profiles before update on public.user_profiles
  for each row execute function public.touch_user_profiles();

commit;

-- Desfazer (rollback):
--   drop table if exists public.user_profiles;
--   drop function if exists public.touch_user_profiles();
