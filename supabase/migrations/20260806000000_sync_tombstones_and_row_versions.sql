-- Sync reliability: cloud tombstones and edit-version guards (idempotent).
--
-- Two gaps this closes.
--
-- 1. Deletes did not propagate. The push path issued a real DELETE, so a device
--    doing an incremental pull — the normal case — never saw the row again and
--    kept its local copy forever. Only a *full* pull (a fresh install, no
--    cursor) noticed the absence and tombstoned locally. Deleting a shift on a
--    phone therefore left it on the tablet indefinitely. `deleted_at` turns a
--    delete into an ordinary update that incremental pulls carry like any other
--    change.
--
-- 2. Updates had no concurrency check. `update ... where id = $1` always won, so
--    a device that came back online with a stale edit silently overwrote a newer
--    edit made elsewhere.
--
--    `client_updated_at` is the edit-version token: the wall-clock instant at
--    which the *device* last modified the row, which is exactly the value the
--    pull side already compares against (`remote.updated_at > local.updatedAt`).
--    Writers filter on `client_updated_at <= <their own edit time>`, so a stale
--    write matches no row and is reported to the client instead of applied.
--
--    Deliberately not a monotonic `version` counter. A counter detects the
--    collision but says nothing about which edit is newer, so resolving it still
--    means comparing edit times — and a counter would need a new column in every
--    Room entity to hold the last-seen value, whereas every entity already
--    stores `updatedAt`.
--
-- Both columns are additive with defaults, so existing rows keep their values.

-- ── deleted_at + client_updated_at columns ──────────────────────────────────
--
-- client_updated_at is backfilled from updated_at rather than left NULL: a NULL
-- fails every `client_updated_at <= x` filter, which would make every row
-- written before this migration look like a permanent conflict and block its
-- next edit forever.

do $$
declare
  t text;
begin
  foreach t in array array[
    'shifts', 'refund_claims', 'compensation_profiles', 'premium_profiles', 'tasks'
  ] loop
    if not exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = t and column_name = 'deleted_at'
    ) then
      execute format('alter table public.%I add column deleted_at timestamptz default null', t);
    end if;

    if not exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = t and column_name = 'client_updated_at'
    ) then
      execute format(
        'alter table public.%I add column client_updated_at timestamptz not null default now()', t
      );
      execute format('update public.%I set client_updated_at = updated_at', t);
    end if;
  end loop;
end
$$;

-- ── Indexes ─────────────────────────────────────────────────────────────────

-- Incremental pulls page by (updated_at, id) — see pullIncremental's keyset
-- pagination. Without a matching index every page is a full sort of the user's
-- history, and the total ordering the client depends on for correctness is
-- exactly the ordering the planner is least likely to produce cheaply.
create index if not exists shifts_updated_at_id_idx
  on public.shifts (updated_at, id);
create index if not exists refund_claims_updated_at_id_idx
  on public.refund_claims (updated_at, id);
create index if not exists compensation_profiles_updated_at_id_idx
  on public.compensation_profiles (updated_at, id);
create index if not exists premium_profiles_updated_at_id_idx
  on public.premium_profiles (updated_at, id);
create index if not exists tasks_updated_at_id_idx
  on public.tasks (updated_at, id);

-- The (user_id, start_time) uniqueness that the sync engine treats as a shift's
-- natural key must ignore tombstones. Otherwise deleting a shift and recording
-- another at the same start time — a retimed shift, a corrected entry — collides
-- with a row the user already deleted, and the push fails permanently.
drop index if exists public.shifts_user_id_start_time_uidx;
create unique index if not exists shifts_user_id_start_time_live_uidx
  on public.shifts (user_id, start_time)
  where deleted_at is null;

-- Supports the one-running-shift resolver's lookup of open shifts.
--
-- Deliberately NOT unique. A unique constraint would make the second device's
-- clock-in fail its push and sit in FAILED forever, which is worse than the
-- duplicate it prevents: the user's clock-in would never reach the cloud at all.
-- The invariant is enforced by RunningShiftResolver on both push and pull
-- instead. It is deterministic, so each device independently picks the same
-- winner and they converge without the server arbitrating.
create index if not exists shifts_user_id_open_idx
  on public.shifts (user_id, start_time)
  where end_time is null and deleted_at is null;
