-- ============================================================
-- ElmTrackr Schema
-- Run this in your Supabase SQL editor (Dashboard > SQL Editor)
-- ============================================================

-- Profiles: mirrors auth.users, extended with display info
create table if not exists public.profiles (
  id         uuid primary key references auth.users(id) on delete cascade,
  email      text not null,
  full_name  text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- User settings: per-user overtime + weekend config
create table if not exists public.user_settings (
  id                                 uuid primary key default gen_random_uuid(),
  user_id                            uuid not null unique references auth.users(id) on delete cascade,
  timezone                           text not null default 'UTC',
  -- Daily overtime kicks in after this many minutes (default: 8 hours)
  daily_overtime_threshold_minutes   integer not null default 480,
  -- Weekly overtime kicks in after this many minutes (default: 40 hours)
  weekly_overtime_threshold_minutes  integer not null default 2400,
  -- ISO weekday numbers for weekend: 0=Sun,1=Mon,2=Tue,3=Wed,4=Thu,5=Fri,6=Sat
  -- Default: Friday (5) and Saturday (6)
  weekend_days                       integer[] not null default '{5,6}',
  created_at                         timestamptz not null default now(),
  updated_at                         timestamptz not null default now()
);

-- Shifts: the core time-tracking record
create table if not exists public.shifts (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  start_time     timestamptz not null,
  end_time       timestamptz,           -- null = currently active/clocked in
  break_minutes  integer not null default 0 check (break_minutes >= 0),
  notes          text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  -- Prevent break longer than shift duration (checked at app level too)
  constraint valid_time_range check (end_time is null or end_time > start_time)
);

-- Index for fast per-user shift lookups by time range
create index if not exists shifts_user_id_start_time_idx
  on public.shifts (user_id, start_time desc);

-- Index for finding active shifts quickly
create index if not exists shifts_active_idx
  on public.shifts (user_id, end_time)
  where end_time is null;

-- ============================================================
-- Auto-update updated_at timestamps
-- ============================================================

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

-- ============================================================
-- Auto-create profile + settings on new user signup
-- ============================================================

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

-- ============================================================
-- Row Level Security
-- ============================================================

alter table public.profiles      enable row level security;
alter table public.user_settings enable row level security;
alter table public.shifts        enable row level security;

-- Profiles: users can only read/update their own profile
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = id);

create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = id);

-- User settings: full CRUD on own row only
create policy "settings_select_own" on public.user_settings
  for select using (auth.uid() = user_id);

create policy "settings_insert_own" on public.user_settings
  for insert with check (auth.uid() = user_id);

create policy "settings_update_own" on public.user_settings
  for update using (auth.uid() = user_id);

create policy "settings_delete_own" on public.user_settings
  for delete using (auth.uid() = user_id);

-- Shifts: full CRUD on own shifts only
create policy "shifts_select_own" on public.shifts
  for select using (auth.uid() = user_id);

create policy "shifts_insert_own" on public.shifts
  for insert with check (auth.uid() = user_id);

create policy "shifts_update_own" on public.shifts
  for update using (auth.uid() = user_id);

create policy "shifts_delete_own" on public.shifts
  for delete using (auth.uid() = user_id);
