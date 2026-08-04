# ElmTrackr — Product Quality Audit (July 2026)

Three-lens code audit of the Android app: pay-customization model, navigation/interaction UX, and cross-surface consistency (dashboard / widgets / notification / shortcuts / Wear). Findings verified against source; file references are relative to `android/app/src/main/java/com/elmtrackr/app/` unless noted.

Legend: **P0** wrong money/data shown · **P1** breaks user trust (data loss, silent failure) · **P2** inconsistent/confusing behavior · **P3** polish and dead code.

---

## P0 — Wrong money or data shown

### P0-1. Premium profiles are inert outside the edit-form preview
Every persisted or displayed pay computation calls the calculator without the `premiumProfiles` argument (defaults to empty): dashboard total (`ui/dashboard/DashboardViewModel.kt:130`), reports total (`ui/reports/ReportsViewModel.kt:149`), shift rows (`ui/shifts/ShiftRowDisplayModel.kt:41`), shift detail (`ui/reports/ReportsScreen.kt:1378`), PDF export (`ReportExporter.kt:166`), all insights builders. The only caller that passes them is the live preview (`ui/shifts/ShiftEditFormUi.kt:578`).

Compounding: assigning a premium sets `isSpecialDay = premiumProfileId != null` (`ShiftsViewModel.kt:221,258`), so premium shifts are silently priced at the **holiday multiplier (default 150%)** instead of the premium's rate.

**User impact:** assign a 200% premium → preview shows 200%, but dashboard, lists, reports, and PDF all show 150%. The entire premium feature (6 stacking policies) does not affect any saved number.

### P0-2. Currency can disagree between screens
Currency is stored three times: `settings.currency` (enum), `settings.currencyCode` (string), `profile.currencyCode` (string). Reports and PDF read the enum (`ReportsScreen.kt:663,813,1580`; `ReportExporter.kt:63`); Dashboard reads profile/string first (`DashboardScreen.kt:386-388`). Editing currency on the Compensation-rules screen updates the strings but never the enum (`CompensationResolver.kt:172-182` has no enum field).

**User impact:** change the profile currency ILS→USD → Dashboard shows `$`, Reports/PDF still show `₪` for the same amounts.

### P0-3. Edit-form pay preview diverges from the saved value
The preview computes without week context (`priorWeekMinutes = 0`, `PayrollCalculator.kt:42-50` via `ShiftEditFormUi.kt:578`), while the saved shift is computed with the real week (weekly OT tiers). Combined with P0-1, the number the user approves in the editor is not the number shown afterwards.

### P0-4. IL engine silently ignores night premium and deductions
`CompensationRules.night*` and `deductions*` are honored only by the generic engine (`PayrollCalculator.kt:199-207,643`); `IsraeliCompensationEngine` returns `nightGross = 0.0, deductionsGross = 0.0` (`IsraeliCompensationEngine.kt:168-169`). Israeli-region users can configure these rules with zero effect and no warning.

### P0-5. "Day progress" means different things per surface
Dashboard ring = current-shift elapsed (`DashboardScreen.kt:664-671,739-741`); widget and Wear = whole-day total across shifts (`widget/WidgetStateMapper.kt:67-84`, `wear-sync` `WearDisplayMath.kt:34-37`). On a multi-shift day the phone can show 12% / no overtime while the watch shows 87%.

---

## P1 — Breaks user trust

### P1-1. Onboarding replay silently resets customization
Replay hardcodes `clockStyle = CLASSIC`, `featuresClockStyles = true`, `featuresPaidProjects = false` (`ui/onboarding/OnboardingScreen.kt:322-326`) and writes them to settings (`OnboardingViewModel.kt:109-120`); `preserveExisting` guards only the compensation profile. A user replaying onboarding loses their chosen clock face without any indication.

### P1-2. `featuresPaidProjects` is a dead flag
Stored and synced but gates nothing: task surfaces appear whenever tasks exist (`DashboardScreen.kt:391`, `ShiftEditFormUi.kt:352`, Settings→Pay→Tasks always visible). No UI toggle exists (`FeaturesDetailScreen` omits it), yet `SettingsViewModel.updateFeatureFlag(PAID_PROJECTS)` and a passing unit test exist for a control no screen offers.

### P1-3. Destructive actions without confirmation
Premium-profile delete (`ui/settings/PremiumProfilesScreen.kt:212-216`) and refund-claim delete (`ui/refunds/RefundClaimsSection.kt:354-361`) act on a single tap. Shifts, tasks, and account deletion all confirm — these two are the outliers, and both destroy user-entered data (a receipt photo, in the refund case).

### P1-4. Unsaved settings edits can be lost silently
Back navigation from PROFILE/PAY/APPEARANCE/FEATURES ignores `unsavedCount` (`ui/settings/SettingsScreen.kt:105-107,167`) — no discard prompt. Worse, field state is keyed on `remember(state.settings.x)` (`SettingsScreen.kt:213-230`), so any background settings emission (e.g., a sync pull) reinitializes fields **while the user is typing**.

### P1-5. Notification clock-out can resurrect a deleted shift
`ShiftDao.getShiftById` does not filter `deletedAt IS NULL` (`data/local/ShiftDao.kt:30-31`); `ClockOutReceiver` uses it (`notification/ClockOutReceiver.kt:33`) and will write an `endTime` onto a shift already deleted on another device, marking it `PENDING_UPDATE` and re-syncing it. Widget/shortcut/Wear paths correctly no-op via `observeActiveShift`.

---

## P2 — Inconsistent or confusing behavior

### P2-1. Settings→Pay edits a copy that does not drive computation, and only for the default profile
Post-migration, the engine reads compensation-profile rules; Settings→Pay reads/edits `settings.*` and mirrors only into the **default** profile (`SettingsViewModel.kt:266-297`). With a second job profile, editing "Daily overtime" in Settings→Pay has no effect on that job's shifts, and the screen does not say so. Two undisclosed editors exist for the same hourly rate (Settings→Pay vs Compensation rules). Additional gap: clearing the profile's base rate does not clear `settings.hourlyRate` (merge via `?:` in `UserSettings.kt:54`), leaving a stale rate on display.

### P2-2. Live clock-in cannot select a compensation profile or task-correct rate
Clock-in always books to the default profile (`DashboardViewModel.kt:238-245`); the per-shift profile picker exists only in the edit form. Widget/Wear punches additionally skip `ensureMigrated` and the default-profile fallback (`widget/WidgetActions.kt:12-36`, `wear/WearActions.kt:12-41`) and attach the auto-suggested task, whose rate snapshot overrides the base rate — a headless punch can freeze a different rate than the user intended.

### P2-3. Device-timezone leaks
Reports opens on `YearMonth.now(ZoneId.systemDefault())` (`ReportsViewModel.kt:58-59`) while everything else uses the work timezone — across a month boundary the Dashboard and Reports disagree about "this month." The edit-start-time dialog also uses the device zone (`DashboardScreen.kt:1789,1803-1807`) and allows a future start time, producing a negative timer on the phone (unclamped, `DashboardScreen.kt:668`) and 0:00 on watch/widget (clamped).

### P2-4. Mixed save semantics and feedback styles
On Appearance, theme/language/reduce-motion/crash-reports save instantly while clock-style and the clock-faces toggle wait for the save bar — adjacent switches, different behavior, no visual cue (`SettingsScreen.kt:288-291,348-358`). Save feedback uses three mechanisms across the app (snackbar+haptic, inline auto-dismiss text, dismissible card). The Features screen exposes 3 of 5 flags; "Clock faces" lives on Appearance and paid-projects nowhere (P1-2).

### P2-5. Dashboard tasks overlay state surprise
`showTasks` is `rememberSaveable` (`DashboardScreen.kt:170`): switching tabs away and back can land the user inside Task Management unexpectedly; the same screen opened from Settings has different (correct) back behavior.

---

## P3 — Polish and dead weight

- First-clock-in celebration unreachable if the first punch is from widget/watch (`DashboardViewModel.kt:231,248`); the moment is lost forever.
- Notification clock-out doesn't eagerly refresh widget/Wear (relies on a debounced observer) — slower repaint than other paths.
- Widget date/day-total self-corrects only while the 10s worker runs; Wear can keep a timer ticking if offline at clock-out (inherent, worth a stale-state guard).
- Dead code: onboarding `ClockStyleStep` never wired (`OnboardingScreen.kt:960-971`) — the mechanism behind P1-1; `updateFeatureFlag`/`updateWeekendDays`/`clearPasswordResetFeedback` have no production callers but are asserted by tests; two `StackingPolicy` values are declared but unselectable.
- Password-reset feedback text lingers until the ViewModel dies (`SettingsDetailScreens.kt:344-351`).
- Premium Profiles screen uses different chrome (Scaffold+TopAppBar) than every sibling settings screen.
- Dashboard `clockOut` lacks the `runCatching` guard its notification/Wear counterparts have (narrow race → crash).

---

## Verified consistent (no action needed)

Double clock-in across surfaces is mutex-safe with a single repository; sync triggers fire uniformly from the repository; Dashboard and Reports monthly totals agree (same builder, same zone, completed-only); overnight shifts attribute consistently to the start month; Wear tile/complication/app render from shared math; travel-refunds and insights flags gate coherently everywhere; empty states and back-handling are well covered.

---

## Recommended fix waves

1. **Money correctness (P0):** thread `premiumProfiles` through every calculation via one shared entry point; decouple `isSpecialDay` from premium assignment; unify currency to a single source of truth; give the preview real week context; either implement night/deductions in the IL engine or hide those fields for IL profiles; pick one "day progress" definition (recommend day-total everywhere).
2. **Trust (P1):** preserve settings on onboarding replay; delete-confirmations for premium profiles and refund claims; discard-changes guard + stop reinitializing fields on background emissions; filter soft-deleted shifts in `getShiftById`; decide the paid-projects flag (recommend: remove the flag, keep tasks as an opt-in-by-use feature).
3. **Coherence (P2):** work-timezone in Reports initial month and the start-time dialog + future-time validation; punch parity (`ensureMigrated` + celebration flag from all surfaces); disclose "applies to default job only" on Settings→Pay or fold it into Compensation rules; unify save semantics per screen.
4. **Polish (P3):** dead-code removal, feedback unification, stale-surface guards.

---

## Status — verified against code, August 2026

Every finding re-checked against the source rather than assumed from the fix waves.
Where a change since July altered a finding's meaning, that is stated rather than
quietly dropped.

### Closed

| Item | How |
|---|---|
| P0-1 | `premiumProfiles` threaded through `PayrollCalculator`; **both** engines return the premium's own multiplier early, so `isSpecialDay = premiumProfileId != null` no longer prices a premium shift at the holiday rate. That assignment still exists but is now a UI coupling, not a money bug. |
| P0-2 | `profileToLegacySettingsUpdates` writes the `currency` enum alongside `currencyCode`, closing the root cause. Dashboard, Reports and the PDF share one precedence via `displayCurrencyCode()`; the last four screens reading the raw enum now go through it too. |
| P0-3 | The edit preview uses `calculateShiftPayInContext` with the real month context. |
| P0-5 | Dashboard reads the whole-day total, matching widget and Wear. |
| P1-1 | `preserveExisting` guards `clockStyle` and `featuresClockStyles` on replay. |
| P1-3 | Both deletes confirm. |
| P1-4 | Field state is `rememberSaveable` without settings keys, so a sync pull no longer resets fields mid-typing; back navigation checks `unsavedCount`. |
| P1-5 | Guarded at the repository layer (`LocalShiftsRepository` applies `takeIf { it.deletedAt == null }`); the DAO deliberately still returns deleted rows for the three callers that need them, and says so. |
| P2-2 | Widget and Wear both route through `ClockInActions.clockInHeadless`, which calls `ensureMigrated`. |
| P2-3 | Reports seeds from the device zone then corrects to the work zone once settings load, guarded so it cannot fight user navigation. Future start times are rejected by the dialog. |
| P2-5 | `showTasks` is plain `remember`. |
| P3 | First-clock-in celebration reaches headless punches via a pending flag; dead code (`ClockStyleStep`, `updateFeatureFlag`) removed; Premium Profiles uses the shared settings chrome. |

### Fixed in this pass

- **P0-4 — night premium ignored on the Israeli engine.** `nightGross` was hardcoded
  to `0.0`, so an IL-region user could configure a night premium and see no effect
  anywhere and no warning. The deductions half had been fixed earlier; this was the
  other half. Found on the way: the breakdown loop existed **twice**, and the copies
  had drifted — only one honoured `forceRegularRate` in the manual-holiday check.
  Both now call one shared builder.
- **P2-1b — stale hourly rate.** `UserSettings.apply` merges with `?:`, which cannot
  distinguish "not specified" from "explicitly none", so clearing a profile's base
  rate left the old number in `settings.hourlyRate` — still seeding the Settings→Pay
  field and the four gates that decide whether to show pay. `Updates` now carries an
  explicit `clearHourlyRate`, set only by the profile-mirror path so every other
  caller keeps the "leave alone" semantics.

### Superseded by changes since July

- **P1-2** recommended removing `featuresPaidProjects` as a dead flag. Paid Projects
  then shipped as a real feature; the flag is live and gates it.
- **P3's** "widget self-corrects only while the 10s worker runs" — that worker no
  longer exists, replaced by a minute-aligned chain (`WidgetTimerScheduler`).

### Open, and deliberately so

- **P2-1a** — Settings→Pay still edits the default profile only. The disclosure the
  audit asked for is in place (`settings_overtime_thresholds_hint`); folding the two
  editors into one is an architectural change, not a fix.
- **P2-2's second half** — live clock-in still books to the default profile; the
  per-shift picker exists only in the edit form. A feature, not a defect.
- **P2-4** — mixed save semantics on Appearance (some switches instant, clock style
  behind the save bar). A design decision that needs one answer applied across the
  screen.
