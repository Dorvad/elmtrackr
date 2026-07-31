# ElmTrackr Android — Code Review (July 2026, post-Paid-Projects)

Full review of the native Android app covering calculation accuracy, UX/UI, and
platform reliability. This review follows up on `product-quality-audit.md`
(July 2026), which predates the Paid Projects merge (#114): section 1 records
the current status of every finding in that audit; sections 2–4 are new
findings. Every claim below was verified against source at commit `0e424b2`;
file references are relative to `android/app/src/main/java/com/elmtrackr/app/`
unless noted.

Severity legend follows the earlier audit: **P0** wrong money or data shown ·
**P1** breaks user trust · **P2** inconsistent or confusing behavior ·
**P3** polish, drift, and dead weight.

Two scope notes. First, platform-behavior findings (Doze deferral, Android 14
notification dismissibility, `Theme.NoDisplay` timing) are based on documented
platform behavior, not on-device measurement — verify on hardware before
scheduling the fix. Second, the Israeli statutory figures in `RegionPresets.kt`
(516/2520 minutes, 125%/150% tiers, 7h night day) are structurally coherent and
match commonly cited rules, but this review is not a payroll authority; the
source's own "verify with payroll/HR" comment still stands.

---

## 1. Status of the July 2026 audit findings

| Finding | Status | Evidence |
|---|---|---|
| P0-1 premium profiles inert | **Fixed**, two residues (§2.6) | `premiumProfiles` threaded through dashboard (`ui/dashboard/DashboardViewModel.kt:255`), reports (`ui/reports/ReportsViewModel.kt:296-333`), rows, PDF, insights |
| P0-2 currency triple storage | **Mostly fixed**, three residues (§2.6) | `UserSettings.displayCurrencyCode()` is the read path for Reports/PDF |
| P0-3 preview vs saved pay | **Fixed** | `ui/shifts/ShiftEditFormUi.kt:658` now uses `calculateShiftPayInContext` with the month's shifts |
| P0-4 IL engine ignores night/deductions | **Half fixed** (§2.6) | Deductions now apply (`domain/compensation/IsraeliCompensationEngine.kt:169`); `nightGross` still hardcoded `0.0` (`:177`) |
| P0-5 "day progress" definitions differ | Not re-verified this pass | — |
| P1-1 onboarding replay resets customization | **Fixed** | `ui/onboarding/OnboardingViewModel.kt:118-122` preserves on replay |
| P1-2 `featuresPaidProjects` dead flag | **Fixed** | Now drives tab visibility, nav guard, settings toggle, onboarding step |
| P1-3 deletes without confirmation | **Fixed** | `PremiumProfilesScreen.kt:141-157`, `RefundClaimsSection.kt:113-130`; new project deletes follow suit |
| P1-4 unsaved settings lost / fields reinit while typing | **Fixed** | Discard guard `ui/settings/SettingsScreen.kt:106-142`; `rememberSaveable` migration noted at `:257-260` |
| P1-5 notification clock-out resurrects deleted shift | **Fixed** | `data/repository/LocalShiftsRepository.kt:50` filters `deletedAt == null`; `notification/ClockOutReceiver.kt:46` goes through the repository, in `runCatching` |
| P2-1 Settings→Pay edits a copy; thresholds split | **Still open** — and it now produces visibly wrong report data (§2.4) | |
| P2-3 device-timezone leaks | **Mostly fixed** | Reports corrected (`ReportsViewModel.kt:70-72`); Shifts ViewModel re-anchors to the work zone (`ui/shifts/ShiftsViewModel.kt:139-144`) and `nextMonth()` gates on it (`:194`). Residues in §3.4 |
| P2-4 mixed save semantics | **Still open** | Appearance screen unchanged: theme/language apply instantly, clock style waits for the save bar (`SettingsScreen.kt:343-346` vs `:414-417`) |
| P2-5 tasks overlay restore surprise | **Fixed** | `ui/dashboard/DashboardScreen.kt:170-172` |

The Paid Projects globalization/accessibility pass (commit `9696279`) deserves
credit: bidi isolation for user data, single-phrase amount announcements,
named toggle nodes, new-destination announcements, label-dropping at large font
scale, and full Hebrew string parity (1,444 strings) were all verified present.

---

## 2. Calculation findings

### 2.1 P0 — Project report divides the whole-project fee by one month's hours

`ReportsViewModel.buildProjectBundle` scopes tracked minutes to the selected
month (`ui/reports/ReportsViewModel.kt:151-156`), then hands them to
`ProjectMetrics.summarize`, which computes
`effectiveHourlyRate(project.fee, timeSummary.trackedMinutes)`
(`domain/projects/ProjectMetrics.kt:134`) — the **full contracted fee** over
**one month's minutes**. `RevenueEfficiency.valuePerHour` has the same shape.

**Impact:** a ₪10,000 project worked evenly over three months shows roughly
three times its true effective rate in every monthly report and in the CSV's
"Effective hourly rate" column. `ProjectReportTest` never feeds inconsistent
periods, so nothing catches it.

**Recommended fix:** compute the effective rate from all-time tracked minutes
(matching the fee's scope), and label it "all-time" in the report; if a
per-period rate is wanted, apportion revenue by recognized billing in the
period instead. Add an invariant test: any figure labelled with a period must
be computed entirely from that period's inputs.

### 2.2 P0 — Project CSV rows and TOTAL row use different accounting bases

Per-project columns come from `ProjectInsights` → `summary.billing.*`, which is
**all-time** (`ProjectBillingStatusResolver.resolve` applies no date filter,
`domain/projects/ProjectBillingStatus.kt:104-121`). The TOTAL row reads
`report.totals.billed/received/outstanding`, which are **period-filtered**
(`domain/projects/ProjectPeriodTotals.kt:107-109`; CSV at
`domain/projects/ProjectReportCsv.kt:99-102`).

**Impact:** a project billed 11,800 in June and viewed in July shows 11,800 on
its row and 0 in TOTAL. The same mixing exists inside one row: "Tracked hours"
is period-scoped while "Billed"/"Paid" are not. A user reconciling the CSV
against an accountant's ledger cannot make it add up.

**Recommended fix:** pick one basis per surface and state it in the column
header. The cleanest split: rows and TOTAL both period-scoped for billed /
received / outstanding, with a separate all-time "Project status" column set.

### 2.3 P0 — Rounding to zero bypasses the minimum-shift guarantee

`PayrollCalculator.payableNetMinutes` rounds first, then applies the minimum
only when `net in 1 until minimum` (`domain/PayrollCalculator.kt:359-369`).
With `incrementMinutes = 15`, direction "nearest", a 7-minute shift rounds to
`(7+7)/15 = 0`; zero is outside `1 until minimum`, so `minimumShiftMinutes`
never fires and the shift pays nothing — the opposite of what reporting-time
pay exists for.

**Recommended fix:** apply the minimum before rounding, or change the guard to
`net in 0 until minimum` when the pre-rounding net was positive.
`PayrollCalculatorTest.kt` tests rounding and minimum separately
(`:377`) — add the composed case.

### 2.4 P0 — Report overtime hours and paid overtime use different thresholds

Carried over from audit P2-1, now with user-visible consequences on default
settings. `MonthlyReportBuilder.buildShiftBreakdown` reads
`settings.dailyOvertimeThresholdMinutes` (default **480**, `UserSettings.kt:57`)
while every pay figure reads the resolved profile rules (IL preset **516**,
`domain/compensation/RegionPresets.kt`). An IL user on preset defaults sees
overtime *hours* counted from 8:00 in the report card, CSV and PDF while pay
treats the same minutes as regular until 8:36. The KDoc at `UserSettings.kt:11`
("Israeli defaults: daily threshold 8h, weekly 40h") and
`supabase/schema.sql:25-26` (480/2400) are both stale relative to the preset.

**Recommended fix:** make `MonthlyReportBuilder` resolve thresholds through
`CompensationResolver` exactly as the pay path does, and add a test asserting
that report overtime minutes and payroll overtime brackets agree for the same
shift. Update the stale defaults/docs.

### 2.5 P0 — Generic engine classifies weekend/holiday from the start date only

`calculateGenericShiftPay` decides weekend status once from the shift's start
date (`domain/PayrollCalculator.kt:141-144`). The IL engine classifies each
minute (`IsraeliCompensationEngine.kt`, `isWeeklyRestAt`), and
`MonthlyReportBuilder` splits weekend *hours* proportionally per local day.

**Impact (non-IL profiles):** a Friday 23:00 → Saturday 07:00 shift is paid
entirely at the weekday rate; Saturday 23:00 → Sunday 07:00 entirely at the
weekend rate. For midnight-crossing shifts the report's weekend hours and the
payroll's weekend money describe different splits of the same shift.

**Recommended fix:** split generic-engine shifts at local-midnight boundaries
(reusing `OvernightShiftDetector`) and price each segment by its own day, or —
if all-or-nothing is a deliberate simplification — apply the same rule to the
report's hour split so the two agree. Add a midnight-crossing weekend pay test.

### 2.6 P1 — Residues of audit P0-1 / P0-2 / P0-4

- `data/repository/LocalReportsRepository.kt:35,50` still call
  `buildMonthlyReport` / `groupByWeek` without `profiles`/`premiumProfiles`.
  Any surface fed by this repository silently reverts premium shifts to the
  150% special-day rate — the exact failure mode of P0-1. Thread the arguments
  through, and add a test that the repository output matches the ViewModel
  path for a premium shift.
- Three call sites still read the legacy currency enum instead of
  `displayCurrencyCode()`: `ui/shifts/ShiftsListUi.kt:207,401` and
  `ui/shifts/ShiftEditFormUi.kt:169`. Changing the profile currency leaves the
  shifts list and the edit-form preview on the old symbol.
- `IsraeliCompensationEngine.kt:177` returns `nightGross = 0.0`; a non-1.0
  `nightMultiplier` configured by an IL user has no effect and no warning. The
  preset default (1.0 — night as a shortened day, not a premium) is fine;
  either honor an edited multiplier or disable the field for IL profiles with
  an explanatory note.

### 2.7 P1 — Weekly overtime is truncated at every month boundary

`sumMonthlyPay` and `calculateShiftPayInContext` derive week context from the
shift list they receive, and every caller passes only the selected month
(`ReportsViewModel.kt:296`, `DashboardViewModel.kt:255`,
`ui/shifts/ShiftsListUi.kt:196`). A pay week straddling the 1st starts with
`priorWeekMinutes = 0`, so early-month shifts never see hours worked in the
same week of the previous month.

**Impact:** systematic overtime under-count in the first partial week of every
month, worst for weekly-threshold regimes (IL 42h; US federal weekly-only).

**Recommended fix:** have callers fetch shifts from the start of the pay week
containing the 1st (the DAO already queries by range), pass them for context,
and report only the month's shifts. Add a cross-month pay-week test — none
exists today.

### 2.8 P2 — Report "overdue" is measured against period end, not today

`ProjectPeriodTotals` counts a record overdue when `dueOn < to` — the last day
of the selected month (`domain/projects/ProjectPeriodTotals.kt:149-155`).
Viewing the current month on the 5th, an invoice due the 25th is already in
the overdue total, while the project's own badge correctly says BILLED
(`ProjectBillingStatus.kt:119` uses `today.isAfter(dueOn)`).

**Recommended fix:** `dueOn < minOf(today, to)` — past months keep their
end-of-period semantics, the current month stops predicting the future. Add a
test where `to` is later than `today`; `ProjectReportTest.kt:454` currently
pins them four days apart with a due date already past.

### 2.9 P3 — Lower-priority calculation notes

- **IL `breakRatio` distortion.** `payableNetMinutes` can exceed gross under
  round-up or minimum-shift top-up; `breakRatio = net / grossMinutes`
  (`IsraeliCompensationEngine.kt:294-295`) then maps topped-up minutes into a
  compressed slice of wall clock, so rest/night classification of those minutes
  is decided by the wrong time window. Also a hot loop: one `Instant.atZone`
  per payable minute, and `weekStateBeforeShift` re-classifies every prior
  shift — roughly O(shifts² × minutes) per month, mitigated only by
  `flowOn(computationDispatcher)`. Clamp the classified minutes to gross and
  classify per segment rather than per minute.
- **Friday pay rises when overtime is turned off.** The no-overtime fallback
  uses `WeekendRules.isWeekendDate` (whole Friday is weekend,
  `IsraeliCompensationEngine.kt:245-250`) while the overtime path honors
  `weeklyRestStartTime = 17:00`. Toggling overtime off raises pay for a Friday
  morning shift. Align the fallback on `isWeeklyRestAt`.
- **`PayWeekMinutes.forEachWithPriorWeekMinutes` groups by ISO Monday** while
  the engines anchor weeks on `rules.weekStartDay` (Sunday for IL/US).
  Currently harmless — `sumMonthlyPay` discards the accumulator — but the
  helper's name promises a boundary it does not honor. Fix or rename before
  someone wires it in.
- **Double-based wage money rounds only at display.** Per-bracket amounts are
  rounded independently, so displayed brackets can sum one minor unit away
  from the displayed total. The `NoBinaryFloatingPointTest.kt:100` boundary
  accepts this for wages; if bracket-total agreement matters for the PDF, round
  to the minor unit before summing.
- **Payments against a cancelled billing record vanish from `received`**
  (`ProjectPeriodTotals.kt:109` filters to active records).
  `ProjectBillingCorrection.canCancel` blocks the normal route into this state
  (`:52-55`), so this is defense-in-depth for sync races and future multi-record
  UI: include payments whose record is cancelled, or assert the invariant.
- **480 as the default day** is declared three times:
  `WearShiftSnapshot.DEFAULT_DAILY_GOAL_MINUTES`,
  `WidgetShiftState.DEFAULT_DAILY_GOAL_MINUTES`,
  `OvertimeReminderPolicy.FALLBACK_THRESHOLD_MINUTES`. Single-source it.
- **Widget/watch "today" mixes bases:** completed shifts counted net of break,
  the active shift gross (`widget/WidgetStateMapper.kt:76-87`). Live clock-ins
  start with `breakMinutes = 0` (`LocalShiftsRepository.kt:75`), so this only
  shows when a running shift is edited to add a break — the ring then drops at
  clock-out. Count the active shift net of its break.

---

## 3. UX/UI findings

### 3.1 P1 — The Projects tab error state is a dead end

`ui/projects/ProjectsScreen.kt:138`:
`is ProjectsUiState.Error -> ErrorState(message = current.message, onRetry = {})`.
`ErrorState` always renders a "Try again" button, so the user sees an
affordance that does nothing. `ProjectsViewModel` exposes no retry, and the
state comes from `.catch { emit(Error) }` on the flow (`ProjectsViewModel.kt:128`),
which terminates it — once the tab errors, the only recovery is killing the
app. Every sibling screen has a working retry (Reports, Shifts, Settings, Task
Management).

**Recommended fix:** add a refresh nonce to `ProjectsViewModel` (the pattern
already used by `ShiftsViewModel._refreshNonce`) and wire it to `onRetry`.

### 3.2 P1 — Project, billing, and payment forms discard typed data silently

`ProjectFormUi.kt` and `ProjectBillingUi.kt` have no dirty tracking, no
`BackHandler`, no discard dialog (zero occurrences; `ShiftEditFormUi.kt` has
seventeen). A user who fills in name, client, fee, currency, tax and dates —
or a payment amount — loses everything to one back gesture. The shift form
(`ShiftEditFormUi.kt:218-240`) and Settings both guard; these forms carry at
least as much typed data.

**Recommended fix:** replicate the shift form's `pendingDiscard` pattern in
both forms.

### 3.3 P2 — The app-lock biometric prompt is hardcoded English

`ui/settings/SettingsScreen.kt:452-459` passes literal strings
("Enable app lock", "Confirm to require biometric unlock…") to
`BiometricAuthPrompt.show`. The resources exist
(`strings_security.xml`) and the onboarding path resolves them correctly; a
Hebrew user enabling app lock from Settings gets an English system dialog.

### 3.4 P2 — Remaining device-timezone residues

- `ui/shifts/ShiftsListUi.kt:134` — `canGoNext = month < YearMonth.now()`
  (device zone) gates the forward chevron, while the ViewModel gates actual
  navigation on the work zone (`ShiftsViewModel.kt:194`). Near a month
  boundary across zones, the chevron's enabled state and its behavior disagree.
  Pass the ViewModel's current-month cap into the composable.
- `ui/dashboard/DashboardScreen.kt:398-400` — the refund reminder banner uses
  `LocalDate.now()` twice (device zone, and two separate reads can straddle
  midnight) on a screen that provides `LocalWorkZone`. Use the work zone, once.
- `ui/shifts/ShiftRowDisplayModel.kt:31` defaults `zone = ZoneId.systemDefault()`;
  every caller currently passes a zone — remove the unsafe default.

### 3.5 P2 — Durations are untranslated on every hourly surface

`ShiftDurationCalculator.formatMinutes` builds "8h 30m" in code
(`domain/ShiftDurationCalculator.kt:39-48`), reaching ~52 call sites including
Reports insights, the shift form summary, compensation tier labels, and the
exported PDF — the artifact a user hands to an employer. The Paid Projects
module localized its own durations, so the app now ships two standards. The
release record (`paid-projects-release.md`, limitation 5) already names this.
Route the formatter through resources; `values-iw` already exists.
Relatedly, `validateShiftTimes` (`ShiftDurationCalculator.kt:54-66`) returns
raw English sentences and has no callers — delete it or localize it before it
finds one.

### 3.6 P2/P3 — Smaller UX notes

- **Mixed save semantics on Appearance** (audit P2-4, still open): adjacent
  controls commit differently with no cue. Either move clock style to
  immediate-apply or badge deferred controls as such.
- **Light-theme `outline` contrast ≈ 2.4:1** (`ElmTrackrColors.kt:42`,
  `#A7A2C8` on white) — below the 3:1 non-text target that
  `DarkThemeContrastTest.kt:23-26` asserts for dark only. Darken the light
  outline and add the mirror-image test.
- **Projects DETAIL hides the bottom bar** (`ProjectsScreen.kt:100-102`)
  although it is read-only; Settings sub-screens keep it. Intentional per the
  comment, but inconsistent — recommend hiding chrome only for FORM/BILLING/
  PAYMENT.
- **Frozen-web trap worth documenting:** the archived web app hardcodes
  `features_paid_projects: false` on any features save
  (`app/settings/features/page.tsx:80`), so a user touching the old web UI
  silently disables the Android Projects tab on next sync. Worth a line in the
  supabase contract doc, and an argument for retiring the web deploy.

---

## 4. Platform and reliability findings

### 4.1 P1 — No foreground service backs a running shift

There is no foreground service anywhere in the repo (zero matches for
`startForeground`/`ForegroundService`). A running shift is a Room row plus an
`setOngoing(true)` notification. The design is otherwise right — elapsed time
derives from the stored `startTime` everywhere, `setUsesChronometer(true)`
makes SystemUI render the count-up, and `BootCompletedReceiver` covers reboot
and app replacement — but:

1. On Android 14+ (targetSdk is 36) ongoing notifications are user-dismissible.
   A dismissed notification leaves a running shift with no visible affordance
   and no Clock Out action until the app or widget is opened.
   `android/README.md:623` ("cannot be swiped away") is stale.
2. OEM task-killers can kill the process; the shift row survives, but the
   app-scope observer that re-posts the notification dies with it.

**Recommended fix:** promote the active shift to a foreground service
(`shortService`/special-use type), started at clock-in from all punch paths and
stopped at clock-out; keep the current notification as its required
notification. Consider a battery-optimization exemption prompt only if field
reports justify it.

### 4.2 P1 — At-threshold overtime alerts silently drop under Doze

All reminders run through WorkManager `setInitialDelay`
(`notification/OvertimeReminderScheduler.kt:57-69`) with no exact alarm. Under
Doze, execution can defer by hours, and the worker deliberately stays quiet
when it wakes late (`notification/ReminderRuleWorker.kt:44-47` skips a stale
pre-warning; `:72-76` skips a day-shifted scheduled reminder). Reasonable for a
*pre*-warning; for "you have entered overtime" — the app's highest-value
notification — a late alert beats none.

**Recommended fix:** use `AlarmManager.setExactAndAllowWhileIdle` (with the
`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` policy decision documented) for the
at-threshold firing only, keeping WorkManager for repeats and scheduled-time
rules.

### 4.3 P2 — Granting notification permission does not restore the notification

`MainActivity` asks for `POST_NOTIFICATIONS` on resume when a shift is active
(a good catch for widget-first users), but the grant callback
(`MainActivity.kt:52-57`) only invokes the stored continuation — nothing
re-posts the active-shift notification, so it stays absent until the next
`observeActiveShift` emission. Call `ActiveShiftRestorer.restore()` on grant.

### 4.4 P3 — Platform notes, in priority order

- **`HeadlessTrampolineActivity`** finishes inside `lifecycleScope.launch`
  under `Theme.NoDisplay` (`shortcuts/HeadlessTrampolineActivity.kt:13-22`);
  if the platform force-finishes first, the scope is cancelled mid-write. The
  sibling receiver uses `goAsync()` correctly — delegate to it, or switch to a
  translucent theme. Verify on-device before relying on this analysis.
- **`WearMessageListenerService` is exported** and dispatches PUNCH_IN/OUT with
  no `sourceNodeId` or permission check (`wear/WearMessageListenerService.kt:20-26`).
  Play Services normally restricts senders to same-signature apps, but nothing
  here verifies it; the app-lock guard fires only when app lock is on. Add
  `com.google.android.gms.permission.BIND_LISTENER`-style protection or a
  node-ID check.
- **Release builds silently fall back to the debug key** when the keystore is
  missing (`android/app/build.gradle.kts:79-88`, `wear` likewise), and
  `build_release.bat` still prints "BUILD SUCCESS - signed APK produced". Fail
  the build instead; Play rejecting the artifact is not a control for
  sideloaded copies.
- **Auth deep link is interceptable:** `elmtrackr://auth/*` with
  `autoVerify="false"` means any app can register the scheme and receive the
  Supabase tokens. Migrate the callback to verified HTTPS App Links —
  `proxy.ts:13` shows the `.well-known` plumbing was started and never finished.
- **Watch/phone format drift:** `WearDisplayMath.minutesToShort` renders
  "2h 0m" where phone surfaces render "2h"; `elapsedHm` is duplicated
  byte-for-byte between `wear-sync` and the widget. Six of the ten
  `WearDisplayState` fields (hardcoded English) are consumed by nothing but a
  test — delete them before someone wires UI to them.
- **Build scripts:** three of four `.bat` files hardcode one developer's
  absolute path (`build_release.bat:15`, `build_bundle.bat:62`,
  `build_incremental.bat:105`) — `build-apk.bat`'s `%~dp0` approach is the
  model. `build_bundle.bat` also skips `lintVital*` and reports only the phone
  AAB, while the release checklist requires the phone/wear pair.
- **Stale README sections** (`android/README.md:623-663`): dismissibility
  claim, `startActiveShiftObserver`, `LongShiftReminderWorker`, "not
  live-ticking", and the permission-request location are all out of date.
- **Gradle performance flags** (`gradle.properties:14-20`): configuration
  cache, parallel, and incremental compilation are all off — likely leftover
  debugging state worth revisiting.

---

## 5. Test additions recommended

The suite (115 classes, ~1,336 tests) is strongest where it asserts invariants;
these gaps map directly to the findings above:

1. `IsraeliCompensationEngineTest` — the 500-line minute classifier is tested
   only through `PayrollCalculatorTest`.
2. A DST property test: shifts crossing Asia/Jerusalem transitions, asserting
   payable minutes, night-window and daily-standard behavior (no DST test
   exists anywhere in payroll).
3. Cross-month pay week: `priorWeekMinutes` through a month boundary (§2.7).
4. Rounding composed with minimum-shift, including the round-to-zero case (§2.3).
5. Hours/money agreement: report overtime minutes vs payroll overtime brackets
   for the same shift (§2.4), and a midnight-crossing weekend shift on the
   generic engine (§2.5).
6. Period-consistency invariant for project reports: every period-labelled
   figure computed from period inputs (§2.1, §2.2, §2.8), plus a case where
   `to` is after `today`.
7. `LocalReportsRepository` parity with the ViewModel path for premium shifts
   (§2.6).
8. Light-scheme contrast assertions mirroring `DarkThemeContrastTest` (§3.6).

---

## 6. Recommended fix waves

1. **Money correctness (P0):** project effective rate and CSV basis (§2.1,
   §2.2); rounding/minimum composition (§2.3); unify report thresholds with pay
   thresholds (§2.4); midnight-crossing weekend pay on the generic engine
   (§2.5); the three P0 residues (§2.6).
2. **Trust (P1):** Projects retry (§3.1); form discard guards (§3.2); foreground
   service for the active shift (§4.1); exact alarm for the overtime threshold
   (§4.2); month-boundary overtime context (§2.7).
3. **Coherence (P2):** overdue-vs-today (§2.8); biometric prompt strings (§3.3);
   timezone residues (§3.4); duration localization (§3.5); save-semantics
   unification and notification re-post (§4.3).
4. **Hygiene (P3):** §2.9 and §4.4 items — dead Wear fields, formatter
   unification, signing fail-fast, build scripts, README refresh, contrast test.
