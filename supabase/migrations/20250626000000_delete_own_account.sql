-- In-app account deletion (Google Play policy). Callable by authenticated users only.
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
  delete from public.compensation_profiles where user_id = uid;
  delete from public.user_settings where user_id = uid;
  delete from public.profiles where id = uid;

  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_own_account() from public;
grant execute on function public.delete_own_account() to authenticated;
