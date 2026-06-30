-- Add overtime reminders feature flag to user_settings (Android sync)

do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'user_settings'
      and column_name = 'features_overtime_reminders'
  ) then
    alter table public.user_settings
      add column features_overtime_reminders boolean not null default true;
  end if;
end $$;

notify pgrst, 'reload schema';
