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

Incremental pull uses `updated_at > lastPulledAt` per entity (see `SyncCursorStore`). Remote rows missing locally are treated as deletes until server-side `deleted_at` tombstones land.

Local `PENDING_*` rows always win over remote until pushed.

`delete_own_account` removes tasks, shifts, refund claims, compensation profiles, user settings, profiles, and refund-receipt storage objects.

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
| `focus`, `bold`, `night`, `retro`, `pulse`, `dial`, `strand`, `prism`, `sand`, `blocks`, `orbit` | same name | yes (`ExpressiveClockCard`) |
| unknown | `CLASSIC` | |

All persisted styles render natively on the Android dashboard (`SupportedClockStyle.kt`); unknown values fall back to Classic.

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
