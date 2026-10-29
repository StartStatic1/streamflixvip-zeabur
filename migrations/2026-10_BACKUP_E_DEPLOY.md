# Migrations de 2026-10 — backup e como aplicar

Nada disto foi aplicado. Ordem sugerida (cada arquivo é uma transação: ou aplica tudo, ou nada):

1. **Backup antes** (Supabase → Database → Backups → confirmar o backup diário de hoje), e um
   export rápido das tabelas tocadas:
   ```sql
   create table if not exists backup_20261029_title_comments as table public.title_comments;
   ```
2. Aplicar `2026-10_profiles.sql` (só cria tabela nova `user_profiles`).
3. Aplicar `2026-10_comments.sql` (adiciona 2 colunas com padrão, tabela `comment_reports`, 2 gatilhos).
4. Conferir:
   ```sql
   select count(*) from public.title_comments where is_hidden;  -- deve ser 0
   select * from public.user_profiles limit 1;
   ```
5. Se algo der errado: os blocos "Desfazer" no fim de cada arquivo revertem; os apps continuam
   funcionando nos dois cenários (com ou sem as migrations).

Os apps (celular e TV) detectam sozinhos se as tabelas/colunas existem; não é preciso
publicar nova versão depois de aplicar.
