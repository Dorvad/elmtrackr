-- ============================================================
-- ElmTrackr Schema  (idempotent — safe to re-run at any time)
-- Run this in your Supabase SQL editor (Dashboard > SQL Editor)
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
end
$$;

-- ── Indexes ───────────────────────────────────────────────────

create index if not exists shifts_user_id_start_time_idx
  on public.shifts (user_id, start_time desc);

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
  updated_at    timestamptz not null default now(),
  unique (shift_id, direction)
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
      and tablename in ('profiles', 'user_settings', 'shifts', 'refund_claims')
  loop
    execute format('drop policy if exists %I on public.%I', pol.policyname, pol.tablename);
  end loop;
end
$$;

alter table public.refund_claims enable row level security;

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
