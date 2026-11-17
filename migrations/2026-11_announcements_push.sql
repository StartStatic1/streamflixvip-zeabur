-- Avisos do app: marcar um aviso para também virar notificação no celular.
-- Só adiciona coluna (aditivo). O app consulta /api/notify-feed, que lê push=true.
alter table app_announcements add column if not exists push boolean not null default false;
create index if not exists app_announcements_push_idx on app_announcements (created_at desc) where active and push;
