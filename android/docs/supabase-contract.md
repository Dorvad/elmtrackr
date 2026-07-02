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
| `tasks` | Paid-project tasks for clock-in (synced per user) |

Full DDL: [`supabase/schema.sql`](../../supabase/schema.sql)

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
| `features_travel_refunds` | bool | | bool |
| `features_paid_projects` | bool | | bool |
| `features_insights` | bool | | bool |
| `features_clock_styles` | bool | | bool |
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
| `stacking_policy` | text | `highest_only`, `additive` | `StackingPolicy` |
| `effective_from` / `effective_until` | timestamptz | ISO-8601 | Instant |
| `is_default` / `is_archived` | bool | | bool |

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
| `classic` | `CLASSIC` | yes |
| `minimal` | `MINIMAL` | yes |
| `aurora` | `AURORA` | yes |
| `focus`, `bold`, `night`, `retro`, `pulse`, `dial`, `strand`, `prism`, `sand`, `blocks`, `orbit` | same name | fallback to Classic |
| unknown | `CLASSIC` | |

Only Classic, Minimal, and Aurora render natively on Android dashboard; others persist but display as Classic.

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

| Wire | Android |
|------|---------|
| `highest_only` | `HIGHEST_ONLY` |
| `additive` | `ADDITIVE` |


## Adding a new enum value (checklist)

- [ ] Migration updates DB check constraint if applicable
- [ ] Kotlin `enum` entry added
- [ ] `fromPersisted()` updated with fallback documented here
- [ ] `RemoteMapper.kt` normalize on ingest if web used different casing
- [ ] Unit test in `PersistedEnumParsingTest` or mapper tests
- [ ] This file updated
