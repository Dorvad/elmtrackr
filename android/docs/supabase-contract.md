# Supabase contract (Android-owned)

**Owner:** Android team  
**Consumers:** `android/` (primary), legacy `types/` (mirror only)

This document is the canonical reference for tables, columns, and **wire formats** persisted in Supabase. Web TypeScript types are not authoritative.

When changing the contract:

1. Add a migration under `supabase/migrations/`
2. Update this file
3. Update Android mappers and `fromPersisted()` parsers
4. Run `android` unit tests (`./gradlew testDebugUnitTest`)

**Parsing rule:** Never use `Enum.valueOf()` on synced strings. Always use `fromPersisted()` with a safe fallback.

---

## Tables

| Table | Purpose |
|-------|---------|
| `profiles` | User display info (mirrors `auth.users`) |
| `user_settings` | Overtime, weekend, payroll, feature flags, theme-related prefs that sync |
| `shifts` | Time entries (core entity) |
| `refund_claims` | Travel refund receipts per shift direction |
| `compensation_profiles` | Named pay-rule profiles |
| `premium_profiles` | Reusable shift premium multipliers and stacking types |
| `tasks` | Paid-project tasks for clock-in (synced per user) |

Base DDL for `profiles`, `user_settings`, `shifts`, and `refund_claims` predates the
in-repo migrations and lives in the Supabase project itself (not in this repo).
Everything added since is under [`supabase/migrations/`](../../supabase/migrations/),
applied in filename order. This document is the authoritative column list for the
base tables.

### Not in the contract: Paid Projects

Three Room tables have **no Supabase counterpart and no remote data source**:

| Room entity | Table |
|-------------|-------|
| `ProjectEntity` | `projects` |
| `ProjectBillingRecordEntity` | `project_billing_records` |
| `ProjectPaymentEntity` | `project_payments` |

They carry `remoteId` and `syncStatus` columns, and an index on
`(userId, syncStatus)`, because they were modelled on the synced entities — not
because anything syncs them. `SyncRepositoryImpl` deliberately excludes their
pending rows from `hasPendingWork` so the app does not show a "changes waiting"
badge the user can never clear.

**What this means for the user.** Paid Projects data — including the record of
money owed to them — does not survive a reinstall or a device change unless they
export a local backup first (Settings → Backup, which does include these
tables). Nothing in the UI says so.

**To close it:** add migrations for the three tables with the same
`auth.uid() = user_id` RLS policies as the rest, add remote data sources and
mappers, add push/pull steps ordered after `tasks` (billing records reference
projects), and remove the exclusion in `hasPendingWork`. Settings columns for
Paid Projects defaults are also preserved local-only on pull
(`preserveLocal` in `pullUserSettings`) and would need the same treatment.

---

## `user_settings`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `timezone` | text | IANA ID, e.g. `Asia/Jerusalem` | string |
| `daily_overtime_threshold_minutes` | int | minutes | int |
| `weekly_overtime_threshold_minutes` | int | minutes | int |
| `weekend_days` | int[] | 0=Sun … 6=Sat | `List<Int>` |
| `hourly_rate` | numeric | decimal or null | `Double?` |
| `currency` | text | ISO-ish, default `ILS` | string |
| `region_code` | text | see RegionCode | `RegionCode.fromPersisted()` |
| `currency_code` | text | e.g. `ILS`, `USD` | string |
| `default_compensation_profile_id` | uuid | nullable | string? |
| `onboarding_completed` | bool | `true` / `false` | bool |
| `onboarding_completed_at` | timestamptz | ISO-8601 or null | `Instant?` |
| `features_travel_refunds` | bool | | bool |
| `features_paid_projects` | bool | | bool |
| `features_insights` | bool | | bool |
| `features_clock_styles` | bool | | bool |
| `features_overtime_reminders` | bool | default `true` (migration `20250630000000`) | bool |
| `clock_style` | text | see ClockStyle | `ClockStyle.fromPersisted()` |

---

## `shifts`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `start_time` | timestamptz | ISO-8601 | epoch millis |
| `end_time` | timestamptz | ISO-8601 or null | epoch millis? |
| `break_minutes` | int | ≥ 0 | int |
| `notes` | text | nullable | string? |
| `is_special_day` | bool | | bool |
| `premium_profile_id` | uuid | nullable FK → `premium_profiles.id` | string? |
| `refund_action` | text | see RefundAction | `RefundAction.fromPersisted()` |
| `compensation_profile_id` | uuid | nullable | string? |
| `compensation_snapshot_json` | jsonb | snapshot at clock-out | JSON string in Room |
| `task_id` | uuid | nullable FK → `tasks.id` | string? |
| `task_name_snapshot` | text | nullable | string? |
| `task_icon_snapshot` | text | nullable | string? |
| `task_hourly_rate_snapshot` | numeric | nullable | double? |

DB check constraint (legacy): `refund_action in ('no_ride_taken', 'remind_later', 'submitted')`

---

## `refund_claims`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `direction` | text | `to_work`, `from_work` | `RefundDirection.fromPersisted()` |
| `provider` | text | see RefundProvider | `RefundProvider.fromPersisted()` |
| `amount` | numeric | > 0 | double |
| `ride_at` | timestamptz | ISO-8601 | epoch millis |
| `receipt_path` | text | Supabase storage path | string? |

Unique: `(shift_id, direction)`

---

## `compensation_profiles`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `region_code` | text | see RegionCode | `RegionCode.fromPersisted()` |
| `currency_code` | text | | string |
| `timezone` | text | IANA | string |
| `base_hourly_rate` | numeric | nullable | `Double?` |
| `rules_json` | jsonb | CompensationRules shape | `CompensationRulesCodec` |
| `stacking_policy` | text | see StackingPolicy (aligned with `premium_type`) | `StackingPolicy.fromPersisted()` |
| `effective_from` / `effective_until` | timestamptz | ISO-8601 | Instant |
| `is_default` / `is_archived` | bool | | bool |

---

## `premium_profiles`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `name` | text | non-empty | string |
| `multiplier` | numeric | e.g. `1.5` = 150% | double |
| `premium_type` | text | see PremiumType | `PremiumType.fromPersisted()` |
| `is_default` / `is_archived` | bool | | bool |

### PremiumType (wire → Android)

| Wire value | Android |
|------------|---------|
| `highest_only` | `HIGHEST_ONLY` |
| `additive` | `ADDITIVE` |
| `multiplicative` | `MULTIPLICATIVE` |
| `base_plus_premium` | `BASE_PLUS_PREMIUM` |
| `premium_in_regular_rate` | `PREMIUM_IN_REGULAR_RATE` |
| `excluded_from_regular_rate` | `EXCLUDED_FROM_REGULAR_RATE` |

Default profile: name `"Premium"`, multiplier `1.5`, type `highest_only`.

---

## `tasks`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `name` | text | non-empty | string |
| `icon` | text | emoji / short label | string |
| `color` | text | hex color e.g. `#5B4DF2` or null | string? |
| `hourly_rate` | numeric | decimal | double |
| `is_archived` | bool | | bool |
| `last_used_at` | timestamptz | ISO-8601 or null | epoch millis? |

Shifts may reference `task_id` (nullable FK). Clock-in stores `task_*_snapshot` columns on the shift row.

---

## Sync order (Android)

Push and pull phases run in order:

1. **tasks** (before shifts — shifts may reference task IDs)  
2. shifts  
3. refund claims  
4. user settings  
5. **profiles** (display name / `full_name`)  
6. compensation profiles  

Pull runs profiles before compensation profiles and user settings so the display name is available early.

Incremental pull uses `updated_at >= lastPulledAt` per entity (see `SyncCursorStore`).

Local `PENDING_*` rows always win over remote until pushed.

`delete_own_account` removes tasks, shifts, refund claims, compensation profiles, user settings, profiles, and refund-receipt storage objects.

### `deleted_at` and `client_updated_at`

`shifts`, `refund_claims`, `compensation_profiles`, `premium_profiles` and
`tasks` each carry two sync columns, added by
`20260806000000_sync_tombstones_and_row_versions.sql`.

| Column | Type | Meaning |
|--------|------|---------|
| `deleted_at` | timestamptz, null | Set = the row is a tombstone |
| `client_updated_at` | timestamptz, not null | When the *device* last edited the row |

**Deletes are tombstones, never `DELETE`.** A removed row is invisible to an
incremental pull — the other devices ask for rows changed since their cursor, and
a row that no longer exists is not a change — so a real delete only ever reached
devices doing a full pull. Writing `deleted_at` makes a delete an ordinary update
that propagates like any edit. Pulled tombstones soft-delete the local row;
tombstones for rows a device has never held are ignored rather than materialised.

**`client_updated_at` is the edit-version guard.** Every update is filtered
`client_updated_at <= <the value being written>` and asks for the row back, so a
write carrying an older edit than the stored one matches nothing and returns no
row. The client reads that as a conflict and adopts the remote copy instead of
overwriting it — the newer edit wins, which is the same rule the pull side
applies, so the two directions agree. Both `RemoteXUpdate.clientUpdatedAt` and
the local entity's `updatedAt` are the same value; nothing else has to be stored.

A rejected write is never recorded as sent. That includes tombstones: a delete is
an edit and loses to a newer one like any other.

**Uniqueness ignores tombstones.** `shifts_user_id_start_time_live_uidx` is
partial (`where deleted_at is null`), so a start time frees up again once its
shift is deleted.

**One running shift.** Enforced client-side by `RunningShiftResolver`, not by a
database constraint — a unique index would make the second device's clock-in fail
its push permanently, which loses more than the duplicate costs. The rule (keep
the earliest open shift, tombstone the rest, merge across any detail the winner
lacks) is a pure function of the rows, so every device reaches the same answer
independently and they converge.

### Pull paging

Pages are ordered by `(updated_at, id)` and fetched with `range`, and
`pullIncremental` tracks an offset within the current cursor timestamp.

Both parts are load-bearing. Ordering by `updated_at` alone is not a total order,
and Postgres may return tied rows differently on each request, so paging through
it silently skips rows. And because the cursor *is* a timestamp, it cannot
advance through a block of rows that share one — a restore or an import easily
produces more than one page of those — which used to make the pull detect it was
stuck and give up, losing everything past the first page. The offset walks the
block; it resets as soon as the timestamp moves on.

### What the client assumes about RLS

None of the `fetchUpdatedSince` queries filter by user. They rely entirely on the
`*_select_own` policies to scope the result set, so a table added without an RLS
policy leaks straight into every user's Room database.

As a second layer, every pulled row is checked against the signed-in user before
it is applied (`ownerOf` in `pullIncremental`) and skipped if it does not match.
This is not redundant: `SyncWorker` resolves "who am I" from the
`lastActiveUserId` preference while PostgREST resolves it from the session JWT,
and the two can disagree. A non-zero count is reported to crash reporting — it
means either RLS or the session is wrong, and neither should be discovered from a
user's bug report.

`profiles` is the one table whose owner column is `id`, not `user_id`
(`profiles.id` *is* the auth uid).

**Known gap, deliberately not closed.** PostgREST answers a `DELETE` with 204
whether it removed a row or none, so a delete blocked by RLS is
indistinguishable from a delete of a row that was already gone — and the push
path marks both `SYNCED`. If a delete were ever blocked, the next pull would
restore the row after the user had been told it was deleted. It is left alone
because every table has a `*_delete_own` policy of `auth.uid() = user_id`, so
this cannot happen for a user's own rows, while the benign already-deleted case
(a retry after a lost response) is genuinely reachable — treating 0 affected rows
as a failure would leave those rows permanently `FAILED` and permanently in the
"unsynced changes" count. Closing it properly needs an existence probe after a
0-row delete, not just the affected-row count.

---

## Enumerations (wire → Android)

Normalization: trim, uppercase; `-` → `_` where noted. Unknown values use the documented fallback (never crash).

### RefundAction (`shifts.refund_action`)

| Wire (snake_case) | Android | Notes |
|-------------------|---------|-------|
| `no_ride_taken` | `NO_RIDE_TAKEN` | |
| `remind_later` | `REMIND_LATER` | |
| `submitted` | `SUBMITTED` | |
| null / blank | null | unresolved |
| unknown | null | skip or ignore row |

Parser: `RefundAction.fromPersisted()` in `domain/model/Enums.kt`

### RefundDirection (`refund_claims.direction`)

| Wire | Android |
|------|---------|
| `to_work` | `TO_WORK` |
| `from_work` | `FROM_WORK` |
| unknown | `TO_WORK` (fallback) |

### RefundProvider (`refund_claims.provider`)

| Wire (case-insensitive) | Android |
|-------------------------|---------|
| `lime` | `LIME` |
| `dott` | `DOTT` |
| `bird` | `BIRD` |
| `taxi` | `TAXI` |
| other / unknown | `OTHER` |

Android pushes provider with capitalized label (e.g. `Lime`) — pull accepts any case.

### ClockStyle (`user_settings.clock_style`)

| Wire (lowercase) | Android enum | Native render |
|------------------|--------------|---------------|
| `classic` | `CLASSIC` | yes (dedicated card) |
| `minimal` | `MINIMAL` | yes (dedicated card) |
| `aurora` | `AURORA` | yes (dedicated card) |
| `focus`, `bold`, `night`, `retro`, `pulse`, `dial`, `strand`, `prism`, `sand`, `blocks`, `orbit`, `tide`, `sprout`, `metro`, `vinyl`, `luna` | same name | yes (`ExpressiveClockCard`) |
| unknown | `CLASSIC` | |

All persisted styles render natively on the Android dashboard (`SupportedClockStyle.kt`); unknown values fall back to Classic.

**Packs do not change this column.** The faces are grouped into packs the user adds
or removes (`ClockFaceGroup`), and which packs a user has is **device-local**, not
synced — removing a pack on a tablet must not remove it from the phone. Only the
selected face syncs, and it is the column above.

That split creates one case worth stating: a user can sync a `clock_style` from a
pack this device does not have. It is handled by deriving availability as
`stored packs + bundled + the pack holding the selected face`
(`ClockFacePacks.available`), so the selected face is reachable by construction and
there is no migration step to run. A server value from a pack the device has not
added therefore renders normally and appears in the gallery as available.

Adding a face means adding it to a `ClockFaceGroup`; `ClockFaceCatalogTest` fails if
a face belongs to no group, because the gallery is the only place all faces are
listed.

### RegionCode

| Wire (case-insensitive) | Android |
|-------------------------|---------|
| `il` | `IL` |
| `us` | `US` |
| `us_ca` | `US_CA` |
| `gb` | `GB` |
| `eu` | `EU` |
| `custom` | `CUSTOM` |
| unknown | `IL` (fallback) |

### StackingPolicy (`compensation_profiles.stacking_policy`)

Aligned with `premium_profiles.premium_type` since migration `20260706000000`: both
accept the same six wire values, and matching options combine rates with identical math
(`PremiumStacking`).

| Wire | Android | Notes |
|------|---------|-------|
| `highest_only` | `HIGHEST_ONLY` | |
| `additive` | `ADDITIVE` | |
| `multiplicative` | `MULTIPLICATIVE` | |
| `base_plus_premium` | `BASE_PLUS_PREMIUM` | |
| `premium_in_regular_rate` | `PREMIUM_IN_REGULAR_RATE` | combines like `highest_only`; OT-base semantics apply to shift premiums only. Not offered in the rules picker. |
| `excluded_from_regular_rate` | `EXCLUDED_FROM_REGULAR_RATE` | combines like `highest_only`; OT-base semantics apply to shift premiums only. Not offered in the rules picker. |
| unknown | `HIGHEST_ONLY` (fallback) | |

The same values are accepted for the per-rule `stacking` fields inside `rules_json`
(weekend/holiday/night).


## Adding a new enum value (checklist)

- [ ] Migration updates DB check constraint if applicable
- [ ] Kotlin `enum` entry added
- [ ] `fromPersisted()` updated with fallback documented here
- [ ] `RemoteMapper.kt` normalize on ingest if web used different casing
- [ ] Unit test in `PersistedEnumParsingTest` or mapper tests
- [ ] This file updated
