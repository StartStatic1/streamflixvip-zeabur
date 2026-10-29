-- =====================================================================
-- 2026-10 · Comentários: moderação, denúncias, avatar e anti-flood
-- AINDA NÃO APLICADA — aguarda aprovação.
-- O app novo já funciona sem isto (cai na consulta antiga). Depois de aplicada:
--   * comentários com is_hidden = true somem para todos (moderação);
--   * "Denunciar" grava em comment_reports;
--   * o avatar do perfil aparece ao lado do nome;
--   * o servidor bloqueia flood (1 comentário a cada 10 s e 30 por hora por usuário).
-- Só ADICIONA colunas com valor padrão — nenhum comentário existente é alterado ou apagado.
-- =====================================================================
begin;

alter table public.title_comments add column if not exists is_hidden     boolean not null default false;
alter table public.title_comments add column if not exists author_avatar text;
create index if not exists title_comments_title_idx
  on public.title_comments (tmdb_id, media_type, created_at desc) where not is_hidden;

create table if not exists public.comment_reports (
  id          bigserial primary key,
  comment_id  bigint not null references public.title_comments(id) on delete cascade,
  reporter_id uuid   not null references auth.users(id) on delete cascade,
  reason      text   not null default 'app' check (char_length(reason) <= 200),
  created_at  timestamptz not null default now(),
  unique (comment_id, reporter_id)
);
alter table public.comment_reports enable row level security;
drop policy if exists comment_reports_insert_own on public.comment_reports;
create policy comment_reports_insert_own on public.comment_reports
  for insert with check (auth.uid() = reporter_id);
drop policy if exists comment_reports_admin_read on public.comment_reports;
create policy comment_reports_admin_read on public.comment_reports
  for select using ((auth.jwt() ->> 'email') = 'xfdapx@gmail.com');

-- 3 denúncias de pessoas diferentes escondem o comentário automaticamente
create or replace function public.auto_hide_reported() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.comment_reports where comment_id = new.comment_id) >= 3 then
    update public.title_comments set is_hidden = true where id = new.comment_id;
  end if;
  return new;
end $$;
drop trigger if exists trg_auto_hide_reported on public.comment_reports;
create trigger trg_auto_hide_reported after insert on public.comment_reports
  for each row execute function public.auto_hide_reported();

-- anti-flood
create or replace function public.comments_rate_limit() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from public.title_comments
             where user_id = new.user_id and created_at > now() - interval '10 seconds') then
    raise exception 'Aguarde alguns segundos para comentar de novo' using errcode = 'P0001';
  end if;
  if (select count(*) from public.title_comments
      where user_id = new.user_id and created_at > now() - interval '1 hour') >= 30 then
    raise exception 'Limite de comentários por hora atingido' using errcode = 'P0001';
  end if;
  new.is_hidden := false;  -- ninguém publica já escondido/forjado
  return new;
end $$;
drop trigger if exists trg_comments_rate_limit on public.title_comments;
create trigger trg_comments_rate_limit before insert on public.title_comments
  for each row execute function public.comments_rate_limit();

commit;

-- Desfazer (rollback):
--   drop trigger if exists trg_comments_rate_limit on public.title_comments;
--   drop function if exists public.comments_rate_limit();
--   drop table if exists public.comment_reports;          -- (apaga só as denúncias)
--   drop function if exists public.auto_hide_reported();
--   drop index if exists title_comments_title_idx;
--   alter table public.title_comments drop column if exists author_avatar, drop column if exists is_hidden;
