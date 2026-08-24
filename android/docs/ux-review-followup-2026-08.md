# UX follow-up — August 2026

A second pass, written to sit **after** `ux-ui-review-2026-08.md` and not to repeat it.
Everything in that review's backlog is still open and still correctly prioritised; nothing
below duplicates it.

**Method:** source audit, plus `:app:testDebugUnitTest` and `:app:lintDebug`. The app was
not run on a device, and no finding here rests on a screenshot. Where a claim is about how
something *looks* rather than what the code does, it says so.

**What is different about this pass:** it started from two user reports rather than from a
sweep, and both turned out to be the visible edge of something larger — a settings screen
that could not reach a stored value at all, and a preference filed under the wrong heading
in the wrong control.

---

## Changed in this pass

### 1. Sick pay is configurable per employer

**The problem.** `SickLeavePolicy.payTiers` drives every sick-day estimate, and no screen
could edit it. The ladder was written once when the workplace's policy row was created,
from the region preset, and `WorkplacesRepository.updatePolicyRules` — the method that
changes it — **had no caller anywhere in the app**.

For an Israeli user that meant the statutory ladder, whose first day pays nothing, applied
whatever their contract said. Paying sick leave in full from day one is a common
improvement on the statutory minimum, and there was no way to enter it.

The data model was already right: `LeavePresets` ships both `israeliSickTiers` and
`fullPayFromDayOneTiers`, and `SickPayCalculator` has no `if (day == 1)` in it. Only the UI
was missing.

**What shipped.** A *Sick leave* card on the compensation profile screen:

- a switch for whether sick days are paid at all;
- a segmented control — **From day 1** / **Standard** — over `SickPayOption`;
- underneath, the resulting ladder spelled out day by day ("Day 1: not paid · Days 2–3:
  50% · Day 4 onwards: 100%"), because the selector names an arrangement while a user
  reconciling against a payslip needs the rungs;
- a third **Custom** segment that appears only when the stored ladder matches neither named
  option, so a policy built elsewhere or synced from a future version is recognised rather
  than destroyed the moment the screen opens;
- the standing caveat that the standard ladder is a common arrangement, not a statement of
  what the law requires of an employer.

Saving goes through `updatePolicyRules`, which supersedes the outgoing policy rather than
editing it — so an absence reported last month keeps the explanation it was priced with.
Saving an unchanged arrangement writes nothing.

**One honest limitation, disclosed in the UI.** See finding 7: the ladder belongs to a
*workplace*, and nothing in the app assigns a workplace to a pay profile, so all profiles
share one. With more than one profile the card now says so instead of implying otherwise.

### 2. Language moved out of "Appearance & clock", into its own screen

**The problem.** Three things at once.

*Filed under the wrong heading.* Language sat inside a screen titled "Appearance & clock",
between the theme control and the clock faces. It is not appearance, and a top-level
preference under an unrelated heading is one people stop finding.

*The wrong control.* `LanguageSegmentedControl` and `ThemeSegmentedControl` were both named
for a segmented control and both rendered `FilterChip`s. Filter chips are Material's
**multi-select filtering** component; a screen reader met four independent buttons where the
user was making one choice of four. The app's own design system already ships
`ElmSegmentedPillRow`, which carries `Role.RadioButton` and announces "selected, 1 of 3" —
these two screens simply did not use it. The language row had also outgrown the control: a
comment in the source records that four chips no longer fit a phone's width and had to wrap.

*No room to say what the options do.* A chip cannot explain that "Device language" currently
resolves to Hebrew.

**What shipped.**

- **Language is its own row** in the App group, with the language in use as its subtitle, and
  its own screen.
- That screen is a **single-choice list**: each language written in its own language, a
  "Device language" row that names what it currently resolves to, and — when the device
  option is chosen — an **In use** marker on the language actually on screen, so choosing
  "Device language" on a Hebrew phone does not leave the Hebrew row looking unrelated to
  what the user is reading.
- New `SettingsChoiceRow` / `SettingsChoiceGroup` in the settings component layer, built like
  `SettingsToggleRow`: the row is the target, the title is the control's name, the radio is
  state only. The group carries `selectableGroup()` so the list is announced as one choice.
- A note that the switch is immediate and that dates, numbers and amounts follow it.

### 3. Theme says what "System" resolves to

`ThemeSegmentedControl` now uses `ElmSegmentedPillRow`, and when *System* is selected it adds
one line: **"Following the device — dark right now"**. System was the only option whose
effect could not be read off its own label, and someone who cannot tell whether the app is
following the device or is simply set to light had no way to find out from three words.

The clock-face picker, reduce motion and the face preview stay where they are; that screen is
coherent once language leaves it.

---

## Findings, not fixed

Ordered by what they cost the user. Each says what it would take.

### 4. The compensation form can lose everything you typed, two different ways — **high**

The largest form in the app — region, currency, timezone, rate, work week, premiums, breaks,
rounding, deductions, and now sick pay — has an explicit Save button and **no unsaved-changes
protection at all**.

The settings hub *does* have one: `navigateGuarded` shows a discard dialog when
`unsavedCount > 0`. But that counter is fed only by `SettingsFormHost`, and Compensation,
Premium profiles and Task management are separate branches of the outer `when(dest)` that
never render it. So the guard is permanently inert for exactly the three screens with the
most to lose.

Two ways to lose the work:

1. **Back.** `onBack = { destination = SettingsDestination.PAY }` — no check, no prompt.
2. **Tapping another profile chip.** Every field is `rememberSaveable(state.profile.id)`, so
   selecting a different profile re-keys and resets them. This one is worse: it happens on
   the same screen, so no navigation guard would catch it.

The source already records a third: `CompensationRules` is not `Bundle`-saveable, so unsaved
tier edits are lost on rotation.

**Proposed.** Have the screen report a dirty flag the way `SettingsFormHost` does — compare
the working values against `state.profile` and hoist the count — so `navigateGuarded` covers
it, and gate the profile chips on the same flag with the existing discard dialog. Both reuse
what is already built; neither needs a new pattern.

### 5. Clearing the hourly rate silently switches off every pay figure — **high**

`hourlyRateText.toDoubleOrNull()` is written straight to `baseHourlyRate`. Blank becomes
`null`, and so does anything unparseable — "1.2.3", or a comma typed by a Hebrew or Arabic
keyboard. `PayrollCalculator` then returns `null` for every shift, and pay disappears from
the dashboard, the shift list, Reports and the exports.

Nothing warns. The save reports success, and the field keeps showing whatever was typed.

`SettingsViewModel` validates the same value on the legacy Pay screen
(`settings_error_rate_nonnegative`); `CompensationSettingsViewModel.saveProfile` validates
nothing.

**Proposed.** Distinguish the two cases, because they are different: *blank* is a legitimate
state — a profile can genuinely have no rate — and should be confirmed rather than blocked
("This job will show no pay estimates"). *Unparseable* is an error and should mark the field
with a message, as the tasks screen already does with `tasks_error_rate_invalid`, and block
the save. The decimal-separator half is worth fixing at the same time: the field filters to
`isDigit() || '.'`, so a comma cannot even be typed while both RTL locales are likely to
offer one.

### 6. Changing the region preset silently discards every rule you customised — **high**

Selecting a region in the dropdown runs `rules = preset.rules`, replacing the whole rule set.
The comment above it is right about why — keeping the previous region's thresholds produced
wrong overtime — but the user is not told. Someone who has tuned their overtime ladder,
break policy and deductions, then opens the region dropdown to check what it says, loses all
of it with no prompt and no undo.

**Proposed.** Confirm before replacing, and only when there is something to lose: compare the
current rules against the outgoing preset and show the dialog only when they differ. Same
dialog pattern as the delete-profile confirmation already on this screen.

### 7. The multi-job model is fully built and completely unreachable — **high**

`Workplace`, per-workplace `LeavePolicy`, per-workplace pay history and workplace-scoped
absences all exist, are synced, and have repository methods. **No screen creates, renames,
archives or assigns a workplace.** `upsertWorkplace` and `archiveWorkplace` have zero callers,
exactly as `updatePolicyRules` did before this pass, and nothing ever sets
`CompensationProfile.workplaceId`.

What the user sees today:

- The absence form renders a workplace dropdown that will always contain exactly one item.
- Every pay profile resolves to the same default workplace, so the sick-pay arrangement added
  above is shared by all of them — which is why that card now says so.

A user with two jobs can already model them as two pay profiles. They cannot give those jobs
different sick or vacation arrangements, which is the case the data model was built for.

**Proposed.** Smallest useful step: assign a workplace to a pay profile. Create one on demand
when a second profile is created, set `workplaceId` on it, and let the profile's name rename
the workplace. That alone makes leave per-job and turns the absence form's dropdown into a
real choice, without a workplace-management screen. Hide the dropdown while there is one
workplace either way.

### 8. The leave form's "Enter amount" field does nothing — **high**

Recorded in full in `optimization-debug-audit-2026-08.md`; repeated here because it is a UX
defect as much as a logic one. When the engine cannot value a day it names a gap and the form
offers "Enter amount" — and the amount is never used, because `LeaveCalculator` reads
`manualDailyAmount` only under the `MANUAL` pay basis, which no screen can select. The day
saves at 0.00.

It needs a product decision first (does a typed amount pass through the sick ladder? does it
scale by partial-day units?), which is why it is not fixed here.

### 9. A failed leave save can show an empty error — **medium**

`AbsenceFormViewModel` sets `validationError = UiText.Raw(error.message ?: "")`. Two problems
in one line: repository failures carry English developer text (`error("Shift not found")`),
which reaches a Hebrew or Arabic reader untranslated; and an exception with no message
renders as an **empty string** — the save button re-enables and nothing at all explains why
the absence did not save.

`ui/common/UserFacingError.kt` exists specifically to replace this pattern, and its KDoc names
it. Two more sites remain in `RefundClaimViewModel` (lines 170 and 238).

**Proposed.** Route all three through `UserFacingError.message(e, R.string.error_generic)`.
Mechanical, and it removes the last of the raw-message surfaces the earlier review started
closing.

### 10. Two carry-overs worth restating — **medium**

Both are in `optimization-debug-audit-2026-08.md` with full evidence; both are user-visible
and both are UX decisions rather than bugs, so they belong on this list too.

- **Amounts are formatted `Locale.US` with the currency symbol glued on by hand** at 37 of
  the app's 46 money render sites, in an app shipping Hebrew and Arabic. The locale-aware
  formatter exists and is used by Paid Projects only. Fixing it means moving `MoneyFormatter`
  and `HoursFormatter` together, since the latter is pinned to `Locale.US` deliberately to
  match the former.
- **Pay-bracket labels are English on screen** — the shift edit form's pay preview reads
  `8.6 שע׳ 100% — Regular` in Hebrew — *and* those same strings are how the money is sorted
  into categories. Localising them without first giving `PayBracket` a typed category would
  silently collapse every category into "regular".

---

## Verification

```
./gradlew :app:testDebugUnitTest :app:lintDebug
```

**1 729 tests, 0 failures, 0 skipped; `lintDebug` reported no errors.** Up from 1 710:
19 tests added.

New tests: `SickPayOptionsTest` covers recognising a stored ladder, the round trip through
each option, and describing a ladder by what it *pays* rather than how it is stored —
including overlapping rungs and holes. `CompensationSettingsViewModelTest` gains five cases
covering the previously dead write path: the preset shown for an unconfigured workplace, the
from-day-one arrangement reaching the policy, the rest of the leave policy surviving the
write, the old policy being superseded rather than edited, and an unchanged arrangement
writing nothing.

`verifyPaparazziDebug` was not run, for the reason in `declined-findings.md`: the goldens are
host-specific and fail on any machine other than the one that recorded them. This pass
changes the appearance screen, the settings hub and the compensation screen, so the goldens
covering those **are expected to change** and should be re-recorded on the CI image once
backlog item 4 of `ux-ui-review-2026-08.md` is done.

**Not verified visually.** No screenshot of the new Language screen, the reworked theme
control or the sick-leave card was reviewed, on either theme or in RTL. The layouts use the
existing token scale and existing components, and the design-system budget test passes, but
that is an argument about the code, not evidence about the pixels.

**Translations.** The Hebrew and Arabic strings added here were written for this change and
have not been reviewed by a native speaker. The Hebrew is idiomatic; the Arabic is Modern
Standard and is the one I would put in front of a reviewer before release.
