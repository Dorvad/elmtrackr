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

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, email, full_name)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data->>'full_name', null)
  );

  insert into public.user_settings (user_id)
  values (new.id);

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
      and tablename in ('profiles', 'user_settings', 'shifts')
  loop
    execute format('drop policy if exists %I on public.%I', pol.policyname, pol.tablename);
  end loop;
end
$$;

-- Profiles
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = id);

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
