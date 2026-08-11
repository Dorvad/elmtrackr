-- Workplaces, leave policies, reported absences and payslip balance snapshots.
--
-- Reporting a vacation or sick day is deliberately *not* a shift. A shift means
-- worked time: it feeds net minutes, overtime ladders, weekend/holiday/night
-- premiums and the shift count. An absence has none of those properties, and
-- modelling one as an 8-hour shift would inflate every one of those numbers and
-- silently create overtime the user never worked. Absences therefore live in
-- their own tables and only ever meet worked time at the money level, where the
-- reports add work / vacation / sick gross together and keep worked minutes to
-- shifts alone.
--
-- `workplaces` exists because a compensation profile is not an employer. A
-- worker stays at the same job while their wage changes, their overtime rules
-- change, or a new profile becomes effective — and leave entitlement, seniority
-- and payslip balances belong to the *job*, not to whichever pay profile was
-- effective when they were entered. Employer identity is kept intentionally
-- minimal: no registration numbers, addresses or HR contacts, because nothing
-- in the feature needs them.

-- ── Workplaces ──────────────────────────────────────────────────────────────
--
-- employment_start_date is an epoch-day integer, matching how Paid Projects
-- stores calendar dates, so a start date cannot shift by a day through a
-- timezone conversion. It is nullable: seniority is not computed in this
-- release and the field is there to record what the user knows.

create table if not exists public.workplaces (
  id                     uuid primary key default gen_random_uuid(),
  user_id                uuid not null references auth.users(id) on delete cascade,
  name                   text not null,
  region_code            text not null,
  currency_code          text not null,
  timezone               text not null,
  employment_start_date  integer,
  is_default             boolean not null default false,
  is_archived            boolean not null default false,
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now(),
  deleted_at             timestamptz,
  client_updated_at      timestamptz not null default now()
);

-- ── Leave policies ──────────────────────────────────────────────────────────
--
-- rules_json holds the sick pay tier ladder and the vacation pay basis (see
-- LeavePolicyCodec). It is data, not code: the Israeli default of 0% / 50% /
-- 50% / 100% is a *preset the user can replace*, and a workplace with a better
-- agreement configures a single 100% tier instead. Nothing in the app branches
-- on "is this Israel" to decide what a sick day pays.
--
-- Effective-dated like compensation_profiles, so editing a policy today cannot
-- restate what an absence reported last month was estimated to pay.

create table if not exists public.leave_policies (
  id                 uuid primary key default gen_random_uuid(),
  user_id            uuid not null references auth.users(id) on delete cascade,
  workplace_id       uuid not null references public.workplaces(id) on delete cascade,
  region_code        text not null,
  rules_json         jsonb not null,
  effective_from     timestamptz not null default now(),
  effective_until    timestamptz,
  is_active          boolean not null default true,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  deleted_at         timestamptz,
  client_updated_at  timestamptz not null default now()
);

-- ── Absence events ──────────────────────────────────────────────────────────
--
-- User-level, not workplace-level, and that is the whole point for sick leave.
-- For an hourly or daily worker with more than one employer the ordinal sick
-- day is counted from the *illness*, not restarted at each employer: if the
-- first day the worker would have worked at their second job is the third day
-- of one continuous illness, it is sick day 3 there too, not day 1. Counting
-- per employer would pay the 0%/50% opening tiers over and over and understate
-- what the user is owed.
--
-- Dates are epoch-day integers: an absence is a calendar date, not an instant,
-- and must not move because a device is in another timezone.

create table if not exists public.absence_events (
  id                 uuid primary key default gen_random_uuid(),
  user_id            uuid not null references auth.users(id) on delete cascade,
  type               text not null check (type in ('sick', 'vacation')),
  start_date         integer not null,
  end_date           integer not null,
  notes              text,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  deleted_at         timestamptz,
  client_updated_at  timestamptz not null default now()
);

-- ── Absence allocations ─────────────────────────────────────────────────────
--
-- One row per (event, workplace, affected date): the workplace-level half of
-- an absence. The event says "I was ill from the 10th to the 13th"; the
-- allocations say which of those days the user would have worked where, what
-- each is estimated to pay under that workplace's policy, and how much
-- entitlement it consumes.
--
-- policy_snapshot_json and calculation_snapshot_json follow the same rule as
-- shifts.compensation_snapshot_json: a historical estimate must stay
-- reproducible after the wage, the policy or the workplace settings change.
-- Old allocations are never silently recomputed — the user asks for a
-- recalculation explicitly.
--
-- estimated_gross_pay is `numeric`, matching compensation_profiles.base_hourly_rate
-- and every other wage figure, not the `text` that Paid Projects uses for money.
-- The two are different kinds of number: a billed fee is an amount of money that
-- must round-trip byte for byte, while this is a derived estimate recomputed from
-- an hourly rate and a multiplier, and is presented as an estimate everywhere it
-- appears.
--
-- Deliberately no unique index on (absence_event_id, workplace_id, affected_date).
-- The client builds exactly one allocation per triple inside one transaction, and
-- editing an event rebuilds its allocations as tombstone-plus-insert. A unique
-- index would let a rebuild whose tombstone had not yet been pushed collide with
-- its own replacement and leave the push permanently FAILED — the same reason
-- shifts_user_id_open_idx is not unique.

create table if not exists public.absence_allocations (
  id                         uuid primary key default gen_random_uuid(),
  user_id                    uuid not null references auth.users(id) on delete cascade,
  absence_event_id           uuid not null references public.absence_events(id) on delete cascade,
  workplace_id               uuid not null references public.workplaces(id) on delete cascade,
  affected_date              integer not null,
  entitlement_units          numeric(10, 4) not null default 1,
  unit                       text not null check (unit in ('days', 'hours')),
  expected_work_minutes      integer,
  policy_snapshot_json       jsonb,
  calculation_snapshot_json  jsonb,
  estimated_gross_pay        numeric(12, 2) not null default 0,
  created_at                 timestamptz not null default now(),
  updated_at                 timestamptz not null default now(),
  deleted_at                 timestamptz,
  client_updated_at          timestamptz not null default now()
);

-- ── Leave balance snapshots ─────────────────────────────────────────────────
--
-- The balance a user reads off their payslip, kept as history rather than as a
-- mutable workplaces.sick_balance column. Entering August's payslip must not
-- erase July's, because the whole estimate is "the last official balance, minus
-- what has been reported since it" — and that needs the date the official
-- number was true, not just the number.
--
-- as_of_date is an epoch day: a payslip balance is dated to a day.
--
-- No unique constraint on (workplace, type, as_of_date). Correcting a mistyped
-- balance for a date the user has already entered is normal, and the estimator
-- reads the most recently created snapshot on or before today, so a correction
-- simply supersedes the earlier row while leaving it in the history.

create table if not exists public.leave_balance_snapshots (
  id                 uuid primary key default gen_random_uuid(),
  user_id            uuid not null references auth.users(id) on delete cascade,
  workplace_id       uuid not null references public.workplaces(id) on delete cascade,
  balance_type       text not null check (balance_type in ('vacation', 'sick')),
  balance            numeric(10, 4) not null,
  unit               text not null check (unit in ('days', 'hours')),
  as_of_date         integer not null,
  source             text not null check (source in ('payslip', 'manual')),
  label              text,
  notes              text,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  deleted_at         timestamptz,
  client_updated_at  timestamptz not null default now()
);

-- ── Workplace links on existing tables ──────────────────────────────────────
--
-- Both nullable with no default and deliberately not backfilled. A NULL
-- workplace_id reads as "not assigned yet", which is what every row written
-- before this release is. Backfilling would mean rewriting historical shifts
-- during an upgrade, and this schema's rule is that an upgrade never restates a
-- recorded shift or its pay. The client adopts existing rows into a workplace
-- the first time it creates one (ensureDefaultWorkplace), where the write is a
-- normal edit the user can see and undo, rather than an invisible migration.
--
-- on delete set null, matching shifts.compensation_profile_id: removing a
-- workplace must not delete the user's work history.

do $$
begin
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'shifts' and column_name = 'workplace_id'
  ) then
    alter table public.shifts add column workplace_id uuid default null
      references public.workplaces(id) on delete set null;
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'compensation_profiles'
      and column_name = 'workplace_id'
  ) then
    alter table public.compensation_profiles add column workplace_id uuid default null
      references public.workplaces(id) on delete set null;
  end if;
end
$$;

-- ── Indexes ─────────────────────────────────────────────────────────────────
--
-- The (updated_at, id) index on every table is what incremental pull pages by;
-- without it each page is a full sort of the user's history and the total
-- ordering the client depends on for correctness is the ordering the planner is
-- least likely to produce cheaply.

create index if not exists workplaces_user_id_idx on public.workplaces (user_id);
create index if not exists workplaces_updated_at_id_idx on public.workplaces (updated_at, id);

create index if not exists leave_policies_user_id_idx on public.leave_policies (user_id);
create index if not exists leave_policies_workplace_id_idx on public.leave_policies (workplace_id);
create index if not exists leave_policies_updated_at_id_idx on public.leave_policies (updated_at, id);

create index if not exists absence_events_user_id_idx on public.absence_events (user_id);
create index if not exists absence_events_user_id_start_date_idx
  on public.absence_events (user_id, start_date);
create index if not exists absence_events_updated_at_id_idx on public.absence_events (updated_at, id);

create index if not exists absence_allocations_user_id_idx on public.absence_allocations (user_id);
create index if not exists absence_allocations_absence_event_id_idx
  on public.absence_allocations (absence_event_id);
-- Serves both the balance estimator (a workplace's allocations after a snapshot
-- date) and the monthly report breakdown.
create index if not exists absence_allocations_workplace_id_affected_date_idx
  on public.absence_allocations (workplace_id, affected_date);
create index if not exists absence_allocations_updated_at_id_idx
  on public.absence_allocations (updated_at, id);

create index if not exists leave_balance_snapshots_user_id_idx
  on public.leave_balance_snapshots (user_id);
create index if not exists leave_balance_snapshots_lookup_idx
  on public.leave_balance_snapshots (workplace_id, balance_type, as_of_date);
create index if not exists leave_balance_snapshots_updated_at_id_idx
  on public.leave_balance_snapshots (updated_at, id);

create index if not exists shifts_workplace_id_idx on public.shifts (workplace_id);
create index if not exists compensation_profiles_workplace_id_idx
  on public.compensation_profiles (workplace_id);

-- ── Row level security ──────────────────────────────────────────────────────
--
-- None of the client's fetch queries filter by user — they rely entirely on
-- these policies to scope the result set — so a table added without them would
-- leak straight into every user's local database. Sick leave is health-adjacent
-- data, so that would be worse here than for a shift.
--
-- Ownership on the child tables is enforced through their own user_id *and*
-- through the parent's: a row whose workplace or event belongs to somebody else
-- is rejected on write even if its user_id is the caller's, so a client that
-- sent a mismatched parent id could not attach leave to another user's job.

alter table public.workplaces              enable row level security;
alter table public.leave_policies          enable row level security;
alter table public.absence_events          enable row level security;
alter table public.absence_allocations     enable row level security;
alter table public.leave_balance_snapshots enable row level security;

do $$
declare
  t text;
begin
  foreach t in array array[
    'workplaces', 'leave_policies', 'absence_events',
    'absence_allocations', 'leave_balance_snapshots'
  ] loop
    execute format('drop policy if exists %I on public.%I', t || '_select_own', t);
    execute format('drop policy if exists %I on public.%I', t || '_insert_own', t);
    execute format('drop policy if exists %I on public.%I', t || '_update_own', t);
    execute format('drop policy if exists %I on public.%I', t || '_delete_own', t);

    execute format(
      'create policy %I on public.%I for select using (auth.uid() = user_id)',
      t || '_select_own', t
    );
    execute format(
      'create policy %I on public.%I for insert with check (auth.uid() = user_id)',
      t || '_insert_own', t
    );
    execute format(
      'create policy %I on public.%I for update using (auth.uid() = user_id) ' ||
      'with check (auth.uid() = user_id)',
      t || '_update_own', t
    );
    execute format(
      'create policy %I on public.%I for delete using (auth.uid() = user_id)',
      t || '_delete_own', t
    );
  end loop;
end
$$;

-- Parent ownership, enforced on the three tables that carry a workplace or
-- event reference. auth.uid() = user_id already stops one user reading
-- another's rows; these add that a row cannot *point* at a parent the caller
-- does not own.

create or replace function public.owns_workplace(target uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.workplaces w
    where w.id = target and w.user_id = auth.uid()
  );
$$;

create or replace function public.owns_absence_event(target uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.absence_events e
    where e.id = target and e.user_id = auth.uid()
  );
$$;

revoke all on function public.owns_workplace(uuid) from public;
revoke all on function public.owns_absence_event(uuid) from public;
grant execute on function public.owns_workplace(uuid) to authenticated;
grant execute on function public.owns_absence_event(uuid) to authenticated;

alter table public.leave_policies
  drop constraint if exists leave_policies_workplace_owned_chk;
alter table public.leave_policies
  add constraint leave_policies_workplace_owned_chk
  check (public.owns_workplace(workplace_id)) not valid;

alter table public.leave_balance_snapshots
  drop constraint if exists leave_balance_snapshots_workplace_owned_chk;
alter table public.leave_balance_snapshots
  add constraint leave_balance_snapshots_workplace_owned_chk
  check (public.owns_workplace(workplace_id)) not valid;

alter table public.absence_allocations
  drop constraint if exists absence_allocations_parents_owned_chk;
alter table public.absence_allocations
  add constraint absence_allocations_parents_owned_chk
  check (
    public.owns_workplace(workplace_id) and public.owns_absence_event(absence_event_id)
  ) not valid;

-- `not valid` skips the one-time scan of existing rows, which is what we want:
-- these tables are new so there is nothing to scan, and auth.uid() is null
-- outside a request, so validating would evaluate the predicate with no session
-- and fail. New and updated rows are still checked.

-- ── updated_at triggers ─────────────────────────────────────────────────────
--
-- handle_updated_at is created by the compensation_profiles migration; this
-- reuses it so server-side updated_at keeps meaning "when the server last wrote
-- this row", which is what the incremental pull cursor reads.

do $$
declare
  t text;
begin
  foreach t in array array[
    'workplaces', 'leave_policies', 'absence_events',
    'absence_allocations', 'leave_balance_snapshots'
  ] loop
    execute format('drop trigger if exists %I on public.%I', t || '_updated_at', t);
    execute format(
      'create trigger %I before update on public.%I ' ||
      'for each row execute function public.handle_updated_at()',
      t || '_updated_at', t
    );
  end loop;
end
$$;

-- ── Account deletion ────────────────────────────────────────────────────────
--
-- The cascade from auth.users would remove these rows anyway, but
-- delete_own_account is the function the app calls and what the promise made in
-- Settings is measured against, so the leave tables are named explicitly — as
-- tasks were when they were added. Children first: allocations and policies
-- reference events and workplaces.
--
-- Sick-leave records are the most sensitive rows this app stores, so "delete my
-- account" has to actually remove them rather than rely on a cascade nobody
-- tests.

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

  delete from public.absence_allocations where user_id = uid;
  delete from public.absence_events where user_id = uid;
  delete from public.leave_balance_snapshots where user_id = uid;
  delete from public.leave_policies where user_id = uid;

  delete from public.refund_claims where user_id = uid;
  delete from public.shifts where user_id = uid;
  delete from public.tasks where user_id = uid;
  delete from public.compensation_profiles where user_id = uid;
  delete from public.workplaces where user_id = uid;
  delete from public.user_settings where user_id = uid;
  delete from public.profiles where id = uid;

  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_own_account() from public;
grant execute on function public.delete_own_account() to authenticated;

-- Supabase hosted projects cache the schema in PostgREST; without this the app
-- gets "Could not find the table 'public.workplaces' in the schema cache" until
-- the cache happens to refresh.
notify pgrst, 'reload schema';
