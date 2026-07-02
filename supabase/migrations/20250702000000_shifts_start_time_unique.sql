-- Prevent duplicate shifts for the same user at the exact same start time.
-- Safe to re-run: dedupe step only removes rows that violate the new constraint.

-- Keep the best row per (user_id, start_time): prefer completed shifts, then
-- most recently updated, then earliest created.
with ranked as (
  select
    id,
    row_number() over (
      partition by user_id, start_time
      order by
        (case when end_time is not null then 0 else 1 end),
        updated_at desc,
        created_at asc
    ) as rn
  from public.shifts
)
delete from public.shifts s
using ranked r
where s.id = r.id
  and r.rn > 1;

create unique index if not exists shifts_user_id_start_time_uidx
  on public.shifts (user_id, start_time);
