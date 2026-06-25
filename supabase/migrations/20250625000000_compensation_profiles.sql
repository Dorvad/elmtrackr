-- ============================================================
-- Compensation globalization migration (idempotent)
-- Run in Supabase Dashboard → SQL Editor if you see:
--   "Could not find the table 'public.compensation_profiles' in the schema cache"
-- ============================================================

-- user_settings columns
do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'region_code'
  ) then
    alter table public.user_settings add column region_code text default null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'currency_code'
  ) then
    alter table public.user_settings add column currency_code text default null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'default_compensation_profile_id'
  ) then
    alter table public.user_settings add column default_compensation_profile_id uuid default null;
  end if;
end $$;

-- shifts columns
do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'compensation_profile_id'
  ) then
    alter table public.shifts add column compensation_profile_id uuid default null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'compensation_snapshot_json'
  ) then
    alter table public.shifts add column compensation_snapshot_json jsonb default null;
  end if;
end $$;

-- compensation_profiles table
create table if not exists public.compensation_profiles (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references auth.users(id) on delete cascade,
  name              text not null,
  region_code       text not null,
  currency_code     text not null,
  timezone          text not null,
  base_hourly_rate  numeric(10, 2) default null,
  rules_json        jsonb not null,
  stacking_policy   text not null default 'highest_only'
                    check (stacking_policy in ('highest_only', 'additive')),
  effective_from    timestamptz not null default now(),
  effective_until   timestamptz,
  is_default        boolean not null default false,
  is_archived       boolean not null default false,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

create index if not exists compensation_profiles_user_id_idx
  on public.compensation_profiles (user_id);

-- updated_at trigger (reuses handle_updated_at if present)
create or replace function public.handle_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists compensation_profiles_updated_at on public.compensation_profiles;
create trigger compensation_profiles_updated_at
  before update on public.compensation_profiles
  for each row execute function public.handle_updated_at();

-- foreign keys (added after compensation_profiles exists)
do $$
begin
  if not exists (
    select 1 from information_schema.table_constraints
    where constraint_name = 'user_settings_default_compensation_profile_id_fkey'
  ) then
    alter table public.user_settings
      add constraint user_settings_default_compensation_profile_id_fkey
      foreign key (default_compensation_profile_id)
      references public.compensation_profiles(id) on delete set null;
  end if;
exception when others then null;
end $$;

do $$
begin
  if not exists (
    select 1 from information_schema.table_constraints
    where constraint_name = 'shifts_compensation_profile_id_fkey'
  ) then
    alter table public.shifts
      add constraint shifts_compensation_profile_id_fkey
      foreign key (compensation_profile_id)
      references public.compensation_profiles(id) on delete set null;
  end if;
exception when others then null;
end $$;

-- RLS
alter table public.compensation_profiles enable row level security;

drop policy if exists "compensation_profiles_select_own" on public.compensation_profiles;
create policy "compensation_profiles_select_own" on public.compensation_profiles
  for select using (auth.uid() = user_id);

drop policy if exists "compensation_profiles_insert_own" on public.compensation_profiles;
create policy "compensation_profiles_insert_own" on public.compensation_profiles
  for insert with check (auth.uid() = user_id);

drop policy if exists "compensation_profiles_update_own" on public.compensation_profiles;
create policy "compensation_profiles_update_own" on public.compensation_profiles
  for update using (auth.uid() = user_id);

drop policy if exists "compensation_profiles_delete_own" on public.compensation_profiles;
create policy "compensation_profiles_delete_own" on public.compensation_profiles
  for delete using (auth.uid() = user_id);

-- Notify PostgREST to reload schema cache (Supabase hosted projects)
notify pgrst, 'reload schema';
