-- 2026-11 — Indique e ganhe + Retrospectiva do ano
-- Só ADITIVA: 3 tabelas novas + 1 trigger novo em watch_progress (não altera colunas nem apaga nada).
-- Backup antes de aplicar: /root/backups/referral-retro-<timestamp>/ (watch_progress, vip_status, favorites, title_comments).

-- ── Indique e ganhe ─────────────────────────────────────────────────────────
create table if not exists public.referral_codes (
  user_id    uuid primary key,
  code       text not null unique,
  created_at timestamptz not null default now()
);

create table if not exists public.referrals (
  referred_user_id uuid primary key,            -- 1 indicação por conta indicada (nunca 2)
  referrer_user_id uuid not null,
  code             text not null,
  status           text not null default 'pending'
                   check (status in ('pending', 'rewarded', 'ineligible')),
  reward_days      integer,
  rewarded_at      timestamptz,
  sale_id          text,
  note             text,
  created_at       timestamptz not null default now(),
  constraint referrals_no_self check (referred_user_id <> referrer_user_id)
);
create index if not exists referrals_referrer_idx on public.referrals (referrer_user_id);

-- Só o servidor (service role) lê/escreve: RLS ligado e sem policy para anon/authenticated.
alter table public.referral_codes enable row level security;
alter table public.referrals enable row level security;

-- ── Retrospectiva: histórico que não some ──────────────────────────────────
-- watch_progress apaga o item ao terminar (>=95%). watch_history guarda o que foi visto.
create table if not exists public.watch_history (
  user_id              uuid not null,
  tmdb_id              integer not null,
  media_type           text not null,
  season               integer not null default 0,
  episode              integer not null default 0,
  title                text,
  poster_path          text,
  max_position_seconds integer not null default 0,
  duration_seconds     integer,
  first_seen_at        timestamptz not null default now(),
  last_seen_at         timestamptz not null default now(),
  primary key (user_id, tmdb_id, media_type, season, episode)
);
create index if not exists watch_history_user_seen_idx on public.watch_history (user_id, last_seen_at);
alter table public.watch_history enable row level security;
do $$ begin
  if not exists (select 1 from pg_policies where tablename = 'watch_history' and policyname = 'watch_history_select_own') then
    create policy watch_history_select_own on public.watch_history for select using (auth.uid() = user_id);
  end if;
end $$;

create or replace function public.log_watch_history() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  begin
    insert into public.watch_history as h
      (user_id, tmdb_id, media_type, season, episode, title, poster_path,
       max_position_seconds, duration_seconds, first_seen_at, last_seen_at)
    values
      (new.user_id, new.tmdb_id, new.media_type, coalesce(new.season, 0), coalesce(new.episode, 0),
       new.title, new.poster_path, greatest(coalesce(new.position_seconds, 0), 0),
       nullif(new.duration_seconds, 0), now(), now())
    on conflict (user_id, tmdb_id, media_type, season, episode) do update set
      title                = coalesce(excluded.title, h.title),
      poster_path          = coalesce(excluded.poster_path, h.poster_path),
      max_position_seconds = greatest(h.max_position_seconds, excluded.max_position_seconds),
      duration_seconds     = coalesce(excluded.duration_seconds, h.duration_seconds),
      last_seen_at         = now();
  exception when others then
    -- nunca atrapalha salvar o progresso
    null;
  end;
  return new;
end $$;

do $$ begin
  if not exists (select 1 from pg_trigger where tgname = 'trg_watch_progress_history') then
    create trigger trg_watch_progress_history
      after insert or update on public.watch_progress
      for each row execute function public.log_watch_history();
  end if;
end $$;

-- Preenche com o que já existe hoje em watch_progress.
insert into public.watch_history
  (user_id, tmdb_id, media_type, season, episode, title, poster_path,
   max_position_seconds, duration_seconds, first_seen_at, last_seen_at)
select user_id, tmdb_id, media_type, season, episode, title, poster_path,
       greatest(position_seconds, 0), nullif(duration_seconds, 0), updated_at, updated_at
from public.watch_progress
on conflict do nothing;
