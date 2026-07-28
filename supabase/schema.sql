-- ============================================================
-- ElmTrackr Schema  (idempotent — safe to re-run at any time)
-- Run this in your Supabase SQL editor (Dashboard > SQL Editor)
--
-- If you only need compensation globalization, you can instead run:
--   supabase/migrations/20250625000000_compensation_profiles.sql
-- ============================================================

-- ── Tables ────────────────────────────────────────────────────

-- Profiles: mirrors auth.users, extended with display info
create table if not exists public.profiles (
  id         uuid primary key references auth.users(id) on delete cascade,
  email      text not null,
  full_name  text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- User settings: per-user overtime + weekend + payroll config
create table if not exists public.user_settings (
  id                                 uuid primary key default gen_random_uuid(),
  user_id                            uuid not null unique references auth.users(id) on delete cascade,
  timezone                           text not null default 'UTC',
  daily_overtime_threshold_minutes   integer not null default 480,
  weekly_overtime_threshold_minutes  integer not null default 2400,
  weekend_days                       integer[] not null default '{5,6}',
  hourly_rate                        numeric(10, 2) default null,
  currency                           text not null default 'ILS',
  region_code                        text default null,
  currency_code                      text default null,
  default_compensation_profile_id    uuid default null,
  created_at                         timestamptz not null default now(),
  updated_at                         timestamptz not null default now()
);

-- Shifts: the core time-tracking record
create table if not exists public.shifts (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  start_time     timestamptz not null,
  end_time       timestamptz,
  break_minutes  integer not null default 0 check (break_minutes >= 0),
  notes          text,
  is_special_day boolean not null default false,
  compensation_profile_id uuid default null,
  compensation_snapshot_json jsonb default null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint valid_time_range check (end_time is null or end_time > start_time)
);

-- ── Columns added after initial deploy (idempotent) ───────────

do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'hourly_rate'
  ) then
    alter table public.user_settings add column hourly_rate numeric(10, 2) default null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'is_special_day'
  ) then
    alter table public.shifts add column is_special_day boolean not null default false;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'refund_action'
  ) then
    alter table public.shifts add column refund_action text check (
      refund_action in ('no_ride_taken', 'remind_later', 'submitted')
    ) default null;
  end if;

  -- Onboarding state
  -- DEFAULT true so existing users are treated as already onboarded.
  -- New users get false via the handle_new_user trigger below.
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'onboarding_completed'
  ) then
    alter table public.user_settings add column onboarding_completed boolean not null default true;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'onboarding_completed_at'
  ) then
    alter table public.user_settings add column onboarding_completed_at timestamptz default null;
  end if;

  -- Feature flags
  -- Existing users: travel_refunds=true (they may have existing refund data),
  -- insights=true, clock_styles=true, paid_projects=false.
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'features_travel_refunds'
  ) then
    alter table public.user_settings add column features_travel_refunds boolean not null default true;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'features_paid_projects'
  ) then
    alter table public.user_settings add column features_paid_projects boolean not null default false;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'features_insights'
  ) then
    alter table public.user_settings add column features_insights boolean not null default true;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'features_clock_styles'
  ) then
    alter table public.user_settings add column features_clock_styles boolean not null default true;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'clock_style'
  ) then
    alter table public.user_settings add column clock_style text not null default 'classic';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'features_overtime_reminders'
  ) then
    alter table public.user_settings add column features_overtime_reminders boolean not null default true;
  end if;
end
$$;

-- ── Indexes ───────────────────────────────────────────────────

create index if not exists shifts_user_id_start_time_idx
  on public.shifts (user_id, start_time desc);

create unique index if not exists shifts_user_id_start_time_uidx
  on public.shifts (user_id, start_time);

create index if not exists shifts_active_idx
  on public.shifts (user_id, end_time)
  where end_time is null;

-- ── Functions & Triggers ──────────────────────────────────────

create or replace function public.handle_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create or replace trigger profiles_updated_at
  before update on public.profiles
  for each row execute function public.handle_updated_at();

create or replace trigger user_settings_updated_at
  before update on public.user_settings
  for each row execute function public.handle_updated_at();

create or replace trigger shifts_updated_at
  before update on public.shifts
  for each row execute function public.handle_updated_at();

-- Refund claims: up to one receipt per direction (to_work / from_work) per shift
create table if not exists public.refund_claims (
  id            uuid primary key default gen_random_uuid(),
  shift_id      uuid not null references public.shifts(id) on delete cascade,
  user_id       uuid not null references auth.users(id) on delete cascade,
  direction     text not null default 'from_work' check (direction in ('to_work', 'from_work')),
  provider      text not null,
  amount        numeric(10, 2) not null check (amount > 0),
  ride_at       timestamptz not null,
  notes         text,
  receipt_path  text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
  -- No unique (shift_id, direction): a shift may hold several rides per direction.
);

-- Migration: add direction column and update unique constraint for existing deployments
do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'refund_claims' and column_name = 'direction'
  ) then
    alter table public.refund_claims
      add column direction text not null default 'from_work'
      check (direction in ('to_work', 'from_work'));
    alter table public.refund_claims
      drop constraint if exists refund_claims_shift_id_key;
    alter table public.refund_claims
      add constraint refund_claims_shift_id_direction_key unique (shift_id, direction);
  end if;
end $$;

do $$ begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'user_settings' and column_name = 'currency'
  ) then
    alter table public.user_settings add column currency text not null default 'ILS';
  end if;
end $$;

-- Compensation globalization columns
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

-- Compensation profiles: user-configured pay estimation rules
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

create or replace trigger compensation_profiles_updated_at
  before update on public.compensation_profiles
  for each row execute function public.handle_updated_at();

-- FK from user_settings to compensation_profiles (added after table exists)
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

create index if not exists refund_claims_user_id_idx on public.refund_claims (user_id);
create index if not exists refund_claims_shift_id_idx on public.refund_claims (shift_id);

create or replace trigger refund_claims_updated_at
  before update on public.refund_claims
  for each row execute function public.handle_updated_at();

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, email, full_name)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data->>'full_name', null)
  );

  -- New users start with onboarding incomplete and recommended feature defaults.
  insert into public.user_settings (
    user_id,
    onboarding_completed,
    features_travel_refunds,
    features_paid_projects,
    features_insights,
    features_clock_styles,
    clock_style
  ) values (
    new.id,
    false,   -- must complete onboarding
    false,   -- recommended default: off
    false,
    true,
    true,
    'classic'
  );

  return new;
end;
$$;

create or replace trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ── Row Level Security ────────────────────────────────────────

alter table public.profiles      enable row level security;
alter table public.user_settings enable row level security;
alter table public.shifts        enable row level security;

-- Drop all policies before recreating so this script is re-runnable
do $$
declare
  pol record;
begin
  for pol in
    select policyname, tablename
    from pg_policies
    where schemaname = 'public'
      and tablename in ('profiles', 'user_settings', 'shifts', 'refund_claims', 'compensation_profiles')
  loop
    execute format('drop policy if exists %I on public.%I', pol.policyname, pol.tablename);
  end loop;
end
$$;

alter table public.refund_claims enable row level security;
alter table public.compensation_profiles enable row level security;

-- Profiles
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = id);

create policy "profiles_insert_own" on public.profiles
  for insert with check (auth.uid() = id);

create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = id);

-- User settings
create policy "settings_select_own" on public.user_settings
  for select using (auth.uid() = user_id);

create policy "settings_insert_own" on public.user_settings
  for insert with check (auth.uid() = user_id);

create policy "settings_update_own" on public.user_settings
  for update using (auth.uid() = user_id);

create policy "settings_delete_own" on public.user_settings
  for delete using (auth.uid() = user_id);

-- Shifts
create policy "shifts_select_own" on public.shifts
  for select using (auth.uid() = user_id);

create policy "shifts_insert_own" on public.shifts
  for insert with check (auth.uid() = user_id);

create policy "shifts_update_own" on public.shifts
  for update using (auth.uid() = user_id);

create policy "shifts_delete_own" on public.shifts
  for delete using (auth.uid() = user_id);

-- Refund claims
create policy "refund_claims_select_own" on public.refund_claims
  for select using (auth.uid() = user_id);

create policy "refund_claims_insert_own" on public.refund_claims
  for insert with check (auth.uid() = user_id);

create policy "refund_claims_update_own" on public.refund_claims
  for update using (auth.uid() = user_id);

create policy "refund_claims_delete_own" on public.refund_claims
  for delete using (auth.uid() = user_id);

-- Compensation profiles
create policy "compensation_profiles_select_own" on public.compensation_profiles
  for select using (auth.uid() = user_id);

create policy "compensation_profiles_insert_own" on public.compensation_profiles
  for insert with check (auth.uid() = user_id);

create policy "compensation_profiles_update_own" on public.compensation_profiles
  for update using (auth.uid() = user_id);

create policy "compensation_profiles_delete_own" on public.compensation_profiles
  for delete using (auth.uid() = user_id);

-- ── Storage bucket (run once in Dashboard or via CLI) ─────────
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'refund-receipts',
  'refund-receipts',
  false,
  10485760,
  array['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']
) on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "refund_receipts_select" on storage.objects;
create policy "refund_receipts_select" on storage.objects
  for select using (bucket_id = 'refund-receipts' and auth.uid()::text = (storage.foldername(name))[1]);

drop policy if exists "refund_receipts_insert" on storage.objects;
create policy "refund_receipts_insert" on storage.objects
  for insert with check (bucket_id = 'refund-receipts' and auth.uid()::text = (storage.foldername(name))[1]);

drop policy if exists "refund_receipts_update" on storage.objects;
create policy "refund_receipts_update" on storage.objects
  for update using (bucket_id = 'refund-receipts' and auth.uid()::text = (storage.foldername(name))[1]);

drop policy if exists "refund_receipts_delete" on storage.objects;
create policy "refund_receipts_delete" on storage.objects
  for delete using (bucket_id = 'refund-receipts' and auth.uid()::text = (storage.foldername(name))[1]);

-- ── Premium profiles (configurable shift premiums) ────────────
-- Required for Android shift sync: RemoteShiftInsert/Update send
-- premium_profile_id and force_regular_rate on every push, so a project
-- bootstrapped from this file without them rejected every shift write with
-- PGRST204 and stalled the whole shift queue. Safe to re-run.
-- Source: supabase/migrations/20250703120000_premium_profiles.sql

create table if not exists public.premium_profiles (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  name          text not null,
  multiplier    numeric(6, 3) not null default 1.5
                check (multiplier > 0),
  premium_type  text not null default 'highest_only'
                check (premium_type in (
                  'highest_only',
                  'additive',
                  'multiplicative',
                  'base_plus_premium',
                  'premium_in_regular_rate',
                  'excluded_from_regular_rate'
                )),
  is_default    boolean not null default false,
  is_archived   boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create index if not exists premium_profiles_user_id_idx
  on public.premium_profiles (user_id);

alter table public.shifts
  add column if not exists premium_profile_id uuid
  references public.premium_profiles(id) on delete set null;

create index if not exists shifts_premium_profile_id_idx
  on public.shifts (premium_profile_id);

-- Per-shift override: pay at the regular rate even on a configured weekend day.
-- Source: supabase/migrations/20260708000000_shifts_force_regular_rate.sql
alter table public.shifts
  add column if not exists force_regular_rate boolean not null default false;

alter table public.premium_profiles enable row level security;

drop policy if exists "Users can manage own premium profiles" on public.premium_profiles;
create policy "Users can manage own premium profiles"
  on public.premium_profiles
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop trigger if exists premium_profiles_updated_at on public.premium_profiles;
create trigger premium_profiles_updated_at
  before update on public.premium_profiles
  for each row execute function public.handle_updated_at();

-- Stacking options: the same six values are accepted on compensation_profiles
-- as on premium_profiles. The inline check above only allows the original two.
-- Source: supabase/migrations/20260706000000_compensation_stacking_options.sql
alter table public.compensation_profiles
  drop constraint if exists compensation_profiles_stacking_policy_check;

alter table public.compensation_profiles
  add constraint compensation_profiles_stacking_policy_check
  check (stacking_policy in (
    'highest_only',
    'additive',
    'multiplicative',
    'base_plus_premium',
    'premium_in_regular_rate',
    'excluded_from_regular_rate'
  ));

-- Several refund claims may exist per shift and direction (multiple rides).
-- Source: supabase/migrations/20260729000000_refund_claims_multiple_rides.sql
alter table public.refund_claims
  drop constraint if exists refund_claims_shift_id_direction_key;
alter table public.refund_claims
  drop constraint if exists refund_claims_shift_id_key;
create index if not exists refund_claims_shift_id_direction_idx
  on public.refund_claims (shift_id, direction);

-- ── Tasks (paid projects / clock-in labels) ───────────────────
-- Required for Android task sync. Safe to re-run.
-- Source: supabase/migrations/20250628000000_tasks.sql

create table if not exists public.tasks (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  name          text not null,
  icon          text not null default '📋',
  color         text,
  hourly_rate   numeric(10, 2) not null,
  is_archived   boolean not null default false,
  last_used_at  timestamptz,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create index if not exists tasks_user_id_idx on public.tasks (user_id);

drop trigger if exists tasks_updated_at on public.tasks;
create trigger tasks_updated_at
  before update on public.tasks
  for each row execute function public.handle_updated_at();

do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'task_id'
  ) then
    alter table public.shifts add column task_id uuid default null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'task_name_snapshot'
  ) then
    alter table public.shifts add column task_name_snapshot text default null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'task_icon_snapshot'
  ) then
    alter table public.shifts add column task_icon_snapshot text default null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'task_hourly_rate_snapshot'
  ) then
    alter table public.shifts add column task_hourly_rate_snapshot numeric(10, 2) default null;
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from information_schema.table_constraints
    where constraint_name = 'shifts_task_id_fkey'
  ) then
    alter table public.shifts
      add constraint shifts_task_id_fkey
      foreign key (task_id) references public.tasks(id) on delete set null;
  end if;
exception when others then null;
end $$;

alter table public.tasks enable row level security;

drop policy if exists "tasks_select_own" on public.tasks;
create policy "tasks_select_own" on public.tasks
  for select using (auth.uid() = user_id);

drop policy if exists "tasks_insert_own" on public.tasks;
create policy "tasks_insert_own" on public.tasks
  for insert with check (auth.uid() = user_id);

drop policy if exists "tasks_update_own" on public.tasks;
create policy "tasks_update_own" on public.tasks
  for update using (auth.uid() = user_id);

drop policy if exists "tasks_delete_own" on public.tasks;
create policy "tasks_delete_own" on public.tasks
  for delete using (auth.uid() = user_id);

notify pgrst, 'reload schema';

-- ── Account deletion (Google Play policy) ─────────────────────
create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = public, auth, storage
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'Not authenticated';
  end if;

  delete from storage.objects
  where bucket_id = 'refund-receipts'
    and (storage.foldername(name))[1] = uid::text;

  delete from public.refund_claims where user_id = uid;
  delete from public.shifts where user_id = uid;
  delete from public.tasks where user_id = uid;
  delete from public.compensation_profiles where user_id = uid;
  -- Also covered by the auth.users cascade below, but listed explicitly so this
  -- function is a complete record of what the account owns.
  delete from public.premium_profiles where user_id = uid;
  delete from public.user_settings where user_id = uid;
  delete from public.profiles where id = uid;

  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_own_account() from public;
grant execute on function public.delete_own_account() to authenticated;
