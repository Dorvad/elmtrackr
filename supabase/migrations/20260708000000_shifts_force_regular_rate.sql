-- Per-shift override: pay the shift at the regular rate even when it falls
-- on a configured weekend day or carries special-day flags. Set from the
-- weekend/holiday switch on the Android shift edit form.

alter table public.shifts
  add column if not exists force_regular_rate boolean not null default false;

notify pgrst, 'reload schema';
