-- Edit-version guards for user_settings and profiles.
--
-- 20260806000000 gave shifts, refund_claims, compensation_profiles,
-- premium_profiles and tasks a client_updated_at column, and every update to
-- those tables is filtered `client_updated_at <= <the value being written>` so a
-- write carrying an older edit than the stored one matches nothing and is read as
-- a conflict. user_settings and profiles were left out of that array, and nobody
-- noticed because supabase-contract.md claims the filter applies to *every*
-- update.
--
-- The consequence is worse for user_settings than for most tables it did cover.
-- A device that has been offline holds the old thresholds, hourly rate, weekend
-- days, currency, region and all six feature flags as PENDING_UPDATE. When it
-- syncs it overwrites the server unconditionally, and the pull that runs after
-- the push in the same pass then adopts those stale values back onto the device
-- that had the newer ones. Both devices converge on the old settings, silently,
-- and every pay figure recomputes against them.
--
-- Defaulted and not null, so clients that do not yet send the column keep working
-- against a migrated database. Apply this BEFORE releasing an app build that
-- filters on it: a new client against an un-migrated server would filter on a
-- column that does not exist and fail every settings push.

do $$
declare
  t text;
begin
  foreach t in array array['user_settings', 'profiles'] loop
    if not exists (
      select 1 from information_schema.columns
      where table_schema = 'public' and table_name = t and column_name = 'client_updated_at'
    ) then
      execute format(
        'alter table public.%I add column client_updated_at timestamptz not null default now()', t
      );
      -- Seed from updated_at rather than now(): every existing row then carries
      -- the edit time it actually has, so the first write from a device holding an
      -- older copy is correctly rejected instead of being let through because the
      -- server's version looked brand new.
      execute format('update public.%I set client_updated_at = updated_at', t);
    end if;
  end loop;
end $$;

-- An index on the filter column, matching the five tables the earlier migration
-- covered. The filter runs on every settings and profile update.
create index if not exists user_settings_client_updated_at_idx
  on public.user_settings (client_updated_at);
create index if not exists profiles_client_updated_at_idx
  on public.profiles (client_updated_at);
