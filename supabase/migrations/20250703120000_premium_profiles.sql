-- Premium profiles: configurable shift premiums (replaces manual holiday/shabbat toggle)

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

-- RLS
alter table public.premium_profiles enable row level security;

create policy "Users can manage own premium profiles"
  on public.premium_profiles
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- updated_at trigger
drop trigger if exists premium_profiles_updated_at on public.premium_profiles;
create trigger premium_profiles_updated_at
  before update on public.premium_profiles
  for each row execute function public.handle_updated_at();
