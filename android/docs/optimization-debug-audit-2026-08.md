# Optimisation and debug audit — August 2026

**Scope:** the pay and reporting calculations, the screens that display them, and the
paths that run them. Data layer, sync and receipts were re-read but not re-audited —
`backend-perf-audit-2026-08.md` covers them and its conclusions still hold.

**Method:** code reading, a full unit-test run, and — for the performance section —
**measured** before/after timings. That last part is new: `backend-perf-audit-2026-08.md`
states plainly that no measurement was taken and that none of its numbers may be quoted
as one. Every figure in *this* document's performance table came from running the code.

**Baseline established before any change:** `:app:testDebugUnitTest` — **1 687 tests,
0 failures, 0 skipped** on an untouched checkout of the branch.

**Measurement caveat:** the timings are wall clock on a JDK 21 x86-64 Linux container,
not on a phone. They are valid as a *ratio* between before and after, which is what the
fixes are argued on. Absolute per-shift cost on a mid-range Android device will be
several times higher. Nothing here was run on hardware.

---

## Fixed

### 1. The report counted overtime that a weekly-only profile never pays

`code-review-2026-07.md` records this defect as closed under "Found while closing the
above". The gate it describes landed in `MonthlyReportBuilder.buildMonthlyReport`'s
per-week fold only. `buildShiftBreakdown` — the per-shift function — still measured
every minute past the profile's daily *standard*, whether or not the profile had a
daily ladder to pay from, and whether or not overtime was enabled at all.

That breakdown is what five surfaces read:

| Surface | Site |
|---|---|
| Shift-row overtime badge | `ui/shifts/ShiftRowDisplayModel.kt:47` |
| Per-shift rows in Reports | `ui/reports/ReportsScreen.kt` (`ShiftReportRow`) |
| CSV export, per-shift rows | `ui/reports/ReportsViewModel.kt:482` |
| PDF export, per-shift rows | `ui/reports/ReportExporter.kt:190` |
| Per-task overtime totals | `domain/TaskMonthlyReportBuilder.kt:59` |

Two presets are affected as shipped: **US federal** (`dailyOvertimeTiers` is empty by
design — FLSA overtime is weekly) and **UK** (`overtimeEnabled = false` — UK law sets no
statutory overtime).

The sharpest form of it is inside one exported file. For a US federal user working four
10-hour days in a week, the CSV's per-shift rows each reported 2.00 overtime hours while
its own TOTAL row reported 0.00 — and the comment above that TOTAL row says the row
exists precisely so the export agrees with the screen.

**Fix.** One named predicate, `CompensationRules.paysDailyOvertime`
(`domain/model/CompensationModels.kt`), now used by all three sites that had the
condition spelled out separately: `buildShiftBreakdown`, `buildMonthlyReport` and
`PayrollCalculator.priorStraightTimeMinutesBefore`. Spelling it out three times is what
let the fix land in one place and not the others.

Weekly overtime is unaffected: it belongs to the week, not to a day, and
`buildMonthlyReport` still applies it over real pay weeks. A US federal week of six
8-hour days still reports its 8 hours of weekly overtime — with every per-shift
breakdown at zero, which is the honest attribution.

**Tests added** (`MonthlyReportBuilderTest`): a weekly-only profile reports no daily
overtime; its report overtime equals its paid overtime brackets; it still reports weekly
overtime; an overtime-disabled profile reports none; and California — which does pay a
daily ladder — still reports its daily overtime.

### 2. Switching overtime off raised Friday pay

Open item, `code-review-2026-07.md` §2.9.

The Israeli engine's no-overtime path (`flatRestOrRegularSegments`) decided weekly rest
from the shift's start *date*, through `WeekendRules.isWeekendDate` — a whole-day test.
The overtime path decides it per minute through `isWeeklyRestAt`, which honours
`weeklyRestStartTime` (17:00 on the IL preset). So the whole of Friday counted as rest
whenever overtime was off.

Measured on the preset defaults at 60/h, a Friday 08:00–16:00 shift:

- overtime **on**: 495 — 420 minutes regular, 60 at 1.25× (the pre-rest day standard is
  7 hours)
- overtime **off**, before the fix: **720** — all 480 minutes at the 1.5× rest rate

Switching a premium off raised the estimate by 45%. The same bug paid a shift entirely
at the rest rate or entirely at the weekday rate depending on which side of 17:00 it
started.

**Fix.** The no-overtime path now classifies minute by minute through the same
`isWeeklyRestAt` the overtime path uses, and splits at the boundary. It also takes the
rest base as `maxOf(weekend, holiday)` rather than preferring holiday unconditionally —
which is what the overtime path already did, and which matters as soon as a user
configures a weekend multiplier above their holiday one.

After the fix: 480 for the morning shift; 630 for a Friday 14:00–22:00 shift (three
hours weekday, five at 1.5×); Saturday unchanged at 720.

**Tests added** (`PayrollCalculatorTest`): the Friday morning case; the invariant that
overtime-off can never exceed overtime-on, asserted against the overtime path's own
output; the boundary-crossing split; and Saturday and Sunday as regression guards.

### 3. The month's payroll ran on the main thread on the home screen

`ReportsViewModel` moved this computation to `Dispatchers.Default` with a comment saying
why: "a heavy month janked month navigation on mid-range devices".
`DashboardViewModel` runs the same computation — `sumMonthlyPay` plus
`MonthlyReportBuilder.buildMonthlyReport` over every completed shift of the month — and
had no `flowOn` at all, so it ran in the `stateIn` coroutine: `viewModelScope`, which is
`Dispatchers.Main.immediate`.

On the app's home screen, at startup, and again on every shift, settings or sync
emission.

**Fix.** `.flowOn(computationDispatcher)` placed immediately after the payroll transform
and before the light state combines, with the `@ComputationDispatcher` qualifier already
provided by `di/CoroutineModule` — the same shape `ReportsViewModel` uses, including the
injected-for-tests default.

### 4. Per-row payroll recomputed on every recomposition in Reports

`ShiftReportRow` called `MonthlyReportBuilder.buildShiftBreakdown`,
`CompensationResolver.isWeekendShift`, `resolveShiftCompensation` and
`PayrollCalculator.calculateShiftPayInContext` inline, with no `remember`. The last of
those walks the shift's pay week and, on the Israeli engine, re-classifies every prior
shift of that week. `RefundAnalytics` called `sumMonthlyPay` over the whole month the
same way.

Both ran on the main thread on every recomposition of the row or card — outside the
`flowOn` the ViewModel had added for exactly this cost.

**Fix.** Both are wrapped in `remember` keyed on their inputs. `ShiftReportRow`'s five
derived figures are remembered as one value object so they cannot drift out of step.

### 5. The Shifts screen's week cards used a narrower pay-week context than Reports

`ShiftWeekGrouper.groupByWeek` called `sumMonthlyPay(weekShifts, …)` with no
`contextShifts` — so each week card's pay was computed with only that week's *in-month*
shifts as context. `sumMonthlyPay`'s own KDoc warns about this: a pay week straddling
the 1st then begins with no prior minutes and under-counts overtime.

Reports already handles it (it loads the previous month and passes `payContext`); the
dashboard already handles it (`observeShiftsForPayContext`). The Shifts screen did not,
so the two screens showed different pay for the same first week of a month.

**Fix.** `ShiftsViewModel` switched from `observeShiftsByMonthInZone` to the existing
`observeShiftsForPayContext`, filtering back to the month for everything displayed and
carrying the wider window as `ShiftsUiState.Ready.payContextShifts`. That context is
threaded to the week cards, the per-row pay figures and the hero summary. One query, a
handful of extra rows.

### 6. The payroll hot loop

`code-review-2026-07.md` §2.9 flags this as the higher-priority half of a split finding,
and `declined-findings.md` records it as explicitly **not** declined: "one
`Instant.atZone` per payable minute, and `weekStateBeforeShift` re-classifying every
prior shift, is roughly O(shifts² × minutes) per month".

Two changes, both structured so the arithmetic is unchanged:

**Per-minute zone conversion.** The classifiers read exactly two things from the clock:
which local day a minute falls on, and how far into that day it is. Getting them through
`Instant.atZone(zone)` costs an allocation and a zone-rules lookup per minute. A zone's
offset is fixed between DST transitions, and inside such a stretch both numbers follow
from the instant by integer arithmetic — the same arithmetic `java.time` performs
internally. New `domain/time/ZoneMinutes` does that, guarded by `hasFixedOffset`; a shift
containing a transition still converts every minute the original way.

`ZoneMinutes` is verified against `java.time` rather than against literals: every minute
of several days in Jerusalem (winter and summer), New York, Sydney, Kolkata (a half-hour
offset), UTC and pre-epoch instants, plus both Jerusalem transitions and the exact
boundary case. 13 tests.

**Night minutes hoisted out of the tier loop.** `applyNightPremium` recomputed
`countNightMinutes` — itself a minute-by-minute walk — once per pay bracket, and once per
classified segment on the Israeli path. It is a property of the shift: nothing in it
varies by bracket. It is now resolved once per shift (`nightFractionFor`), with the
per-bracket part reduced to the rate stacking that genuinely does vary.
`countNightMinutes` uses the same `ZoneMinutes` fast path.

---

## Measured

Same host, same JVM, same harness. Medians of 25 samples after warm-up, taken by
stashing the changes and re-running rather than by reasoning about the diff. Israeli
preset, 8h36 weekdays — the configuration with the heaviest classification path.

| Workload | Before | After | Change |
|---|---|---|---|
| One shift's pay, one-shift context | 87 µs | 49 µs | −44% |
| `sumMonthlyPay`, 10 shifts | 4 ms | 3 ms | −25% |
| `sumMonthlyPay`, 22 shifts | 9 ms | 7 ms | −22% |
| `sumMonthlyPay`, 44 shifts | 16 ms | 7 ms | −56% |
| Per-row pay for every shift, 10 shifts | 3 ms | 1 ms | −67% |
| Per-row pay for every shift, 22 shifts | 7 ms | 3 ms | −57% |
| Per-row pay for every shift, 44 shifts | 17 ms | 7 ms | −59% |

The quadratic term is visible in the before column: doubling the month from 22 to 44
shifts roughly *doubles* the cost of `sumMonthlyPay` and more than doubles the per-row
pass. After the change the 44-shift cases land where the 22-shift cases used to, on both
workloads — the shape is unchanged, the constant is not.

Two honesty notes on these numbers. The container is shared, so individual samples ranged
widely (the 22-shift `sumMonthlyPay` maximum was 20 ms against a 7 ms median); medians are
what the table quotes and what the before/after pairs were matched on. And the 10- and
22-shift `sumMonthlyPay` rows are only two to three sample-widths apart, so treat those
two percentages as directional. The 44-shift rows and the single-shift row are the
measurements to rely on.

The harness itself is not committed — a wall-clock assertion in CI is a flake. It builds a
month of consecutive weekday shifts on the IL preset and times `sumMonthlyPay` and a
per-shift `calculateShiftPayInContext` sweep, which is enough to rebuild from this
paragraph.

---

## Reported, not fixed

### The leave form's "Enter amount" field does nothing

`LeaveEstimate.NeedsInput` is documented at length: an unexplained 0 reads as "this day
is worth nothing", so instead the engine names a gap, and "every gap below names
something the user can supply". The form honours that — when a day comes back with a
gap, `AbsenceFormScreen` shows an `ElmDecimalField` labelled "Enter amount"
(`AbsenceFormScreen.kt:540`), and typing into it re-prices the draft.

The typed amount is never used. `LeaveCalculator` consults `manualDailyAmount` only in
the `MANUAL` pay-basis branch, and no screen sets that basis — the presets use
`ISRAEL_STATUTORY_AVERAGE_90` and `ACTUAL_WORKDAYS_AVERAGE`. So the re-price returns the
same gap, and on save `priceAllocations` stores `estimatedGrossPay = 0.0`
(`LocalLeaveRepository.kt:380`) and drops the amount. The one channel the whole
`NeedsInput` design exists to offer is wired to a basis the UI cannot select.

Not fixed here because the correct behaviour is a product decision, and two sub-questions
have to be answered before the code can be right:

1. **Does a typed amount pass through the sick ladder?** `finish()` returns
   `base * tier.multiplier`. If a user types 500 on a day at a 50% sick tier, is the day
   worth 500 or 250?
2. **Does it scale by partial-day units?** The field sits on the day row, next to that
   day's own figure, which suggests "this day is worth what I typed" — but the existing
   `MANUAL` basis multiplies by `entitlementUnits`, and for `LeaveBalanceUnit.HOURS`
   `finish()` ignores the day value entirely and uses `rate × units`.

Either answer is a one-line change; picking one on your behalf would be guessing at
policy. Whichever is chosen, `finish()` should stop stamping
`manualOverride(enabled = true)` on snapshots where the amount had no effect.

### Two money formatters with opposite policies

`domain/money/MoneyFormat` is locale-aware, and its KDoc says why: "Nothing here
concatenates a currency symbol by hand — that is what puts '₪' on the wrong side of the
number in Hebrew."

`domain/MoneyFormatter` does exactly that. `formatLegacy` builds
`"${currency.symbol}$separator$number"` and formats the number with `Locale.US`.

It is used at **37 sites across 9 files** — the dashboard, the shift list and edit form,
Reports, the PDF exporter, refunds, leave and onboarding. The locale-aware formatter is
used at 9 sites, all in Paid Projects. The app ships English, **Hebrew and Arabic**
(`localeFilters += listOf("en", "iw", "ar")`), i.e. two RTL locales, with Israel as its
primary market.

Not fixed here because it is one decision with app-wide visual reach, and it does not
stand alone: `HoursFormatter` is deliberately pinned to `Locale.US` *to match*
`MoneyFormatter` — "Hours and money appear side by side throughout the app; they have to
agree on what a decimal separator looks like." Making money locale-aware without hours
creates the mismatch that comment exists to prevent, so the change is both formatters or
neither.

The mechanical part is small: `MoneyFormatter.format` can delegate to `MoneyFormat`, and
all 37 sites move at once. What needs a decision is whether Hebrew and Arabic users
should see CLDR placement and separators — which is a product call about how the app
looks in its main market, not a bug fix.

### Pay-bracket labels are English on every screen, and are also the classification key

`ShiftEditFormUi` renders each pay bracket as
`stringResource(R.string.shifts_pay_bracket_line, hours, bracket.label, amount)`. The
pattern is translated in both `values-iw` and `values-ar`; `bracket.label` is not. It is
built in the domain layer as a hard-coded English string — `"100% — Regular"`,
`"175% — Weekly rest overtime"`, `"200% — Premium (Night double)"`. So the pay preview
on the shift edit form — the screen where a user checks what a shift earned — reads
`8.6 שע׳ 100% — Regular - ₪516.00` in Hebrew.

It cannot simply be moved to resources, and that is the more important half of this
finding: **those same label strings are how the money is classified.** Both engines split
gross into categories by substring-matching them:

```kotlin
tier.label.contains("overtime", ignoreCase = true) -> overtimeGross += …
tier.label.contains("Weekly rest", ignoreCase = true) -> weekendGross += …
```

Translate the labels and every category silently collapses into `regularGross` — no
error, no failing test, just wrong numbers on the reports and in the exports. The label
is doing double duty as a display string and as a typed key, and only the fact that it is
never localised keeps the second job working.

**Recommended fix:** give `PayBracket` an explicit category — an enum beside the label —
classify from the enum in both engines, and then render the label from resources. In that
order; localising first is the change that breaks pay silently. The category enum is also
worth having on its own: the current `when` chain has a dead branch
(`contains("Overtime", ignoreCase = true)` after `contains("overtime", ignoreCase = true)`
in `calculateGenericShiftPay`), which is exactly the kind of thing string matching hides.

### The Shifts screen still computes its list on the main thread

`buildShiftsLazyListItems` is correctly wrapped in `remember`, but `remember` runs during
composition. So on month navigation, on any sync emission and at first paint, the whole
month's payroll — per-week `sumMonthlyPay` plus a per-row `calculateShiftPayInContext`
for every shift — runs on the main thread. Fix 6 cut the constant factor and fix 5 fixed
the numbers, but the work is still where Reports and the dashboard no longer put it.

The right fix mirrors what those two do: compute the list in `ShiftsViewModel` on the
computation dispatcher and expose it as state. It was left out of this pass because it
changes the screen's state shape — the list stops being synchronously available, which
needs a loading state for the list region — and that is a UI-behaviour change rather than
an optimisation.

### The O(shifts²) week-state recomputation remains

`IsraeliCompensationEngine.weekStateBeforeShift` re-derives the week's accumulated state
from scratch for every shift, re-classifying each prior shift of that week. Computing a
month walks the same prefixes repeatedly.

Fix 6 made each of those walks substantially cheaper but did not remove the repetition.
Removing it means memoising a classification keyed on everything
`classifyShiftSegments` reads — the shift's times, break, special-day and premium flags,
the resolved rules, the zone and the two "minutes before" counters. An incomplete key in
a payroll cache is a wrong-pay bug, and the engine has no dedicated test file to catch
one: `IsraeliCompensationEngine` is exercised only through
`PayrollCalculatorTest` (10 references to `RegionCode.IL`) and `Wave1PayRegressionTest`.

**Recommended order:** a characterisation suite for the Israeli engine first, then the
memo. Not the other way round.

### `PayWeekMinutes.forEachWithPriorWeekMinutes` still groups by ISO Monday

`declined-findings.md` resolved this as "rename rather than re-implement". Neither has
happened — the helper still groups by `isoWeekStart` while the engines anchor weeks on
`rules.weekStartDay` (Sunday for both IL and US). Still harmless, because
`sumMonthlyPay` discards the accumulator it produces; still a trap, and now also visible
waste, since `sumMonthlyPay` pays for the grouping and the running total and then throws
them away.

### The CSV's TOTAL row has no pay-week context

`exportCsv` calls `buildMonthlyReport(…)` without `contextShifts`, while the on-screen
report passes `payContext`. So a month whose first week straddles the 1st can export a
TOTAL overtime figure lower than the screen shows for the same month. Same class as fix
5, one argument, but on an export path — worth confirming against a real month before
changing, since the export's totals row is what a user hands to an employer.

---

## Checked and found sound

Recorded so the next pass does not re-derive it.

- **`Money` / `MoneyPolicy`.** `BigDecimal` throughout, one explicit rounding step at the
  display scale, cross-currency arithmetic refused rather than silently converted,
  persisted as plain strings. The strongest part of the calculation layer.
- **Throwing collection operations.** Every `first`, `last`, `maxOf` and `reduce` in
  `domain/` is either guarded by an emptiness check immediately above it
  (`ReceiptParser`, `BidiText.isolate`, `mergeAdjacentSegments`) or reads a `groupBy`
  value, which cannot be empty.
- **Division by zero in the refund analytics.** `RefundAnalytics` divides by
  `claims.size` and by `total`, and calls `claims.maxOf`. The composable is guarded by
  `if (claims.isNotEmpty())`, and `UpsertRefundClaim` enforces `amount > 0.0`, so
  neither denominator can be zero.
- **Room indices.** Every synced table carries `userId`, `(userId, syncStatus)` and
  `remoteId` indices, plus the query-shaped composites the leave and billing tables need.
- **`payableNetMinutes`.** Break handling, auto-deduction, rounding and the
  minimum-shift floor compose in the right order, and the floor is judged against the
  *worked* net rather than the rounded one — the composed case
  `code-review-2026-07.md` §2.3 raised is closed and commented.
- **The category split.** `regularGross + overtimeGross + weekendGross + holidayGross +
  nightGross` reconstructs `totalGross` because the night uplift is blended into each
  bracket's rate rather than added as a sixth bucket, which is what keeps every bucket
  non-negative.

---

## Verification

```
./gradlew :app:testDebugUnitTest :app:lintDebug
```

**1 710 tests, 0 failures, 0 skipped; `lintDebug` reported no errors** (371 warnings and
1 hint, which is the existing baseline — none of them new). Up from the 1 687-test
baseline: 23 tests added — 5 on the weekly-only overtime gate, 5 on the overtime-off rest
boundary, and 13 on `ZoneMinutes`.

Two existing suites needed updating, both because a fix changed something they were
relying on rather than because the fix was wrong:

- The five `Dashboard*ViewModelTest` classes now pass `mainDispatcherRule.dispatcher` as
  the computation dispatcher. The production `flowOn` would otherwise run the payroll
  transform off the test scheduler, so `advanceUntilIdle` could not await its emissions —
  the same reason `ReportsViewModelTest` already did this.
- Three `ShiftsViewModelTest` cases seeded a January 2024 shift and asserted it appeared
  in the current month's list. That only worked because `FakeShiftsRepository` does no
  date filtering and the ViewModel passed its list straight through. The ViewModel now
  narrows the pay-context window back to the selected month, so the fixtures moved into
  the month under test.

`verifyPaparazziDebug` was **not** run, for the reason already recorded in
`declined-findings.md` and `ux-ui-review-2026-08.md`: all 31 goldens fail on any host
other than the one that recorded them, including against an untouched baseline. No
goldens were re-recorded. No visual change was made in this pass — the UI edits are a
`flowOn`, two `remember` wrappers and one extra state field.

Not run: `bundleRelease`, `lintVitalRelease` and the instrumented Room and DAO tests.
The release bundle needs a signing keystore this environment does not have, and the
instrumented tests need an emulator. CI covers all four.
