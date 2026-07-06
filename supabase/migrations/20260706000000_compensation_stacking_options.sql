-- Align compensation_profiles.stacking_policy with premium_profiles.premium_type:
-- the same six stacking options are now accepted on both tables. Existing values
-- ('highest_only', 'additive') remain valid; no data change.

alter table public.compensation_profiles
  drop constraint if exists compensation_profiles_stacking_policy_check;

alter table public.compensation_profiles
  add constraint compensation_profiles_stacking_policy_check
  check (stacking_policy in (
    'highest_only',
    'additive',
    'multiplicative',
    'base_plus_premium',
    'premium_in_regular_rate',
    'excluded_from_regular_rate'
  ));
