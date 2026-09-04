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

**Correction to the above, found during Wave F.** The claim that the API is "not
deprecated" was wrong, and wrong because of how it was checked: `javap` prints
class-level attributes *after* the member list, and the check only read the header. The
Kotlin compiler surfaced it as soon as warnings were read rather than only errors.

What is actually true, verified by comparing both artifacts:

- `EncryptedSharedPreferences` and `MasterKey` each carry a **class-level** `@Deprecated`
  in `1.1.0`. Neither does in `1.1.0-alpha06`. So stabilising and deprecating happened in
  the same release, and the bump introduced 11 deprecation warnings.
- The `create(Context, String, MasterKey, …)` overload the app calls is still not
  *individually* deprecated — that part of the earlier check holds. The deprecated
  overload is the older `create(String, String, Context, …)`, which this app does not use.

The bump stands anyway, and the reasoning is in `DatabasePassphraseStore`'s KDoc: a
deprecated stable release beats an alpha for the component holding the database key, and
moving off the library is a keyset migration with a one-way failure mode, not a dependency
swap. The warnings are suppressed at the class with that explanation attached, rather than
left to be silenced later by someone without the context.

**Still owed, and it cannot be done off a device:** install a build on `1.1.0-alpha06`,
record a shift so the database is written, then upgrade in place to the `1.1.0` build and
confirm the app opens the existing database. That is the one failure mode the version
comparison cannot rule out, and it is unrecoverable if wrong — which is why
`DatabasePassphraseStore` now throws `DatabasePassphraseUnreadableException` rather than
minting a replacement key over an existing database. The replacement API to migrate *to*
needs checking against current AndroidX docs; the artifact names none.

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

---

## Wave F: M3 Expressive is not on stable material3 yet

Decision 3 was recorded as "bump the Compose BOM for M3 Expressive — verified target
`composeBom = 2026.08.00`, which pins **material3 1.4.0**, the stable release carrying M3
Expressive." Two parts of that turned out to be wrong, and both were caught only by
building against it.

### The target BOM does not build on this project

`compose-bom 2026.08.00` pins Compose **1.12.0**, whose AAR metadata declares
`minCompileSdk=37` and `minAndroidGradlePluginVersion=9.1.0`. This project is on
`compileSdk 36` and AGP `9.0.0`, so `checkDebugAarMetadata` fails with 22 issues before
anything compiles. The earlier verification checked that the BOM existed and which
material3 it pinned — it never checked the AAR metadata, which is where that requirement
lives.

Compose 1.12.0 is the version that raised the bar. Everything through **1.11.4** declares
`minCompileSdk=35` / AGP `8.6.0`:

| Compose | minCompileSdk | min AGP |
|---|---|---|
| 1.10.2 – 1.11.4 | 35 | 8.6.0 |
| 1.12.0 | **37** | **9.1.0** |

Every BOM from `2026.01.01` to `2026.06.01` pins material3 **1.4.0** — the same stable
material3 the decision was about — with Compose 1.10.x/1.11.x. **`2026.06.01` is
therefore the correct target**: identical material3, newest Compose that does not force an
AGP and compileSdk migration. That is what shipped.

Moving to 1.12.0 later means AGP 9.0.0 → 9.1.0+ (lint reports 9.4.0 as available) and
compileSdk 36 → 37, which also needs SDK platform 37 in CI. Worth doing as its own change,
with the Robolectric cap re-checked at the same time.

### material3 1.4.0 carries the expressive *theme*, not the expressive *components*

Verified by inspecting the 1.4.0 artifact:

- `LoadingIndicator`, `ButtonGroup` and shape morphing **do not exist**. Only internal
  token tables ship (`tokens/LoadingIndicatorTokens`, `tokens/ButtonGroupSmallTokens`),
  which is AndroidX landing the token data ahead of the components.
- `MotionScheme` and the `MaterialTheme(colorScheme, motionScheme, …)` overload exist but
  are **`internal`**. `javap` shows them as JVM-public because Kotlin `internal` is
  recorded in `@Metadata`, which `javap` does not decode; the Kotlin compiler rejects
  them outright.

Both are public in the **1.5.0-alpha** line (1.5.0-alpha27 at the time of writing).
material3 1.4.0 is the newest stable.

So Wave F steps 2 and 3 are not blocked on effort — they are blocked on there being no
stable release to do them against, which is precisely the condition decision 3 was trying
to satisfy. Adopting an alpha material3 for visual polish, on an app whose last Wear
submission was already rejected once, is not a trade worth making. Revisit when
material3 1.5.0 is stable.

### When it is revisited, the dependency runs Aurora → Material

Written down because the analysis is easy to get backwards and was implemented backwards
once before being reverted.

Do **not** take Material's motion as the source for `AuroraMotion`. Material's default
scheme is spring-based; Aurora's identity is three named cubic-bezier curves and fixed
durations, and `AuroraEaseOut` (`0.16, 1, 0.3, 1`) is a deliberate signature. Replacing it
with springs re-times every animation in the app — a visual rewrite dressed as a
dependency upgrade, and the opposite of "Aurora keeps its identity".

Instead, implement `MotionScheme` **from** Aurora's curves and hand it to `MaterialTheme`,
so Material's components animate in Aurora's language. Nothing is lost: `tween` is a
`FiniteAnimationSpec`, so a duration plus an easing expresses everything the interface
asks for. Map spatial specs (position, size) to `AuroraEaseOut` and effects specs (alpha,
colour) to `AuroraSoftEase` — a line Aurora already draws.

**The prize is the reduce-motion gate.** Today `auroraMotionEnabled()` covers Aurora's own
modifiers and nothing else, so a user who asks the system for less motion still gets every
Material component's internal animation at full length. A second scheme returning `snap()`
for all six specs, selected by the same gate, extends the preference to every Material
component at once — no per-component work and no way to forget one. That is a real
accessibility gap, and it stays open until `MotionScheme` is public.

---

## Wave G: U8 (touch targets) is not a runtime defect

The audit reported `ElmGradientButton(compact = true)` as "36dp minimum at 8 sites, below
the documented 48dp floor and outside `TouchTargetRenderTest`". The first two clauses are
true of the source and the third was the real gap; the conclusion was not.

`heightIn(min = 36.dp)` is a floor on the *declared* minimum, and the composable it is
applied to is a Material 3 `Button`, which enforces the 48dp minimum interactive size
itself. A test written to fail — `assertHeightIsAtLeast(48.dp)` on a compact button —
passes as-is. So there was nothing to fix, only something to pin, and the test now does
that: if someone ever disables the enforcement, or replaces the `Button` with a bare
`Box`, it fails.

Worth recording because the finding reads like a defect and the fix ("grow the target
with `sizeIn`") would have made things worse: `sizeIn(minHeight = 48.dp)` grows the
*painted* gradient too, since the background brush is applied in the same modifier chain.
The button would have gone from correct-looking-and-correct to visibly taller.

## Wave G: the widget's remaining colour drift is enumerated, not fixed

`WidgetColorParityTest` now exists (modelled on the watch's, which is why the watch has
not drifted). It caught the reported `#22D3EE` — the brand tertiary at the wrong value in
fourteen places across the ring arcs, the progress fill and the active status dot, where
the phone and the watch both use `#16C8D6`. That is fixed.

It also caught six values that are **not** in the Aurora palette and are not obviously
mistakes:

| Value | Nearest token |
|---|---|
| `#7C6BFF` | a lighter indigo; `AuroraIndigo` is `5B4DF2` |
| `#5C4EE5` | a hair off `AuroraIndigo` |
| `#3D2CC0` | a hair off `AuroraIndigoDeep` (`4133C8`) |
| `#5CF0A0` | a success green; the watch uses `34D399` |
| `#181A38` | a hair off `AuroraNavy` (`181530`) |
| `#171D33` | a hair off `AuroraDarkSurface` (`151D2E`) |

Each sits a few points from a real token, which means someone either hand-tuned a gradient
stop for the widget's own rendering — Glance cannot use a Compose `Brush`, so the widget
builds gradients as drawables — or copied a value that has since moved. Mapping them onto
the nearest token changes what the widget looks like; adding them to `Color.kt` invents
brand colours. Both are design decisions, not something the test that found them should
guess at.

They are listed in the test's `KNOWN_DRIFT` set, so the drift is bounded and anything
*new* still fails. Resolve each with a design call and delete the entry.

---

## Wave G: the light theme's `outline` fails AA as text (measured)

`SecondaryInkContrastTest` was added for the three cases the existing contrast suite did
not measure — disabled labels, supporting text, and `outline` used as a text colour. Two
of the three are fine. The third is a real accessibility defect, and here are the numbers:

| Pair | Ratio | Verdict |
|---|---|---|
| light `outline` (`AuroraFaint` `#8A84B4`) on `#FFFFFF` | **3.48:1** | border ✓ (needs 3:1), **body text ✗** (needs 4.5:1) |
| dark `outline` (`AuroraDarkOutline` `#A4B0C3`) on `#151D2E` | 7.68:1 | ✓ both |
| light `onSurfaceVariant` on `#FFFFFF` | 6.17:1 | ✓ |
| dark `onSurfaceVariant` on `#151D2E` | 10.95:1 | ✓ |

`outline` is a *border* role, and 3.48:1 is correct for one (WCAG 1.4.11 asks 3:1 for a
non-text boundary). It is also used to colour text or an icon tint at **29 sites**, plus
the `auroraFaintText()` helper whose entire purpose is faint text. In light mode those
text sites render body copy at 3.48:1, which fails WCAG 1.4.3 AA.

Not fixed here, because every available fix is a design decision:

1. **Darken `AuroraFaint`.** The honest reading — a palette whose faint-text token cannot
   carry body text needs a darker token. But it is a brand colour, and picking a
   replacement means choosing a value that clears 4.5:1 while still reading as a tier
   below `AuroraInk2` (`#615C8A`, 6.17:1). That is a call for whoever owns the palette.
2. **Point `auroraFaintText()` at `onSurfaceVariant`.** One line, and it collapses a
   deliberate three-tier text hierarchy into two — faint would become identical to
   secondary.
3. **Reclassify the 29 sites.** Some are icon tints, where 3:1 is the right bar and
   nothing is wrong. Separating them needs reading each one, and the ones that are text
   still need a colour from (1) or (2).

The test asserts 3:1 in both themes, so a value that is unreadable at *any* size would
fail, and its KDoc explains why it stops short of 4.5:1: asserting the bar the code
cannot meet would just leave a red build with no decision attached.

## Wave G: four items deferred, with reasons

**U4/U5 — `AuroraScreenHeader` on the four primary tabs, and Reports/Onboarding/Settings
onto `ElmCard`.** Blocked on a design-system gap, not effort: `AuroraScreenHeader`
requires `onBack` and `backContentDescription`, and the four primary tabs have no back
affordance. Adopting it there means first designing a no-back variant, which is a change
to the component rather than adoption of it. The plan itself asks for "one screen per
commit with a before/after review", and a review is exactly what is not available in this
environment. Four title scales for one role is real drift and worth closing — with eyes on
each screen.

**U7 — retire `EmptyState` in favour of `ElmEmptyState`, and one `ElmSkeleton` for four
bespoke loaders.** Adding an action slot to `ElmEmptyState` is additive and safe; deleting
the other family and replacing four hand-built skeletons changes what four screens look
like while loading, which is the same visual-review problem.

**U9 — migrate 17 `BackHandler`s to `PredictiveBackHandler`.** The finding is right: the
manifest sets `enableOnBackInvokedCallback=true`, and an enabled `BackHandler` suppresses
the system's back preview, so the app opts in and draws nothing. But `PredictiveBackHandler`
is a progress-driven suspend API — it hands you a `Flow<BackEventCompat>` so you can
animate the gesture. Migrating without designing that animation per screen replaces "no
preview" with "a preview that does not move", which is not obviously better. This is
design work with a code deliverable, not a mechanical rename.

**U14 — `core-splashscreen`.** Mechanical to add and it changes what every cold start
looks like. Same reason as the others.
