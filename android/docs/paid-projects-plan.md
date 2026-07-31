# Paid Projects — technical implementation plan

**Status:** Proposal (Prompt 0 audit). No application code changed.
**Scope:** Native Android app (`android/`) + Supabase contract (`supabase/`).
**Branch:** `claude/paid-projects-android-bq9i2q`

---

## 1. Repository architecture summary

### 1.1 Modules and toolchain

| Module | Contents |
|--------|----------|
| `android/app` | The phone/tablet app. Everything below lives here unless noted. |
| `android/wear` | Wear OS companion (tile, complication, minimal UI). |
| `android/wear-sync` | Shared codec/message contract between phone and watch (pure Kotlin). |

AGP 9.0.0, Kotlin 2.3.21, KSP, JDK 17, `compileSdk`/`targetSdk` 36, `minSdk` 26.
Compose BOM 2024.12.01, Material 3, Navigation-Compose 2.8.5, Room 2.7.2,
Hilt 2.60.1, WorkManager 2.10.0, DataStore, kotlinx.serialization, SQLCipher 4.6.1,
Supabase-kt 3.2.2, Paparazzi 2.0.0-alpha05, Sentry.

CI (`.github/workflows/android.yml`): three jobs — `assembleDebug` + `testDebugUnitTest`
+ `lintDebug`; an emulator job running **only** `ElmTrackrDatabaseMigrationTest`,
`ShiftDaoTest`, `SettingsDaoTest` on API 30; and `bundleRelease` + `lintVitalRelease`.

The Next.js web app at the repo root is **frozen** per `ANDROID_FIRST.md`. Nothing in
this plan touches it.

### 1.2 Layering

```
ui/<feature>/          Screen.kt + UiState.kt + ViewModel.kt  (Compose + MVVM)
navigation/            AppRoute, AppNavGraph, MainScaffold, BottomNavItem
ui/navigation/         ElmSideNavigation (tablet rail)
domain/                pure calculators (no Android deps): PayrollCalculator,
                       MonthlyReportBuilder, TaskMonthlyReportBuilder, MoneyFormatter…
domain/model/          immutable data classes + enums with fromPersisted()
domain/repository/     repository interfaces
data/repository/       Local*Repository implementations (Room-backed)
data/local/            ElmTrackrDatabase, entity/, dao/, mapper/, converter/, preferences/
data/remote/           Remote*DataSource + Remote*Models (@SerialName) + Remote*Mapper
data/sync/             SyncRepositoryImpl, SyncWorker, cursors, LocalBackup{Format,Exporter,Importer}
di/                    Hilt modules (AppModule, DatabaseModule, RepositoryModule, …)
```

State management: `StateFlow` in `@HiltViewModel` ViewModels, `combine` +
`flatMapLatest` over repository flows, `stateIn(SharingStarted.WhileSubscribed(5_000))`,
sealed `UiState` interfaces (`Loading` / `Ready` / `Error`). Heavy payroll transforms are
pushed onto an injected `@ComputationDispatcher`.

DI: Hilt with `SingletonComponent`. `RepositoryModule` `@Binds` each interface to its
`Local*` implementation; `DatabaseModule` `@Provides` the DB and every DAO;
`di/entrypoint/AppEntryPoints` + `BackgroundEntryPoint` give receivers/workers access
outside the Hilt graph.

### 1.3 Navigation (two nested NavHosts)

**Root** — `navigation/AppNavGraph.kt`: a `NavHost` over `AppRoute.{LOADING, AUTH,
ONBOARDING, MAIN}`. `AppShellViewModel.navState` (auth profile + `SessionBootstrapGate`
+ `settings.onboardingCompleted`) drives a `LaunchedEffect` that navigates with
`popUpTo(0) { inclusive = true }`.

**Tabs** — `navigation/MainScaffold.kt`: a **second** `rememberNavController()` with its
own `NavHost`, `startDestination = BottomNavItem.DASHBOARD.route`. Tab switches use:

```kotlin
navController.navigate(route) {
    popUpTo(BottomNavItem.DASHBOARD.route) { saveState = true }
    launchSingleTop = true
    restoreState    = true
}
```

`BottomNavItem` (enum, `navigation/BottomNavItem.kt`) is the single source of truth for
the four tabs and is consumed in exactly four places:

1. `ElmBottomNav` in `MainScaffold.kt` (phone bar) — `BottomNavItem.entries.forEach`
2. `ElmSideNavigation` in `ui/navigation/ElmSideNavigation.kt` (tablet rail) — same
3. `routeIndex()` in `MainScaffold.kt` — enum ordinal drives slide direction
4. `app/src/test/.../navigation/BottomNavItemTest.kt` — asserts exactly four tabs

Phone vs tablet is decided by `ui/layout/AppWindowSize.kt`
(`smallestScreenWidthDp >= 600`, stable across rotation) — `MainScaffold` branches
early and renders the rail + NavHost in a `Row`, `return`ing before the `Scaffold`.
Both branches share the same `navController`.

`hideNavChrome` (a `rememberSaveable` boolean fed by `onFormVisibilityChanged`) hides
the bar/rail while the shift form is open.

**Deep links:** the manifest registers only `elmtrackr://auth` (Supabase auth
callbacks), handled imperatively in `MainActivity.handleDeepLink` — it never touches the
nav graph. App shortcuts and widgets act through `HeadlessTrampolineActivity` /
broadcast receivers, not routes. **There are no deep links into tab destinations today.**

**Process-death restoration:** Navigation-Compose persists its own back stack.
`MainScaffold` keeps `replayOnboarding`, `pendingShiftEditId`, `pendingSettingsLaunch`
and `hideNavChrome` in `rememberSaveable`. Active-shift side effects are restored
outside the UI by `notification/ActiveShiftRestorer` (called from
`BootCompletedReceiver`) and `startup/AppStartupCoordinator`; `ElmTrackrDatabase.preWarm`
opens the encrypted DB on a background thread at process start.

### 1.4 Onboarding and the feature selector

`ui/onboarding/OnboardingScreen.kt` is a nine-step wizard driven by a local
`rememberSaveable` `step: Int` (language → welcome → region → profile → pay → work week
→ **features** → security → review). Step 7 is `FeaturesStep`, which today exposes two
switches: travel refunds and insights. `ReviewStep` reports
`enabledCount = listOf(travelRefunds, insights, enableAppLock).count { it }`.
`OnboardingViewModel.completeOnboarding` writes settings, creates/updates the default
compensation profile from `RegionPresets`, and sets `onboardingCompleted`.

Settings hub: `ui/settings/SettingsScreen.kt` + `SettingsDetailScreens.kt`.
`FeaturesDetailScreen` shows three toggles (travel refunds, insights, overtime
reminders). Flags are edited locally in `rememberSaveable` state and committed through a
save bar → `SettingsViewModel.saveSettings(featureFlags = SettingsFeatureFlags(...))`.

### 1.5 Settings persistence and the existing `featuresPaidProjects` flag

Two stores:

* **Room `user_settings`** — synced, per-user domain settings (`UserSettings`), including
  all five `features*` flags. `LocalSettingsRepository.createDefaultSettings` inserts a
  `UserSettings()` with `PENDING_CREATE`.
* **DataStore** (`data/local/preferences/`) — device-local, unsynced: theme, language,
  reduce-motion, crash reports, app lock, onboarding-seen, setup-checklist bookkeeping.

**`featuresPaidProjects` already exists end to end and is inert:**

| Layer | Location |
|-------|----------|
| Domain | `UserSettings.featuresPaidProjects` (default `false`, with a "reserved / do not surface" comment) |
| Room | `UserSettingsEntity.featuresPaidProjects` — present since schema **v1** |
| Mapper | `UserSettingsMapper` both directions |
| Remote | `RemoteUserSettingsModels` `@SerialName("features_paid_projects")`, `RemoteUserSettingsMapper` |
| Supabase | `user_settings.features_paid_projects boolean not null default false` |
| Backup | `UserSettingsBackupRow.featuresPaidProjects` (format v3) |
| Settings VM | `SettingsFeatureFlags.paidProjects` → `saveSettings` (covered by `SettingsViewModelTest`) |
| Onboarding | `OnboardingInput.featuresPaidProjects`, preserved on replay |

What is missing: a UI toggle (`FeaturesDetailScreen` omits it), an onboarding card
(`OnboardingScreen.kt:317` hardcodes `false`), and any consumer.
`docs/product-quality-audit.md` P1-2 flags it as a dead flag.

**Consequence: no migration is needed for the flag.** It already round-trips through
sync and backup, and every existing user has it `false`, so enabling the module ships
invisible by default.

### 1.6 Database and migrations

`ElmTrackrDatabase` (`data/local/ElmTrackrDatabase.kt`) — Room, **version 14**,
`exportSchema = true`, eight entities, encrypted with SQLCipher via
`SupportOpenHelperFactory` and a passphrase from `DatabasePassphraseStore`;
`PlaintextDatabaseMigrator` upgrades pre-encryption installs. Thirteen hand-written
`Migration` objects, all `internal val` in the companion so the instrumented test can
reference them.

Every table shares a sync envelope: `localId` (TEXT PK, UUID), `remoteId`, `userId`,
`createdAt`/`updatedAt` (epoch millis), `deletedAt` (soft delete), `syncStatus`
(`SyncStatus` enum via `Converters`), `lastSyncError`, `lastSyncedAt`. Index convention:
`userId`, `(userId, syncStatus)`, `remoteId`.

Two migrations in this file are repairs for previously shipped mistakes and are worth
reading before writing a new one:

* `MIGRATION_8_9` — `MIGRATION_7_8` created indexes that were never declared on the
  entities, so Room's post-migration validation rejected every upgraded database.
* `MIGRATION_11_12` — the original 2→3 migration created snake_case columns that never
  matched the camelCase entity fields; v12 rebuilds `user_settings` and `shifts`.

Exported schemas live in `app/schemas/…/{1..14}.json` and are wired into `androidTest`
assets. **`9.json` is missing** — a pre-existing gap.

### 1.7 Shift and timer tracking

`ShiftEntity` / `Shift` — `startTime`, nullable `endTime` (null = clocked in),
`breakMinutes`, `notes`, `isSpecialDay`, `forceRegularRate`, `premiumProfileId`,
`refundAction`, `compensationProfileId`, `compensationSnapshotJson`, and four task
columns. Unique index on `(userId, startTime)` — the sync engine's natural key.

Clock-in/out through `ShiftsRepository.clockIn/clockOut`; also reachable headlessly from
widgets (`ClockInWidgetAction`), shortcuts (`ClockInActions`), the notification
(`ClockOutReceiver`), and Wear (`WearActions`). Side effects (ongoing notification,
overtime reminders, Wear snapshot, widget repaint) are centralised in
`sideeffects/ActiveShiftSideEffectsCoordinator`. Live timers render via
`ui/components/motion/LiveClockTimer`.

### 1.8 Tasks and rates — how a shift is associated with a task or rate

Two independent mechanisms:

**Task** — `shifts.taskId` (nullable) plus denormalized snapshots
`taskNameSnapshot`, `taskIconSnapshot`, `taskHourlyRateSnapshot`. Set at clock-in
(`TaskClockInHelper.paramsFromTask`, auto-suggested by `TaskHabitSuggestionBuilder`),
editable in the shift form via `TaskSnapshotApplier`. Snapshots survive task deletion.

**Compensation profile** — `shifts.compensationProfileId` (nullable, else
`settings.defaultCompensationProfileId`), with `compensationSnapshotJson` frozen at
clock-out as a fallback when the profile is gone.

`CompensationResolver.resolveShiftCompensation` picks live profile → snapshot → legacy
settings, then:

```kotlin
// CompensationResolver.kt:112-113
val taskRate = shift.taskHourlyRateSnapshot?.takeIf { it > 0 }
val withRate = if (taskRate != null) resolved.copy(baseHourlyRate = taskRate) else resolved
```

**The task's hourly rate becomes the employee's base pay rate for that shift.** It flows
into `PayrollCalculator`, `IsraeliCompensationEngine`, every overtime and premium tier,
`MonthlyPaySummary`, the dashboard, reports, the CSV export, and the Wear/widget
snapshots. This single fact determines the shift↔project recommendation in §4.

Note also a terminology collision: `docs/supabase-contract.md` already describes the
`tasks` table as *"Paid-project tasks for clock-in"*, and
`docs/product-strategy.md` calls task rates *"paid-project (task) rates"*. The new
module needs distinct user-facing and internal naming.

### 1.9 Overtime and premium calculations

`domain/PayrollCalculator.kt` (766 lines) plus `domain/compensation/`
(`CompensationResolver`, `IsraeliCompensationEngine`, `RegionPresets`,
`CompensationRulesCodec`, `StackingPolicyLabels`) and `domain/premium/PremiumStacking`.
Rules live in `compensation_profiles.rules_json`: daily/weekly standards and tier
ladders, weekend/holiday/night multipliers, rounding, auto-break deduction, minimum
shift, seventh-consecutive-day, deductions, six stacking policies. Israel has a
dedicated engine; other regions go through `calculateGenericShiftPay`.
Everything is labelled an *estimate* (`COMPENSATION_ESTIMATE_NOTE`,
`COMPENSATION_DISCLAIMER`).

### 1.10 Reports and dashboard summaries

`LocalReportsRepository` observes settings → resolves the work zone →
`shiftDao.observeShiftsByDateRange` → `MonthlyReportBuilder` / `WeeklyBreakdownBuilder`.
`ReportsViewModel` combines that with shifts, tasks, refund claims, compensation and
premium profiles and computes `PayrollCalculator.MonthlyPaySummary` on the computation
dispatcher; `ReportsUiState.Ready` carries ~15 defaulted fields, so adding one is
non-breaking. `TaskMonthlyReportBuilder` is the closest precedent for a per-entity
breakdown: it groups completed shifts by `taskId` (falling back to the name snapshot),
reuses a per-shift breakdown cache, and returns `null`-able pay when no rate resolves.
`ReportExporter` writes CSV/share output. `DashboardViewModel` mirrors the pattern for
the current month plus active shift, task selector, setup checklist and insights.

### 1.11 Currency, decimal and monetary handling

* `CurrencyCode` enum — ILS, USD, EUR, GBP, CAD, AUD, JPY (0 digits), CHF, with
  `fractionDigits` and `from(value)` fallback to ILS.
* Effective currency is `UserSettings.displayCurrencyCode()` = `currencyCode ?: currency.name`,
  mirrored from the default compensation profile.
* `MoneyFormatter` formats symbol-first with `Locale.US` grouping and `HALF_UP`, and
  falls back to `java.util.Currency` for unknown codes.
* **All money is `Double`** — `hourlyRate`, `baseHourlyRate`, `RefundClaimEntity.amount`,
  every `*Gross` field; Postgres side is `numeric`.
* **There is no currency conversion anywhere.** `MonthlyPaySummary.currencyCode` is
  simply overwritten by each shift in the loop, so a mixed-currency month already
  reports the last shift's code — a pattern not to copy.

### 1.12 Localization, Hebrew, RTL

English in `res/values/`, Hebrew in `res/values-iw/`, both split into 17 parallel files
(`strings_settings.xml`, `strings_reports.xml`, `strings_tasks.xml`, …).
`res/xml/locales_config.xml` declares `en` and `he`; `MainActivity` extends
`AppCompatActivity` so `AppCompatDelegate.setApplicationLocales` persists the in-app
language on API ≤ 32, with `AppLocalesMetadataHolderService` in the manifest.
`android:supportsRtl="true"`. RTL support uses `Icons.AutoMirrored.*`,
`ui/design/RtlMirror.kt`, and `ui/common/AppLocale.kt`. Enum labels are mapped to
string resources through helper objects (`PremiumTypeLabels`, `StackingPolicyLabels`) —
though `PayrollCalculator` still builds English bracket labels in code.

### 1.13 Notifications

`notification/`: `NotificationChannels.createAll` at startup;
`ActiveShiftNotificationManager` (ongoing notification with a Clock Out action);
`OvertimeReminderPolicy` + `OvertimeReminderScheduler`; user-configurable
`ReminderRule` / `ReminderRulesStore` / `ReminderRuleWorker` (WorkManager);
`NotificationPermissionCoordinator` for the API 33+ runtime prompt.

### 1.14 Export, backup, sync

`LocalBackupFormat.kt` — versioned, full-fidelity JSON (`BACKUP_FORMAT_VERSION = 3`,
`MIN_SUPPORTED = 2`), one `*BackupRow` per entity with every column and nullable
defaults so older documents still parse. `LocalBackupExporter` / `LocalBackupImporter`
plus `SyncBackupShare` in settings.

`SyncRepositoryImpl` (1119 lines) — offline-first push/pull per entity with
`SyncCursorStore` incremental cursors (`updated_at > lastPulledAt`), `SyncIdMapper`
local↔remote id mapping, optimistic-concurrency `markSyncedIfUnchanged`, a mutex, and a
documented order: **tasks → shifts → refund claims → user settings → profiles →
compensation profiles** (tasks first because shifts reference task ids).
`LocalUserDataCleaner` deletes every table in one transaction; `delete_own_account`
does the server side.

---

## 2. Relevant files

**Must change**

| File | Change |
|------|--------|
| `data/local/ElmTrackrDatabase.kt` | version 15, three new entities, three new DAOs, `MIGRATION_14_15` |
| `data/local/entity/ShiftEntity.kt` | `projectId`, `projectNameSnapshot` |
| `domain/model/Shift.kt` | same two fields |
| `data/local/mapper/ShiftMapper.kt` | map the new fields |
| `di/DatabaseModule.kt`, `di/RepositoryModule.kt` | provide/bind the new DAOs + repository |
| `navigation/BottomNavItem.kt` | `PROJECTS` entry + flag predicate + `visibleItems()` |
| `navigation/MainScaffold.kt` | flag collection, conditional bar/rail items, unconditional destination, disabled-route guard |
| `ui/navigation/ElmSideNavigation.kt` | consume `visibleItems()` |
| `ui/settings/SettingsDetailScreens.kt` | Paid Projects toggle in `FeaturesDetailScreen` |
| `ui/settings/SettingsScreen.kt` | wire the toggle into the save bar / `SettingsFeatureFlags` |
| `ui/onboarding/OnboardingScreen.kt` | `FeaturesStep` third card, `enabledCount`, drop the hardcoded `false` at :317 |
| `ui/onboarding/OnboardingViewModel.kt` | `featuresPaidProjects` no longer needs the `preserveExisting` guard once a step exists |
| `domain/model/UserSettings.kt` | remove the "reserved / do not surface" comment |
| `data/sync/LocalBackupFormat.kt` + importer/exporter | new rows, shift fields, `BACKUP_FORMAT_VERSION = 4` |
| `data/local/LocalUserDataCleaner.kt` | delete the three new tables |
| `res/values/`, `res/values-iw/` | new `strings_projects.xml` (both) |
| `app/schemas/…/15.json` | exported schema |
| `docs/supabase-contract.md` | new tables, columns, enums, sync order |
| `supabase/migrations/` | new migration + `delete_own_account` update |

**New**

```
domain/model/Project.kt                      Project, ProjectBillingRecord, ProjectPayment
domain/model/ProjectEnums.kt                 ProjectWorkStatus, ProjectBillingStatus, ProjectTaxMode
domain/projects/ProjectFinancials.kt         pure money/hours engine
domain/projects/ProjectBillingStatusResolver.kt
domain/projects/ProjectMonthlyReportBuilder.kt
domain/repository/ProjectsRepository.kt
data/local/entity/Project*Entity.kt          three entities
data/local/dao/Project*Dao.kt                three DAOs
data/local/mapper/ProjectMapper.kt
data/repository/LocalProjectsRepository.kt
ui/projects/ProjectsScreen.kt / ProjectsUiState.kt / ProjectsViewModel.kt
ui/projects/ProjectFormUi.kt, ProjectDetailUi.kt, ProjectStatusLabels.kt
data/remote/RemoteProject*.kt                (deferred to the sync prompt)
```

**Read-only, must not change** — `domain/PayrollCalculator.kt`,
`domain/compensation/*`, `domain/premium/*`, `wear/`, `widget/`, `wear-sync/`.

---

## 3. Proposed domain model

```kotlin
enum class ProjectWorkStatus { DRAFT, ACTIVE, PAUSED, COMPLETED, CANCELLED, ARCHIVED;
    companion object { fun fromPersisted(raw: String?): ProjectWorkStatus /* fallback ACTIVE */ } }

enum class ProjectBillingStatus { NOT_BILLED, BILLED, PARTIALLY_PAID, PAID, OVERDUE }
// Derived, never persisted. No fromPersisted() needed.

enum class ProjectTaxMode { TAX_EXCLUSIVE, TAX_INCLUSIVE;
    companion object { fun fromPersisted(raw: String?): ProjectTaxMode /* fallback TAX_EXCLUSIVE */ } }

data class Project(
    val id: String,
    val userId: String,
    val name: String,
    val clientName: String?,          // plain text, not a CRM entity
    val notes: String?,
    val color: String?,               // mirrors Task.color
    val workStatus: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
    val currencyCode: String,         // per project
    val feeMinorUnits: Long,          // interpreted per taxMode
    val minorUnitDigits: Int,         // from CurrencyCode.fractionDigits at creation
    val taxMode: ProjectTaxMode = ProjectTaxMode.TAX_EXCLUSIVE,
    val taxRateBasisPoints: Int = 0,  // 1800 = 18.00 %
    val hourBudgetMinutes: Int? = null,
    val startDate: LocalDate? = null,
    val targetEndDate: LocalDate? = null,
    val createdAt: Instant, val updatedAt: Instant, val remoteId: String? = null,
)

data class ProjectBillingRecord(          // one per project in MVP; table, not columns
    val id: String, val userId: String, val projectId: String,
    val externalReference: String?,       // the user's own invoice/reference number
    val amountMinorUnits: Long,           // defaults to the project's gross
    val billedOn: LocalDate,
    val dueOn: LocalDate?,
    val notes: String?,
    val createdAt: Instant, val updatedAt: Instant, val remoteId: String? = null,
)

data class ProjectPayment(
    val id: String, val userId: String, val projectId: String,
    val billingRecordId: String?,         // nullable: a payment may precede billing
    val amountMinorUnits: Long,
    val receivedOn: LocalDate,
    val method: String?, val reference: String?, val notes: String?,
    val createdAt: Instant, val updatedAt: Instant, val remoteId: String? = null,
)

// Shift gains exactly two fields — and deliberately no rate:
val projectId: String? = null
val projectNameSnapshot: String? = null
```

### 3.1 Why money is `Long` minor units, not `Double`

The rest of the app uses `Double`, which is fine for an *estimate* that is only ever
displayed. Paid Projects sums payments and compares them to a fee to decide `PAID` vs
`PARTIALLY_PAID`. With `Double`, three payments of a third of the fee leave a project
permanently `PARTIALLY_PAID` with `₪0.01` outstanding. Minor units make "fully paid"
exact, need no Room type converter, and serialize cleanly to the backup JSON and to a
Postgres `bigint`. `BigDecimal` was considered and rejected: it needs a converter, a
serializer, and a rounding policy at every operation for no additional correctness here.
Conversion to `Double` happens once, at the `MoneyFormatter` boundary.

`minorUnitDigits` is stored per project rather than re-derived, so a project priced in
JPY (0 digits) stays correct even if `CurrencyCode` changes later.

### 3.2 Tax

Store the **mode** and the **rate**; derive net/tax/gross with a pure function so
correcting a rate recomputes cleanly.

```
TAX_EXCLUSIVE:  net   = fee
                tax   = round(net * bp / 10_000)          // HALF_UP on the minor unit
                gross = net + tax
TAX_INCLUSIVE:  gross = fee
                net   = round(gross * 10_000 / (10_000 + bp))
                tax   = gross - net                        // residual, so net+tax==gross exactly
```

Taking tax as the residual in the inclusive case guarantees the three numbers always
reconcile, which a naive independent rounding does not.

### 3.3 Derived billing status

```kotlin
fun resolve(
    billing: ProjectBillingRecord?,
    payments: List<ProjectPayment>,
    grossMinorUnits: Long,
    today: LocalDate,          // computed in the WORK timezone, not the device zone
): BillingState
```

| Condition | Status |
|-----------|--------|
| no billing record and no payments | `NOT_BILLED` |
| `paid >= gross` | `PAID` |
| `paid > 0` and `today > dueOn` | `OVERDUE` |
| `paid > 0` | `PARTIALLY_PAID` |
| billed, `paid == 0`, `today > dueOn` | `OVERDUE` |
| billed, `paid == 0` | `BILLED` |

Rules: `PAID` always outranks `OVERDUE` (a late-but-settled project is settled).
`OVERDUE` outranks `BILLED` and `PARTIALLY_PAID` because it is the actionable state.
Due *today* is not overdue. `dueOn == null` can never be overdue.
`BillingState` also exposes `paidMinorUnits`, `outstandingMinorUnits`
(clamped at 0 — over-payment shows a separate credit figure rather than a negative
balance), `isOverdue` and `daysUntilDue`, so the UI can render
"Partially paid · overdue" without the enum having to carry two states.

**Open product decision:** a payment recorded with no billing record. The table above
treats it as `PARTIALLY_PAID`/`PAID`, on the basis that money received is a stronger
signal than a missing bookkeeping row. Confirm before implementing.

### 3.4 Future-proofing without destructive migration

| Deferred feature | Why it is already additive |
|---|---|
| Multiple / recurring invoices | billing is a child table, not project columns; drop the unique index |
| Milestones, expenses | new child tables keyed on `projectId` |
| Clients as a CRM entity | `clientName` is free text; add `clients` + nullable `clientId`, backfill by name |
| Credit notes, quotes | new child tables + a `kind` column on billing records |
| Invoice PDF / compliance | pure render over existing data; `externalReference` stays what it is |
| Currency conversion | add a nullable `fxRate`/`baseCurrency` per payment; totals already grouped by currency |
| Per-shift compensation source | add a nullable `compensationSource` to `shifts`; `NULL` = today's behavior |
| Time-and-materials projects | add nullable `billingModel` + `rateMinorUnits`; fixed fee stays the default |

---

## 4. Shift ↔ project: recommendation

**Recommendation: add `shifts.projectId` as a third, independent nullable association.
A shift may carry both a project and a task. Do not model projects as tasks. Do not
introduce a compensation-source discriminator in the MVP. Never put a project rate on
a shift.**

### 4.1 Why not "a project instead of a task"

Tasks are a *rate source*: `CompensationResolver` line 112 overwrites `baseHourlyRate`
with `taskHourlyRateSnapshot`. A project is a *fixed-fee client billing container*. If
projects reused the task mechanism — or if a project rate were snapshotted onto the
shift the way a task rate is — a ₪12,000 project fee or a derived project rate would
become the user's hourly wage for that shift, and would propagate through the overtime
ladder, premium stacking, the monthly gross, the CSV export and the Wear snapshot.
Tasks and projects also answer different questions ("what was I doing?" vs "who am I
billing?") and users will want both on one shift.

### 4.2 Why not a separate compensation-source model in the MVP

A `compensationSource` discriminator on the shift ("employee pay" vs "client project")
is the right long-term shape, but for the MVP it:

* forces a value for every existing row and every headless clock-in path
  (widget, shortcut, Wear, notification) — a wide behavioral change for no MVP feature;
* is unnecessary, because project money is **never** derived from duration × rate. It
  comes from the project's fee and its payment rows. Project time only feeds *hours*
  aggregates (budget consumption, effective hourly rate), computed in a separate builder;
* remains fully additive later — a nullable column where `NULL` means today's behavior.

### 4.3 The separation, stated as an invariant

```
employee pay    = f(shift times, compensation profile, premium profile, task rate)
                  — unchanged, must not read projectId
project revenue = f(project fee, tax mode/rate, payment rows)
                  — must not read shift duration or any hourly rate
project hours   = f(shift times where projectId matches)
                  — read-only over shifts, feeds budget and effective rate only
```

Enforced by:

1. **No `projectRateSnapshot` column.** There is nothing to leak.
2. Zero references to `projectId` in `domain/PayrollCalculator.kt`,
   `domain/compensation/*`, `domain/premium/*` — asserted by a test.
3. `PayrollIsolationTest`: for a corpus of shifts, `sumMonthlyPay` and
   `calculateShiftPayInContext` produce **identical** results with and without
   `projectId` / `projectNameSnapshot` populated.
4. Effective hourly rate lives only in `ProjectFinancials`, computed as
   `netMinorUnits × 60 / trackedMinutes`, never through a rate ladder.
5. `projectNameSnapshot` exists purely so a deleted project still labels its shifts,
   mirroring `taskNameSnapshot`.

A join table (`project_shift_allocations`) was considered for split time and rejected
for the MVP: one shift belongs to one engagement, the nullable-column precedent already
exists for tasks, and a join table can be added later with the column as the primary
allocation.

---

## 5. Proposed database migrations

### 5.1 Room 14 → 15 (`MIGRATION_14_15`)

Pure additive DDL. No table rebuild, no data rewrite, no non-NULL defaults on `shifts`.

```sql
CREATE TABLE IF NOT EXISTS projects (
    localId TEXT NOT NULL PRIMARY KEY,
    remoteId TEXT, userId TEXT NOT NULL,
    name TEXT NOT NULL, clientName TEXT, notes TEXT, color TEXT,
    workStatus TEXT NOT NULL,
    currencyCode TEXT NOT NULL,
    feeMinorUnits INTEGER NOT NULL, minorUnitDigits INTEGER NOT NULL,
    taxMode TEXT NOT NULL, taxRateBasisPoints INTEGER NOT NULL,
    hourBudgetMinutes INTEGER, startDate INTEGER, targetEndDate INTEGER,
    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER,
    syncStatus TEXT NOT NULL, lastSyncError TEXT, lastSyncedAt INTEGER
);
CREATE INDEX IF NOT EXISTS index_projects_userId ON projects(userId);
CREATE INDEX IF NOT EXISTS index_projects_userId_syncStatus ON projects(userId, syncStatus);
CREATE INDEX IF NOT EXISTS index_projects_remoteId ON projects(remoteId);

CREATE TABLE IF NOT EXISTS project_billing_records ( … projectLocalId TEXT NOT NULL,
    externalReference TEXT, amountMinorUnits INTEGER NOT NULL,
    billedOn INTEGER NOT NULL, dueOn INTEGER, notes TEXT, …envelope… );
CREATE INDEX … _userId, _projectLocalId, _userId_syncStatus, _remoteId

CREATE TABLE IF NOT EXISTS project_payments ( … projectLocalId TEXT NOT NULL,
    billingRecordLocalId TEXT, amountMinorUnits INTEGER NOT NULL,
    receivedOn INTEGER NOT NULL, method TEXT, reference TEXT, notes TEXT, …envelope… );
CREATE INDEX … _userId, _projectLocalId, _userId_syncStatus, _remoteId

ALTER TABLE shifts ADD COLUMN projectId TEXT;
ALTER TABLE shifts ADD COLUMN projectNameSnapshot TEXT;
```

Decisions and hazards:

* **No SQL foreign keys.** Soft deletes are the norm here (only `receipts` uses an FK,
  `ON DELETE SET NULL`), and a real FK would rarely fire while adding cascade-order
  constraints to sync. Children are cascaded as soft deletes in
  `LocalProjectsRepository`. FK-with-CASCADE is a viable alternative if the team prefers
  DB-enforced integrity — but then Room requires the parent to be an entity and the
  migration must match exactly.
* Dates that are calendar dates (`billedOn`, `dueOn`, `receivedOn`, `startDate`,
  `targetEndDate`) are stored as **epoch-day INTEGER**, not epoch millis — a due date is
  a date, not an instant, and storing millis invites a timezone bug at the overdue
  boundary. `Converters` gains a `LocalDate`↔`Long` pair.
* Statuses are persisted lowercase snake_case and parsed only through
  `fromPersisted()` with a documented fallback — never `valueOf` (`AGENTS.md`, contract doc).
* **Every index created here must be declared on the entity, and vice versa.** This is
  the exact mistake that made `MIGRATION_7_8` reject upgraded databases.
* Export `15.json`; extend `ElmTrackrDatabaseMigrationTest` with a 14→15 case and push
  `fullChainFrom2ProducesValidSchema` to 15. Note the missing `9.json` — it blocks a
  clean validate at the 8→9 hop and should be reported, not silently regenerated.

### 5.2 Supabase (`supabase/migrations/2026…_paid_projects.sql`)

`projects`, `project_billing_records`, `project_payments` with snake_case columns, RLS
policies mirroring `tasks`, `bigint` minor units on the wire (keeping exactness across
the boundary), `alter table shifts add column project_id uuid`, `project_name_snapshot text`,
and `delete_own_account` extended to the three tables. Then update
`docs/supabase-contract.md`: table list, column tables, the two new enums with their
fallbacks, and the sync order — **projects push/pull before shifts**, exactly as tasks do,
because shifts reference project ids.

Remote sync may be deferred to a later prompt (local-only is a legitimate MVP slice),
but the `remoteId` envelope and the columns must exist from day one so enabling sync is
purely additive.

### 5.3 Existing users

`featuresPaidProjects` is already `false` for every existing row, so after upgrading:
four tabs, no project surfaces, three empty tables, two NULL columns on `shifts`.
Turning the flag off later hides the UI and leaves all rows intact (never delete on
disable) — which satisfies the "existing project data must remain stored" requirement.

---

## 6. Proposed navigation behavior

```kotlin
enum class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val requiresPaidProjects: Boolean = false,
) {
    DASHBOARD("dashboard", …), SHIFTS("shifts", …), REPORTS("reports", …),
    PROJECTS("projects", R.string.nav_projects, Icons.Outlined.Folder, requiresPaidProjects = true),
    SETTINGS("settings", …),
    ;
    companion object {
        const val MAX_PRIMARY_DESTINATIONS = 5
        fun visibleItems(paidProjectsEnabled: Boolean): List<BottomNavItem> =
            entries.filter { paidProjectsEnabled || !it.requiresPaidProjects }
    }
}
```

* **Order:** DASHBOARD, SHIFTS, REPORTS, **PROJECTS**, SETTINGS. Settings stays last by
  convention; inserting before it preserves the relative order of the existing four and
  keeps `routeIndex()` monotonic with visual order.
* `visibleItems()` is the **single** decision point, consumed by both `ElmBottomNav` and
  `ElmSideNavigation`. Tablet and phone cannot diverge.
* `routeIndex()` keeps using the full `entries` list, so slide direction is identical
  whether or not the tab is visible.
* `MAX_PRIMARY_DESTINATIONS` is asserted in a test — the five-destination ceiling is
  structural, not a comment.

**Register the `projects` destination unconditionally.** Only the bar/rail item list is
conditional. Rationale, against each named failure mode:

| Failure mode | How this avoids it |
|---|---|
| Crash when the selected destination is disabled | the destination always exists; a graph that drops it while it is current throws |
| Corrupted back stack | the `NavHost` graph never changes shape, so the back stack is never rebuilt |
| Lost navigation state | `rememberNavController()` stays `remember`-scoped to `MainScaffold`; a flag change is a recomposition, not a recreation. `popUpTo(saveState = true)` + `restoreState = true` keep per-tab state |
| Duplicate destinations | one `composable(PROJECTS.route)` registration; `launchSingleTop` on every tab navigate |
| Broken deep links | no tab deep links exist today; a future `elmtrackr://projects` attaches to the always-present destination and bounces when disabled |
| Broken tablet navigation | one shared `visibleItems()` and one shared `navController` |
| Unnecessary app recreation | nothing calls `recreate()`; the flag flows through a `StateFlow` |

**Disabled-while-selected guard.** Collect the flag as a **nullable** `Boolean?`
(`null` = not loaded yet) and only act once loaded:

```kotlin
LaunchedEffect(paidProjectsEnabled, currentRoute) {
    if (paidProjectsEnabled == false && currentRoute == BottomNavItem.PROJECTS.route) {
        navController.navigate(BottomNavItem.DASHBOARD.route) {
            popUpTo(BottomNavItem.DASHBOARD.route) { inclusive = false; saveState = false }
            launchSingleTop = true
        }
    }
}
```

The nullability matters: with a non-null `false` initial value, a cold start that
restores the projects tab would bounce the user to the dashboard before settings load.
`saveState = false` on the guard clears the stale projects entry so re-enabling starts
clean. The decision itself should be extracted as a pure function
(`fun redirectTarget(currentRoute: String?, enabled: Boolean?): String?`) so it is
unit-testable without an emulator, and `ProjectsScreen` should additionally render a
benign empty state rather than crash if it is ever composed while disabled.

`hideNavChrome` extends to the project create/edit form exactly as it does for the
shift form.

---

## 7. Proposed onboarding behavior

* `FeaturesStep` (step 7) gains a third `FeatureCard`: "Paid projects — track a fixed
  project fee, time spent, and payments received." New strings in
  `strings_onboarding.xml` and its Hebrew twin.
* `OnboardingScreen.kt:317` `featuresPaidProjects = false` becomes the user's choice, and
  `ReviewStep.enabledCount` includes it.
* `OnboardingViewModel.kt:115` currently reads
  `if (input.preserveExisting) base.featuresPaidProjects else input.featuresPaidProjects`
  — correct *only* while the wizard has no control. Once the step exists this must become
  `input.featuresPaidProjects` unconditionally, and the comment above it updated, or a
  replaying user can never turn the module on from the wizard. **This is a real behavior
  change and must be called out in the commit.**
* **No project-creation step.** The wizard is already nine steps; first-run project
  creation belongs on the Projects tab's empty state.
* `SetupChecklist`: add a `PROJECTS` step completing on `hasAnyProject`, filtered out
  when the flag is off — the checklist already filters `WIDGET` on `widgetPinSupported`,
  so the precedent exists. **Product decision required:** for a user who has already
  finished (and dismissed the celebration of) the checklist and then enables the module,
  a new step re-opens the checklist. Acceptable, but it should be a deliberate choice.
* Settings → Features gains the toggle, following the existing `SettingsToggleRow` +
  save-bar pattern (not instant-save — `SettingsScreen` batches flags behind the bar).

---

## 8. Financial-calculation strategy

A single pure object, no Android and no repository dependencies, mirroring
`PayrollCalculator` / `MonthlyReportBuilder`:

```kotlin
object ProjectFinancials {
    fun compute(
        project: Project,
        billing: ProjectBillingRecord?,
        payments: List<ProjectPayment>,
        shifts: List<Shift>,          // already filtered to this project
        today: LocalDate,             // WorkTimezone.zoneFor(settings)
    ): ProjectSummary
}

data class ProjectSummary(
    val currencyCode: String, val minorUnitDigits: Int,
    val feeNetMinorUnits: Long, val taxMinorUnits: Long, val feeGrossMinorUnits: Long,
    val paidMinorUnits: Long, val outstandingMinorUnits: Long, val creditMinorUnits: Long,
    val trackedMinutes: Int, val budgetMinutes: Int?, val budgetUtilization: Float?,
    val effectiveHourlyRateMinorUnits: Long?,   // null when trackedMinutes == 0
    val billingStatus: ProjectBillingStatus, val isOverdue: Boolean, val daysUntilDue: Long?,
)
```

Rules:

* All arithmetic in `Long` minor units; `HALF_UP` at the single rounding point per §3.2.
* Effective hourly rate is based on the **net** fee (tax is not the user's revenue) and
  is labelled as such in the UI. `null` — never `Infinity` or `0` — when no time is
  tracked; `budgetUtilization` is `null` when no budget is set.
* Tracked minutes come from the same helper the rest of the app uses
  (`ShiftDurationCalculator`), so break handling matches the shifts list.
* **Currency: no conversion, ever.** Aggregate tiles (dashboard outstanding, report
  totals) group by `currencyCode` and render either a single-currency figure or a
  per-currency list. Summing across currencies is the single biggest correctness trap
  here, and `MonthlyPaySummary`'s last-writer-wins `currencyCode` is a pattern to avoid,
  not to copy.
* Validation at the repository boundary: `feeMinorUnits >= 0`,
  `taxRateBasisPoints in 0..10_000`, `amountMinorUnits > 0` on payments,
  `dueOn >= billedOn`, name non-blank. Rejections surface as UI validation, not
  exceptions.
* Report/dashboard integration is additive: `projectBreakdown: List<ProjectMonthlySummary>
  = emptyList()` on `ReportsUiState.Ready`, built by a `ProjectMonthlyReportBuilder`
  modeled on `TaskMonthlyReportBuilder`; a flag-gated outstanding/overdue card on the
  dashboard. `ReportExporter` gains a project section **only when enabled**, since
  appending columns changes the CSV shape.
* **Wear and widgets are out of scope.** `WearSnapshotMapper` and `WidgetStateMapper`
  stay untouched.
* **No official invoices.** UI copy uses "billing reference" / "record a reference
  number you created elsewhere", never "invoice issued". A disclaimer follows the
  existing `COMPENSATION_DISCLAIMER` pattern.
* **No notifications in the MVP.** The due date is stored so a later
  `ProjectDueReminderWorker` can reuse `ReminderRule` infrastructure with no schema change.

---

## 9. Testing strategy

Conventions: JVM unit tests in `app/src/test` (JUnit4, `kotlinx-coroutines-test`,
`MainDispatcherRule`, hand-written fakes in `test/.../fake/`), Robolectric where a
Context is unavoidable, Paparazzi goldens in `app/src/test/snapshots/images`,
instrumented tests reserved for Room migrations and DAOs (only those run on CI's
emulator job — a new migration test must be added to that job's class list).

| # | Test | Asserts |
|---|------|---------|
| 1 | `ProjectFinancialsTest` | tax inclusive/exclusive both directions; `net + tax == gross` exactly; JPY 0-digit; zero-hours rate is `null`; over-payment → `PAID` with outstanding clamped and credit reported |
| 2 | `ProjectBillingStatusResolverTest` | all five statuses; `PAID` beats `OVERDUE`; `OVERDUE` beats `BILLED`/`PARTIALLY_PAID`; due-today is not overdue; null `dueOn` never overdue; work-timezone boundary |
| 3 | **`PayrollIsolationTest`** | `sumMonthlyPay` / `calculateShiftPayInContext` identical with and without `projectId`; no `projectId` reference in `PayrollCalculator` or `domain/compensation/*` |
| 4 | `BottomNavItemTest` (extended) | `entries.size <= 5`; `visibleItems(false)` == the existing four in the existing order; `visibleItems(true)` == five with Projects 4th; routes unique |
| 5 | `NavRedirectTest` | pure `redirectTarget()` — projects + disabled → dashboard; projects + `null` flag → no redirect; other routes → no redirect |
| 6 | `ElmTrackrDatabaseMigrationTest` (extended) | 14→15 creates tables/columns and preserves rows; full chain 2→15 validates |
| 7 | `ProjectDaoTest` (instrumented) | soft delete, per-user scoping, pending-sync queries, child cascade |
| 8 | Backup round-trip | v4 export/import including projects; a v3 document imports cleanly; children with a missing parent are dropped, not fatal |
| 9 | `ProjectsViewModelTest` | flag off → no project state emitted; empty, loaded and error states |
| 10 | Reports/dashboard | project sections absent when the flag is off; `ReportsUiState` defaults unchanged |
| 11 | Currency | a USD project and an ILS project are never summed |
| 12 | Paparazzi | new `projects-list` / `projects-detail` goldens in light, dark and Hebrew; flag-off captures must be **pixel-identical** to today, which is itself the regression guard |

Note: any golden that renders the bottom bar with the flag **on** changes — five tabs
across `PhoneContentMaxWidth` (448 dp) narrows each tab. Flag-off goldens must not move.

---

## 10. Risks

| # | Risk | Severity | Mitigation |
|---|------|----------|-----------|
| 1 | Project money leaking into employee pay via `CompensationResolver:112` | **Critical** | no project rate on the shift; `PayrollIsolationTest`; no-reference assertion |
| 2 | Cross-currency aggregation producing meaningless totals | **High** | group by currency; never sum; test 11 |
| 3 | `Double` drift leaving "₪0.01 outstanding" on a paid project | **High** | `Long` minor units end to end |
| 4 | Room schema/index validation bricking launch (happened twice: 7→8, 2→3) | **High** | declare every index on the entity; export `15.json`; instrumented migration test in CI |
| 5 | Nav graph rebuild on flag toggle losing the back stack or crashing | **High** | unconditional registration + nullable-flag redirect guard |
| 6 | Onboarding replay can never enable the module (the `preserveExisting` branch) | Medium | change to unconditional once the step exists; covered by `OnboardingViewModelTest` |
| 7 | Sync order — shifts pushed before the projects they reference | Medium | projects first, mirroring tasks; document in the contract |
| 8 | `LocalUserDataCleaner` / `delete_own_account` missing the new tables → orphaned data after account deletion | Medium | extend both in the same prompt as the schema |
| 9 | Screenshot golden churn | Low | regenerate flag-on goldens; assert flag-off goldens unchanged |
| 10 | Missing `app/schemas/9.json` blocks a clean full-chain validate at 8→9 | Low | report it; do not silently regenerate |
| 11 | Terminology collision — the contract already calls `tasks` "paid-project tasks" | Low | rename in the doc; distinct user-facing labels for Projects vs Tasks |
| 12 | Copy implying an issued invoice (Play policy / user trust) | Medium | "billing reference" language + explicit disclaimer; no PDF |
| 13 | Setup checklist re-opening for users who already finished it | Low | flag-filter the step; confirm as a product decision |

---

## 11. Recommended implementation order

1. **Schema + domain model** — entities, DAOs, `MIGRATION_14_15`, `15.json`, `LocalDate`
   converters, migration + DAO tests, `LocalUserDataCleaner`. No UI, no behavior change.
2. **Repository + DI + backup** — `ProjectsRepository`, `LocalProjectsRepository`,
   Hilt wiring, backup format v4, importer/exporter round-trip tests.
3. **Financial engine** — `ProjectFinancials`, `ProjectBillingStatusResolver`, label
   objects, plus tests 1–3. Pure Kotlin; the highest-value slice to get right first.
4. **Navigation** — `BottomNavItem.PROJECTS`, `visibleItems()`, `MainScaffold` +
   `ElmSideNavigation` wiring, redirect guard, tests 4–5, placeholder Projects screen.
5. **Projects UI** — list, detail, create/edit form; billing record and payment entry;
   English + Hebrew strings; RTL pass.
6. **Shift ↔ project** — project picker in the shift form and at clock-in;
   `projectNameSnapshot` maintenance; re-run the isolation test.
7. **Feature selector** — Settings → Features toggle, onboarding `FeaturesStep` card,
   the `OnboardingViewModel` fix, setup-checklist step.
8. **Dashboard + reports + export** — flag-gated card, `ProjectMonthlyReportBuilder`
   section, CSV section, tests 9–11.
9. **Supabase + sync** — SQL migration, `RemoteProject*` sources/mappers,
   `SyncRepositoryImpl` wiring (projects before shifts), `delete_own_account`,
   contract doc.
10. **Hardening** — Paparazzi goldens (light/dark/Hebrew), full regression, lint,
    `docs/product-quality-audit.md` P1-2 resolution note.

Steps 1–3 change no user-visible behavior and can land independently of the rest.

---

## 12. Checklist for the remaining prompts

**Decisions to confirm before coding**

- [ ] Tab position — Projects 4th, before Settings (recommended)
- [ ] Icon for the Projects tab (`Icons.Outlined.Folder` vs `WorkOutline` vs `Receipt`)
- [ ] Money as `Long` minor units for the new tables (recommended)
- [ ] A payment recorded with no billing record → `PARTIALLY_PAID`/`PAID` (§3.3)
- [ ] `OVERDUE` outranks `PARTIALLY_PAID` in the single-enum projection
- [ ] Setup-checklist step re-opens a completed checklist on enable
- [ ] Sync in the MVP, or local-only first with the envelope in place
- [ ] Hebrew terminology for Project / billing reference / outstanding / overdue

**Prompt-by-prompt**

- [ ] **P1 Schema** — entities, DAOs, `MIGRATION_14_15`, `15.json`, `LocalDate`
      converters, `shifts.projectId` + `projectNameSnapshot`, `LocalUserDataCleaner`,
      migration + DAO tests
- [ ] **P2 Repository** — interface + `Local*` impl + Hilt + backup v4 + round-trip tests
- [ ] **P3 Engine** — `ProjectFinancials`, status resolver, label objects, tests 1–3
- [ ] **P4 Navigation** — `visibleItems()`, both nav surfaces, redirect guard, tests 4–5
- [ ] **P5 Projects UI** — list/detail/form, billing + payments, en/he strings, RTL
- [ ] **P6 Shift association** — picker in form + clock-in, snapshot upkeep, isolation test
- [ ] **P7 Feature selector** — settings toggle, onboarding card, `OnboardingViewModel` fix,
      checklist step
- [ ] **P8 Reports/dashboard** — breakdown builder, dashboard card, CSV section
- [ ] **P9 Supabase/sync** — SQL migration, remote sources, sync order, contract doc
- [ ] **P10 Hardening** — goldens, regression, lint, audit-doc update

**Invariants to re-verify at every prompt**

- [ ] `BottomNavItem.entries.size <= 5`
- [ ] Flag off → exactly the original four tabs, original order, no project surfaces
- [ ] No `projectId` reference in `PayrollCalculator` or `domain/compensation/*`
- [ ] Employee pay totals unchanged by the presence of a project on a shift
- [ ] No currency conversion; no cross-currency sums
- [ ] Disabling the module hides UI and deletes nothing
- [ ] No enum parsed with `valueOf` on persisted or synced strings
- [ ] No UI copy claiming an invoice was issued
