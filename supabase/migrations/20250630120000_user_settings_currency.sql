-- Add legacy currency column for Android user_settings sync (idempotent)

do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'currency'
  ) then
    alter table public.user_settings add column currency text not null default 'ILS';
  end if;
end $$;

notify pgrst, 'reload schema';
