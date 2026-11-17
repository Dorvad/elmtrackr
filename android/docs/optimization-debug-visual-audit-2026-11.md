# Optimisation, debug and visual-gaps audit — November 2026

**Scope:** the Android phone app (`android/app`) and the Supabase backend (`supabase/`).
The home-screen widget, the Wear OS modules, the frozen web app and the iOS starter
were not audited.

**Method.** Three read-only sweeps over the source (performance, correctness, visual
and design system), each checked first against the seven earlier audit documents in
this directory so that closed and declined items were not raised again. Every finding
reported here was then re-read in source. The four data-safety findings were each
proven with a test that failed on the unfixed code before anything was changed. The
visual findings came from rendering the screens fresh — the committed goldens are from
5 August and predate Wave 2 — in dark theme, in Hebrew, Arabic and Russian, and at
1.3× and 2× font size.

**Baseline, on an untouched checkout of `Main` at `e3acc16`:**
`:app:testDebugUnitTest` 2 254 tests, `:wear-sync:test` 51 tests, 0 failures in either.

**Urgency scale.** *Critical* — data loss, wrong pay or an exploitable hole in normal
use. *High* — wrong data in a realistic case, a security weakness, an accessibility
failure on a primary screen, or a gap that blocks verifying other work. *Medium* — a
degraded experience, a measurable cost, or a rare-case defect. *Low* — polish.

**Nothing found meets Critical.** Ten items were fixed: nine rated High and one
Medium that was fixed because proving it and fixing it were the same work.

Most of what the August audits left open is now closed and is not repeated here: the
typed `PayCategory`, the locale-aware `MoneyFormatter`, CSV totals with pay-week
context, the Shifts list computed off the main thread, the compensation-form guards,
workplace assignment, the leave form's "Enter amount", and sync-health coverage of
projects, billing and leave.

---

## Fixed

### 1. A scanned receipt came loose from its claim whenever the claim was rewritten — High

`receipts.refundClaimId` is the database's only foreign key, declared
`ON DELETE SET NULL`. `RefundClaimDao.insertClaim` and `upsertClaim` wrote through
`OnConflictStrategy.REPLACE`, which SQLite carries out as a DELETE followed by an
INSERT — and the DELETE fires the foreign-key action. So every rewrite of an existing
claim cleared the link on its receipt: an edit that did not come through the form
(`LocalRefundsRepository.updateClaim`), a sync pull (`pullRefundClaims`), and
adopting the server's copy after a rejected push (`adoptNewerRemoteClaim`).

The image is found through that link and nothing else: opening it from the claim,
restoring it into the form, and `uploadPendingReceipts`, which looks up
`receiptDao.getByRefundClaimId(claim.localId)` and skips the claim when it gets
nothing. A receipt that had not reached cloud storage before its claim was next synced
was therefore never uploaded. The form's save path re-links afterwards
(`RefundClaimViewModel`), which is why this was not visible on the common path.

**Fix.** Claims are inserted-or-ignored and then updated in place, in one
transaction, so the row is never deleted. Room's `@Upsert` was not used: its fallback
decides it hit a conflict by reading the exception message, and the database here is
SQLCipher rather than the framework's SQLite.

**Not recoverable by the fix:** receipts already detached keep their image file and
their row, but nothing identifies which claim they belonged to.

**Tests.** `RefundClaimReceiptLinkTest`, against a real Room database — a fake cannot
show what SQLite does with the statement. Two of its four cases failed on the old DAO.

### 2. A link could sign the user into someone else's account — High

The Supabase client is PKCE-only (`SupabaseClientProvider`), so none of the app's own
flows return tokens in the callback URL: sign-up confirmation, password reset and
browser sign-in all send a code challenge and come back with `?code=`. This was
checked against supabase-kt 3.2.2's `AuthImpl` (`resetPasswordForEmail`, the OAuth
URL builder and `updateUser` all call `preparePKCEIfEnabled`).

`SupabaseAuthRepository.handleDeepLink` nevertheless still accepted
`#access_token=…&refresh_token=…` and called `importAuthToken` with whatever it was
given. `elmtrackr://auth` is an unverified custom scheme, so another app, or a link on
a web page, could open that URL with a session of its own choosing. The user would be
signed into an account they do not own, and their shifts, notes and pay figures would
sync to it.

This is separate from the declined HTTPS App Links item. That one is about someone
*intercepting* a callback; this is about someone *sending* one, and it needs no
hosting to close.

**Fix.** `AuthCallbackParser` returns a code or fails. A code is not open to the same
trick: it is only useful together with the verifier this device generated.

**Not verified here:** a real sign-up or reset link on a device. If the Supabase
project sends any link type that is not initiated by the app — a dashboard invite or
an admin-generated magic link — that link would previously have signed the user in
and will now be refused. I found no such flow in the code.

### 3. A cancelled sync was recorded as a failed one — High

`runSyncStep` and `runSyncPipeline` wrapped suspending work in a bare `runCatching`,
as did every per-row block inside the push loops — 20 sites in `SyncRepositoryImpl`.
`runCatching` catches the `CancellationException` along with everything else.
WorkManager cancels a running sync whenever its network constraint is lost or a newer
request replaces it. That ordinary interruption was recorded as a failed step, the
pipeline carried on through every remaining step, and the run finished by publishing
`Failed: … was cancelled` as the sync status — in an app that Wave 2 went to some
trouble to make truthful about where data is.

Bounded to a misreported status and wasted work. No data was lost.

**Fix.** `runCatchingCancellable` (`data/RunCatchingCancellable.kt`) rethrows when the
coroutine itself has been cancelled and treats everything else as a failure. It asks
the coroutine rather than the exception's type, because a `CancellationException` can
also come from a `withTimeout` inside a library while the caller is still alive, and
rethrowing that would end the caller with no error and no result. The same check goes
in front of eight `catch (e: Exception)` blocks in `SupabaseAuthRepository` and the
profile migration.

Sign-out's "best-effort" catch was left alone on purpose: local state must still be
cleared if the remote call is interrupted.

**Test.** `a cancelled sync stops where it is and is not recorded as a failure`, which
failed with "steps ran after the sync was cancelled".

### 4. The newest database migration had no test — High

The database is at version 20. `ElmTrackrDatabaseMigrationTest` still had
`CURRENT_VERSION = 19` and its migration list stopped at `MIGRATION_18_19`, so 19→20 —
the step every install on the previous release takes — was not exercised anywhere, and
19 was not a tested start version. Nothing failed, because the suite is instrumented
and runs only in the emulator job.

**Fix.** The instrumented suite is brought to 20, with a data-preservation case for
19→20. `LatestMigrationJvmTest` now runs the newest migration on the JVM — a database
built from the previous exported schema, opened through Room with the migration
attached, which validates the result on open — and holds the instrumented suite's
version and migration list to the `@Database` version so it cannot drift again.

**The migration itself is sound.** The JVM test ran it and Room accepted the schema.
The instrumented changes compile but were **not run**: there is no emulator here.

### 5. A task's hourly rate typed with a decimal comma was saved ten times too high — High

The task editor kept digits and `.` only. A decimal comma — the separator key on
Hebrew, Russian and most European keyboards — was dropped, so "62,5" was saved as
625 and every shift booked to that task was priced at ten times its rate, with a
success message. Wave 2 fixed the same defect in onboarding; this field had its own
filter.

**Fix.** The field reads through the shared `DecimalInput`. Three other number fields
with their own filters were moved to it as well: the refund amount refused any input
containing a comma, and the project and settings fields passed Arabic-Indic digits
through unconverted, where they then failed to parse.

**Tests.** Two cases in `TaskEditorRenderTest`; on the old code the first failed with
`expected:<62.5> but was:<625.0>`.

### 6. Receipt previews were decoded at full resolution on the main thread — High

`ReceiptReviewDialog` and the claim form called `BitmapFactory.decodeFile` inside
`remember`: during composition, on the main thread, at the file's native resolution.
The full-screen preview decoded off-thread but unbounded, and its remote path used
`decodeStream`, which cannot subsample.

**Fix.** All four go through `ReceiptBitmapDecoder`, which already bounded OCR and the
PDF export. The inline previews load through `rememberReceiptImage`, which reports
"loading" separately from "no image" so the claim form does not flash its "no receipt
attached" placeholder while a receipt is being read.

**Not measured.** The effect is argued from what the code does, not timed on a
device.

### 7. The hours disappeared from the Dashboard and Reports at large font sizes — High

Found by the render pass; it was not in the plan.

Both screens lead with hours on one side and estimated gross on the other, as a `Row`
whose first block had `weight(1f)`. At larger font sizes the estimate's label took the
row. At 1.3× the hours lost their unit and the two labels touched. At Android's
largest setting the hours block was squeezed to one character: the figure vanished
and "Worked" ran down the card a letter per line, on top of the other label. On
Reports that pushed the breakdown most of a screen down.

The existing large-font tests assert `isDisplayed`, which a figure laid out one letter
per line still satisfies.

**Fix.** `AuroraFigurePair` decides before measuring: two blocks whose natural widths
fit share a row; otherwise each gets its own. The default-size layout is unchanged,
confirmed in renders.

**Tests.** The rule is tested with fixed-size blocks (`AuroraFigurePairRenderTest`).
Robolectric measures every glyph about a pixel wide, so a test built on the real
labels cannot tell a healthy layout from a broken one — a first attempt at one failed
on the fixed code for exactly that reason and was discarded. The screens themselves
were confirmed in fresh renders at 1.3× and 2×.

### 8. Small coloured text was drawn in colours that fail contrast — High

Three brand accents were used directly as text colours. Each is right as a graphic
and fails as text. Computed with the WCAG formula:

| Text | On | Ratio | Needs |
|---|---|---|---|
| `AuroraPeach` — overtime labels, Dashboard and Reports | white card | **2.01:1** | 4.5:1 |
| `AuroraIndigo` — money, counts, dashed buttons | dark surface | **3.04:1** | 4.5:1 |
| `#F43F5E` — month-over-month decrease | white card | **3.67:1** | 4.5:1 |
| `outline` — captions in the clock-face store | white card | **3.48:1** | 4.5:1 |

**Fix.** Those text sites use `auroraOvertimeInk()`, `colorScheme.primary`,
`colorScheme.error` and `onSurfaceVariant`. Every replacement measures 5.2:1 or
better. The accents are untouched wherever they colour a bar, dot, ring or fill, and
icon tints keep `outline`, where 3:1 is the correct bar.

In light theme `primary` *is* `AuroraIndigo`, so only dark theme changes at those
sites.

**A decision taken here that you may want to reverse.** `declined-findings.md`
records the `outline`-as-text question as a design call with three options. This
change takes its option 3 for nine caption sites in the clock-face store, moving them
to `onSurfaceVariant`. The palette is not changed and `outline` itself is not
changed. Two sites were left: the Dashboard's idle timer, which is display-size text
and passes the 3:1 large-text bar, and a placeholder value in `ProjectCommonUi`.

### 9. The screenshot suite could not catch anything — High

The committed goldens were recorded on a developer machine, so all of them fail on
any other, and `Verify Paparazzi goldens` has been `continue-on-error` since it was
added. They are also three months stale. On inspection:

- 13 screenshot tests had no committed golden, and 3 goldens have no test behind them
  (`03-onboarding-profile`, `05-onboarding-work-week`, `06-onboarding-features`).
- Two capture names were used twice (38 and 39), so one test's image overwrote
  another's.
- The "RTL" cases only flip the layout direction. No screenshot rendered a single
  translated string.
- `10-shifts-list` is a column assembled by hand in the test, not the Shifts screen.
- No coverage at all for Projects, Leave, Refunds, the Reports insight, refund and
  project tabs, or the recap and notice cards.

**Fix.**
- `.github/workflows/android-record-goldens.yml`, triggered by hand, records the
  goldens on the CI image and uploads them as an artifact for review and commit. It
  needs no write permission and commits nothing. This is the condition
  `declined-findings.md` names for making verification blocking.
- 25 new cases render the Dashboard, Reports, the shift form, Settings and onboarding
  in Hebrew, Arabic and Russian, in dark theme, and at 1.3× and 2×.
- The duplicated names are renumbered.

**Still to do, and it needs you:** run the workflow once, review and commit its
artifact, then remove `continue-on-error` from the verify step and pin that job to
the same runner image. **The workflow has not been run** — it can only be proven on
GitHub. No golden was re-recorded here, and the three orphaned images were left in
place; removing them is your decision.

### 10. A replayed watch punch-in could undo a deleted shift — Medium

The local unique index on `(userId, startTime)` covers tombstones, unlike the
server's partial one. The replay guard looked the start time up with
`deletedAt IS NULL`, so a deleted shift was invisible to it; the insert went ahead
and REPLACE removed the tombstone, leaving an open shift where the user had deleted
one and no pending delete to send. Rare — it needs a watch replay and a deletion in
between. Recorded as open in `wave2-ux-2026-10.md` §6.

**Fix.** The guard sees deleted rows. A replay that matches one is acknowledged and
nothing is re-created. Manual shift creation is deliberately unchanged: re-adding a
shift at a deleted shift's start time is a legitimate edit and resolves correctly on
push.

**Test.** `ShiftTombstoneReplayTest`, against a real Room database — the in-memory
fakes do not model the unique index.

---

## Found by the render pass, not fixed

All Medium unless stated. Each was seen in a fresh render, not inferred from code.

- **Shift form dates wrap onto two lines** ("8 Apr / 2025") in every language. The
  half-width field with a calendar icon cannot hold the pattern. *Solution:* drop the
  year when it is the current one, or let the field's text shrink to fit.
- **The shift form's Save button floats over the content with no backing.** Text is
  visible above and below it as the form scrolls. *Solution:* a surface-coloured
  gradient behind the sticky bar.
- **Arabic mixes two numeral systems on one card.** Hours and money render in
  Arabic-Indic digits ("٩٣٫٤"); percentages, counts, dates and times stay Latin
  ("32%", "12", "15:30"). `MoneyFormatter`'s own KDoc names this as the thing to
  avoid. *Solution:* route percentages, counts and the date and time formatters
  through `FormattingPolicy`, as hours and money already are. Needs a native reader's
  view on which system Arabic-speaking users expect — that is a product decision.
- **Currency names are cut mid-word in onboarding** in Russian ("Швейцарски",
  "Новый"). `maxLines = 1` with no ellipsis. *Solution:* allow two lines, or
  ellipsise.
- **Low: the task chips on the shift form** wrap with a noticeably larger gap between
  rows than between chips.

Not reviewed visually, because no stateless composable was readily renderable:
Projects, Leave, Refunds, the Shifts screen proper, and every tablet branch.

---

## Reported, not fixed

### Correctness

| Urgency | Finding | Recommended solution |
|---|---|---|
| Medium | Five ViewModels surface raw exception text — `e.message ?: "Unknown error"` in `DashboardViewModel`, `ReportsViewModel`, `SettingsViewModel`, `ShiftsViewModel`, `ProjectsViewModel`. SQLite and repository messages reach Hebrew and Arabic readers untranslated. | `UserFacingError.message(e, R.string.error_generic)`, which exists for this. |
| Medium | Replay onboarding seeds Israel when settings carry no region (`OnboardingScreen.kt`, `regionCode = settings.regionCode ?: regionCode`); confirming replaces a non-Israeli profile's rules. | Seed from the default compensation profile's region. |
| Medium | Work-timezone bypasses: reminder notification text (`ActiveShiftNotificationManager`), the absence form's "today" (`AbsenceFormViewModel`), task slot labels (`TaskSelectorBar`). | Pass `WorkTimezone.zoneFor(settings)`. |
| Medium | Unsaved input lost on rotation or process death: the task editor, billing and payment drafts (`ProjectsScreen`), and the compensation form's rules and sick-leave state. | `rememberSaveable` with savers, or drafts in a `SavedStateHandle`. |
| Medium | `supabase/schema.sql` is headed as the full idempotent schema and creates 7 of the 15 tables. A fresh project set up from it alone is incomplete. | Correct the header to name `supabase/migrations/` as authoritative. |
| Medium | Sync-pull indexes are `(updated_at, id)` with no `user_id`, while every pull is per-user under RLS. Cost should grow with all users' rows rather than one user's. **Not measured.** | A migration adding `(user_id, updated_at, id)` per synced table. Confirm with `EXPLAIN` on a copy of production before dropping the old ones. |
| Low | Backup import reads the whole file before checking its size, and accepts a backup from a newer app version, silently dropping fields it does not know. | Bounded read; refuse a format version above the supported one, with a message. |
| Low | The onboarding review step still lists "Name" and "Optional features", which the wizard no longer asks. Sign-out leaves sync cursors behind. A change to exact-alarm permission mid-shift is not observed. `ShiftsListUi` carries a dead `showRefunds` parameter. | One-line fixes each. |

### Optimisation

None of these was measured. They are ranked on what the code does.

| Urgency | Finding | Recommended solution |
|---|---|---|
| Medium | The Shifts summary card runs `buildMonthlyReport` and `sumMonthlyPay` inside `remember`, during composition (`ShiftsHeroSummaryCard`). The August audit moved the rest of that screen's payroll off the main thread; this was missed. | Compute it in `buildShiftsPayFacts`, already on the computation dispatcher. |
| Medium | Reports composes every shift, week, task and leave row eagerly inside `verticalScroll`. Each shift row runs payroll in `remember` on the main thread, with an unremembered sort and per-row `DateTimeFormatter`s. | Precompute row models in `ReportsViewModel` (the `ShiftsPayFacts` pattern), then move to the lazy `AuroraListScreen`. Two steps. |
| Medium | 51 `collectAsState` sites and no `collectAsStateWithLifecycle`. Flows and their payroll transforms keep running while the app is in the background. | Mechanical swap; the dependency is already present. |
| Medium | The Dashboard's one-second ticker is read at the top of `DashboardClockSection`, recomposing the section and three money formats each second. `MoneyFormat` builds a new `NumberFormat` per call. | Read elapsed time in the leaf composables; cache formatters per locale and currency. |
| Medium | Main-thread transforms: `ProjectsViewModel` has no `flowOn` and reads the whole shifts table; `ReportsViewModel`'s project and leave bundles, and `DashboardViewModel.projectDashboardSummary`, run after their `flowOn`. | `flowOn(computationDispatcher)` and a project-time-only DAO query. |
| Medium | Sync pull writes row by row with no transaction — one table invalidation per row, each waking the observers above. `pendingSnapshot` loads full rows to count them. | Wrap each page in `TransactionRunner.inTransaction`, after confirming `applyRow` makes no network call. `COUNT` queries. |
| Low | No baseline profile. Sentry initialisation and three WorkManager enqueues run on the main thread at start. Sync pushes one row per request. `app_logo.png` is 327 KB. Gradle's build cache is off in CI. The Israeli engine's per-shift path still re-derives the week. | A baseline profile needs a device to generate. The rest are small. |

### Visual and design system

| Urgency | Finding | Recommended solution |
|---|---|---|
| Medium | The billing and payment forms have no `imePadding` (`ProjectBillingUi`); the keyboard can cover the focused field. **Not seen** — Paparazzi cannot show a keyboard. | Host them in `AuroraScreen`, or add `imePadding`. |
| Medium | `maxLines = 1` with no ellipsis on user-entered names and on money totals (`ProjectCard`, `ReportsScreen`, `DashboardScreen`, `ShiftRecapCard`), and a fixed 48 dp date block holding a translated badge (`ShiftsListUi`). | Ellipsis on names; let totals shrink or wrap; a minimum width in place of a fixed one. |
| Medium | Screen-reader gaps: `auroraRowClickable` has no role and no press indication (13 call sites); 13 more `clickable`s have no role; Reports' `SectionLabel` and the five tab titles are not headings; the archived-tasks toggle reports no state. | Fix in the shared helpers, `AuroraInteraction.kt` and `AuroraSemantics.kt`. |
| Medium | Reduce-motion is bypassed where the per-file budget check cannot see it: `ProjectCard`, `SettingsListUi`, `OnboardingScreen`, `ReportsScreen`. | Gate with `auroraMotionEnabled()`, and tighten the budget test to count call sites rather than files. |
| Medium | Design-system drift, improved and still open. Raw `dp` 809 (1 126 in August); raw cards 33 against 18 `ElmCard`; 26 `FilterChip`; 32 raw text fields; two empty-state families; four skeletons; four section-header and four screen-title conventions. | Continue the ratchet one directory per commit. Worth doing once goldens are trustworthy, not before. |
| Low | One unmirrored chevron (`TaskManagementScreen`). Concatenated month and year strings. `count == 1` string pairs where `<plurals>` belongs — the Russian translation works around it ("смен: 12") and reads correctly. `Locale.US` in `ReminderRuleWorker`. | Individually small. |

Tablet branches, landscape, predictive back and the splash screen remain as recorded
in `declined-findings.md`. The reasons given there still hold.

---

## Decisions that are yours

1. **Run the golden-recording workflow** and commit what it produces. Until then the
   screenshot suite still proves nothing.
2. **The clock-face captions** now use `onSurfaceVariant` (fix 8). Keep, or revert in
   favour of a darker faint-text token.
3. **Arabic numerals** — one system throughout, and which.
4. **The three orphaned goldens** — delete or keep.
5. **The Supabase index migration** — whether to apply it. It needs an `EXPLAIN`
   against real data first, which I cannot run.

---

## Corrections to earlier documents

- `declined-findings.md`, "`PayWeekMinutes.forEachWithPriorWeekMinutes` grouping by
  ISO Monday": the helper no longer exists. `sumMonthlyPay` sorts once and no longer
  groups by week. The entry is obsolete.
- The same document cites 29 sites using `outline` as a text colour, and
  `SecondaryInkContrastTest`'s comment says 33. On this branch there are 34
  references, of which about 21 are icon tints (correctly held to 3:1) and 11 were
  text. `auroraFaintText()` has no callers.
- `ux-ui-review-2026-08.md` reports no `SnackbarHost` on Dashboard, Reports or
  Projects. `MainScaffold` now hosts one for the whole app; only Reports uses it.
- `code-review-2026-07.md` lists the English biometric prompt as open. The prompt's
  title and subtitle now come from resources.

---

## Checked and found sound

Recorded so the next pass does not re-derive it.

- **String parity.** 1 972 default strings; Hebrew, Arabic and Russian each have none
  missing, none extra, and every plural carries all its CLDR forms.
- **Supabase.** All 15 tables enable row-level security. All 10 `security definer`
  functions pin `search_path`.
- **Persisted enums.** No `valueOf` or `enumValueOf` on synced data anywhere; the
  `fromPersisted` rule holds.
- **No `runBlocking`, `GlobalScope` or `Thread.sleep`** in main source. Six `!!`
  assertions, each guarded on the line above.
- **Room.** No destructive-migration fallback. Migrations 1→20 are complete,
  including 8→9 and 9→10 across the missing `9.json`.
- **Sync conflict rule.** Remote `updated_at` against local `lastSyncedAt` — server
  time against server time, never the device clock.
- **Billing.** Pending purchases grant nothing; acknowledgement is retried on every
  refresh; restore replaces the cached ownership from Play.
- **Logging.** 47 `Log` calls, none carrying an email, a token or a pay amount.
- **Release shrinking.** R8 full mode and resource shrinking are on, so
  `material-icons-extended` is stripped in release builds.

---

## Verification

Run on the final commit, JDK 21, Android SDK platform 36 / build-tools 36.0.0, Linux:

```
./gradlew testDebugUnitTest :wear-sync:test :app:lintDebug :app:assembleDebug
```

`BUILD SUCCESSFUL`, on the code at `597a5b8` (the two commits after it change
documentation only).

| Suite | Baseline (`Main` `e3acc16`) | This branch |
|---|---|---|
| `:app:testDebugUnitTest` | 2 254 tests, 0 failures | **2 308 tests, 0 failures, 0 skipped** (+54) |
| `:wear-sync:test` | 51 tests, 0 failures | 51 tests, 0 failures |
| `:wear:testDebugUnitTest` | 36 tests, 3 failures¹ | 36 tests, 0 failures |
| `:app:lintDebug` | not run | 0 errors, 385 warnings, 7 hints |
| `:app:assembleDebug` | — | built |

¹ Environmental, not a defect: Robolectric fetches its Android runtime from Maven
Central at test time, and that host rate-limited this machine (HTTP 429). With the
artifact placed in the local Maven repository the three pass. `wave2-ux-2026-10.md`
records the same thing. Nothing in the repository was changed for it.

**Lint.** No baseline was taken, so the warning total cannot be compared directly;
the August audit recorded 371 warnings on a smaller codebase. What was checked
instead: of the 27 main-source files this branch touches, lint reports one finding,
at `ReportsScreen.kt:1328` — a scroll value read during composition, on a line last
changed on 4 August and not part of this work. It is the carousel item in the
optimisation table above.

The 54 new tests: the receipt link (4), the deleted-shift replay (2), the auth
callback (4, replacing 1), sync cancellation (1), the migration tripwire (3), the
task rate (2), receipt down-sampling (5), the figure-pair layout (7), contrast (2),
and the 25 new screenshot cases, which run as tests under `testDebugUnitTest`.

Each data-safety test was run against the unfixed code first and failed: fixes 1, 3,
5 and 10. For fix 7 the before-and-after is in renders, for the reason given there.

**Not run, and stated as such:**

- **Instrumented tests** — the updated migration suite and the DAO tests. No emulator
  here; CI's emulator job covers them.
- **`verifyPaparazziDebug`** — all goldens fail on any host but the one that recorded
  them. See fix 9.
- **The golden-recording workflow** — it can only run on GitHub.
- **Release build** — needs a signing keystore this environment does not have.
- **Anything on a device**: the auth callback with a real link, receipt decoding
  under memory pressure, the keyboard over the billing form.
- **Any performance number.** No measurement was taken in this pass. Nothing in the
  optimisation section may be quoted as one.

The renders used for the visual review were produced with
`./gradlew :app:recordPaparazziDebug` and removed from the working tree afterwards;
they are not committed.
