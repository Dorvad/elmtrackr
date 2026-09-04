# Declined audit findings

Items raised by one of the four audits and **deliberately not done**, with the
reason. Recorded so they are not re-raised as oversights, and so the reasoning can
be challenged rather than guessed at.

A declined item is not a permanent no. Each entry says what would change the answer.

---

## Golden image testing

**Raised as:** re-record the 31 Paparazzi goldens and make `verifyPaparazziDebug`
blocking; add goldens for the Projects screens, the Reports refunds/projects tabs,
the insight carousel, and dark variants.

**Declined because the suite currently proves nothing.** All 31 goldens fail on a
machine other than the one that recorded them, against an *unmodified* checkout —
verified with a `git worktree` on a pre-change commit. Paparazzi renders through
layoutlib and text rasterisation varies with the host JDK and font stack; CI pins
JDK 17 while a current developer image is on 21. Re-recording locally only moves the
failure to everyone else, and adding more images to a suite that cannot distinguish
a real change from a host difference is negative value.

**What would change it:** record the images inside the CI container, so the same
environment that checks them produces them. Then the blocking step is worth having
and new coverage is worth adding.

**In the meantime** the visual work in this effort was verified through Robolectric
render tests, semantics assertions and computed contrast ratios — which caught real
defects the goldens would not have (a mispositioned link, four unreadable status
pills), because a golden only tells you a pixel changed, not whether it was wrong.

---

## Landscape and foldable layouts

**Raised as:** there is no `WindowSizeClass`, no fold awareness and no orientation
branch; a phone in landscape stays a 448 dp centred column with wide margins.

**Declined as not worth the work now.** It is accurate, and it is real work across
every screen. But nothing is broken — landscape is usable, just not optimised — and
a time tracker is used in portrait, held in one hand, for a few seconds at a time.

**What would change it:** tablet usage showing up in analytics, or a foldable
becoming a target device.

---

## Resolving the "Main job" default profile name from resources

**Declined because the fix creates a worse bug.** The name is persisted. Resolving
it from resources means a profile created while the app is in Hebrew keeps a Hebrew
name permanently, including for a user who then switches to English — and the app
cannot tell that name apart from one the user typed.

**What would change it:** storing a "this is the default name" marker separately
from the name itself, so display can be localised while the stored value stays
stable. That is a data-model change, not a string change.

---

## detekt / ktlint

**Declined as cost without coverage.** `DesignSystemBudgetTest` already ratchets
five drift metrics and has blocked real regressions during this effort — it caught a
raw `18.dp` that would otherwise have shipped. A second linter would need
configuring, tuning and silencing before it caught anything the budget test does
not.

**What would change it:** wanting rules the budget test cannot express — formatting,
complexity limits — and the appetite to tune them. If adopted it should *replace*
the budget test, not sit beside it.

---

## Rounding wage money to the minor unit before summing

**Raised as:** per-bracket amounts are rounded independently, so displayed brackets
can sum one minor unit away from the displayed total.

**Declined because the codebase already made this decision explicitly.**
`NoBinaryFloatingPointTest` accepts double-based arithmetic for wages, and the
boundary is documented there. Nobody has reported a one-agora discrepancy, and the
change touches every money path in the pay engine.

**What would change it:** bracket-total agreement mattering on the exported PDF —
i.e. someone reconciling a payslip line by line and finding it off by one unit.

---

## Payments against a cancelled billing record

**Raised as:** `ProjectPeriodTotals` filters to active records, so a payment whose
record was later cancelled vanishes from `received`.

**Declined because the route into that state is blocked.**
`ProjectBillingCorrection.canCancel` refuses to cancel a record that has payments
against it. This is defence-in-depth for a sync race that cannot currently occur.

**What would change it:** multi-record billing UI, or a sync path that can cancel a
record on one device while a payment lands on another. At that point assert the
invariant rather than filter it away.

---

## Folding Settings → Pay into the Compensation rules screen

**Raised as:** Settings → Pay edits only the default profile, and two undisclosed
editors exist for the same hourly rate.

**Declined as an architecture change dressed as a bug fix.** The disclosure the
audit actually asked for shipped — `settings_overtime_thresholds_hint` says the
thresholds apply to the default job. Merging the two editors is a product decision
about which screen owns pay setup.

---

## A per-shift compensation profile picker at clock-in

**Declined because it is a feature request, not a defect.** Clock-in books to the
default profile; the per-shift picker exists in the edit form. A user with two jobs
can correct a punch afterwards. Belongs on the product list.

---

## `PayWeekMinutes.forEachWithPriorWeekMinutes` grouping by ISO Monday

**Fix declined; the rename is not.** The helper groups by ISO Monday while the
engines anchor weeks on `rules.weekStartDay`. Currently harmless because
`sumMonthlyPay` discards the accumulator — but the name promises a boundary it does
not honour, which is a trap for the next person to wire it in. Rename rather than
re-implement.

---

## The IL `breakRatio` classification edge case

**Split, and half declined.** `payableNetMinutes` can exceed gross under round-up or
a minimum-shift top-up, and `breakRatio = net / grossMinutes` then maps topped-up
minutes into a compressed slice of wall clock, so their rest/night classification
uses the wrong window. Narrow: it needs rounding *and* a top-up to bite.

The performance half of the same finding was **not** declined. Half of it is now closed:
the per-payable-minute `Instant.atZone` is gone (`domain/time/ZoneMinutes`), and
`flowOn(computationDispatcher)` reached the dashboard, which never had it. The
`weekStateBeforeShift` repetition — the O(shifts²) part — is still open, and
`optimization-debug-audit-2026-08.md` says why it was left: memoising a classification in
a payroll engine needs a complete cache key, and the Israeli engine has no dedicated test
file to catch an incomplete one. Characterisation suite first, memo second.

---

## Foreground service for a running shift

**Not declined — blocked on verification.** See `README.md` and
`code-review-2026-07.md`. From Android 12 the background-start restrictions apply to
exactly the punch paths that need it most (widget, Wear, shortcut), so which
exemption covers a widget tap must be confirmed against current platform docs and on
hardware. Shipping it unverified risks turning a widget punch into a
`ForegroundServiceStartNotAllowedException`, which is worse than the gap it closes.

## HTTPS App Links for the auth callback

**Not declined — blocked on infrastructure.** Needs a hosted
`.well-known/assetlinks.json` carrying the release signing fingerprint, and the
Supabase redirect URLs updated. Flipping `autoVerify` without the hosted file breaks
sign-in outright. The client-side half — PKCE, so an intercepted callback carries a
useless one-time code instead of live tokens — has shipped.

Narrower than it was. Google sign-in goes through Credential Manager, which returns
an ID token in-process and never touches a redirect URL, so the custom scheme is now
only on the paths that genuinely need a link: email confirmation, password recovery,
and the Google browser fallback for devices with no credential provider.

---

## Wave D decisions, September 2026

Three engine edge cases the audit flagged as "intent undocumented" were resolved by
choosing a rule and pinning it, not by leaving them open. Recorded here with the
uncertainty that remains, so the next reader inherits the reasoning rather than the
question.

### Night classification is measured before the break, not after

`countNightMinutes` used to scale wall-clock night minutes by the break ratio *before*
testing them against the two-hour threshold, so recording a 30-minute break could drop a
16:00–00:05 shift from 125 night minutes to 117, move its daily standard from 420 back to
516, and quietly remove 35 overtime minutes. A break the user took made the shift longer
in pay terms.

**Decided:** the threshold test uses raw wall-clock minutes inside the night window; the
break-scaled figure is kept only for the *premium fraction*, which is genuinely
proportional. The Israeli Hours of Work and Rest Law describes a night workday by when
the work falls, and the break's position within the shift is not recorded, so scaling it
uniformly before a wall-clock test was arbitrary in both directions.

**Residual uncertainty:** a long unpaid break sitting entirely inside the night window
now counts toward the threshold. Recording break start and end would settle it properly;
until then this direction is the one that does not penalise taking a break.

### The daily standard is a property of the shift, resolved once

The Israeli engine re-decided the standard per minute, so a Thursday 20:00 → Friday 10:00
shift dropped from 516 to 420 at midnight, mid-shift. **Decided:** resolve once, from the
shift's first payable minute. Masked on the shipped IL preset — any midnight-crossing
shift starting before 22:00 already clears the night threshold — so it bites only when
`nightEnabled` is off or the window has been edited.

### The overtime ladder's gap pays the first tier's rate

When a hand-built ladder's first tier starts later than the daily standard, the minutes
between belong to no tier. **Decided:** they take the lowest tier's rate, not 1.0, and
the same holds for the weekly ladder. `OvertimeLadderGapTest` pins it and
`PayrollCalculator.dailyMultiplier` carries the reasoning: the standard is what
`ShiftClassifier` and `MonthlyReportBuilder` use to call a minute overtime, so paying 1.0
there would report an hour as overtime and pay it at straight time — the hours-versus-money
divergence Wave B existed to remove. No shipped preset opens the gap.

---

## Corrections to the September 2026 audit

Two findings in that audit did not survive first-hand re-reading. Recorded so they are
not "fixed" later by someone working from the list rather than the code.

- **"`TessBaseAPI` is re-initialised per recognition" is wrong.**
  `TesseractHebrewTextRecognizer` is a `@Singleton` that caches the engine in a field,
  guards it with a mutex, and calls `clear()` (not `recycle()`) after each image. The
  engine is initialised once per process. Nothing to do.
- **"`premium_profiles` has one `FOR ALL` policy where every other table has four" is
  cosmetic, not a gap.** The policy carries *both* `using (auth.uid() = user_id)` and
  `with check (auth.uid() = user_id)`
  (`20250703120000_premium_profiles.sql:37-41`), which is what makes a `FOR ALL` policy
  equivalent to four per-command ones: `using` gates read, update and delete, `with
  check` gates the rows insert and update may write. A `FOR ALL` policy *without* `with
  check` would be a real hole — a client could insert a row carrying someone else's
  `user_id`. This one is not that. Splitting it into four would change nothing but the
  file.
- **"`user_settings` marks synced unconditionally" was wrong** and was corrected in the
  Wave C notes: `markUserSettingsSynced` does call `markSyncedIfUnchanged`. The real gap
  was the *other* guard — the server-side `client_updated_at` filter — which Wave C added.

## security-crypto: moved to stable, with one check still owed

`androidx.security:security-crypto` was pinned to `1.1.0-alpha06` — an alpha, in
production, holding the SQLCipher passphrase. Wave D moved it to the stable `1.1.0`.
Verified against Google Maven before making the change, not assumed:

- `1.1.0` is published and is the newest release (`maven-metadata.xml`).
- The overload this app calls — `EncryptedSharedPreferences.create(Context, String,
  MasterKey, PrefKeyEncryptionScheme, PrefValueEncryptionScheme)` — exists in `1.1.0`
  with the same signature and is **not** deprecated. The deprecated one is the older
  `create(String fileName, String masterKeyAlias, Context, …)`, which this app does not
  use.
- Both versions depend on `com.google.crypto.tink:tink-android:1.8.0`, so the on-disk
  keyset is written and read by the same crypto library across the change.

**Still owed, and it cannot be done off a device:** install a build on `1.1.0-alpha06`,
record a shift so the database is written, then upgrade in place to the `1.1.0` build and
confirm the app opens the existing database. That is the one failure mode the version
comparison above cannot rule out, and it is unrecoverable if wrong — which is why
`DatabasePassphraseStore` now throws `DatabasePassphraseUnreadableException` rather than
minting a replacement key over an existing database.

## Capping ComputationDispatcher's parallelism — considered, rejected

The audit listed "`ComputationDispatcher` is bare `Dispatchers.Default`" as hygiene.
It was implemented as `Dispatchers.Default.limitedParallelism(2)` and then reverted
before committing, because the reasoning does not hold up: `Dispatchers.Default` is
already bounded to the core count, and what runs on it here is three short transforms
(Dashboard, Shifts, Reports), not a fan-out. A cap of two can make the third screen wait
on the other two — real queueing, added to fix contention nobody has measured.

The `@Singleton` on the provider was kept. It is a no-op for `Dispatchers.Default` and
the thing that would matter if a cap is ever introduced: a per-injection
`limitedParallelism` view is a separate limit, so handing each call site its own would
silently undo the cap it was added for.

Revisit with a trace, not a hunch.

## R8 keep narrowing — deliberately not done in Wave D

The three whole-package keeps (`widget.**`, `notification.**`, `domain.model.**`) are
wider than the philosophy at the top of `proguard-rules.pro` allows, and narrowing them
is worth doing. It is **not** worth doing without running the resulting release build on
hardware: what those packages hold is Glance widget receivers, notification actions and
Room/serialization model classes — all resolved by name at runtime, all invisible to a
unit test, and all silent when they break. A widget that stops updating after an R8 pass
looks exactly like a widget that is working until someone opens their home screen.

Narrow it in a change of its own, with a release build installed and each surface
exercised: widget refresh, a notification action, a clock-out from the watch, and a sync
round trip. Do not fold it into an unrelated wave.

---

## Wave E scope: clock-style durations stay ASCII

Wave E moved every *decimal* number a user reads onto CLDR — money and hours. It
deliberately did not touch the `H:MM` timers, which are still `String.format(Locale.US,
"%d:%02d", …)`:

- `widget/WidgetShiftState.kt:49,54`
- `wear-sync/…/WearDisplayMath.kt:15,17,25,32`
- `ui/settings/ReminderRulesEditor.kt:527` (a time of day, `%02d:%02d`)

These are a different formatting category from `8.5` hours: a running clock, drawn in a
digital-readout style, where the convention is ASCII digits and a colon in every language
— the same reason a stopwatch face is not localised. An Arabic reader may still prefer
Arabic-Indic digits there; that is a legitimate open question and a product decision, not
a defect, and it is the kind of change that wants a screenshot review rather than a
grep-and-replace.

It also has a testing cost the money path did not: the widget and tile have
colour-parity tests but nothing asserting their digits, and the Wear timer string feeds
`WearDisplayMath`, whose tests assert exact literals. Changing it means writing those
tests first.

`domain/receipt/ReceiptParser.kt`'s `lowercase(Locale.US)` / `uppercase(Locale.US)` calls
are correct as they stand — that is locale-invariant case folding for keyword matching,
not display, and localising it would break receipt parsing on a Turkish device (the
dotless-i problem).
