-- Contador de joinha e respostas em comentários
alter table title_comments
  add column if not exists up_count int not null default 0,
  add column if not exists down_count int not null default 0,
  add column if not exists parent_id bigint references title_comments(id) on delete cascade;

create table if not exists comment_votes (
  comment_id bigint not null references title_comments(id) on delete cascade,
  user_id uuid not null,
  value int not null check (value in (1, -1)),
  primary key (comment_id, user_id)
);

alter table comment_votes enable row level security;

create policy "votes select" on comment_votes for select using (true);
create policy "votes insert own" on comment_votes for insert with check (auth.uid() = user_id);
create policy "votes update own" on comment_votes for update using (auth.uid() = user_id);

-- Função para votar (incrementa/decrementa contador com segurança)
create or replace function vote_comment(p_comment_id bigint, p_value int)
returns void
language plpgsql
security definer
as $$
declare
  uid uuid := auth.uid();
  old_val int;
begin
  if uid is null then raise exception 'auth required'; end if;
  if p_value not in (1, -1) then raise exception 'invalid value'; end if;

  select value into old_val from comment_votes where comment_id = p_comment_id and user_id = uid;

  if old_val is null then
    insert into comment_votes(comment_id, user_id, value) values (p_comment_id, uid, p_value);
    if p_value = 1 then
      update title_comments set up_count = up_count + 1 where id = p_comment_id;
    else
      update title_comments set down_count = down_count + 1 where id = p_comment_id;
    end if;
  elsif old_val = p_value then
    -- remove vote
    delete from comment_votes where comment_id = p_comment_id and user_id = uid;
    if p_value = 1 then
      update title_comments set up_count = greatest(up_count - 1, 0) where id = p_comment_id;
    else
      update title_comments set down_count = greatest(down_count - 1, 0) where id = p_comment_id;
    end if;
  else
    -- switch vote
    update comment_votes set value = p_value where comment_id = p_comment_id and user_id = uid;
    if p_value = 1 then
      update title_comments set up_count = up_count + 1, down_count = greatest(down_count - 1, 0) where id = p_comment_id;
    else
      update title_comments set down_count = down_count + 1, up_count = greatest(up_count - 1, 0) where id = p_comment_id;
    end if;
  end if;
end;
$$;
