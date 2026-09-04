-- Restore the tables that `delete_own_account` stopped naming.
--
-- `20260806100000_paid_projects.sql` added explicit deletes for the three Paid
-- Projects tables. `20260811000000_workplaces_and_leave.sql` then rebuilt the whole
-- function with `create or replace` and, in adding the leave tables, dropped those
-- three lines. `premium_profiles` has never been named by any version of it.
--
-- Nothing leaks: every one of these tables cascades from `auth.users`, and the
-- function deletes that row last, so the data does go. But the repository's rule is
-- that account deletion names every table it removes rather than relying on a cascade
-- somebody could later alter to `on delete set null` without noticing this function.
-- An explicit delete also fails loudly if a table is renamed, where a cascade would
-- quietly stop covering it.
--
-- Deliberately unchanged: the function is still `security definer` with a pinned
-- `search_path`, still refuses an unauthenticated caller, and still ends by deleting
-- the `auth.users` row.
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

  -- Children first: both reference public.projects(id).
  delete from public.project_payments        where user_id = uid;
  delete from public.project_billing_records where user_id = uid;
  delete from public.projects                where user_id = uid;

  delete from public.premium_profiles where user_id = uid;
  delete from public.compensation_profiles where user_id = uid;
  delete from public.workplaces where user_id = uid;
  delete from public.user_settings where user_id = uid;
  delete from public.profiles where id = uid;

  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_own_account() from public;
grant execute on function public.delete_own_account() to authenticated;

notify pgrst, 'reload schema';
