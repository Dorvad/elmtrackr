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
| `projects` | Fixed-fee projects |
| `project_billing_records` | What has been billed against a project |
| `project_payments` | What has been received against a billing record |
| `workplaces` | A job. Owns leave entitlement and payslip balances |
| `leave_policies` | A workplace's sick/vacation rules, effective-dated |
| `absence_events` | A period of absence as the user experienced it (user-level) |
| `absence_allocations` | One absent date at one workplace, priced |
| `leave_balance_snapshots` | A balance read off a payslip, kept as history |

Base DDL for `profiles`, `user_settings`, `shifts`, and `refund_claims` predates the
in-repo migrations and lives in the Supabase project itself (not in this repo).
Everything added since is under [`supabase/migrations/`](../../supabase/migrations/),
applied in filename order. This document is the authoritative column list for the
base tables.

### Paid Projects

| Room entity | Table |
|-------------|-------|
| `ProjectEntity` | `projects` |
| `ProjectBillingRecordEntity` | `project_billing_records` |
| `ProjectPaymentEntity` | `project_payments` |

Added by `20260806100000_paid_projects.sql`. Before it these three tables had no
Supabase counterpart at all, so a project and the record of money owed for it
lived on one device and nowhere else — a reinstall or a new phone lost them
unless the user had exported a local backup first.

Column names, nullability and semantics mirror the Room entities exactly. The
app is the only writer and the shapes were already settled; a second, subtly
different model on the server would be a source of bugs rather than a
refinement.

**Money columns are `text`, not `numeric`.** Room stores them as canonical
decimal strings because binary floating point cannot represent most decimal
amounts exactly and a stored fee must round-trip byte for byte. `numeric` would
hold the value correctly in Postgres, but PostgREST serialises numeric as a JSON
number — so the value would pass through a double on its way to and from the
client, which is the exact conversion the local schema exists to avoid. A check
constraint keeps non-numeric text out, and the app does all project arithmetic
client-side (`ProjectFee`, `ProjectMetrics`), so nothing needs to sum these in
SQL. Kotlin writes them with `BigDecimal.toPlainString()`; `toString()` would
emit scientific notation at some scales and fail the constraint.

**Calendar dates are epoch-day integers** (`billed_on`, `paid_on`, a project's
`deadline`), matching Room, so a due date cannot shift by a day through a
timezone conversion.

**Foreign keys are real**, so sync order matters: projects, then billing
records, then payments, in both directions. A child pushed before its parent is
rejected; a child pulled before its parent has no local row to attach to, so the
pull holds its cursor and retries next run.

**Still local-only:** the `user_settings` columns holding Paid Projects
*defaults* for new projects (region, currency, tax label and rate). They are
preserved across pulls by `preserveLocal` in `pullUserSettings`. They are per-
device preferences rather than records of money, so losing them on a device
change is an annoyance rather than data loss — but closing that gap means adding
those columns to `user_settings` and dropping the `preserveLocal` clause.

### Workplaces and leave

| Room entity | Table |
|-------------|-------|
| `WorkplaceEntity` | `workplaces` |
| `LeavePolicyEntity` | `leave_policies` |
| `AbsenceEventEntity` | `absence_events` |
| `AbsenceAllocationEntity` | `absence_allocations` |
| `LeaveBalanceSnapshotEntity` | `leave_balance_snapshots` |

Added by `20260811000000_workplaces_and_leave.sql` (Room v19, `MIGRATION_18_19`).

**Absences are not shifts, deliberately.** A shift means worked time and feeds net
minutes, the overtime ladders, weekend/holiday/night premiums and the shift count.
An absence has none of those properties, so there is no `shiftType` column and no
synthetic eight-hour shift: a sick day recorded that way would inflate all four
and invent overtime nobody worked. The two domains meet only at the money level,
where a report adds work, vacation and sick gross into an estimated total.

**`workplaces` is not `compensation_profiles` — but the split is internal now.**
A profile answers "how is worked time paid" and changes with a raise or a new
effective-dated profile. Leave entitlement, seniority and payslip balances belong
to the employer and survive all of that, so they hang off a workplace instead.

To the *user* there is one thing, a **work profile**, and the word "workplace"
appears nowhere in the app. Every profile owns exactly one workplace, created with
it; the two rows stay separate so entitlement outlives a wage change, which is the
only reason they are two. Do not expose the workplace as a second concept, and do
not merge the tables to make the model look simpler — that would trade a real
guarantee for a cosmetic one. Added by
`20260824000000_work_profile_identity_and_task_scope.sql`, which also gives a
profile its `color`/`icon` and scopes `tasks` to one.

**Money here is `numeric`, not the `text` Paid Projects uses.**
`absence_allocations.estimated_gross_pay` and `leave_balance_snapshots.balance`
are `numeric` and map to Kotlin `Double`, matching
`compensation_profiles.base_hourly_rate` and every other wage figure. The two are
different kinds of number: a billed fee is an amount of money that must
round-trip byte for byte, while this is a derived estimate recomputed from an
hourly rate that is itself a REAL/`Double`, and it is labelled an estimate
everywhere it appears. Storing it as text would mean the reports could not add
work and leave gross together without crossing between two numeric systems.

**Calendar dates are epoch-day integers** (`affected_date`, `as_of_date`,
`start_date`, `end_date`, `employment_start_date`), matching Paid Projects, so an
absence cannot move by a day through a timezone conversion.

**`rules_json`, `policy_snapshot_json` and `calculation_snapshot_json`** are
`jsonb`, read through `LeavePolicyCodec` with a documented default for every
field. The two snapshot columns follow the same rule as
`shifts.compensation_snapshot_json`: a historical estimate stays reproducible
after a wage or policy change and is never silently recomputed.

**Foreign keys are real**, so sync order matters: workplaces, then leave policies
and balance snapshots, then absence events, then absence allocations. The two
`workplace_id` links added to `shifts` and `compensation_profiles` are
`on delete set null`, so archiving or removing a workplace never deletes work
history.

**Not backfilled, on purpose.** Both `workplace_id` columns — and
`tasks.compensation_profile_id`, added later on the same principle — are nullable
with no default and no backfill; NULL reads as "not assigned yet", which is what
every row written before their release is. For a task, NULL is read as the default
work profile rather than as no profile, so an upgrading user keeps their whole task
list. Rows join a workplace the first time the user
has one (`LocalWorkplacesRepository.ensureDefaultWorkplace`, which also creates
the default policy), where it is an ordinary edit that syncs like any other
rather than an upgrade rewriting recorded history.

**Extra RLS on the child tables.** Beyond `auth.uid() = user_id`, the three
tables carrying a parent reference also check `owns_workplace()` /
`owns_absence_event()`, so a row cannot *point at* a parent the caller does not
own. The check constraints are `not valid`, which skips the one-time scan: the
tables are new so there is nothing to scan, and `auth.uid()` is null outside a
request, so validating would evaluate the predicate with no session.

**`delete_own_account` was extended** with the five tables, children first. Sick
leave is the most sensitive data this app stores, so account deletion names them
explicitly rather than relying on the `auth.users` cascade.

**Client-side sync is not wired yet.** The tables, RLS and account deletion are
in place, and the Room entities carry the full `remoteId` / `syncStatus` /
`deletedAt` / `lastSyncedAt` block, but `SyncRepositoryImpl` has no push or pull
step for them and there is no `data/remote/RemoteLeave*`. Leave is therefore
device-local until that is added — the same order Paid Projects shipped in (Room
v16 first, cloud sync in a later change). Until then a reinstall loses reported
leave and entered balances unless the user exported a local backup.

---

## `workplaces`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `name` | text | non-empty | string |
| `region_code` | text | see RegionCode | `RegionCode.fromPersisted()` |
| `currency_code` | text | e.g. `ILS` | string |
| `timezone` | text | IANA | string |
| `employment_start_date` | integer | epoch day, nullable | `LocalDate?` |
| `is_default` / `is_archived` | bool | | bool |

## `leave_policies`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `workplace_id` | uuid | FK → `workplaces.id` | `workplaceLocalId` |
| `region_code` | text | see RegionCode | `RegionCode.fromPersisted()` |
| `rules_json` | jsonb | LeavePolicyRules shape | `LeavePolicyCodec` |
| `effective_from` / `effective_until` | timestamptz | ISO-8601 | Instant |
| `is_active` | bool | | bool |

## `absence_events`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `type` | text | `sick`, `vacation` | `AbsenceType.fromPersisted()` |
| `start_date` / `end_date` | integer | epoch day | LocalDate |
| `notes` | text | nullable, never medical detail | string? |

DB check constraint: `type in ('sick', 'vacation')`

## `absence_allocations`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `absence_event_id` | uuid | FK → `absence_events.id` | `absenceEventLocalId` |
| `workplace_id` | uuid | FK → `workplaces.id` | `workplaceLocalId` |
| `affected_date` | integer | epoch day | LocalDate |
| `entitlement_units` | numeric | 1 = whole day, 0.5 = half | double |
| `unit` | text | `days`, `hours` | `LeaveBalanceUnit.fromPersisted()` |
| `expected_work_minutes` | integer | nullable | Int? |
| `policy_snapshot_json` | jsonb | nullable | `LeavePolicyCodec` |
| `calculation_snapshot_json` | jsonb | nullable | `LeavePolicyCodec` |
| `estimated_gross_pay` | numeric | derived estimate | double |

Check constraints: `unit in ('days', 'hours')`

Deliberately **no** unique index on `(absence_event_id, workplace_id,
affected_date)`. The client writes exactly one row per triple inside one
transaction, and editing an event rebuilds its allocations as
tombstone-plus-insert; a unique index would let a rebuild whose tombstone had not
yet been pushed collide with its own replacement and leave the push permanently
FAILED — the same reason `shifts_user_id_open_idx` is not unique.

## `leave_balance_snapshots`

| Column | Type | Wire format | Android |
|--------|------|-------------|---------|
| `workplace_id` | uuid | FK → `workplaces.id` | `workplaceLocalId` |
| `balance_type` | text | `sick`, `vacation` | `AbsenceType.fromPersisted()` |
| `balance` | numeric | may be negative | double |
| `unit` | text | `days`, `hours` | `LeaveBalanceUnit.fromPersisted()` |
| `as_of_date` | integer | epoch day | LocalDate |
| `source` | text | `payslip`, `manual` | `LeaveBalanceSource.fromPersisted()` |
| `label` / `notes` | text | nullable | string? |

Check constraints: `balance_type in ('vacation','sick')`, `unit in ('days','hours')`,
`source in ('payslip','manual')`

No unique constraint on `(workplace_id, balance_type, as_of_date)`: correcting a
mistyped balance for a date already entered is normal, and the estimator reads
the latest by `as_of_date` then by `created_at`, so a correction supersedes the
earlier row while leaving it in the history.

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
| `workplace_id` | uuid | nullable FK → `workplaces.id`, never backfilled | string? |

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
| `workplace_id` | uuid | nullable FK → `workplaces.id`, never backfilled | string? |

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

0. **workplaces → leave policies / balance snapshots → absence events → absence
   allocations** (real foreign keys; workplaces also before shifts and
   compensation profiles, which carry a workplace link). *Not yet implemented —
   see "Client-side sync is not wired yet" above.*
1. **tasks** (before shifts — shifts may reference task IDs)  
2. **projects → project billing records → project payments** (real foreign keys;
   also before shifts, which carry a project link)  
3. shifts  
4. refund claims  
5. user settings  
6. **profiles** (display name / `full_name`)  
7. compensation profiles  

Pull runs profiles before compensation profiles and user settings so the display name is available early.

Incremental pull uses `updated_at >= lastPulledAt` per entity (see `SyncCursorStore`).

Local `PENDING_*` rows always win over remote until pushed.

`delete_own_account` removes absence allocations, absence events, leave balance snapshots, leave policies, tasks, shifts, refund claims, compensation profiles, workplaces, user settings, profiles, and refund-receipt storage objects.

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


### AbsenceType (`absence_events.type`, `leave_balance_snapshots.balance_type`)

| Wire | Android |
|------|---------|
| `sick` | `SICK` |
| `vacation` | `VACATION` |
| unknown / null | **null — the row is skipped** |

The one enum here with no fallback, on purpose. Guessing between sick and
vacation would put a day against the wrong balance and pay it under the wrong
policy, which is worse than dropping a row this build did not write.

### LeaveBalanceUnit (`absence_allocations.unit`, `leave_balance_snapshots.unit`)

| Wire | Android |
|------|---------|
| `days` | `DAYS` |
| `hours` | `HOURS` |
| unknown | `DAYS` (fallback) |

### LeaveBalanceSource (`leave_balance_snapshots.source`)

| Wire | Android |
|------|---------|
| `payslip` | `PAYSLIP` |
| `manual` | `MANUAL` |
| unknown | `MANUAL` (fallback — the weaker of the two claims) |

### Leave pay bases (inside `leave_policies.rules_json`)

`sick.payBasis`: `historical_average` (fallback), `scheduled_hours`,
`fixed_daily_hours`, `manual`.

`vacation.payBasis`: `israel_statutory_average_90` (fallback),
`actual_workdays_average`, `scheduled_hours`, `fixed_daily_hours`, `manual`.

Sick pay tiers are data, not code: `payTiers` is a list of
`{fromDay, toDay, multiplier}`, and the Israeli default of 0% / 50% / 50% / 100%
is three of them. A workplace with a better agreement replaces it with a single
`{fromDay: 1, multiplier: 1.0}` rung, and no code path asks whether a workplace
is Israeli to decide what a sick day pays. A day the ladder does not cover
resolves to *no tier* rather than to zero — zero is a real multiplier here, so it
cannot also mean "the policy does not say".

## Adding a new enum value (checklist)

- [ ] Migration updates DB check constraint if applicable
- [ ] Kotlin `enum` entry added
- [ ] `fromPersisted()` updated with fallback documented here
- [ ] `RemoteMapper.kt` normalize on ingest if web used different casing
- [ ] Unit test in `PersistedEnumParsingTest` or mapper tests
- [ ] This file updated
