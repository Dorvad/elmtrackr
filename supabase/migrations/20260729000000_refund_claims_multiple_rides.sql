-- Allow several refund claims per shift and direction.
--
-- The app supports adding any number of rides to a shift, in either direction
-- (see UpsertRefundClaim / RefundClaimsSection). The original schema constrained
-- refund_claims to one row per (shift_id, direction), so the second ride pushed
-- for a direction failed with 23505. The sync engine read that as an idempotent
-- retry of its own insert, adopted a remote id that existed on no row, and marked
-- the claim SYNCED — the ride never reached the server and was later tombstoned
-- locally by the next full pull.
--
-- Safe to re-run: both statements are conditional.

alter table public.refund_claims
  drop constraint if exists refund_claims_shift_id_direction_key;

-- The pre-direction deployments used a per-shift constraint; drop that too so a
-- project migrated through the older path ends in the same state.
alter table public.refund_claims
  drop constraint if exists refund_claims_shift_id_key;

-- Claims are listed per shift and pulled by remote id, so keep those lookups indexed.
create index if not exists refund_claims_shift_id_direction_idx
  on public.refund_claims (shift_id, direction);
